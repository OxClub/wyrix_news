package com.wyrix.news.data.remote

import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.ArticleIds
import com.wyrix.news.domain.model.FeedSpec
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class FreeNewsApiIoProvider(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.freenewsapi.io",
    private val maxArticles: Int = 10,
    private val userAgent: String = "Wyrix (Android)",
    private val now: () -> Long = { System.currentTimeMillis() },
) : NewsProvider {

    override val id: String = "freenewsapiio"
    override val displayName: String = "FreeNewsApi.io"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override suspend fun fetch(spec: FeedSpec, offset: Int): ProviderResult {
        if (apiKey.isBlank() || spec.hosts.isNotEmpty()) return ProviderResult.Failure(ProviderError.UNSUPPORTED)
        if (offset > 0) return ProviderResult.Success(emptyList())

        val listUrl = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("v1/news")
            spec.query?.takeIf { it.isNotBlank() }?.let { addQueryParameter("q", it) }
            spec.language?.let { addQueryParameter("language", it) }
            spec.countries.firstOrNull()?.let { addQueryParameter("country", it.lowercase()) }
            addQueryParameter("order_by", "recent")
        }.build()
        val listBody = when (val r = get(listUrl.toString())) {
            is Body.Ok -> r.text
            is Body.Fail -> return ProviderResult.Failure(r.error, r.retryAfterMs)
        }
        val items = try {
            json.decodeFromString<ListDto>(listBody).data
        } catch (e: IllegalArgumentException) {
            return ProviderResult.Failure(ProviderError.MALFORMED)
        } ?: return ProviderResult.Failure(ProviderError.MALFORMED)

        val articles = ArrayList<Article>()
        var lastError: ProviderError? = null
        for ((index, item) in items.take(maxArticles).withIndex()) {
            val uuid = item.uuid ?: continue
            if (index > 0) delay(550)
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("v1/details").addQueryParameter("uuid", uuid).build().toString()
            when (val r = get(url)) {
                is Body.Ok -> {
                    val detail = try {
                        json.decodeFromString<DetailDto>(r.text).data
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                    detail?.let { toArticle(it, item) }?.let { articles += it }
                }
                is Body.Fail -> {
                    lastError = r.error
                    if (r.error == ProviderError.RATE_LIMITED || r.error == ProviderError.UNAUTHORIZED) break
                }
            }
        }
        if (articles.isEmpty() && lastError != null) return ProviderResult.Failure(lastError)
        return ProviderResult.Success(articles, rawCount = articles.size)
    }

    private sealed interface Body {
        data class Ok(val text: String) : Body
        data class Fail(val error: ProviderError, val retryAfterMs: Long? = null) : Body
    }

    private suspend fun get(url: String): Body {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .header("x-api-key", apiKey)
            .build()
        val response = try {
            client.newCall(request).await()
        } catch (e: SocketTimeoutException) {
            return Body.Fail(ProviderError.TIMEOUT)
        } catch (e: IOException) {
            return Body.Fail(ProviderError.NETWORK)
        }
        return withContext(Dispatchers.IO) {
            response.use { r ->
                if (r.code !in 200..299) {
                    val retry = r.header("Retry-After")?.toLongOrNull()?.let { it * 1000 }
                    return@use Body.Fail(ProviderError.fromHttp(r.code), retry)
                }
                val text = try {
                    r.body?.string()
                } catch (e: IOException) {
                    return@use Body.Fail(ProviderError.NETWORK)
                }
                if (text.isNullOrBlank()) Body.Fail(ProviderError.MALFORMED) else Body.Ok(text)
            }
        }
    }

    private fun toArticle(detail: DetailItem, listItem: ListItem): Article? {
        val rawUrl = detail.originalUrl?.trim().orEmpty()
        if (!(rawUrl.startsWith("https://") || rawUrl.startsWith("http://"))) return null
        val title = FreeNewsParser.cleanText(detail.title ?: listItem.title).takeIf { it.isNotBlank() } ?: return null
        val published = runCatching { Instant.parse((detail.publishedAt ?: listItem.publishedAt).orEmpty()) }
            .getOrNull()?.toEpochMilli() ?: return null
        val host = (rawUrl.toHttpUrlOrNull()?.host ?: return null).lowercase().removePrefix("www.")
        val excerpt = FreeNewsParser.cleanText(detail.body).take(300).takeIf { it.isNotBlank() && it != title }
        val publisher = (detail.publisher ?: listItem.publisher)?.trim()?.takeIf { it.isNotBlank() }
        return Article(
            id = ArticleIds.forUrl(rawUrl),
            title = title,
            url = rawUrl,
            description = excerpt,
            sourceName = publisher ?: SourceNames.forHost(host),
            sourceHost = host,
            publishedAt = minOf(published, now()),
            imageUrl = detail.thumbnail?.takeIf { it.startsWith("https://") },
            author = detail.authors?.firstOrNull { !it.startsWith("http") }
                ?.let { FreeNewsParser.cleanText(it) }?.takeIf { it.isNotBlank() },
            language = detail.languages?.firstOrNull()?.lowercase(),
        )
    }

    @Serializable
    private data class ListDto(val data: List<ListItem>? = null)

    @Serializable
    private data class ListItem(
        val uuid: String? = null,
        val title: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val publisher: String? = null,
    )

    @Serializable
    private data class DetailDto(val data: DetailItem? = null)

    @Serializable
    private data class DetailItem(
        val title: String? = null,
        val thumbnail: String? = null,
        val publisher: String? = null,
        val authors: List<String>? = null,
        val languages: List<String>? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        @SerialName("original_url") val originalUrl: String? = null,
        val body: String? = null,
    )
}
