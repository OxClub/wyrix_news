package com.wyrix.news.presentation.saved

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.news.R
import com.wyrix.news.presentation.components.ArticleActions
import com.wyrix.news.presentation.components.NoInsets
import com.wyrix.news.presentation.components.containerViewModel
import com.wyrix.news.presentation.feed.FeedList
import com.wyrix.news.presentation.feed.FeedUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(bookmarkedIds: Set<String>, actions: ArticleActions) {
    val vm = containerViewModel<SavedViewModel> { SavedViewModel(it.bookmarks) }
    val articles by vm.articles.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_saved)) }) },
    ) { padding ->
        FeedList(
            state = FeedUiState(articles = articles.orEmpty(), isLoading = articles == null),
            bookmarkedIds = bookmarkedIds,
            actions = actions,
            modifier = Modifier.padding(padding).fillMaxSize(),
            emptyText = stringResource(R.string.saved_empty),
        )
    }
}
