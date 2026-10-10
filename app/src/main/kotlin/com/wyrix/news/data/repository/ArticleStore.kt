package com.wyrix.news.data.repository

import com.wyrix.news.data.local.ArticleDao
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
}

class RoomArticleStore(private val dao: ArticleDao) : ArticleStore {
    override fun observeFeed(feedKey: String): Flow<List<Article>> =
        dao.observeFeed(feedKey).map { list -> list.map { it.toDomain() } }

    override suspend fun replaceFeed(feedKey: String, articles: List<Article>, fetchedAt: Long) {
        dao.replaceFeed(feedKey, articles.map { it.toEntity(fetchedAt) }, fetchedAt)
    }

    override suspend fun upsert(articles: List<Article>, fetchedAt: Long) {
        dao.upsertArticles(articles.map { it.toEntity(fetchedAt) })
    }

    override suspend fun getArticle(id: String): Article? = dao.getById(id)?.toDomain()

    override suspend fun lastFetched(feedKey: String): Long? = dao.lastFetched(feedKey)

    override suspend fun prune(before: Long) = dao.prune(before)
}
