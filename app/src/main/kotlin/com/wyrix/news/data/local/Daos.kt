package com.wyrix.news.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ArticleDao {
    @Query(
        "SELECT a.* FROM articles a INNER JOIN feed_items f ON a.id = f.articleId " +
            "WHERE f.feedKey = :feedKey ORDER BY f.position ASC",
    )
    abstract fun observeFeed(feedKey: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertArticles(articles: List<ArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFeedItems(items: List<FeedItemEntity>)

    @Query("DELETE FROM feed_items WHERE feedKey = :feedKey")
    abstract suspend fun clearFeed(feedKey: String)

    @Query("SELECT MAX(fetchedAt) FROM feed_items WHERE feedKey = :feedKey")
    abstract suspend fun lastFetched(feedKey: String): Long?

    @Query("DELETE FROM feed_items WHERE fetchedAt < :before")
    abstract suspend fun pruneFeedItems(before: Long)

    @Query(
        "DELETE FROM articles WHERE fetchedAt < :before " +
            "AND id NOT IN (SELECT articleId FROM feed_items) " +
            "AND id NOT IN (SELECT id FROM bookmarks)",
    )
    abstract suspend fun pruneArticles(before: Long)

    @Transaction
    open suspend fun replaceFeed(feedKey: String, articles: List<ArticleEntity>, fetchedAt: Long) {
        upsertArticles(articles)
        clearFeed(feedKey)
        insertFeedItems(articles.mapIndexed { index, a -> FeedItemEntity(feedKey, a.id, index, fetchedAt) })
    }

    @Transaction
    open suspend fun prune(before: Long) {
        pruneFeedItems(before)
        pruneArticles(before)
    }
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT id FROM bookmarks")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [ArticleEntity::class, FeedItemEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WyrixDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun bookmarkDao(): BookmarkDao
}
