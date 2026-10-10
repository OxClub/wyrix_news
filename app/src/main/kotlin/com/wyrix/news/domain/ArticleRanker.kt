package com.wyrix.news.domain

import com.wyrix.news.domain.model.Article
import kotlin.math.max

/** Freshness + user preference, then breaks up long runs from a single publisher. */
class ArticleRanker(private val now: () -> Long = { System.currentTimeMillis() }) {

    fun rank(
        articles: List<Article>,
        preferenceTerms: Set<String> = emptySet(),
        maxRun: Int = 2,
    ): List<Article> {
        val t = now()
        val sorted = articles
            .map { it to score(it, t, preferenceTerms) }
            .sortedByDescending { it.second }
            .map { it.first }
        return diversify(sorted, maxRun)
    }

    private fun score(article: Article, now: Long, terms: Set<String>): Double {
        val ageHours = max(0.0, (now - article.publishedAt) / 3_600_000.0)
        val freshness = 1.0 / (1.0 + ageHours / 6.0)
        val text = (article.title + " " + article.description.orEmpty()).lowercase()
        val preference = if (terms.isNotEmpty() && terms.any { it in text }) 0.35 else 0.0
        return freshness + preference
    }

    fun diversify(list: List<Article>, maxRun: Int): List<Article> {
        val remaining = list.toMutableList()
        val out = ArrayList<Article>(list.size)
        while (remaining.isNotEmpty()) {
            val lastHost = out.lastOrNull()?.sourceHost
            val runLength = if (lastHost == null) 0 else out.takeLastWhile { it.sourceHost == lastHost }.size
            val index = if (lastHost != null && runLength >= maxRun) {
                remaining.indexOfFirst { it.sourceHost != lastHost }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }
            out += remaining.removeAt(index)
        }
        return out
    }
}

object BreakingPolicy {
    /** An article is only "breaking" if it was published within this window. */
    const val DEFAULT_MAX_AGE_MS: Long = 60L * 60L * 1000L

    fun isBreaking(article: Article, now: Long, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Boolean =
        article.publishedAt in (now - maxAgeMs)..now
}
