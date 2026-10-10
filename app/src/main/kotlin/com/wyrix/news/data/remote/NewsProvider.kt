package com.wyrix.news.data.remote

import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.FeedSpec

enum class ProviderError {
    NETWORK, TIMEOUT, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, RATE_LIMITED, SERVER, MALFORMED,

    /** This provider cannot answer this kind of request (not a failure, simply skipped). */
    UNSUPPORTED,
    UNKNOWN;

    companion object {
        fun fromHttp(code: Int): ProviderError = when (code) {
            401 -> UNAUTHORIZED
            403 -> FORBIDDEN
            404 -> NOT_FOUND
            429 -> RATE_LIMITED
            in 500..599 -> SERVER
            else -> UNKNOWN
        }
    }
}

sealed interface ProviderResult {
    /** [rawCount] is the number of results the provider returned before we dropped unusable ones. */
    data class Success(
        val articles: List<Article>,
        val rawCount: Int = articles.size,
        val total: Int? = null,
    ) : ProviderResult

    data class Failure(val error: ProviderError, val retryAfterMs: Long? = null) : ProviderResult
}

/**
 * One source of real news. Top stories, category, country, region and search requests are all
 * expressed as a [FeedSpec] built by NewsQueryEngine, so providers stay interchangeable.
 */
interface NewsProvider {
    val id: String
    val displayName: String
    suspend fun fetch(spec: FeedSpec, offset: Int = 0): ProviderResult
}
