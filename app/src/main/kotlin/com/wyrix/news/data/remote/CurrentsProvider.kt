package com.wyrix.news.data.remote

import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.ArticleIds
import com.wyrix.news.domain.model.FeedSpec
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class CurrentsProvider(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.currentsapi.services",
    private val userAgent: String = "Wyrix (Android)",
    private val now: () -> Long = { System.currentTimeMillis() },
) : NewsProvider {

    override val id: String = "currents"
    override val displayName: String = "Currents API"

    override suspend fun fetch(spec: FeedSpec, offset: Int): ProviderResult {
        if (apiKey.isBlank() || spec.hosts.size > 1) return ProviderResult.Failure(ProviderError.UNSUPPORTED)
        val request = Request.Builder()
            .url(buildUrl(spec, offset))
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .header("Authorization", "Bearer $apiKey")
            .build()
        val response = try {
            client.newCall(request).await()
        } catch (e: SocketTimeoutException) {
            return ProviderResult.Failure(ProviderError.TIMEOUT)
        } catch (e: IOException) {
            return ProviderResult.Failure(ProviderError.NETWORK)
        }
        return withContext(Dispatchers.IO) { response.use { handle(it) } }
    }

    internal fun buildUrl(spec: FeedSpec, offset: Int) = baseUrl.toHttpUrl().newBuilder().apply {
        val query = spec.query?.takeIf { it.isNotBlank() }
        addPathSegments(if (query != null) "v1/search" else "v1/latest-news")
        if (query != null) {
            addQueryParameter("keywords", query)
            val hours = when (spec.window) {
                "today", "24h" -> 24
                "yesterday", "48h" -> 48
                else -> 24 * 7
            }
            val start = Instant.ofEpochSecond((now() - hours * 3_600_000L) / 1000)
            addQueryParameter("start_date", DateTimeFormatter.ISO_INSTANT.format(start))
        }
        spec.language?.let { addQueryParameter("language", it) }
        spec.countries.singleOrNull()?.let { addQueryParameter("country", it) }
        spec.hosts.singleOrNull()?.let { addQueryParameter("domain", it.removePrefix("www.")) }
        val size = spec.pageSize.coerceIn(1, 100)
        addQueryParameter("page_size", size.toString())
        addQueryParameter("page_number", (offset / size + 1).coerceIn(1, 180).toString())
    }.build()

    private fun handle(response: Response): ProviderResult {
        if (response.code !in 200..299) {
            val retryAfterMs = response.header("Retry-After")?.toLongOrNull()?.let { it * 1000 }
            return ProviderResult.Failure(ProviderError.fromHttp(response.code), retryAfterMs)
        }
        val body = try {
            response.body?.string()
        } catch (e: IOException) {
            return ProviderResult.Failure(ProviderError.NETWORK)
        }
        if (body.isNullOrBlank()) return ProviderResult.Failure(ProviderError.MALFORMED)
        return CurrentsParser.parse(body, now())
    }
}

@Serializable
internal data class CurrentsResponseDto(
    val status: String? = null,
    val news: List<CurrentsArticleDto>? = null,
)

@Serializable
internal data class CurrentsArticleDto(
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val author: String? = null,
    val image: String? = null,
    val language: String? = null,
    val published: String? = null,
)

object CurrentsParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val publishedFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")

    fun parse(body: String, now: Long): ProviderResult {
        val dto = try {
            json.decodeFromString<CurrentsResponseDto>(body)
        } catch (e: IllegalArgumentException) {
            return ProviderResult.Failure(ProviderError.MALFORMED)
        }
        if (dto.status != null && dto.status != "ok") return ProviderResult.Failure(ProviderError.UNKNOWN)
        val results = dto.news ?: return ProviderResult.Failure(ProviderError.MALFORMED)
        return ProviderResult.Success(articles = results.mapNotNull { toArticle(it, now) }, rawCount = results.size)
    }

    private fun toArticle(dto: CurrentsArticleDto, now: Long): Article? {
        val rawUrl = dto.url?.trim().orEmpty()
        if (!(rawUrl.startsWith("https://") || rawUrl.startsWith("http://"))) return null
        val title = FreeNewsParser.cleanText(dto.title).takeIf { it.isNotBlank() } ?: return null
        val published = parseTime(dto.published) ?: return null
        val host = (rawUrl.toHttpUrlOrNull()?.host ?: return null).lowercase().removePrefix("www.")
        val description = FreeNewsParser.cleanText(dto.description).take(300)
            .takeIf { it.isNotBlank() && it != title }
        return Article(
            id = ArticleIds.forUrl(rawUrl),
            title = title,
            url = rawUrl,
            description = description,
            sourceName = SourceNames.forHost(host),
            sourceHost = host,
            publishedAt = minOf(published, now),
            imageUrl = dto.image?.takeIf { it.startsWith("https://") },
            author = dto.author?.let { FreeNewsParser.cleanText(it) }
                ?.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) },
            language = dto.language?.lowercase(),
        )
    }

    internal fun parseTime(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return runCatching { OffsetDateTime.parse(s, publishedFormat).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
    }
}
