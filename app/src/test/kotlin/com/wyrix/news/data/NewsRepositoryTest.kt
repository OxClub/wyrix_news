package com.wyrix.news.data

import com.wyrix.news.data.remote.NewsProvider
import com.wyrix.news.data.remote.ProviderError
import com.wyrix.news.data.remote.ProviderGate
import com.wyrix.news.data.remote.ProviderResult
import com.wyrix.news.data.repository.ArticleStore
import com.wyrix.news.data.repository.Connectivity
import com.wyrix.news.data.repository.FailureReason
import com.wyrix.news.data.repository.NewsRepository
import com.wyrix.news.data.repository.RefreshResult
import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.FeedSpec
import com.wyrix.news.testArticle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStore : ArticleStore {
    val feeds = HashMap<String, MutableStateFlow<List<Article>>>()
    val fetched = HashMap<String, Long>()
    fun feed(key: String) = feeds.getOrPut(key) { MutableStateFlow(emptyList()) }
    override fun observeFeed(feedKey: String): Flow<List<Article>> = feed(feedKey)
    override suspend fun replaceFeed(feedKey: String, articles: List<Article>, fetchedAt: Long) {
        feed(feedKey).value = articles
        fetched[feedKey] = fetchedAt
    }
    override suspend fun upsert(articles: List<Article>, fetchedAt: Long) = Unit
    override suspend fun getArticle(id: String): Article? = null
    override suspend fun lastFetched(feedKey: String): Long? = fetched[feedKey]
    override suspend fun prune(before: Long) = Unit
}

private class FakeProvider(override val id: String, var result: ProviderResult) : NewsProvider {
    override val displayName = id
    var calls = 0
    override suspend fun fetch(spec: FeedSpec, offset: Int): ProviderResult {
        calls++
        return result
    }
}

class NewsRepositoryTest {
    private var now = 1_000_000L
    private var online = true
    private val store = FakeStore()
    private val spec = FeedSpec(key = "test-feed")

    private fun repo(vararg providers: NewsProvider) = NewsRepository(
        providers = providers.toList(),
        store = store,
        gate = ProviderGate { now },
        connectivity = Connectivity { online },
        clock = { now },
    )

    private fun ok(vararg n: Int) = ProviderResult.Success(n.map { testArticle(it) })

    @Test
    fun fallsBackToSecondProviderWhenFirstFails() = runTest {
        val first = FakeProvider("a", ProviderResult.Failure(ProviderError.SERVER))
        val second = FakeProvider("b", ok(1, 2))
        val result = repo(first, second).refresh(spec)
        assertEquals(RefreshResult.Updated, result)
        assertEquals(2, store.feed(spec.key).value.size)
        assertEquals(1, first.calls)
        assertEquals(1, second.calls)
    }

    @Test
    fun rateLimitedProviderIsNotCalledAgainDuringCooldown() = runTest {
        val limited = FakeProvider("a", ProviderResult.Failure(ProviderError.RATE_LIMITED))
        val backup = FakeProvider("b", ok(1))
        val repository = repo(limited, backup)
        repository.refresh(spec)
        now += 11_000
        repository.refresh(spec, force = true)
        assertEquals(1, limited.calls)
        assertEquals(2, backup.calls)
    }

    @Test
    fun offlineDoesNotCallProviders() = runTest {
        online = false
        val provider = FakeProvider("a", ok(1))
        val result = repo(provider).refresh(spec)
        assertEquals(RefreshResult.Failed(FailureReason.OFFLINE), result)
        assertEquals(0, provider.calls)
    }

    @Test
    fun secondRefreshWithinIntervalIsThrottled() = runTest {
        val provider = FakeProvider("a", ok(1))
        val repository = repo(provider)
        assertEquals(RefreshResult.Updated, repository.refresh(spec))
        now += 30_000
        assertEquals(RefreshResult.Throttled, repository.refresh(spec))
        assertEquals(1, provider.calls)
        now += 31_000
        assertEquals(RefreshResult.Updated, repository.refresh(spec))
        assertEquals(2, provider.calls)
    }

    @Test
    fun failedRefreshKeepsCachedArticles() = runTest {
        val provider = FakeProvider("a", ok(1, 2))
        val repository = repo(provider)
        repository.refresh(spec)
        now += 120_000
        provider.result = ProviderResult.Failure(ProviderError.NETWORK)
        val result = repository.refresh(spec)
        assertTrue(result is RefreshResult.Failed)
        assertEquals(2, store.feed(spec.key).value.size)
    }

    @Test
    fun emptySuccessClearsFeedWhenNothingFailed() = runTest {
        val provider = FakeProvider("a", ok(1))
        val repository = repo(provider)
        repository.refresh(spec)
        now += 120_000
        provider.result = ProviderResult.Success(emptyList())
        assertEquals(RefreshResult.Updated, repository.refresh(spec))
        assertTrue(store.feed(spec.key).value.isEmpty())
    }

    @Test
    fun unsupportedProviderIsSkippedSilently() = runTest {
        val unsupported = FakeProvider("rss", ProviderResult.Failure(ProviderError.UNSUPPORTED))
        val real = FakeProvider("a", ok(1))
        assertEquals(RefreshResult.Updated, repo(unsupported, real).refresh(spec))
        val onlyUnsupported = repo(FakeProvider("rss", ProviderResult.Failure(ProviderError.UNSUPPORTED)))
        assertEquals(
            RefreshResult.Failed(FailureReason.UNAVAILABLE),
            onlyUnsupported.refresh(FeedSpec(key = "other")),
        )
    }
}
