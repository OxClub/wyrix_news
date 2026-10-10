package com.wyrix.news.data.remote

import android.util.Xml
import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.ArticleIds
import com.wyrix.news.domain.model.FeedSpec
import java.io.IOException
import java.io.StringReader
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

data class RssFeed(val name: String, val url: String, val host: String)

object RssFeedRegistry {
    /** Parses "Name|https://feed-url|host;Name2|https://...|host2". Invalid entries are ignored. */
    fun parse(config: String): List<RssFeed> = config.split(';').mapNotNull { entry ->
        val parts = entry.split('|').map { it.trim() }
        if (parts.size == 3 && parts[0].isNotEmpty() && parts[1].startsWith("https://") && parts[2].isNotEmpty()) {
            RssFeed(parts[0], parts[1], parts[2].lowercase().removePrefix("www."))
        } else {
            null
        }
    }
}

/**
 * Fallback provider for publisher RSS 2.0 feeds configured at build time (WYRIX_RSS_FEEDS).
 * With no feeds configured it reports UNSUPPORTED and is skipped. It cannot honour country
 * filters (feeds are not country-tagged), so country requests are skipped as well.
 */
class RssProvider(
    private val client: OkHttpClient,
    private val feeds: List<RssFeed>,
    private val userAgent: String = "Wyrix (Android)",
    private val now: () -> Long = { System.currentTimeMillis() },
) : NewsProvider {

    override val id: String = "rss"
    override val displayName: String = "Publisher RSS feeds"

    override suspend fun fetch(spec: FeedSpec, offset: Int): ProviderResult {
        if (feeds.isEmpty() || spec.countries.isNotEmpty()) return ProviderResult.Failure(ProviderError.UNSUPPORTED)
        if (offset > 0) return ProviderResult.Success(emptyList())
        val selected = if (spec.hosts.isEmpty()) feeds else feeds.filter { it.host in spec.hosts }
        if (selected.isEmpty()) return ProviderResult.Failure(ProviderError.UNSUPPORTED)

        val collected = ArrayList<Article>()
        var anySuccess = false
        var lastError: ProviderError? = null
        for (feed in selected) {
            when (val result = fetchFeed(feed)) {
                is ProviderResult.Success -> {
                    anySuccess = true
                    collected += result.articles
                }
                is ProviderResult.Failure -> lastError = result.error
            }
        }
        if (!anySuccess) return ProviderResult.Failure(lastError ?: ProviderError.UNKNOWN)

        val terms = spec.query?.lowercase()?.split(' ')?.filter { it.isNotBlank() }.orEmpty()
        val matching = collected.filter { article ->
            val text = (article.title + " " + article.description.orEmpty()).lowercase()
            terms.all { it in text }
        }
        val sorted = matching.sortedByDescending { it.publishedAt }.take(spec.pageSize)
        return ProviderResult.Success(sorted, rawCount = matching.size)
    }

    private suspend fun fetchFeed(feed: RssFeed): ProviderResult {
        val request = Request.Builder().url(feed.url).header("User-Agent", userAgent).build()
        val response = try {
            client.newCall(request).await()
        } catch (e: SocketTimeoutException) {
            return ProviderResult.Failure(ProviderError.TIMEOUT)
        } catch (e: IOException) {
            return ProviderResult.Failure(ProviderError.NETWORK)
        }
        return withContext(Dispatchers.IO) {
            response.use { r ->
                if (!r.isSuccessful) return@use ProviderResult.Failure(ProviderError.fromHttp(r.code))
                val xml = try {
                    r.body?.string()
                } catch (e: IOException) {
                    return@use ProviderResult.Failure(ProviderError.NETWORK)
                }
                if (xml.isNullOrBlank()) return@use ProviderResult.Failure(ProviderError.MALFORMED)
                try {
                    ProviderResult.Success(RssParser.parse(xml, feed, now()))
                } catch (e: XmlPullParserException) {
                    ProviderResult.Failure(ProviderError.MALFORMED)
                } catch (e: IOException) {
                    ProviderResult.Failure(ProviderError.MALFORMED)
                }
            }
        }
    }
}

internal object RssParser {
    fun parse(xml: String, feed: RssFeed, now: Long): List<Article> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        val out = ArrayList<Article>()
        var inItem = false
        var title: String? = null
        var link: String? = null
        var description: String? = null
        var pubDate: String? = null
        var author: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name
                if (name == "item") {
                    inItem = true
                    title = null; link = null; description = null; pubDate = null; author = null
                } else if (inItem) {
                    when (name) {
                        "title" -> title = parser.nextText()
                        "link" -> link = parser.nextText()
                        "description" -> description = parser.nextText()
                        "pubDate" -> pubDate = parser.nextText()
                        "dc:creator", "author" -> author = parser.nextText()
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "item") {
                inItem = false
                toArticle(feed, title, link, description, pubDate, author, now)?.let { out += it }
            }
            event = parser.next()
        }
        return out
    }

    private fun toArticle(
        feed: RssFeed,
        title: String?,
        link: String?,
        description: String?,
        pubDate: String?,
        author: String?,
        now: Long,
    ): Article? {
        val url = link?.trim().orEmpty()
        if (!url.startsWith("https://") && !url.startsWith("http://")) return null
        val cleanTitle = FreeNewsParser.cleanText(title).takeIf { it.isNotBlank() } ?: return null
        val published = pubDate?.trim()?.let {
            runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        } ?: return null
        val cleanDescription = FreeNewsParser.cleanText(description).take(300)
            .takeIf { it.isNotBlank() && it != cleanTitle }
        return Article(
            id = ArticleIds.forUrl(url),
            title = cleanTitle,
            url = url,
            description = cleanDescription,
            sourceName = feed.name,
            sourceHost = feed.host,
            publishedAt = minOf(published, now),
            author = author?.let { FreeNewsParser.cleanText(it) }?.takeIf { it.isNotBlank() },
        )
    }
}
