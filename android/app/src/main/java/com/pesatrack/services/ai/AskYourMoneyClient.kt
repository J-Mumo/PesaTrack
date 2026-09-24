package com.pesatrack.services.ai

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * Streaming SSE client for `POST /ai/ask`. Emits [AskStreamEvent]s as
 * frames arrive from the backend, closes cleanly on `event: done`, and
 * silently degrades to [AskStreamEvent.Fallback] on any network or
 * parse failure.
 *
 * ## Contract
 *
 *  - Emits [AskStreamEvent.TextChunk] for each `event: text` frame
 *    (contains the `delta` string ready to append to the streaming
 *    assistant bubble's `bodyDraft`).
 *  - Emits [AskStreamEvent.Done] on `event: done` with `fallback: false`
 *    (contains the finalised [AskResponse]).
 *  - Emits [AskStreamEvent.Fallback] on any other terminal state:
 *    `done` with `fallback: true`, a malformed frame, HTTP error, or
 *    network drop. The [AskStreamEvent.Fallback.reason] is a bucketed
 *    string usable directly as the Firebase `PARAM_REASON` value.
 *  - Never throws to the caller. The `Flow` completes on its own after
 *    the terminal event; the ViewModel just collects.
 *
 * ## Auth
 *
 * The Bearer token is attached by [ProAuthInterceptor] (application
 * interceptor on the `@Named("aiPro") OkHttpClient`). This class does
 * not touch auth headers directly — passing the shared OkHttpClient is
 * enough.
 *
 * ## Why not Retrofit
 *
 * Retrofit's `suspend fun` API is a poor fit for SSE — the response is
 * a long-lived `text/event-stream` that yields frames over time, not a
 * single deserialised body. OkHttp's `EventSources.createFactory(...)`
 * gives us exactly the right abstraction and slots into the existing
 * `@Named("aiPro") OkHttpClient` from
 * [com.pesatrack.di.AiHttpModule.provideOkHttpClient], which already
 * carries the auth interceptor + HttpLoggingInterceptor (debug only) +
 * timeouts.
 *
 * See plans/ai-pro-phase3-spec.md §7.3 for the design + code sketch this
 * class is derived from.
 */
class AskYourMoneyClient @javax.inject.Inject constructor(
    @javax.inject.Named("aiPro") private val okHttp: OkHttpClient,
    @javax.inject.Named("aiPro") private val moshi: Moshi,
) {

    private val requestAdapter: JsonAdapter<AskRequestDto> by lazy {
        // serializeNulls() so nullable fields on the wire go out as
        // `"field": null` rather than being dropped. This is the exact
        // discipline we shipped in Phase 2 code 23 for Coach Insight —
        // see the AskWireContractTest for the regression cover.
        moshi.adapter(AskRequestDto::class.java).serializeNulls()
    }
    private val envelopeAdapter: JsonAdapter<AskDoneEnvelope> by lazy {
        moshi.adapter(AskDoneEnvelope::class.java)
    }
    private val textFrameAdapter: JsonAdapter<TextFrameData> by lazy {
        moshi.adapter(TextFrameData::class.java)
    }
    private val factory = EventSources.createFactory(okHttp)

    /**
     * Open a streaming request and cold-emit [AskStreamEvent]s as
     * frames arrive.
     */
    fun stream(request: AskRequestDto): Flow<AskStreamEvent> = callbackFlow {
        val body = requestAdapter.toJson(request)
            .toRequestBody(JSON_MEDIA_TYPE)
        val httpReq = Request.Builder()
            .url((testBaseUrlOverride ?: BASE_URL) + "ai/ask")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(body)
            .build()

        val listener = object : EventSourceListener() {

            // Set to true once we've emitted a terminal AskStreamEvent
            // (Done or Fallback). Stops us double-emitting when
            // handleDoneFrame's cancel() triggers onClosed on the
            // internal EventSource listener chain.
            @Volatile
            private var terminalEmitted = false

            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.i(TAG, "SSE onOpen: status=${response.code}")
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                when (type) {
                    EVENT_TEXT -> handleTextFrame(data)
                    EVENT_DONE -> handleDoneFrame(data, eventSource)
                    EVENT_ERROR -> emitFallback(REASON_SERVER_ERROR, eventSource)
                    else -> Log.w(TAG, "SSE ignoring unknown event type='$type'")
                }
            }

            private fun handleTextFrame(data: String) {
                val parsed = runCatching { textFrameAdapter.fromJson(data) }.getOrNull()
                val delta = parsed?.delta
                if (delta.isNullOrEmpty()) {
                    // A text frame with no delta is malformed but not
                    // fatal — the backend must be doing something odd
                    // but the eventual done frame will still carry the
                    // full body. Log and continue.
                    Log.w(TAG, "SSE text frame with empty/missing delta: $data")
                    return
                }
                trySend(AskStreamEvent.TextChunk(delta))
            }

            private fun handleDoneFrame(data: String, eventSource: EventSource) {
                val envelope = runCatching { envelopeAdapter.fromJson(data) }.getOrNull()
                if (envelope == null) {
                    Log.w(TAG, "SSE done frame failed to parse: $data")
                    emitFallback(REASON_SCHEMA, eventSource)
                    return
                }
                if (envelope.fallback == true) {
                    // Server chose fallback; propagate the reason bucket.
                    emitFallback(envelope.reason ?: REASON_UNKNOWN, eventSource)
                    return
                }
                val response = envelope.asResponseOrNull()
                if (response == null) {
                    Log.w(TAG, "SSE done frame decoded but required fields missing: $data")
                    emitFallback(REASON_SCHEMA, eventSource)
                    return
                }
                terminalEmitted = true
                trySend(AskStreamEvent.Done(response))
                eventSource.cancel()
                close()
            }

            private fun emitFallback(reason: String, eventSource: EventSource) {
                terminalEmitted = true
                trySend(AskStreamEvent.Fallback(reason))
                eventSource.cancel()
                close()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                if (terminalEmitted) {
                    // We already told the collector how the turn ended
                    // (Done or Fallback). Anything after is post-mortem
                    // — log it but don't confuse the ViewModel with a
                    // second terminal event.
                    Log.d(TAG, "SSE onFailure after terminal — ignoring")
                    close()
                    return
                }
                val status = response?.code
                val reason = when {
                    // 429 = rate limit. The ViewModel should surface a
                    // dedicated snackbar for this rather than the
                    // "temporarily unavailable" banner.
                    status == 429 -> REASON_RATE_LIMIT
                    // Any other 4xx points at a client-side bug or an
                    // expired entitlement — bucket as server_error, the
                    // ViewModel treats it identically to other terminal
                    // fails.
                    status != null && status in 400..499 -> REASON_SERVER_ERROR
                    // 5xx or no status = transient / network / provider
                    // failure. Client-side we call it `network` because
                    // that's what the user perceives; the backend's own
                    // log will already have the real cause.
                    else -> REASON_NETWORK
                }
                Log.w(
                    TAG,
                    "SSE onFailure: status=$status, reason=$reason, " +
                        "throwable=${t?.javaClass?.simpleName}: ${t?.message}",
                )
                terminalEmitted = true
                trySend(AskStreamEvent.Fallback(reason))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                // OkHttp calls this when the SSE body ends normally
                // (server closed the connection or EOF on the response
                // body). If a `done` frame was already processed,
                // terminalEmitted is true and this branch is a no-op
                // apart from ensuring the flow completes. If no `done`
                // ever arrived (server closed early — protocol error),
                // synthesise a schema fallback so the collector doesn't
                // hang on a callbackFlow that never terminates.
                Log.d(TAG, "SSE onClosed (terminalEmitted=$terminalEmitted)")
                if (!terminalEmitted) {
                    terminalEmitted = true
                    trySend(AskStreamEvent.Fallback(REASON_SCHEMA))
                }
                close()
            }
        }

        val source = factory.newEventSource(httpReq, listener)
        awaitClose { source.cancel() }
    }

    /**
     * Minimal DTO for a `text` frame's data payload. The server sends
     * `{ "delta": "<chars>" }`; anything else is bucketed as fallback.
     */
    @com.squareup.moshi.JsonClass(generateAdapter = true)
    internal data class TextFrameData(
        val delta: String?,
    )

    companion object {
        private const val TAG = "AskYourMoneyClient"
        private const val BASE_URL = "https://pesatrack-api.jmumo.com/"
        private const val EVENT_TEXT = "text"
        private const val EVENT_DONE = "done"
        private const val EVENT_ERROR = "error"

        /**
         * Test-only override for the target base URL. Set by
         * [AskYourMoneyClientTest] and other unit tests that stand up
         * a local MockWebServer; leave null in production. Not exposed
         * via Hilt / DI on purpose — the app should only ever hit the
         * one real backend.
         */
        internal var testBaseUrlOverride: String? = null

        // Bucketed reason codes — kept in sync with the client-side
        // Firebase telemetry PARAM_REASON enum documented in
        // plans/ai-pro-phase3-spec.md §11.
        const val REASON_NETWORK = "network"
        const val REASON_SCHEMA = "schema"
        const val REASON_SERVER_ERROR = "server_error"
        const val REASON_RATE_LIMIT = "rate_limit"
        const val REASON_UNKNOWN = "unknown"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Events emitted by [AskYourMoneyClient.stream]. The ViewModel collects
 * this Flow and decides how to render each.
 *
 *  - [TextChunk] arrives many times per response — append to the
 *    streaming assistant bubble's draft body.
 *  - [Done] arrives once at the end of a successful turn — replace the
 *    draft body with the finalised value and attach chart / assumptions
 *    / action.
 *  - [Fallback] arrives once at the end of a failed turn — discard any
 *    already-streamed body text and swap in the deterministic template
 *    line. [Fallback.reason] is the bucketed Firebase param.
 *
 * Exactly one of [Done] or [Fallback] terminates the Flow.
 */
sealed interface AskStreamEvent {
    data class TextChunk(val delta: String) : AskStreamEvent
    data class Done(val response: AskResponse) : AskStreamEvent
    data class Fallback(val reason: String) : AskStreamEvent
}
