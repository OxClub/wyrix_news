package com.wyrix.news.data.remote

object SourceNames {
    private val known = mapOf(
        "thehindu.com" to "The Hindu",
        "indianexpress.com" to "The Indian Express",
        "hindustantimes.com" to "Hindustan Times",
        "timesofindia.indiatimes.com" to "The Times of India",
        "economictimes.indiatimes.com" to "The Economic Times",
        "ndtv.com" to "NDTV",
        "livemint.com" to "Mint",
        "bbc.com" to "BBC News",
        "bbc.co.uk" to "BBC News",
        "reuters.com" to "Reuters",
        "theguardian.com" to "The Guardian",
        "aljazeera.com" to "Al Jazeera",
    )

    /** Display name for a publisher host; falls back to the host itself, never a guess. */
    fun forHost(host: String): String {
        val clean = host.lowercase().removePrefix("www.")
        return known[clean] ?: clean
    }
}
