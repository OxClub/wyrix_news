package com.wyrix.news.presentation.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.news.R
import com.wyrix.news.domain.BreakingPolicy
import com.wyrix.news.domain.model.Article
import com.wyrix.news.presentation.components.ArticleImage
import com.wyrix.news.presentation.components.BookmarkButton
import com.wyrix.news.presentation.components.BreakingTag
import com.wyrix.news.presentation.components.ExternalActions
import com.wyrix.news.presentation.components.MessageState
import com.wyrix.news.presentation.components.containerViewModel
import com.wyrix.news.presentation.components.relativeTimeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    id: String,
    bookmarkedIds: Set<String>,
    onToggleBookmark: (Article) -> Unit,
    onBack: () -> Unit,
) {
    val vm = containerViewModel<ArticleViewModel>(key = "article-$id") {
        ArticleViewModel(id, it.repository, it.bookmarks)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val loaded = state as? ArticleUiState.Loaded

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (loaded != null) {
                        BookmarkButton(loaded.article.id in bookmarkedIds) { onToggleBookmark(loaded.article) }
                        IconButton(onClick = { ExternalActions.share(context, loaded.article) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val current = state) {
                ArticleUiState.Loading -> Unit
                ArticleUiState.NotFound -> MessageState(title = stringResource(R.string.article_not_found))
                is ArticleUiState.Loaded -> ArticleBody(current.article)
            }
        }
    }
}

@Composable
private fun ArticleBody(article: Article) {
    val context = LocalContext.current
    Column(Modifier.widthIn(max = 720.dp).verticalScroll(rememberScrollState())) {
        article.imageUrl?.let {
            ArticleImage(it, Modifier.fillMaxWidth().height(220.dp))
        }
        Column(Modifier.padding(16.dp)) {
            if (BreakingPolicy.isBreaking(article, System.currentTimeMillis())) {
                BreakingTag()
                Spacer(Modifier.height(8.dp))
            }
            Text(article.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = article.sourceName + " • " + relativeTimeText(article.publishedAt),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            article.author?.let {
                Text(
                    text = stringResource(R.string.article_by, it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            article.description?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { ExternalActions.openUrl(context, article.url) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_read_original)) }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.article_source, article.sourceName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
