package com.wyrix.news.presentation.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyrix.news.data.repository.BookmarkRepository
import com.wyrix.news.data.repository.NewsRepository
import com.wyrix.news.domain.model.Article
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ArticleUiState {
    data object Loading : ArticleUiState
    data class Loaded(val article: Article) : ArticleUiState
    data object NotFound : ArticleUiState
}

class ArticleViewModel(
    private val id: String,
    private val repository: NewsRepository,
    private val bookmarks: BookmarkRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ArticleUiState>(ArticleUiState.Loading)
    val state: StateFlow<ArticleUiState> = _state

    init {
        viewModelScope.launch {
            val article = repository.article(id) ?: bookmarks.get(id)
            _state.value = if (article != null) ArticleUiState.Loaded(article) else ArticleUiState.NotFound
        }
    }
}
