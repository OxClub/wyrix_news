package com.wyrix.news.data

import com.wyrix.news.data.remote.FreeNewsParser
import com.wyrix.news.data.remote.ProviderError
import com.wyrix.news.data.remote.ProviderGate
import com.wyrix.news.data.remote.ProviderResult
import com.wyrix.news.data.remote.RssFeedRegistry
import com.wyrix.news.data.remote.SourceNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeNewsParserTest {
    private val now = 1_800_000_000_000L

    @Test
    fun parsesValidArticlesAndSkipsUnusableOnes() {
        val body = """
        {"total": 120, "results": [
          {"title": "First &amp; <b>best</b> story", "url": "https://www.example.com/a?utm_source=x",
           "description": "A short summary.", "published_at": "2026-04-03T04:51:12.000Z",
           "host": "www.example.com", "country": "in", "lang": "en", "extra_field": 1},
          {"title": "No date", "url": "https://example.com/b", "host": "example.com"},
          {"title": "Bad url", "url": "javascript:alert(1)", "published_at": "2026-04-03T04:51:12Z"},
          {"title": "", "url": "https://example.com/c", "published_at": "2026-04-03T04:51:12Z"},
          {"title": "Epoch seconds", "url": "https://example.com/d", "published_at": 1775000000, "host": "example.com"}
        ]}
        """.trimIndent()
        val result = FreeNewsParser.parse(body, now) as ProviderResult.Success
        assertEquals(5, result.rawCount)
        assertEquals(120, result.total)
        assertEquals(2, result.articles.size)
        val first = result.articles[0]
        assertEquals("First & best story", first.title)
        assertEquals("example.com", first.sourceHost)
        assertEquals("en", first.language)
        assertEquals("IN", first.country)
        assertTrue(first.publishedAt <= now)
    }

    @Test
    fun malformedJsonIsReported() {
        val result = FreeNewsParser.parse("not json", now)
        assertEquals(ProviderError.MALFORMED, (result as ProviderResult.Failure).error)
    }

    @Test
    fun missingResultsKeyIsMalformed() {
        val result = FreeNewsParser.parse("""{"error": "oops"}""", now)
        assertEquals(ProviderError.MALFORMED, (result as ProviderResult.Failure).error)
    }

    @Test
    fun emptyResultsIsSuccessWithNoArticles() {
        val result = FreeNewsParser.parse("""{"total": 0, "results": []}""", now) as ProviderResult.Success
        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun futureTimestampsAreClampedToNow() {
        val body = """{"results":[{"title":"Future","url":"https://e.com/f","host":"e.com","published_at":"2999-01-01T00:00:00Z"}]}"""
        val result = FreeNewsParser.parse(body, now) as ProviderResult.Success
        assertEquals(now, result.articles.single().publishedAt)
    }
}

class ProviderGateTest {
    private var now = 0L
    private val gate = ProviderGate { now }

    @Test
    fun rateLimitBlocksForFifteenMinutes() {
        gate.recordFailure("p", ProviderError.RATE_LIMITED)
        assertFalse(gate.isAvailable("p"))
        now = ProviderGate.RATE_LIMIT_COOLDOWN_MS - 1
        assertFalse(gate.isAvailable("p"))
        now = ProviderGate.RATE_LIMIT_COOLDOWN_MS
        assertTrue(gate.isAvailable("p"))
    }

    @Test
    fun retryAfterLongerThanDefaultWins() {
        gate.recordFailure("p", ProviderError.RATE_LIMITED, retryAfterMs = 60 * 60_000L)
        now = ProviderGate.RATE_LIMIT_COOLDOWN_MS + 1
        assertFalse(gate.isAvailable("p"))
    }

    @Test
    fun backoffDoublesAndSuccessResets() {
        gate.recordFailure("p", ProviderError.SERVER)
        now = ProviderGate.BASE_BACKOFF_MS
        assertTrue(gate.isAvailable("p"))
        gate.recordFailure("p", ProviderError.SERVER)
        now += ProviderGate.BASE_BACKOFF_MS * 2 - 1
        assertFalse(gate.isAvailable("p"))
        gate.recordSuccess("p")
        assertTrue(gate.isAvailable("p"))
    }

    @Test
    fun otherProvidersAreUnaffected() {
        gate.recordFailure("a", ProviderError.NETWORK)
        assertTrue(gate.isAvailable("b"))
    }
}

class MiscDataTest {
    @Test
    fun rssRegistryParsesValidEntriesOnly() {
        val feeds = RssFeedRegistry.parse("Example|https://example.com/feed.xml|www.Example.com;bad|http://x|y;;")
        assertEquals(1, feeds.size)
        assertEquals("example.com", feeds[0].host)
        assertTrue(RssFeedRegistry.parse("").isEmpty())
    }

    @Test
    fun sourceNamesFallBackToHost() {
        assertEquals("The Hindu", SourceNames.forHost("www.thehindu.com"))
        assertEquals("unknown-site.example", SourceNames.forHost("www.unknown-site.example"))
    }
}
