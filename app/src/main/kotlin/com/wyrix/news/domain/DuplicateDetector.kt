package com.wyrix.news.domain

import com.wyrix.news.domain.model.Article

object DuplicateDetector {
    private val tokenSplit = Regex("[^\\p{L}\\p{M}\\p{N}]+")
    private val schemeAndWww = Regex("^https?://(www\\.)?", RegexOption.IGNORE_CASE)
    private val trackingParams = setOf("fbclid", "gclid", "igshid", "mc_cid", "mc_eid", "ocid", "cmpid")

    /** Lower-cases the host, drops scheme, "www.", fragment, trailing slash and tracking parameters. */
    fun normalizeUrl(raw: String): String {
        val noFragment = raw.trim().substringBefore('#')
        val base = noFragment.substringBefore('?')
        val query = if (noFragment.contains('?')) noFragment.substringAfter('?') else ""
        val kept = query.split('&')
            .filter { it.isNotEmpty() }
            .filterNot { param ->
                val name = param.substringBefore('=').lowercase()
                name.startsWith("utm_") || name in trackingParams
            }
        val withoutScheme = base.replace(schemeAndWww, "")
        val host = withoutScheme.substringBefore('/').lowercase()
        val path = if (withoutScheme.contains('/')) "/" + withoutScheme.substringAfter('/') else ""
        val cleanPath = path.trimEnd('/')
        return host + cleanPath + if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
    }

    fun tokens(text: String): List<String> =
        text.lowercase().split(tokenSplit).filter { it.isNotEmpty() }

    fun normalizeTitle(title: String): String = tokens(title).joinToString(" ")

    fun similarity(a: String, b: String): Double = jaccard(tokens(a).toSet(), tokens(b).toSet())

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.count { it in b }
        val union = a.size + b.size - intersection
        return intersection.toDouble() / union.toDouble()
    }

    /**
     * Removes repeated stories while keeping the original order.
     * Two articles are duplicates when their normalized URLs match, or when both headlines have at
     * least [MIN_TOKENS_FOR_FUZZY] words and are at least [threshold] similar. Short headlines such
     * as "Live updates" are never merged on title alone.
     */
    fun dedupe(articles: List<Article>, threshold: Double = 0.85): List<Article> {
        val seenUrls = HashSet<String>()
        val keptTokens = ArrayList<Set<String>>()
        val kept = ArrayList<Article>()
        for (article in articles) {
            if (!seenUrls.add(normalizeUrl(article.url))) continue
            val tokens = tokens(article.title).toSet()
            val fuzzyEligible = tokens.size >= MIN_TOKENS_FOR_FUZZY
            if (fuzzyEligible && keptTokens.any { it.size >= MIN_TOKENS_FOR_FUZZY && jaccard(it, tokens) >= threshold }) {
                continue
            }
            kept += article
            keptTokens += tokens
        }
        return kept
    }

    private const val MIN_TOKENS_FOR_FUZZY = 4
}
