package com.wyrix.news.data.repository

import com.wyrix.news.data.local.BookmarkDao
import com.wyrix.news.data.local.toBookmark
import com.wyrix.news.data.local.toDomain
import com.wyrix.news.domain.model.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookmarkRepository(
    private val dao: BookmarkDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    val articles: Flow<List<Article>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    val ids: Flow<Set<String>> = dao.observeIds().map { it.toSet() }

    suspend fun get(id: String): Article? = dao.getById(id)?.toDomain()

    suspend fun toggle(article: Article) {
        if (dao.getById(article.id) != null) dao.delete(article.id) else dao.insert(article.toBookmark(clock()))
    }
}
