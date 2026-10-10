package com.wyrix.news.domain

import com.wyrix.news.domain.model.Category
import com.wyrix.news.domain.model.IndiaRegions
import com.wyrix.news.domain.model.WorldCountries
import com.wyrix.news.testArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsQueryEngineTest {
    @Test
    fun stateAndCategoryBecomeEssentialWordsOnly() {
        val rajasthan = IndiaRegions.byId("rajasthan")!!
        val spec = NewsQueryEngine.indiaRegion(rajasthan, Category.POLITICS, "en")
        assertEquals("Rajasthan politics", spec.query)
        assertEquals("en", spec.language)
        assertTrue(spec.countries.isEmpty())
    }

    @Test
    fun latestCategoryHasNoExtraWord() {
        val spec = NewsQueryEngine.indiaRegion(IndiaRegions.byId("goa")!!, Category.LATEST, null)
        assertEquals("Goa", spec.query)
    }

    @Test
    fun unionTerritoryUsesShortQueryTerm() {
        val spec = NewsQueryEngine.indiaRegion(IndiaRegions.byId("dnhdd")!!, Category.LATEST, "en")
        assertEquals("Daman", spec.query)
    }

    @Test
    fun countryFeedFiltersByIsoCode() {
        val spec = NewsQueryEngine.country(WorldCountries.byIso("JP")!!, Category.BUSINESS, "en")
        assertEquals(listOf("JP"), spec.countries)
        assertEquals("business", spec.query)
    }

    @Test
    fun searchIsTrimmedAndSortedByRelevance() {
        val spec = NewsQueryEngine.search("  climate   change ", "en")
        assertEquals("climate change", spec.query)
        assertTrue(spec.sortByRelevance)
    }

    @Test
    fun keysDifferByLanguageAndCategory() {
        val a = NewsQueryEngine.category(Category.SPORTS, "en").key
        val b = NewsQueryEngine.category(Category.SPORTS, null).key
        val c = NewsQueryEngine.category(Category.HEALTH, "en").key
        assertTrue(a != b && a != c)
    }

    @Test
    fun routesResolveToSpecs() {
        assertEquals("india", NewsQueryEngine.forRoute("india", "-", "en").key.substringBefore('|'))
        assertEquals("sports", NewsQueryEngine.forRoute("category", "sports", "en").query)
        assertEquals("Ayodhya", NewsQueryEngine.forRoute("topic", "Ayodhya", "en").query)
    }

    @Test
    fun allRegionsAndCountriesAreUnique() {
        assertEquals(36, IndiaRegions.all.size)
        assertEquals(IndiaRegions.all.size, IndiaRegions.all.map { it.id }.toSet().size)
        assertEquals(WorldCountries.all.size, WorldCountries.all.map { it.iso }.toSet().size)
        assertNotNull(Category.fromId("personal-finance"))
        assertNull(Category.fromId("nope"))
    }
}

class DuplicateDetectorTest {
    @Test
    fun normalizeUrlDropsTrackingAndWww() {
        val a = DuplicateDetector.normalizeUrl("https://www.Example.com/a/b/?utm_source=x&id=7#top")
        val b = DuplicateDetector.normalizeUrl("http://example.com/a/b?id=7")
        assertEquals(a, b)
        assertEquals("example.com/a/b?id=7", a)
    }

    @Test
    fun sameUrlIsRemoved() {
        val list = listOf(
            testArticle(1, url = "https://a.com/x?utm_medium=1"),
            testArticle(2, url = "https://www.a.com/x"),
        )
        assertEquals(1, DuplicateDetector.dedupe(list).size)
    }

    @Test
    fun nearIdenticalLongHeadlinesAreMerged() {
        val list = listOf(
            testArticle(1, title = "Heavy rain floods several districts in northern state overnight"),
            testArticle(2, title = "Heavy rain floods several districts in northern state overnight!"),
            testArticle(3, title = "Stock markets close higher as banks rally strongly"),
        )
        assertEquals(2, DuplicateDetector.dedupe(list).size)
    }

    @Test
    fun shortHeadlinesAreNotMergedByTitleAlone() {
        val list = listOf(testArticle(1, title = "Live updates"), testArticle(2, title = "Live updates"))
        assertEquals(2, DuplicateDetector.dedupe(list).size)
    }

    @Test
    fun similarityIsSymmetricAndBounded() {
        val s = DuplicateDetector.similarity("a b c d", "a b c e")
        assertTrue(s > 0.0 && s < 1.0)
        assertEquals(s, DuplicateDetector.similarity("a b c e", "a b c d"), 0.0)
    }
}

class RankerAndBreakingTest {
    private val now = 10_000_000L

    @Test
    fun freshestComesFirst() {
        val old = testArticle(1, publishedAt = now - 5 * 3_600_000L)
        val fresh = testArticle(2, publishedAt = now - 60_000L)
        val ranked = ArticleRanker { now }.rank(listOf(old, fresh))
        assertEquals(fresh.id, ranked.first().id)
    }

    @Test
    fun preferenceBoostsMatchingArticle() {
        val plain = testArticle(1, title = "Generic story about something unrelated entirely", publishedAt = now - 60_000L)
        val liked = testArticle(2, title = "Rajasthan monsoon arrives early this season", publishedAt = now - 3 * 3_600_000L)
        val ranked = ArticleRanker { now }.rank(listOf(plain, liked), setOf("rajasthan"))
        assertEquals(liked.id, ranked.first().id)
    }

    @Test
    fun noPublisherRunLongerThanTwoWhenAlternativesExist() {
        val sameHost = (1..5).map { testArticle(it, host = "one.example.com", publishedAt = now - it * 1000L) }
        val other = (6..7).map { testArticle(it, host = "two.example.com", publishedAt = now - it * 1000L) }
        val ranked = ArticleRanker { now }.rank(sameHost + other)
        var run = 1
        var maxRun = 1
        for (i in 1 until 4) {
            run = if (ranked[i].sourceHost == ranked[i - 1].sourceHost) run + 1 else 1
            maxRun = maxOf(maxRun, run)
        }
        assertTrue(maxRun <= 2)
    }

    @Test
    fun breakingOnlyWithinWindow() {
        val recent = testArticle(1, publishedAt = now - 10 * 60_000L)
        val stale = testArticle(2, publishedAt = now - 3 * 3_600_000L)
        assertTrue(BreakingPolicy.isBreaking(recent, now))
        assertFalse(BreakingPolicy.isBreaking(stale, now))
    }
}
