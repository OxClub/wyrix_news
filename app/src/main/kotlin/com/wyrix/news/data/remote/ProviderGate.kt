package com.wyrix.news.data.remote

import kotlin.math.min

/**
 * Remembers which providers are cooling down so we never hammer a rate-limited or failing one.
 *  - 429: wait at least 15 minutes (or the server's Retry-After, if longer)
 *  - 401/403: wait 1 hour
 *  - anything else: exponential backoff 30s, 60s, 120s ... capped at 15 minutes
 */
class ProviderGate(private val now: () -> Long = { System.currentTimeMillis() }) {

    private class State {
        var failures = 0
        var blockedUntil = 0L
    }

    private val states = HashMap<String, State>()

    @Synchronized
    fun isAvailable(providerId: String): Boolean = (states[providerId]?.blockedUntil ?: 0L) <= now()

    @Synchronized
    fun recordSuccess(providerId: String) {
        states.remove(providerId)
    }

    @Synchronized
    fun recordFailure(providerId: String, error: ProviderError, retryAfterMs: Long? = null) {
        val state = states.getOrPut(providerId) { State() }
        state.failures += 1
        val delay = when (error) {
            ProviderError.RATE_LIMITED -> maxOf(retryAfterMs ?: 0L, RATE_LIMIT_COOLDOWN_MS)
            ProviderError.UNAUTHORIZED, ProviderError.FORBIDDEN -> AUTH_COOLDOWN_MS
            else -> min(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl (state.failures - 1).coerceAtMost(10))
        }
        state.blockedUntil = now() + delay
    }

    companion object {
        const val BASE_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 15L * 60_000L
        const val RATE_LIMIT_COOLDOWN_MS = 15L * 60_000L
        const val AUTH_COOLDOWN_MS = 60L * 60_000L
    }
}
