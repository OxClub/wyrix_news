package com.wyrix.news.di

import android.content.Context
import androidx.room.Room
import com.wyrix.news.BuildConfig
import com.wyrix.news.WyrixApplication
import com.wyrix.news.ads.AdController
import com.wyrix.news.ads.NoOpAdController
import com.wyrix.news.data.local.WyrixDatabase
import com.wyrix.news.data.prefs.UserPrefs
import com.wyrix.news.data.remote.FreeNewsProvider
import com.wyrix.news.data.remote.NewsProvider
import com.wyrix.news.data.remote.ProviderGate
import com.wyrix.news.data.remote.RssFeedRegistry
import com.wyrix.news.data.remote.RssProvider
import com.wyrix.news.data.repository.AndroidConnectivity
import com.wyrix.news.data.repository.BookmarkRepository
import com.wyrix.news.data.repository.NewsRepository
import com.wyrix.news.data.repository.RoomArticleStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** Hand-written dependency container: no code generation, nothing to break the build. */
class AppContainer(context: Context) {
    private val app: Context = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = UserPrefs(app)

    private val database = Room.databaseBuilder(app, WyrixDatabase::class.java, "wyrix.db").build()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private val userAgent = "Wyrix/${BuildConfig.VERSION_NAME} (Android)"

    private val providers: List<NewsProvider> = listOf(
        FreeNewsProvider(client = http, userAgent = userAgent),
        RssProvider(client = http, feeds = RssFeedRegistry.parse(BuildConfig.RSS_FEEDS), userAgent = userAgent),
    )

    val repository = NewsRepository(
        providers = providers,
        store = RoomArticleStore(database.articleDao()),
        gate = ProviderGate(),
        connectivity = AndroidConnectivity(app),
    )

    val bookmarks = BookmarkRepository(database.bookmarkDao())
    val ads: AdController = NoOpAdController
}

fun Context.appContainer(): AppContainer = (applicationContext as WyrixApplication).container
