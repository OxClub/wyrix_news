package com.wyrix.news.presentation.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.news.R
import com.wyrix.news.domain.model.FeedSpec
import com.wyrix.news.presentation.components.ArticleActions
import com.wyrix.news.presentation.components.ArticleCard
import com.wyrix.news.presentation.components.MessageState
import com.wyrix.news.presentation.components.NoticeBanner
import com.wyrix.news.presentation.components.SkeletonList
import com.wyrix.news.presentation.components.containerViewModel
import com.wyrix.news.presentation.components.errorRes
import com.wyrix.news.presentation.components.noticeRes
import com.wyrix.news.presentation.components.updatedText

/** A feed backed by its own ViewModel (cache-first, pull to refresh, load more). */
@Composable
fun FeedPane(
    vmKey: String,
    bookmarkedIds: Set<String>,
    actions: ArticleActions,
    modifier: Modifier = Modifier,
    breakingOnly: Boolean = false,
    specBuilder: (String?) -> FeedSpec,
) {
    val vm = containerViewModel<FeedViewModel>(key = vmKey) { container ->
        FeedViewModel(container.repository, container.prefs, specBuilder, breakingOnly)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    FeedList(
        state = state,
        bookmarkedIds = bookmarkedIds,
        actions = actions,
        modifier = modifier,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedList(
    state: FeedUiState,
    bookmarkedIds: Set<String>,
    actions: ArticleActions,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    emptyText: String = stringResource(R.string.msg_no_news),
) {
    if (onRefresh != null) {
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = modifier) {
            FeedListBody(state, bookmarkedIds, actions, onRefresh, onLoadMore, emptyText)
        }
    } else {
        Box(modifier) {
            FeedListBody(state, bookmarkedIds, actions, null, onLoadMore, emptyText)
        }
    }
}

@Composable
private fun FeedListBody(
    state: FeedUiState,
    bookmarkedIds: Set<String>,
    actions: ArticleActions,
    onRefresh: (() -> Unit)?,
    onLoadMore: (() -> Unit)?,
    emptyText: String,
) {
    val failure = state.failure
    when {
        state.articles.isEmpty() && state.isLoading -> SkeletonList()
        state.articles.isEmpty() && failure != null -> MessageState(
            title = stringResource(R.string.err_title),
            body = stringResource(failure.errorRes()),
            actionLabel = if (onRefresh != null) stringResource(R.string.action_retry) else null,
            onAction = onRefresh,
        )
        state.articles.isEmpty() -> MessageState(title = emptyText)
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (failure != null) {
                item(key = "notice", span = { GridItemSpan(maxLineSpan) }) {
                    NoticeBanner(stringResource(failure.noticeRes()))
                }
            }
            state.lastUpdated?.let { updated ->
                item(key = "updated", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = updatedText(updated),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.articles, key = { it.id }) { article ->
                ArticleCard(article, article.id in bookmarkedIds, actions)
            }
            if (onLoadMore != null && state.canLoadMore) {
                item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.isLoadingMore) {
                            CircularProgressIndicator()
                        } else {
                            TextButton(onClick = onLoadMore) { Text(stringResource(R.string.action_load_more)) }
                        }
                    }
                }
            }
        }
    }
}
