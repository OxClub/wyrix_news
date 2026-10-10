package com.wyrix.news.domain.model

import com.wyrix.news.domain.DuplicateDetector
import java.security.MessageDigest

/** A news item as shown in the app. Only metadata: headline, short excerpt, source, link. */
data class Article(
    val id: String,
    val title: String,
    val url: String,
    val description: String?,
    val sourceName: String,
    val sourceHost: String,
    val publishedAt: Long,
    val imageUrl: String? = null,
    val author: String? = null,
    val category: String? = null,
    val country: String? = null,
    val language: String? = null,
)

/** Provider-independent description of "which news do I want". Built only by NewsQueryEngine. */
data class FeedSpec(
    val key: String,
    val query: String? = null,
    val countries: List<String> = emptyList(),
    val hosts: List<String> = emptyList(),
    val language: String? = null,
    /** One of: today, yesterday, 24h, 48h, 7d, 30d. */
    val window: String = "7d",
    val pageSize: Int = 30,
    val sortByRelevance: Boolean = false,
)

object ArticleIds {
    /** Stable id derived from the normalized article URL. */
    fun forUrl(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(DuplicateDetector.normalizeUrl(url).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

enum class Category(val id: String, val label: String, val keyword: String?) {
    LATEST("latest", "Latest", null),
    POLITICS("politics", "Politics", "politics"),
    BUSINESS("business", "Business", "business"),
    ECONOMY("economy", "Economy", "economy"),
    MARKETS("markets", "Markets", "stock market"),
    TECHNOLOGY("technology", "Technology", "technology"),
    SCIENCE("science", "Science", "science"),
    HEALTH("health", "Health", "health"),
    EDUCATION("education", "Education", "education"),
    SPORTS("sports", "Sports", "sports"),
    CRICKET("cricket", "Cricket", "cricket"),
    FOOTBALL("football", "Football", "football"),
    ENTERTAINMENT("entertainment", "Entertainment", "entertainment"),
    BOLLYWOOD("bollywood", "Bollywood", "bollywood"),
    LIFESTYLE("lifestyle", "Lifestyle", "lifestyle"),
    TRAVEL("travel", "Travel", "travel"),
    AUTO("auto", "Auto", "automobile"),
    ENVIRONMENT("environment", "Environment", "environment"),
    CLIMATE("climate", "Climate", "climate"),
    WEATHER("weather", "Weather", "weather"),
    CRIME("crime", "Crime", "crime"),
    DEFENCE("defence", "Defence", "defence"),
    SPACE("space", "Space", "space"),
    STARTUPS("startups", "Startups", "startup"),
    JOBS("jobs", "Jobs", "jobs"),
    AGRICULTURE("agriculture", "Agriculture", "agriculture"),
    GOVERNMENT("government", "Government", "government"),
    FINANCE("finance", "Finance", "finance"),
    PERSONAL_FINANCE("personal-finance", "Personal Finance", "personal finance"),
    OPINION("opinion", "Opinion", "opinion");

    companion object {
        fun fromId(id: String): Category? = entries.firstOrNull { it.id == id }

        val stateCategories = listOf(
            LATEST, POLITICS, BUSINESS, SPORTS, EDUCATION, TECHNOLOGY, ENTERTAINMENT, HEALTH, CRIME,
        )
        val countryCategories = listOf(
            LATEST, POLITICS, BUSINESS, TECHNOLOGY, SPORTS, ENTERTAINMENT, HEALTH, SCIENCE,
        )
    }
}

data class IndiaRegion(
    val id: String,
    val name: String,
    val isUnionTerritory: Boolean = false,
    /** Words sent to the provider. All words must appear in an article, so keep it short. */
    val queryTerm: String = name,
)

object IndiaRegions {
    val states: List<IndiaRegion> = listOf(
        IndiaRegion("andhra-pradesh", "Andhra Pradesh"),
        IndiaRegion("arunachal-pradesh", "Arunachal Pradesh"),
        IndiaRegion("assam", "Assam"),
        IndiaRegion("bihar", "Bihar"),
        IndiaRegion("chhattisgarh", "Chhattisgarh"),
        IndiaRegion("goa", "Goa"),
        IndiaRegion("gujarat", "Gujarat"),
        IndiaRegion("haryana", "Haryana"),
        IndiaRegion("himachal-pradesh", "Himachal Pradesh"),
        IndiaRegion("jharkhand", "Jharkhand"),
        IndiaRegion("karnataka", "Karnataka"),
        IndiaRegion("kerala", "Kerala"),
        IndiaRegion("madhya-pradesh", "Madhya Pradesh"),
        IndiaRegion("maharashtra", "Maharashtra"),
        IndiaRegion("manipur", "Manipur"),
        IndiaRegion("meghalaya", "Meghalaya"),
        IndiaRegion("mizoram", "Mizoram"),
        IndiaRegion("nagaland", "Nagaland"),
        IndiaRegion("odisha", "Odisha"),
        IndiaRegion("punjab", "Punjab"),
        IndiaRegion("rajasthan", "Rajasthan"),
        IndiaRegion("sikkim", "Sikkim"),
        IndiaRegion("tamil-nadu", "Tamil Nadu"),
        IndiaRegion("telangana", "Telangana"),
        IndiaRegion("tripura", "Tripura"),
        IndiaRegion("uttar-pradesh", "Uttar Pradesh"),
        IndiaRegion("uttarakhand", "Uttarakhand"),
        IndiaRegion("west-bengal", "West Bengal"),
    )

    val unionTerritories: List<IndiaRegion> = listOf(
        IndiaRegion("andaman-nicobar", "Andaman and Nicobar Islands", true, "Andaman"),
        IndiaRegion("chandigarh", "Chandigarh", true),
        IndiaRegion("dnhdd", "Dadra and Nagar Haveli and Daman and Diu", true, "Daman"),
        IndiaRegion("delhi", "Delhi", true),
        IndiaRegion("jammu-kashmir", "Jammu and Kashmir", true, "Jammu Kashmir"),
        IndiaRegion("ladakh", "Ladakh", true),
        IndiaRegion("lakshadweep", "Lakshadweep", true),
        IndiaRegion("puducherry", "Puducherry", true),
    )

    val all: List<IndiaRegion> = states + unionTerritories

    fun byId(id: String): IndiaRegion? = all.firstOrNull { it.id == id }
}

enum class WorldRegion(val label: String) {
    ASIA("Asia"),
    EUROPE("Europe"),
    NORTH_AMERICA("North America"),
    SOUTH_AMERICA("South America"),
    AFRICA("Africa"),
    OCEANIA("Oceania"),
    MIDDLE_EAST("Middle East"),
}

data class WorldCountry(val iso: String, val name: String, val region: WorldRegion)

object WorldCountries {
    val all: List<WorldCountry> = listOf(
        WorldCountry("US", "United States", WorldRegion.NORTH_AMERICA),
        WorldCountry("GB", "United Kingdom", WorldRegion.EUROPE),
        WorldCountry("CA", "Canada", WorldRegion.NORTH_AMERICA),
        WorldCountry("AU", "Australia", WorldRegion.OCEANIA),
        WorldCountry("NZ", "New Zealand", WorldRegion.OCEANIA),
        WorldCountry("CN", "China", WorldRegion.ASIA),
        WorldCountry("JP", "Japan", WorldRegion.ASIA),
        WorldCountry("KR", "South Korea", WorldRegion.ASIA),
        WorldCountry("RU", "Russia", WorldRegion.EUROPE),
        WorldCountry("UA", "Ukraine", WorldRegion.EUROPE),
        WorldCountry("DE", "Germany", WorldRegion.EUROPE),
        WorldCountry("FR", "France", WorldRegion.EUROPE),
        WorldCountry("IT", "Italy", WorldRegion.EUROPE),
        WorldCountry("ES", "Spain", WorldRegion.EUROPE),
        WorldCountry("BR", "Brazil", WorldRegion.SOUTH_AMERICA),
        WorldCountry("MX", "Mexico", WorldRegion.NORTH_AMERICA),
        WorldCountry("ZA", "South Africa", WorldRegion.AFRICA),
        WorldCountry("SA", "Saudi Arabia", WorldRegion.MIDDLE_EAST),
        WorldCountry("AE", "United Arab Emirates", WorldRegion.MIDDLE_EAST),
        WorldCountry("IL", "Israel", WorldRegion.MIDDLE_EAST),
        WorldCountry("IR", "Iran", WorldRegion.MIDDLE_EAST),
        WorldCountry("TR", "Turkey", WorldRegion.MIDDLE_EAST),
        WorldCountry("SG", "Singapore", WorldRegion.ASIA),
        WorldCountry("ID", "Indonesia", WorldRegion.ASIA),
        WorldCountry("MY", "Malaysia", WorldRegion.ASIA),
        WorldCountry("TH", "Thailand", WorldRegion.ASIA),
        WorldCountry("BD", "Bangladesh", WorldRegion.ASIA),
        WorldCountry("PK", "Pakistan", WorldRegion.ASIA),
        WorldCountry("NP", "Nepal", WorldRegion.ASIA),
        WorldCountry("LK", "Sri Lanka", WorldRegion.ASIA),
    )

    val popularIsoCodes: List<String> = listOf("US", "GB", "CA", "AU", "DE", "FR", "JP", "BR")

    val popular: List<WorldCountry> get() = popularIsoCodes.mapNotNull { byIso(it) }

    fun byIso(iso: String): WorldCountry? = all.firstOrNull { it.iso == iso }

    fun inRegion(region: WorldRegion): List<WorldCountry> = all.filter { it.region == region }
}
