package com.wyrix.news.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wyrix.news.domain.model.Article

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val description: String?,
    val sourceName: String,
    val sourceHost: String,
    val publishedAt: Long,
    val imageUrl: String?,
    val author: String?,
    val category: String?,
    val country: String?,
    val language: String?,
    val fetchedAt: Long,
)

@Entity(
    tableName = "feed_items",
    primaryKeys = ["feedKey", "articleId"],
    indices = [Index("articleId")],
)
data class FeedItemEntity(
    val feedKey: String,
    val articleId: String,
    val position: Int,
    val fetchedAt: Long,
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val description: String?,
    val sourceName: String,
    val sourceHost: String,
    val publishedAt: Long,
    val imageUrl: String?,
    val savedAt: Long,
)

fun ArticleEntity.toDomain() = Article(
    id = id, title = title, url = url, description = description, sourceName = sourceName,
    sourceHost = sourceHost, publishedAt = publishedAt, imageUrl = imageUrl?.takeIf { it.isNotBlank() }, author = author,
    category = category, country = country, language = language,
)

fun Article.toEntity(fetchedAt: Long) = ArticleEntity(
    id = id, title = title, url = url, description = description, sourceName = sourceName,
    sourceHost = sourceHost, publishedAt = publishedAt, imageUrl = imageUrl, author = author,
    category = category, country = country, language = language, fetchedAt = fetchedAt,
)

fun BookmarkEntity.toDomain() = Article(
    id = id, title = title, url = url, description = description, sourceName = sourceName,
    sourceHost = sourceHost, publishedAt = publishedAt, imageUrl = imageUrl?.takeIf { it.isNotBlank() },
)

fun Article.toBookmark(savedAt: Long) = BookmarkEntity(
    id = id, title = title, url = url, description = description, sourceName = sourceName,
    sourceHost = sourceHost, publishedAt = publishedAt, imageUrl = imageUrl, savedAt = savedAt,
)
