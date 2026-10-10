package com.wyrix.news.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyrix.news.data.prefs.UserPrefs
import com.wyrix.news.data.repository.FailureReason
import com.wyrix.news.data.repository.NewsRepository
import com.wyrix.news.data.repository.PageResult
import com.wyrix.news.data.repository.RefreshResult
import com.wyrix.news.domain.BreakingPolicy
import com.wyrix.news.domain.DuplicateDetector
import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.FeedSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(
    private val repository: NewsRepository,
    prefs: UserPrefs,
    private val specBuilder: (String?) -> FeedSpec,
    private val breakingOnly: Boolean = false,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private data class Status(
        val initialLoading: Boolean = true,
        val refreshing: Boolean = false,
        val failure: FailureReason? = null,
        val lastUpdated: Long? = null,
        val loadingMore: Boolean = false,
        val canLoadMore: Boolean = false,
        val pagesLoaded: Int = 1,
    )

    private val extra = MutableStateFlow<List<Article>>(emptyList())
    private val status = MutableStateFlow(Status())

    @Volatile
    private var currentSpec: FeedSpec? = null

    private val specFlow: Flow<FeedSpec> = prefs.settings
        .map { if (it.englishOnly) "en" else null }
        .distinctUntilChanged()
        .map { specBuilder(it) }

    val state: StateFlow<FeedUiState> = specFlow.flatMapLatest { spec ->
        combine(repository.observe(spec), extra, status) { cached, more, st ->
            val merged = DuplicateDetector.dedupe(cached + more)
            val visible = if (breakingOnly) merged.filter { BreakingPolicy.isBreaking(it, clock()) } else merged
            FeedUiState(
                articles = visible,
                isLoading = st.initialLoading && visible.isEmpty(),
                isRefreshing = st.refreshing,
                failure = st.failure,
                lastUpdated = st.lastUpdated,
                canLoadMore = st.canLoadMore && !breakingOnly,
                isLoadingMore = st.loadingMore,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState())

    init {
        viewModelScope.launch {
            specFlow.collectLatest { spec ->
                currentSpec = spec
                extra.value = emptyList()
                status.value = Status()
                runRefresh(spec, force = false)
            }
        }
    }

    fun refresh() {
        val spec = currentSpec ?: return
        viewModelScope.launch { runRefresh(spec, force = true) }
    }

    private suspend fun runRefresh(spec: FeedSpec, force: Boolean) {
        status.update { it.copy(refreshing = true) }
        val result = repository.refresh(spec, force)
        val updated = repository.lastUpdated(spec)
        status.update { st ->
            val next = when (result) {
                is RefreshResult.Updated -> st.copy(failure = null, canLoadMore = true, pagesLoaded = 1)
                is RefreshResult.Throttled -> st.copy(canLoadMore = true)
                is RefreshResult.Failed -> st.copy(failure = result.reason)
            }
            next.copy(refreshing = false, initialLoading = false, lastUpdated = updated)
        }
        if (result is RefreshResult.Updated) extra.value = emptyList()
    }

    fun loadMore() {
        val spec = currentSpec ?: return
        val current = status.value
        if (current.loadingMore || !current.canLoadMore) return
        viewModelScope.launch {
            status.update { it.copy(loadingMore = true) }
            val offset = current.pagesLoaded * spec.pageSize
            when (val result = repository.fetchPage(spec, offset)) {
                is PageResult.Success -> {
                    extra.update { DuplicateDetector.dedupe(it + result.articles) }
                    status.update {
                        it.copy(loadingMore = false, canLoadMore = result.hasMore, pagesLoaded = it.pagesLoaded + 1)
                    }
                }
                is PageResult.Failure -> status.update { it.copy(loadingMore = false) }
            }
        }
    }
}
