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

enum class Category(
    val id: String,
    val label: String,
    val keyword: String?,
    val labelHi: String,
    val keywordHi: String?,
) {
    LATEST("latest", "Latest", null, "ताज़ा खबरें", null),
    POLITICS("politics", "Politics", "politics", "राजनीति", "राजनीति"),
    BUSINESS("business", "Business", "business", "व्यापार", "व्यापार"),
    ECONOMY("economy", "Economy", "economy", "अर्थव्यवस्था", "अर्थव्यवस्था"),
    MARKETS("markets", "Markets", "stock market", "शेयर बाज़ार", "शेयर बाजार"),
    TECHNOLOGY("technology", "Technology", "technology", "तकनीक", "तकनीक"),
    SCIENCE("science", "Science", "science", "विज्ञान", "विज्ञान"),
    HEALTH("health", "Health", "health", "स्वास्थ्य", "स्वास्थ्य"),
    EDUCATION("education", "Education", "education", "शिक्षा", "शिक्षा"),
    SPORTS("sports", "Sports", "sports", "खेल", "खेल"),
    CRICKET("cricket", "Cricket", "cricket", "क्रिकेट", "क्रिकेट"),
    FOOTBALL("football", "Football", "football", "फुटबॉल", "फुटबॉल"),
    ENTERTAINMENT("entertainment", "Entertainment", "entertainment", "मनोरंजन", "मनोरंजन"),
    BOLLYWOOD("bollywood", "Bollywood", "bollywood", "बॉलीवुड", "बॉलीवुड"),
    LIFESTYLE("lifestyle", "Lifestyle", "lifestyle", "जीवनशैली", "जीवनशैली"),
    TRAVEL("travel", "Travel", "travel", "यात्रा", "यात्रा"),
    AUTO("auto", "Auto", "automobile", "ऑटो", "ऑटोमोबाइल"),
    ENVIRONMENT("environment", "Environment", "environment", "पर्यावरण", "पर्यावरण"),
    CLIMATE("climate", "Climate", "climate", "जलवायु", "जलवायु"),
    WEATHER("weather", "Weather", "weather", "मौसम", "मौसम"),
    CRIME("crime", "Crime", "crime", "अपराध", "अपराध"),
    DEFENCE("defence", "Defence", "defence", "रक्षा", "रक्षा"),
    SPACE("space", "Space", "space", "अंतरिक्ष", "अंतरिक्ष"),
    STARTUPS("startups", "Startups", "startup", "स्टार्टअप", "स्टार्टअप"),
    JOBS("jobs", "Jobs", "jobs", "नौकरी", "नौकरी"),
    AGRICULTURE("agriculture", "Agriculture", "agriculture", "कृषि", "कृषि"),
    GOVERNMENT("government", "Government", "government", "सरकार", "सरकार"),
    FINANCE("finance", "Finance", "finance", "वित्त", "वित्त"),
    PERSONAL_FINANCE("personal-finance", "Personal Finance", "personal finance", "निजी वित्त", "निजी वित्त"),
    OPINION("opinion", "Opinion", "opinion", "राय", "राय");

    fun labelFor(hindi: Boolean): String = if (hindi) labelHi else label

    /** Search word sent to the provider; Hindi articles need Hindi words. */
    fun keywordFor(language: String?): String? = if (language == "hi") keywordHi else keyword

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
    val nameHi: String,
    val isUnionTerritory: Boolean = false,
    /** Words sent to the provider. All words must appear in an article, so keep them short. */
    val queryTerm: String = name,
    val queryTermHi: String = nameHi,
) {
    fun nameFor(hindi: Boolean): String = if (hindi) nameHi else name
    fun queryTermFor(language: String?): String = if (language == "hi") queryTermHi else queryTerm
}

