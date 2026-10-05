package com.pesatrack.presentation.screens.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pesatrack.data.repository.AskChatHistoryRepository
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.domain.models.AskChatHistoryEntry
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
 * Chat messages are persisted locally in Room and restored when this
 * ViewModel is created. The transcript never leaves the device except
 * for the bounded history attached to an explicit AI question request.
 *
 * See plans/ai-pro-phase3-spec.md §7.2 for the ViewModel design and §7.3
 * for the stream event contract.
 */
@HiltViewModel
class AskYourMoneyViewModel @Inject constructor(
    private val repository: AskYourMoneyRepository,
    private val chatHistoryRepository: AskChatHistoryRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AskYourMoneyUiState())
    val uiState: StateFlow<AskYourMoneyUiState> = _uiState.asStateFlow()

    private var messageIdCounter: Long = 0
    private fun nextId(): Long = ++messageIdCounter
    private var activeAskJob: Job? = null
    private var historyLoadJob: Job? = null

    init {
        historyLoadJob = viewModelScope.launch {
            val savedMessages = runCatching { chatHistoryRepository.loadHistory() }
                .getOrDefault(emptyList())
            messageIdCounter = maxOf(messageIdCounter, savedMessages.maxOfOrNull { it.id } ?: 0L)
            _uiState.update { state ->
                state.copy(
                    messages = savedMessages.map(::toChatMessage),
                    isHistoryLoaded = true,
                )
            }
        }
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
        if (question.isEmpty() || state.isStreaming || !state.isHistoryLoaded || state.isClearingHistory) return

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
        activeAskJob = viewModelScope.launch {
            try {
                persistCurrentHistory()
                repository.ask(question = question, history = wireHistory)
                    .collect { event -> handleStreamEvent(event, draftId) }
                // The Flow completes after the first Done/Fallback event.
                persistCurrentHistory()
            } finally {
                _uiState.update { it.copy(isStreaming = false) }
                activeAskJob = null
            }
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
     * Wipe the visible and locally persisted transcript. Also cancel a
     * response in flight so it cannot repopulate the cleared history.
     */
    fun onClearChatClicked() {
        if (_uiState.value.isClearingHistory) return
        val askJob = activeAskJob
        val loadJob = historyLoadJob
        askJob?.cancel()
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                isStreaming = false,
                isHistoryLoaded = true,
                isClearingHistory = true,
            )
        }
        viewModelScope.launch {
            askJob?.cancelAndJoin()
            loadJob?.cancelAndJoin()
            runCatching { chatHistoryRepository.clearHistory() }
            _uiState.update { it.copy(isClearingHistory = false) }
        }
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
                        fallbackReason = event.reason,
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

    private suspend fun persistCurrentHistory() {
        val stableMessages = _uiState.value.messages.filterNot { it is ChatMessage.Assistant && it.isDraft }
        val entries = stableMessages.mapNotNull { message ->
            when (message) {
                is ChatMessage.User -> AskChatHistoryEntry(
                    id = message.id,
                    role = AskChatHistoryEntry.Role.USER,
                    text = message.text,
                )
                is ChatMessage.Assistant -> AskChatHistoryEntry(
                    id = message.id,
                    role = AskChatHistoryEntry.Role.ASSISTANT,
                    text = message.text,
                    isFallback = message.isFallback,
                    fallbackReason = message.fallbackReason,
                    assumptions = message.response?.assumptions.orEmpty(),
                )
            }
        }
        // Storage failure must not block the chat request or crash the screen.
        runCatching { chatHistoryRepository.saveHistory(entries) }
    }

    private fun toChatMessage(entry: AskChatHistoryEntry): ChatMessage = when (entry.role) {
        AskChatHistoryEntry.Role.USER -> ChatMessage.User(id = entry.id, text = entry.text)
        AskChatHistoryEntry.Role.ASSISTANT -> ChatMessage.Assistant(
            id = entry.id,
            text = entry.text,
            isDraft = false,
            response = if (!entry.isFallback && entry.assumptions.isNotEmpty()) {
                com.pesatrack.services.ai.AskResponse(
                    body = entry.text,
                    assumptions = entry.assumptions,
                    actionLabel = null,
                    actionDeeplink = null,
                    chart = null,
                )
            } else null,
            isFallback = entry.isFallback,
            fallbackReason = entry.fallbackReason,
        )
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
