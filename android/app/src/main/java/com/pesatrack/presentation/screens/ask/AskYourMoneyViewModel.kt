package com.pesatrack.presentation.screens.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.services.ai.AskStreamEvent
import com.pesatrack.services.ai.AskTurn
import com.pesatrack.services.ai.AskYourMoneyClient
import com.pesatrack.services.ai.AskYourMoneyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [AskYourMoneyScreen].
 *
 * Owns:
 *  - the immutable [AskYourMoneyUiState] surfaced as a [StateFlow]
 *  - the 10-turn wire-history buffer (derived from [uiState.messages]
 *    on send — see [buildWireHistory])
 *  - the current turn's stream collector (cancels on new send)
 *  - Pro entitlement observation (short-circuits sends if the user
 *    becomes not-entitled mid-session)
 *
 * Deliberately does NOT own persistence — history disappears on
 * process death per the plan (§3.3). No Room, no DataStore.
 *
 * See plans/ai-pro-phase3-spec.md §7.2 for the ViewModel design and §7.3
 * for the stream event contract.
 */
@HiltViewModel
class AskYourMoneyViewModel @Inject constructor(
    private val repository: AskYourMoneyRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AskYourMoneyUiState())
    val uiState: StateFlow<AskYourMoneyUiState> = _uiState.asStateFlow()

    private var messageIdCounter: Long = 0
    private fun nextId(): Long = ++messageIdCounter

    init {
        // Reflect the current Pro entitlement so the screen can render
        // a friendly "your subscription expired" message and disable the
        // composer if the user becomes non-Pro while the screen is open.
        appPreferences.proState
            .map { it.isEntitled && (it.expiresAtEpochMs ?: 0L) > System.currentTimeMillis() }
            .onEach { entitled ->
                _uiState.update { it.copy(isProEntitled = entitled) }
            }
            .launchIn(viewModelScope)
    }

    /** Called by the composer's TextField every keystroke. */
    fun onComposerChanged(text: String) {
        _uiState.update { it.copy(composerInput = text) }
    }

    /**
     * Send the current composer input as a new user turn. No-op if the
     * composer is blank or a previous turn is still streaming.
     */
    fun onSendClicked() {
        val state = _uiState.value
        val question = state.composerInput.trim()
        if (question.isEmpty() || state.isStreaming) return

        // Snapshot the pre-send history for wire submission BEFORE we
        // add the new user turn, so `history` contains the prior
        // dialogue only — the current question travels in its own
        // field (see AskRequestDto).
        val wireHistory = buildWireHistory(state.messages)

        val userMsg = ChatMessage.User(id = nextId(), text = question)
        val draftAssistant = ChatMessage.Assistant(
            id = nextId(),
            text = "",
            isDraft = true,
            response = null,
        )

        _uiState.update {
            it.copy(
                messages = it.messages + listOf(userMsg, draftAssistant),
                composerInput = "",
                isStreaming = true,
            )
        }

        val draftId = draftAssistant.id
        viewModelScope.launch {
            repository.ask(question = question, history = wireHistory)
                .collect { event -> handleStreamEvent(event, draftId) }
            // The Flow completes after the first Done/Fallback event.
            _uiState.update { it.copy(isStreaming = false) }
        }
    }

    /**
     * Populate the composer with one of the example prompts shown in
     * the empty state. Tapping the same prompt twice just refills the
     * composer — send is a separate action.
     */
    fun onExamplePromptTapped(prompt: String) {
        _uiState.update { it.copy(composerInput = prompt) }
    }

    /**
     * Wipe the in-memory chat history. Overflow menu action. Never
     * touches persistence — nothing was persisted in the first place.
     */
    fun onClearChatClicked() {
        _uiState.update { it.copy(messages = emptyList()) }
    }

    /** Called by the composable once it has shown the snackbar. */
    fun onSnackbarShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun handleStreamEvent(event: AskStreamEvent, draftId: Long) {
        when (event) {
            is AskStreamEvent.TextChunk -> appendTextChunk(draftId, event.delta)
            is AskStreamEvent.Done -> finaliseDone(draftId, event)
            is AskStreamEvent.Fallback -> finaliseFallback(draftId, event)
        }
    }

    private fun appendTextChunk(draftId: Long, delta: String) {
        _uiState.update { state ->
            val newMessages = state.messages.map { msg ->
                if (msg is ChatMessage.Assistant && msg.id == draftId && msg.isDraft) {
                    msg.copy(text = msg.text + delta)
                } else msg
            }
            state.copy(messages = newMessages)
        }
    }

    private fun finaliseDone(draftId: Long, event: AskStreamEvent.Done) {
        _uiState.update { state ->
            val newMessages = state.messages.map { msg ->
                if (msg is ChatMessage.Assistant && msg.id == draftId) {
                    msg.copy(
                        text = event.response.body,
                        isDraft = false,
                        response = event.response,
                    )
                } else msg
            }
            state.copy(messages = newMessages)
        }
    }

    private fun finaliseFallback(draftId: Long, event: AskStreamEvent.Fallback) {
        val snack = when (event.reason) {
            AskYourMoneyClient.REASON_RATE_LIMIT ->
                "You've asked a lot today. Try again in a bit."
            else -> null
        }
        _uiState.update { state ->
            val newMessages = state.messages.map { msg ->
                if (msg is ChatMessage.Assistant && msg.id == draftId) {
                    msg.copy(
                        text = FALLBACK_TEMPLATE,
                        isDraft = false,
                        isFallback = true,
                        response = null,
                    )
                } else msg
            }
            state.copy(messages = newMessages, snackbarMessage = snack)
        }
    }

    /**
     * Convert the visible message list into the wire-format history.
     * Rules:
     *  - Include only finalised turns (skip streaming drafts + fallback
     *    template lines — the model should not see its own "I couldn't
     *    answer that right now" as prior context).
     *  - Cap at the last 10 turns as defence-in-depth (backend also caps).
     */
    private fun buildWireHistory(messages: List<ChatMessage>): List<AskTurn> {
        val wire = messages.mapNotNull { msg ->
            when (msg) {
                is ChatMessage.User -> AskTurn(role = "user", content = msg.text)
                is ChatMessage.Assistant -> when {
                    msg.isDraft -> null
                    msg.isFallback -> null
                    else -> AskTurn(role = "assistant", content = msg.text)
                }
            }
        }
        return if (wire.size <= MAX_HISTORY) wire else wire.takeLast(MAX_HISTORY)
    }

    companion object {
        private const val MAX_HISTORY = 10

        /** Template fallback line, per plans/ai-pro-phase3-spec.md §9. */
        const val FALLBACK_TEMPLATE =
            "I couldn't answer that right now. You could check your Expenses tab " +
                "for a breakdown, or try asking a simpler question."

        /**
         * Three static example prompts shown when the chat has no
         * messages. Chosen to cover the three answer shapes we care
         * about: factual, projection, and top-recipient.
         *
         * TODO(plan): finalise copy in user research (see spec §3.2 open
         * TODO). These are v1 defaults; iterate on real usage.
         */
        val EXAMPLE_PROMPTS = listOf(
            "How much did I spend on Food & Dining this month?",
            "If I cut takeout by half, how much could I save in a year?",
            "Who am I paying the most this month?",
        )
    }
}
