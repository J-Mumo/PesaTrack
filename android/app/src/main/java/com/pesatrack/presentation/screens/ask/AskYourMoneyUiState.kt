package com.pesatrack.presentation.screens.ask

import com.pesatrack.services.ai.AskResponse

/**
 * UI state for [AskYourMoneyScreen].
 *
 * Immutable — every mutation goes through the ViewModel's
 * `MutableStateFlow.update {}`. See [AskYourMoneyViewModel] for the
 * transitions.
 *
 * The [messages] list is the visible chat history — user turns and
 * assistant turns interleaved in chronological order (oldest first).
 * The tail element is the *streaming* assistant bubble while a turn is
 * in flight; on `Done` we finalise it, on `Fallback` we swap in the
 * template line.
 */
data class AskYourMoneyUiState(
    /** Chronological chat history — user + assistant turns. */
    val messages: List<ChatMessage> = emptyList(),

    /** Current composer input. Empty when nothing is being typed. */
    val composerInput: String = "",

    /**
     * True while a turn is in flight (from send-tap through to Done or
     * Fallback). Blocks the composer's send button so users can't fire
     * a second turn while the first is streaming.
     */
    val isStreaming: Boolean = false,

    /**
     * One-shot snackbar text set by the ViewModel when the turn hits a
     * user-facing terminal state that isn't a chat message —
     * currently only rate limit ("You've asked a lot today…"). Cleared
     * once the composable acknowledges via [AskYourMoneyViewModel.onSnackbarShown].
     */
    val snackbarMessage: String? = null,

    /**
     * Whether the user is entitled to Pro. Read from the Pro entitlement
     * state; the ViewModel short-circuits `send` to a `not_entitled`
     * fallback if this flips to false while the screen is open (rare —
     * subscription expired mid-session).
     */
    val isProEntitled: Boolean = false,
)

/**
 * A single visible chat turn. Sealed so the Compose bubble composables
 * can `when`-branch cleanly.
 */
sealed interface ChatMessage {
    val id: Long

    /** A user's typed question. */
    data class User(
        override val id: Long,
        val text: String,
    ) : ChatMessage

    /**
     * An assistant reply that is still streaming or already finalised.
     *
     * While [isDraft] is true, [text] contains the tokens received so
     * far (may end mid-word) and the [response] fields (assumptions,
     * action, chart) are all null. On Done, [isDraft] flips to false,
     * [text] becomes the finalised body from [AskResponse.body], and
     * the response metadata is populated.
     */
    data class Assistant(
        override val id: Long,
        val text: String,
        val isDraft: Boolean = true,
        val response: AskResponse? = null,
        /**
         * True when this bubble is the deterministic template line
         * ("I couldn't answer that right now…") shown on any fallback
         * path. Rendered in a subtly muted style vs a normal assistant
         * bubble.
         */
        val isFallback: Boolean = false,
    ) : ChatMessage
}
