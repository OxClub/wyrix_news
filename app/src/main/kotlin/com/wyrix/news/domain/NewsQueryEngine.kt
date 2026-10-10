package com.wyrix.news.domain

import com.wyrix.news.domain.model.Category
import com.wyrix.news.domain.model.FeedSpec
import com.wyrix.news.domain.model.IndiaRegion
import com.wyrix.news.domain.model.WorldCountry

/**
 * The single place where "Rajasthan + Politics" becomes a provider query.
 *
 * FreeNewsAPI.ai matches articles that contain ALL query words, so we send only the essential
 * words ("Rajasthan politics") and never filler such as "latest news"; freshness comes from
 * sorting by date inside a time window instead.
 */
object NewsQueryEngine {
    private val WORLD_FEED_COUNTRIES = listOf("US", "GB", "CA", "AU", "DE", "FR", "JP", "BR", "ZA", "SG")

    fun latest(language: String?) = FeedSpec(
        key = key("latest", language),
        language = language,
        window = "48h",
        pageSize = 50,
    )

    fun breaking(language: String?) = FeedSpec(
        key = key("breaking", language),
        language = language,
        window = "24h",
        pageSize = 30,
    )

    fun india(language: String?) = FeedSpec(
        key = key("india", language),
        countries = listOf("IN"),
        language = language,
        window = "48h",
    )

    fun world(language: String?) = FeedSpec(
        key = key("world", language),
        countries = WORLD_FEED_COUNTRIES,
        language = language,
        window = "48h",
    )

    fun category(category: Category, language: String?): FeedSpec {
        val keyword = category.keywordFor(language) ?: return latest(language)
        return FeedSpec(
            key = key("category", category.id, language),
            query = keyword,
            language = language,
        )
    }

    fun indiaRegion(region: IndiaRegion, category: Category, language: String?): FeedSpec {
        val query = listOfNotNull(region.queryTermFor(language), category.keywordFor(language)).joinToString(" ")
        return FeedSpec(
            key = key("state", region.id, category.id, language),
            query = query,
            language = language,
        )
    }

    fun country(country: WorldCountry, category: Category, language: String?) = FeedSpec(
        key = key("country", country.iso, category.id, language),
        query = category.keywordFor(language),
        countries = listOf(country.iso),
        language = language,
    )

    fun topic(topic: String, language: String?): FeedSpec {
        val clean = topic.trim().take(MAX_QUERY_LENGTH)
        return FeedSpec(
            key = key("topic", clean.lowercase(), language),
            query = clean,
            language = language,
        )
    }

    fun search(query: String, language: String?): FeedSpec {
        val clean = query.trim().replace(Regex("\\s+"), " ").take(MAX_QUERY_LENGTH)
        return FeedSpec(
            key = key("search", clean.lowercase(), language),
            query = clean,
            language = language,
            window = "30d",
            pageSize = 20,
            sortByRelevance = true,
        )
    }

    /** Resolves the generic "feed/{kind}/{arg}" navigation route. */
    fun forRoute(kind: String, arg: String, language: String?): FeedSpec = when (kind) {
        "breaking" -> breaking(language)
        "india" -> india(language)
        "world" -> world(language)
        "category" -> category(Category.fromId(arg) ?: Category.LATEST, language)
        "topic" -> topic(arg, language)
        else -> latest(language)
    }

    private fun key(vararg parts: String?): String =
        parts.filterNotNull().joinToString("|").lowercase()

    private const val MAX_QUERY_LENGTH = 100
}
