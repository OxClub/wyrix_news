package com.wyrix.news.data.remote

import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.ArticleIds
import com.wyrix.news.domain.model.FeedSpec
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * FreeNewsAPI.ai - GET /v1/search. Keyless, no signup; terms ask apps to link back to each
 * article's original URL and to cache. 20 requests/second per IP. No SLA.
 * Docs: https://freenewsapi.ai/docs/search
 */
class FreeNewsProvider(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://freenewsapi.ai",
    private val userAgent: String = "Wyrix (Android)",
    private val now: () -> Long = { System.currentTimeMillis() },
) : NewsProvider {

    override val id: String = "freenewsapi"
    override val displayName: String = "FreeNewsAPI.ai"

    override suspend fun fetch(spec: FeedSpec, offset: Int): ProviderResult {
        val url = buildUrl(spec, offset)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
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
        addPathSegments("v1/search")
        spec.query?.takeIf { it.isNotBlank() }?.let { addQueryParameter("q", it) }
        if (spec.countries.isNotEmpty()) addQueryParameter("country", spec.countries.joinToString(","))
        if (spec.hosts.isNotEmpty()) addQueryParameter("host", spec.hosts.joinToString(","))
        spec.language?.let { addQueryParameter("lang", it) }
        addQueryParameter("date", spec.window)
        val relevance = spec.sortByRelevance && !spec.query.isNullOrBlank()
        addQueryParameter("sort", if (relevance) "relevance" else "date")
        addQueryParameter("size", spec.pageSize.coerceIn(1, 100).toString())
        addQueryParameter("offset", offset.coerceIn(0, 9_900).toString())
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
        return FreeNewsParser.parse(body, now())
    }
}

@Serializable
internal data class SearchResponseDto(
    val total: Int? = null,
    val results: List<ArticleDto>? = null,
)

@Serializable
internal data class ArticleDto(
    val title: String? = null,
    val url: String? = null,
    val description: String? = null,
    @SerialName("published_at") val publishedAt: JsonElement? = null,
    val host: String? = null,
    val country: String? = null,
    val lang: String? = null,
    // Not listed in the docs I verified; used only if the API sends it.
    @SerialName("image_url") val imageUrl: String? = null,
    val author: String? = null,
)

object FreeNewsParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val htmlTag = Regex("<[^>]*>")
    private val whitespace = Regex("\\s+")

    fun parse(body: String, now: Long): ProviderResult {
        val dto = try {
            json.decodeFromString<SearchResponseDto>(body)
        } catch (e: IllegalArgumentException) {
            return ProviderResult.Failure(ProviderError.MALFORMED)
        }
        val results = dto.results ?: return ProviderResult.Failure(ProviderError.MALFORMED)
        val articles = results.mapNotNull { toArticle(it, now) }
        return ProviderResult.Success(articles = articles, rawCount = results.size, total = dto.total)
    }

    private fun toArticle(dto: ArticleDto, now: Long): Article? {
        val rawUrl = dto.url?.trim().orEmpty()
        if (!(rawUrl.startsWith("https://") || rawUrl.startsWith("http://"))) return null
        val title = cleanText(dto.title).takeIf { it.isNotBlank() } ?: return null
        // Articles without a verifiable publication time are skipped rather than given a made-up one.
        val published = parseTime(dto.publishedAt) ?: return null
        val host = (dto.host?.takeIf { it.isNotBlank() } ?: rawUrl.toHttpUrlOrNull()?.host ?: return null)
            .lowercase()
            .substringBefore(':')
        val description = cleanText(dto.description).take(300).takeIf { it.isNotBlank() && it != title }
        return Article(
            id = ArticleIds.forUrl(rawUrl),
            title = title,
            url = rawUrl,
            description = description,
            sourceName = SourceNames.forHost(host),
            sourceHost = host.removePrefix("www."),
            publishedAt = minOf(published, now),
            imageUrl = dto.imageUrl?.takeIf { it.startsWith("https://") },
            author = dto.author?.let { cleanText(it) }?.takeIf { it.isNotBlank() },
            country = dto.country?.uppercase(),
            language = dto.lang?.lowercase(),
        )
    }

    internal fun cleanText(raw: String?): String {
        if (raw == null) return ""
        return raw.replace(htmlTag, " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace(whitespace, " ")
            .trim()
    }

    internal fun parseTime(element: JsonElement?): Long? {
        if (element == null || element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.isString) {
            val s = primitive.content.trim()
            return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
                ?: s.toLongOrNull()?.let { normalizeEpoch(it) }
        }
        return primitive.longOrNull?.let { normalizeEpoch(it) }
    }

    private fun normalizeEpoch(value: Long): Long = if (value < 10_000_000_000L) value * 1000 else value
}
