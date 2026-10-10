package com.wyrix.news.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyrix.news.data.prefs.UserPrefs
import com.wyrix.news.data.repository.FailureReason
import com.wyrix.news.data.repository.NewsRepository
import com.wyrix.news.data.repository.PageResult
import com.wyrix.news.domain.DuplicateDetector
import com.wyrix.news.domain.NewsQueryEngine
import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.FeedSpec
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Article> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val failure: FailureReason? = null,
    val searched: Boolean = false,
    val canLoadMore: Boolean = false,
    val tooShort: Boolean = false,
    val recent: List<String> = emptyList(),
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: NewsRepository,
    private val prefs: UserPrefs,
) : ViewModel() {

    private data class Inner(
        val query: String = "",
        val results: List<Article> = emptyList(),
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val failure: FailureReason? = null,
        val searched: Boolean = false,
        val canLoadMore: Boolean = false,
        val tooShort: Boolean = false,
    )

    private val inner = MutableStateFlow(Inner())
    private val typed = MutableStateFlow("")
    private val immediate = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var activeSpec: FeedSpec? = null
    private var lastQuery: String? = null
    private var offset = 0

    val state: StateFlow<SearchUiState> = combine(inner, prefs.settings) { i, settings ->
        SearchUiState(
            query = i.query,
            results = i.results,
            isLoading = i.loading,
            isLoadingMore = i.loadingMore,
            failure = i.failure,
            searched = i.searched,
            canLoadMore = i.canLoadMore,
            tooShort = i.tooShort,
            recent = settings.recentSearches,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    init {
        viewModelScope.launch {
            val debounced = typed.debounce(400).map { it.trim() }.distinctUntilChanged()
            merge(debounced, immediate.map { it.trim() }).collectLatest { q ->
                if (q != lastQuery || inner.value.failure != null) runSearch(q)
            }
        }
    }

    fun onQueryChange(value: String) {
        typed.value = value
        inner.update { it.copy(query = value) }
    }

    fun submit(value: String = typed.value) {
        immediate.tryEmit(value)
    }

    fun selectRecent(value: String) {
        onQueryChange(value)
        submit(value)
    }

    fun retry() = submit()

    fun clearRecent() {
        viewModelScope.launch { prefs.clearRecentSearches() }
    }

    private suspend fun runSearch(q: String) {
        lastQuery = q.ifEmpty { null }
        if (q.isEmpty()) {
            activeSpec = null
            inner.update {
                it.copy(results = emptyList(), loading = false, failure = null, searched = false, canLoadMore = false, tooShort = false)
            }
            return
        }
        if (q.length < 2) {
            inner.update {
                it.copy(results = emptyList(), loading = false, failure = null, searched = false, canLoadMore = false, tooShort = true)
            }
            return
        }
        val language = if (prefs.settings.first().englishOnly) "en" else null
        val spec = NewsQueryEngine.search(q, language)
        activeSpec = spec
        offset = 0
        inner.update { it.copy(loading = true, failure = null, tooShort = false) }
        when (val result = repository.fetchPage(spec, 0)) {
            is PageResult.Success -> {
                inner.update {
                    it.copy(results = result.articles, loading = false, searched = true, canLoadMore = result.hasMore)
                }
                if (result.articles.isNotEmpty()) prefs.addRecentSearch(q)
            }
            is PageResult.Failure -> inner.update {
                it.copy(results = emptyList(), loading = false, searched = true, failure = result.reason, canLoadMore = false)
            }
        }
    }

    fun loadMore() {
        val spec = activeSpec ?: return
        val current = inner.value
        if (current.loadingMore || !current.canLoadMore) return
        viewModelScope.launch {
            inner.update { it.copy(loadingMore = true) }
            offset += spec.pageSize
            when (val result = repository.fetchPage(spec, offset)) {
                is PageResult.Success -> inner.update {
                    it.copy(
                        results = DuplicateDetector.dedupe(it.results + result.articles),
                        loadingMore = false,
                        canLoadMore = result.hasMore,
                    )
                }
                is PageResult.Failure -> inner.update { it.copy(loadingMore = false) }
            }
        }
    }
}
