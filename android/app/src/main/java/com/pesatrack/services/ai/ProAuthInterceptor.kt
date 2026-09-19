package com.pesatrack.services.ai

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * OkHttp interceptor that gates every AI-Pro-backend request on the
 * user's current entitlement.
 *
 * Path policy (see plans/ai-pro-phase1-spec.md §3.4):
 *
 *  | Path                         | Auth required | Behavior when token missing |
 *  |------------------------------|---------------|-----------------------------|
 *  | `/health`                    | no            | dispatch as-is              |
 *  | `/billing/verify`            | no *          | dispatch as-is              |
 *  | `/billing/entitlement`       | yes           | **synthetic 401**           |
 *  | any `/ai/…`                  | yes           | **synthetic 401**           |
 *
 *  \* `/billing/verify` carries the purchase token in the request body
 *  (the caller isn't authenticated yet — this is the call that establishes
 *  the entitlement server-side).
 *
 * "Synthetic 401" means the interceptor returns a fully-formed HTTP 401
 * `Response` **without ever hitting the wire**. This is defence-in-depth:
 * the backend also returns 401 on missing/unknown tokens, but avoiding
 * the round-trip when we already know the client has no entitlement
 * saves latency, battery, and a log line at the server.
 *
 * Reads on [PurchaseTokenProvider.currentToken] MUST be synchronous — see
 * that interface's KDoc.
 */
class ProAuthInterceptor(
    private val tokenProvider: PurchaseTokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath // starts with '/', e.g. '/billing/entitlement'

        if (!needsAuth(path)) {
            return chain.proceed(request)
        }

        val token = tokenProvider.currentToken()
        if (token.isNullOrBlank()) {
            return syntheticUnauthorized(request)
        }

        val authorized = request.newBuilder()
            .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$token")
            .build()
        return chain.proceed(authorized)
    }

    companion object {
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer "

        /**
         * Pure decision helper — exposed at package scope so
         * `ProAuthInterceptorTest` can lock the policy without spinning
         * up MockWebServer.
         */
        internal fun needsAuth(encodedPath: String): Boolean {
            // encodedPath always starts with '/' for absolute URLs (OkHttp
            // guarantees). Strip a single leading '/' before matching.
            val p = encodedPath.trimStart('/')
            return when {
                p.startsWith("ai/") || p == "ai" -> true
                p == "billing/entitlement" -> true
                else -> false
            }
        }

        private fun syntheticUnauthorized(request: okhttp3.Request): Response {
            val body = """{"error":"no_entitlement","source":"client_interceptor"}"""
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized (no local entitlement)")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }
}
