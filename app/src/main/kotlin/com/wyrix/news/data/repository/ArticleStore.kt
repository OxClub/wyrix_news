package com.wyrix.news.data.repository

import com.wyrix.news.data.local.ArticleDao
import com.wyrix.news.data.local.ArticleEntity
import com.wyrix.news.data.local.BookmarkDao
import com.wyrix.news.data.local.toDomain
import com.wyrix.news.data.local.toEntity
import com.wyrix.news.domain.model.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Cache abstraction so the repository can be unit-tested without Room. */
interface ArticleStore {
    fun observeFeed(feedKey: String): Flow<List<Article>>
    suspend fun replaceFeed(feedKey: String, articles: List<Article>, fetchedAt: Long)
    suspend fun upsert(articles: List<Article>, fetchedAt: Long)
    suspend fun getArticle(id: String): Article?
    suspend fun lastFetched(feedKey: String): Long?
    suspend fun prune(before: Long)

    /** null = not checked yet, "" = checked and none found, otherwise the image URL. */
    suspend fun imageState(id: String): String?
    suspend fun updateImage(id: String, imageUrl: String)
}

class RoomArticleStore(
    private val dao: ArticleDao,
    private val bookmarks: BookmarkDao,
) : ArticleStore {

    /** Re-fetched articles must not lose a thumbnail we already resolved. */
    private suspend fun entities(articles: List<Article>, fetchedAt: Long): List<ArticleEntity> {
        if (articles.isEmpty()) return emptyList()
        val existing = articles.map { it.id }.chunked(500)
            .flatMap { dao.imagesFor(it) }
            .associate { it.id to it.imageUrl }
        return articles.map { article ->
            val entity = article.toEntity(fetchedAt)
            if (entity.imageUrl == null) entity.copy(imageUrl = existing[entity.id]) else entity
        }
    }

    override fun observeFeed(feedKey: String): Flow<List<Article>> =
        dao.observeFeed(feedKey).map { list -> list.map { it.toDomain() } }

    override suspend fun replaceFeed(feedKey: String, articles: List<Article>, fetchedAt: Long) {
        dao.replaceFeed(feedKey, entities(articles, fetchedAt), fetchedAt)
    }

    override suspend fun upsert(articles: List<Article>, fetchedAt: Long) {
        dao.upsertArticles(entities(articles, fetchedAt))
    }

    override suspend fun getArticle(id: String): Article? = dao.getById(id)?.toDomain()

    override suspend fun lastFetched(feedKey: String): Long? = dao.lastFetched(feedKey)

    override suspend fun prune(before: Long) = dao.prune(before)

    override suspend fun imageState(id: String): String? = dao.imageOf(id)

    override suspend fun updateImage(id: String, imageUrl: String) {
        dao.setImage(id, imageUrl)
        if (imageUrl.isNotBlank()) bookmarks.setImage(id, imageUrl)
    }
}
