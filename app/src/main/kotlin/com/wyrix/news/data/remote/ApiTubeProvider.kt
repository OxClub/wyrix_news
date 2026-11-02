package com.wyrix.news.data.remote

import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.ArticleIds
import com.wyrix.news.domain.model.FeedSpec
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ApiTubeProvider(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.apitube.io",
    private val perPage: Int = 10,
    private val userAgent: String = "Wyrix (Android)",
    private val now: () -> Long = { System.currentTimeMillis() },
) : NewsProvider {

    override val id: String = "apitube"
    override val displayName: String = "APITube"

    override suspend fun fetch(spec: FeedSpec, offset: Int): ProviderResult {
        if (apiKey.isBlank() || spec.hosts.size > 1) return ProviderResult.Failure(ProviderError.UNSUPPORTED)
        if (offset > 0) return ProviderResult.Success(emptyList())
        val request = Request.Builder()
            .url(buildUrl(spec))
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

    internal fun buildUrl(spec: FeedSpec) = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegments("v1/news/everything")
        val query = spec.query?.trim()?.takeIf { it.length >= 2 }
        if (query != null) addQueryParameter("title", query.take(100))
        spec.language?.let { addQueryParameter("language.code", it) }
        if (spec.countries.isNotEmpty()) {
            addQueryParameter("source.country.code", spec.countries.take(3).joinToString(",") { it.lowercase() })
        }
        spec.hosts.singleOrNull()?.let { addQueryParameter("source.domain", it.removePrefix("www.")) }
        val start = when (spec.window) {
            "today", "24h" -> "NOW-1DAY"
            "yesterday", "48h" -> "NOW-2DAY"
            "30d" -> "NOW-30DAY"
            else -> "NOW-7DAY"
        }
        addQueryParameter("published_at.start", start)
        val relevance = spec.sortByRelevance && query != null
        addQueryParameter("sort.by", if (relevance) "relevance" else "published_at")
        addQueryParameter("sort.order", "desc")
        addQueryParameter("fl", "id,href,title,description,published_at,image,author,language")
        addQueryParameter("per_page", perPage.coerceIn(1, 10).toString())
        addQueryParameter("page", "1")
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
        return ApiTubeParser.parse(body, now())
    }
}

@Serializable
internal data class ApiTubeResponseDto(
    val status: String? = null,
    val results: List<ApiTubeArticleDto>? = null,
)

@Serializable
internal data class ApiTubeAuthorDto(val name: String? = null)

@Serializable
internal data class ApiTubeArticleDto(
    val href: String? = null,
    val title: String? = null,
    val description: String? = null,
    @kotlinx.serialization.SerialName("published_at") val publishedAt: String? = null,
    val image: String? = null,
    val author: ApiTubeAuthorDto? = null,
    val language: String? = null,
)

object ApiTubeParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(body: String, now: Long): ProviderResult {
        val dto = try {
            json.decodeFromString<ApiTubeResponseDto>(body)
        } catch (e: IllegalArgumentException) {
            return ProviderResult.Failure(ProviderError.MALFORMED)
        }
        if (dto.status != null && dto.status != "ok") return ProviderResult.Failure(ProviderError.UNKNOWN)
        val results = dto.results ?: return ProviderResult.Failure(ProviderError.MALFORMED)
        return ProviderResult.Success(articles = results.mapNotNull { toArticle(it, now) }, rawCount = results.size)
    }

    private fun toArticle(dto: ApiTubeArticleDto, now: Long): Article? {
        val rawUrl = dto.href?.trim().orEmpty()
        if (!(rawUrl.startsWith("https://") || rawUrl.startsWith("http://"))) return null
        val title = FreeNewsParser.cleanText(dto.title).takeIf { it.isNotBlank() } ?: return null
        val published = runCatching { Instant.parse(dto.publishedAt?.trim().orEmpty()).toEpochMilli() }
            .getOrNull() ?: return null
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
            author = dto.author?.name?.let { FreeNewsParser.cleanText(it) }?.takeIf { it.isNotBlank() },
            language = dto.language?.lowercase(),
        )
    }
}
