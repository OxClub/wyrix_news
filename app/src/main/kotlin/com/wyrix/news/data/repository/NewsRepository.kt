package com.wyrix.news.data.repository

import com.wyrix.news.data.remote.NewsProvider
import com.wyrix.news.data.remote.ProviderError
import com.wyrix.news.data.remote.ProviderGate
import com.wyrix.news.data.remote.ProviderResult
import com.wyrix.news.domain.DuplicateDetector
import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.FeedSpec
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

enum class FailureReason { OFFLINE, RATE_LIMITED, UNAVAILABLE, MALFORMED }

sealed interface RefreshResult {
    data object Updated : RefreshResult
    data object Throttled : RefreshResult
    data class Failed(val reason: FailureReason) : RefreshResult
}

sealed interface PageResult {
    data class Success(val articles: List<Article>, val hasMore: Boolean) : PageResult
    data class Failure(val reason: FailureReason) : PageResult
}

/**
 * Single entry point for news. Guarantees:
 *  - cache-first: screens observe Room, refresh only updates it
 *  - one in-flight refresh per feed, and a minimum interval between refreshes
 *  - at most [maxConcurrentRequests] outgoing provider calls at once
 *  - providers are tried in order; rate-limited/failing ones cool down (see ProviderGate)
 *  - a failed refresh never clears cached articles
 */
class NewsRepository(
    private val providers: List<NewsProvider>,
    private val store: ArticleStore,
    private val gate: ProviderGate,
    private val connectivity: Connectivity,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val minRefreshIntervalMs: Long = 60_000L,
    private val minForcedIntervalMs: Long = 10_000L,
    maxConcurrentRequests: Int = 3,
) {
    private val permits = Semaphore(maxConcurrentRequests)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val lastRefresh = ConcurrentHashMap<String, Long>()

    fun observe(spec: FeedSpec): Flow<List<Article>> = store.observeFeed(spec.key)

    suspend fun lastUpdated(spec: FeedSpec): Long? = store.lastFetched(spec.key)

    suspend fun article(id: String): Article? = store.getArticle(id)

    suspend fun pruneStale() = store.prune(clock() - STALE_AFTER_MS)

    suspend fun refresh(spec: FeedSpec, force: Boolean = false): RefreshResult {
        val lock = locks.getOrPut(spec.key) { Mutex() }
        return lock.withLock {
            val minInterval = if (force) minForcedIntervalMs else minRefreshIntervalMs
            val last = lastRefresh[spec.key] ?: store.lastFetched(spec.key)
            if (last != null && clock() - last < minInterval) return@withLock RefreshResult.Throttled
            if (!connectivity.isOnline()) return@withLock RefreshResult.Failed(FailureReason.OFFLINE)
            when (val outcome = fetchFromProviders(spec, 0)) {
                is Outcome.Ok -> {
                    val now = clock()
                    store.replaceFeed(spec.key, outcome.articles, now)
                    lastRefresh[spec.key] = now
                    RefreshResult.Updated
                }
                is Outcome.Failed -> RefreshResult.Failed(outcome.reason)
            }
        }
    }

    /** Fetches a page that is not stored as the feed (pagination and search). */
    suspend fun fetchPage(spec: FeedSpec, offset: Int): PageResult {
        if (!connectivity.isOnline()) return PageResult.Failure(FailureReason.OFFLINE)
        return when (val outcome = fetchFromProviders(spec, offset)) {
            is Outcome.Ok -> {
                if (outcome.articles.isNotEmpty()) store.upsert(outcome.articles, clock())
                PageResult.Success(outcome.articles, hasMore = outcome.rawCount >= spec.pageSize)
            }
            is Outcome.Failed -> PageResult.Failure(outcome.reason)
        }
    }

    private sealed interface Outcome {
        data class Ok(val articles: List<Article>, val rawCount: Int) : Outcome
        data class Failed(val reason: FailureReason) : Outcome
    }

    private suspend fun fetchFromProviders(spec: FeedSpec, offset: Int): Outcome = permits.withPermit {
        var sawSuccess = false
        var lastError: ProviderError? = null
        for (provider in providers) {
            if (!gate.isAvailable(provider.id)) continue
            when (val result = provider.fetch(spec, offset)) {
                is ProviderResult.Success -> {
                    gate.recordSuccess(provider.id)
                    sawSuccess = true
                    if (result.articles.isNotEmpty()) {
                        return@withPermit Outcome.Ok(DuplicateDetector.dedupe(result.articles), result.rawCount)
                    }
                }
                is ProviderResult.Failure -> {
                    if (result.error == ProviderError.UNSUPPORTED) continue
                    gate.recordFailure(provider.id, result.error, result.retryAfterMs)
                    lastError = result.error
                }
            }
        }
        val error = lastError
        when {
            error != null -> Outcome.Failed(error.toFailureReason())
            sawSuccess -> Outcome.Ok(emptyList(), 0)
            else -> Outcome.Failed(FailureReason.UNAVAILABLE)
        }
    }

    private fun ProviderError.toFailureReason(): FailureReason = when (this) {
        ProviderError.RATE_LIMITED -> FailureReason.RATE_LIMITED
        ProviderError.MALFORMED -> FailureReason.MALFORMED
        else -> FailureReason.UNAVAILABLE
    }

    companion object {
        const val STALE_AFTER_MS: Long = 14L * 24 * 60 * 60 * 1000
    }
}
