package com.wyrix.news.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.news.R
import com.wyrix.news.presentation.components.ArticleActions
import com.wyrix.news.presentation.components.MessageState
import com.wyrix.news.presentation.components.SectionHeader
import com.wyrix.news.presentation.components.containerViewModel
import com.wyrix.news.presentation.components.errorRes
import com.wyrix.news.presentation.feed.FeedList
import com.wyrix.news.presentation.feed.FeedUiState

@Composable
fun SearchScreen(
    bookmarkedIds: Set<String>,
    actions: ArticleActions,
    onBack: () -> Unit,
) {
    val vm = containerViewModel<SearchViewModel> { SearchViewModel(it.repository, it.prefs) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { vm.onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear_query))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.submit() }),
                )
                Spacer(Modifier.width(0.dp))
            }

            val failure = state.failure
            when {
                state.query.isBlank() -> RecentSearches(state.recent, vm::selectRecent, vm::clearRecent)
                state.tooShort -> MessageState(title = stringResource(R.string.search_prompt))
                state.searched && failure != null && state.results.isEmpty() -> MessageState(
                    title = stringResource(R.string.err_title),
                    body = stringResource(failure.errorRes()),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::retry,
                )
                else -> FeedList(
                    state = FeedUiState(
                        articles = state.results,
                        isLoading = state.isLoading || !state.searched,
                        canLoadMore = state.canLoadMore,
                        isLoadingMore = state.isLoadingMore,
                    ),
                    bookmarkedIds = bookmarkedIds,
                    actions = actions,
                    modifier = Modifier.fillMaxSize(),
                    onLoadMore = vm::loadMore,
                    emptyText = stringResource(R.string.search_no_results),
                )
            }
        }
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onSelect: (String) -> Unit, onClear: () -> Unit) {
    if (recent.isEmpty()) {
        MessageState(title = stringResource(R.string.search_start))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(stringResource(R.string.search_recent), Modifier.weight(1f))
                TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
            }
        }
        items(recent, key = { it }) { query ->
            ListItem(
                headlineContent = { Text(query) },
                modifier = Modifier.clickable { onSelect(query) },
            )
        }
    }
}