object IndiaRegions {
    val states: List<IndiaRegion> = listOf(
        IndiaRegion("andhra-pradesh", "Andhra Pradesh", "आंध्र प्रदेश"),
        IndiaRegion("arunachal-pradesh", "Arunachal Pradesh", "अरुणाचल प्रदेश"),
        IndiaRegion("assam", "Assam", "असम"),
        IndiaRegion("bihar", "Bihar", "बिहार"),
        IndiaRegion("chhattisgarh", "Chhattisgarh", "छत्तीसगढ़"),
        IndiaRegion("goa", "Goa", "गोवा"),
        IndiaRegion("gujarat", "Gujarat", "गुजरात"),
        IndiaRegion("haryana", "Haryana", "हरियाणा"),
        IndiaRegion("himachal-pradesh", "Himachal Pradesh", "हिमाचल प्रदेश"),
        IndiaRegion("jharkhand", "Jharkhand", "झारखंड"),
        IndiaRegion("karnataka", "Karnataka", "कर्नाटक"),
        IndiaRegion("kerala", "Kerala", "केरल"),
        IndiaRegion("madhya-pradesh", "Madhya Pradesh", "मध्य प्रदेश"),
        IndiaRegion("maharashtra", "Maharashtra", "महाराष्ट्र"),
        IndiaRegion("manipur", "Manipur", "मणिपुर"),
        IndiaRegion("meghalaya", "Meghalaya", "मेघालय"),
        IndiaRegion("mizoram", "Mizoram", "मिजोरम"),
        IndiaRegion("nagaland", "Nagaland", "नागालैंड"),
        IndiaRegion("odisha", "Odisha", "ओडिशा"),
        IndiaRegion("punjab", "Punjab", "पंजाब"),
        IndiaRegion("rajasthan", "Rajasthan", "राजस्थान"),
        IndiaRegion("sikkim", "Sikkim", "सिक्किम"),
        IndiaRegion("tamil-nadu", "Tamil Nadu", "तमिलनाडु"),
        IndiaRegion("telangana", "Telangana", "तेलंगाना"),
        IndiaRegion("tripura", "Tripura", "त्रिपुरा"),
        IndiaRegion("uttar-pradesh", "Uttar Pradesh", "उत्तर प्रदेश"),
        IndiaRegion("uttarakhand", "Uttarakhand", "उत्तराखंड"),
        IndiaRegion("west-bengal", "West Bengal", "पश्चिम बंगाल"),
    )

    val unionTerritories: List<IndiaRegion> = listOf(
        IndiaRegion("andaman-nicobar", "Andaman and Nicobar Islands", "अंडमान और निकोबार द्वीप समूह", true, "Andaman", "अंडमान"),
        IndiaRegion("chandigarh", "Chandigarh", "चंडीगढ़", true),
        IndiaRegion("dnhdd", "Dadra and Nagar Haveli and Daman and Diu", "दादरा और नगर हवेली और दमन और दीव", true, "Daman", "दमन"),
        IndiaRegion("delhi", "Delhi", "दिल्ली", true),
        IndiaRegion("jammu-kashmir", "Jammu and Kashmir", "जम्मू और कश्मीर", true, "Jammu Kashmir", "जम्मू कश्मीर"),
        IndiaRegion("ladakh", "Ladakh", "लद्दाख", true),
        IndiaRegion("lakshadweep", "Lakshadweep", "लक्षद्वीप", true),
        IndiaRegion("puducherry", "Puducherry", "पुदुच्चेरी", true),
    )

    val all: List<IndiaRegion> = states + unionTerritories

    fun byId(id: String): IndiaRegion? = all.firstOrNull { it.id == id }
}

enum class WorldRegion(val label: String, val labelHi: String) {
    ASIA("Asia", "एशिया"),
    EUROPE("Europe", "यूरोप"),
    NORTH_AMERICA("North America", "उत्तरी अमेरिका"),
    SOUTH_AMERICA("South America", "दक्षिण अमेरिका"),
    AFRICA("Africa", "अफ्रीका"),
    OCEANIA("Oceania", "ओशिनिया"),
    MIDDLE_EAST("Middle East", "मध्य पूर्व");

