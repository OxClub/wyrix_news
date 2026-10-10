package com.wyrix.news.data.repository

import com.wyrix.news.data.remote.OgImageParser
import com.wyrix.news.data.remote.await
import com.wyrix.news.domain.model.Article
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The news API returns no pictures, so for articles shown on screen we read the preview image the
 * publisher itself declares on its page (first 150 KB only). Results, including "none found",
 * are saved so each article is looked up at most once. At most 4 lookups run at the same time.
 */
class ImageResolver(
    client: OkHttpClient,
    private val store: ArticleStore,
    private val connectivity: Connectivity,
    private val scope: CoroutineScope,
    private val userAgent: String,
) {
    private val http = client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    private val permits = Semaphore(4)
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Fire and forget; safe to call for every visible card. */
    fun request(article: Article) {
        if (article.imageUrl != null || !inFlight.add(article.id)) return
        scope.launch {
            try {
                resolve(article)
            } finally {
                inFlight.remove(article.id)
            }
        }
    }

    suspend fun resolve(article: Article): String? {
        article.imageUrl?.let { return it }
        val saved = store.imageState(article.id)
        if (saved != null) return saved.ifBlank { null }
        if (!connectivity.isOnline()) return null
        return when (val outcome = permits.withPermit { fetch(article.url) }) {
            is Outcome.Found -> {
                store.updateImage(article.id, outcome.url)
                outcome.url
            }
            Outcome.None -> {
                store.updateImage(article.id, "")
                null
            }
            Outcome.Error -> null
        }
    }

    private sealed interface Outcome {
        data class Found(val url: String) : Outcome
        data object None : Outcome
        data object Error : Outcome
    }

    private suspend fun fetch(url: String): Outcome {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        val response = try {
            http.newCall(request).await()
        } catch (e: IOException) {
            return Outcome.Error
        } catch (e: IllegalArgumentException) {
            return Outcome.None
        }
        return withContext(Dispatchers.IO) {
            response.use { r ->
                if (!r.isSuccessful) {
                    return@use if (r.code in 400..499 && r.code != 429) Outcome.None else Outcome.Error
                }
                val html = try {
                    r.peekBody(MAX_BYTES).string()
                } catch (e: IOException) {
                    return@use Outcome.Error
                }
                val image = OgImageParser.parse(html, r.request.url.toString())
                if (image != null) Outcome.Found(image) else Outcome.None
            }
        }
    }

    private companion object {
        const val MAX_BYTES = 150_000L
    }
}
