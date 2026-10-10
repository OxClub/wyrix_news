package com.wyrix.news

import android.app.Application
import com.wyrix.news.di.AppContainer
import kotlinx.coroutines.launch

class WyrixApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { runCatching { container.repository.pruneStale() } }
    }
}
