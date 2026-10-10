package com.wyrix.news.presentation.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyrix.news.data.repository.BookmarkRepository
import com.wyrix.news.domain.model.Article
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Shared by the whole app: which articles are saved, and toggling. */
class BookmarksViewModel(private val repository: BookmarkRepository) : ViewModel() {
    val ids: StateFlow<Set<String>> =
        repository.ids.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun toggle(article: Article) {
        viewModelScope.launch { repository.toggle(article) }
    }
}

class SavedViewModel(repository: BookmarkRepository) : ViewModel() {
    /** null while the database is still loading. */
    val articles: StateFlow<List<Article>?> =
        repository.articles.stateIn<List<Article>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
