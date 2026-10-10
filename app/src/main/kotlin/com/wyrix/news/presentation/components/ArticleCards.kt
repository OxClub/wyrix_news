package com.wyrix.news.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wyrix.news.R
import com.wyrix.news.domain.model.Article

@Composable
fun ArticleImage(url: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.surfaceVariant
    val painter = remember(color) { ColorPainter(color) }
    AsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        placeholder = painter,
        error = painter,
    )
}

@Composable
private fun sourceLine(article: Article): String =
    article.sourceName + " • " + relativeTimeText(article.publishedAt)

@Composable
fun ArticleCard(
    article: Article,
    bookmarked: Boolean,
    actions: ArticleActions,
    modifier: Modifier = Modifier,
    showBreaking: Boolean = false,
) {
    Card(
        onClick = { actions.onOpen(article) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            article.imageUrl?.let { ArticleImage(it, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                if (showBreaking) {
                    BreakingTag()
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                article.description?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sourceLine(article),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    BookmarkButton(bookmarked) { actions.onToggleBookmark(article) }
                    IconButton(onClick = { actions.onShare(article) }) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.action_share),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactArticleCard(
    article: Article,
    bookmarked: Boolean,
    actions: ArticleActions,
    modifier: Modifier = Modifier,
    showBreaking: Boolean = false,
) {
    Card(
        onClick = { actions.onOpen(article) },
        modifier = modifier.width(260.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            article.imageUrl?.let { ArticleImage(it, Modifier.fillMaxWidth().height(130.dp)) }
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
                if (showBreaking) {
                    BreakingTag()
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sourceLine(article),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    BookmarkButton(bookmarked) { actions.onToggleBookmark(article) }
                }
            }
        }
    }
}