    fun labelFor(hindi: Boolean): String = if (hindi) labelHi else label
}

data class WorldCountry(val iso: String, val name: String, val nameHi: String, val region: WorldRegion) {
    fun nameFor(hindi: Boolean): String = if (hindi) nameHi else name
}

object WorldCountries {
    val all: List<WorldCountry> = listOf(
        WorldCountry("US", "United States", "अमेरिका", WorldRegion.NORTH_AMERICA),
        WorldCountry("GB", "United Kingdom", "ब्रिटेन", WorldRegion.EUROPE),
        WorldCountry("CA", "Canada", "कनाडा", WorldRegion.NORTH_AMERICA),
        WorldCountry("AU", "Australia", "ऑस्ट्रेलिया", WorldRegion.OCEANIA),
        WorldCountry("NZ", "New Zealand", "न्यूज़ीलैंड", WorldRegion.OCEANIA),
        WorldCountry("CN", "China", "चीन", WorldRegion.ASIA),
        WorldCountry("JP", "Japan", "जापान", WorldRegion.ASIA),
        WorldCountry("KR", "South Korea", "दक्षिण कोरिया", WorldRegion.ASIA),
        WorldCountry("RU", "Russia", "रूस", WorldRegion.EUROPE),
        WorldCountry("UA", "Ukraine", "यूक्रेन", WorldRegion.EUROPE),
        WorldCountry("DE", "Germany", "जर्मनी", WorldRegion.EUROPE),
        WorldCountry("FR", "France", "फ्रांस", WorldRegion.EUROPE),
        WorldCountry("IT", "Italy", "इटली", WorldRegion.EUROPE),
        WorldCountry("ES", "Spain", "स्पेन", WorldRegion.EUROPE),
        WorldCountry("BR", "Brazil", "ब्राज़ील", WorldRegion.SOUTH_AMERICA),
        WorldCountry("MX", "Mexico", "मैक्सिको", WorldRegion.NORTH_AMERICA),
        WorldCountry("ZA", "South Africa", "दक्षिण अफ़्रीका", WorldRegion.AFRICA),
        WorldCountry("SA", "Saudi Arabia", "सऊदी अरब", WorldRegion.MIDDLE_EAST),
        WorldCountry("AE", "United Arab Emirates", "संयुक्त अरब अमीरात", WorldRegion.MIDDLE_EAST),
        WorldCountry("IL", "Israel", "इज़राइल", WorldRegion.MIDDLE_EAST),
        WorldCountry("IR", "Iran", "ईरान", WorldRegion.MIDDLE_EAST),
        WorldCountry("TR", "Turkey", "तुर्की", WorldRegion.MIDDLE_EAST),
        WorldCountry("SG", "Singapore", "सिंगापुर", WorldRegion.ASIA),
        WorldCountry("ID", "Indonesia", "इंडोनेशिया", WorldRegion.ASIA),
        WorldCountry("MY", "Malaysia", "मलेशिया", WorldRegion.ASIA),
        WorldCountry("TH", "Thailand", "थाईलैंड", WorldRegion.ASIA),
        WorldCountry("BD", "Bangladesh", "बांग्लादेश", WorldRegion.ASIA),
        WorldCountry("PK", "Pakistan", "पाकिस्तान", WorldRegion.ASIA),
        WorldCountry("NP", "Nepal", "नेपाल", WorldRegion.ASIA),
        WorldCountry("LK", "Sri Lanka", "श्रीलंका", WorldRegion.ASIA),
    )

    val popularIsoCodes: List<String> = listOf("US", "GB", "CA", "AU", "DE", "FR", "JP", "BR")

    val popular: List<WorldCountry> get() = popularIsoCodes.mapNotNull { byIso(it) }

    fun byIso(iso: String): WorldCountry? = all.firstOrNull { it.iso == iso }

    fun inRegion(region: WorldRegion): List<WorldCountry> = all.filter { it.region == region }
}
