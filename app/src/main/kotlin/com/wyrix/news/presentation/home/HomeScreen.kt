package com.wyrix.news.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.news.R
import com.wyrix.news.domain.model.Article
import com.wyrix.news.presentation.components.ArticleActions
import com.wyrix.news.presentation.components.ArticleCard
import com.wyrix.news.presentation.components.BreakingTag
import com.wyrix.news.presentation.components.CompactArticleCard
import com.wyrix.news.presentation.components.MessageState
import com.wyrix.news.presentation.components.NoInsets
import com.wyrix.news.presentation.components.NoticeBanner
import com.wyrix.news.presentation.components.SectionHeader
import com.wyrix.news.presentation.components.SkeletonList
import com.wyrix.news.presentation.components.containerViewModel
import com.wyrix.news.presentation.components.errorRes
import com.wyrix.news.presentation.components.noticeRes
import com.wyrix.news.presentation.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bookmarkedIds: Set<String>,
    actions: ArticleActions,
    onNavigate: (String) -> Unit,
) {
    val vm = containerViewModel<HomeViewModel> { HomeViewModel(it.repository, it.prefs) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                actions = {
                    IconButton(onClick = { onNavigate(Routes.SEARCH) }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                    }
                    IconButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val failure = state.failure
            when {
                !state.hasContent && state.isLoading -> SkeletonList()
                !state.hasContent && failure != null -> MessageState(
                    title = stringResource(R.string.err_title),
                    body = stringResource(failure.errorRes()),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::refresh,
                )
                !state.hasContent -> MessageState(title = stringResource(R.string.msg_no_news))
                else -> HomeContent(state, bookmarkedIds, actions, onNavigate)
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    bookmarkedIds: Set<String>,
    actions: ArticleActions,
    onNavigate: (String) -> Unit,
) {
    val topStories = state.topStories.take(4)
    val topIds = topStories.map { it.id }.toSet()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        state.failure?.let { failure ->
            item(key = "notice") {
                NoticeBanner(stringResource(failure.noticeRes()), Modifier.padding(16.dp))
            }
        }
        if (state.breaking.isNotEmpty()) {
            item(key = "breaking-header") {
                SectionHeader(
                    title = stringResource(R.string.section_breaking),
                    onViewAll = { onNavigate(Routes.feed("breaking")) },
                    leading = {
                        BreakingTag()
                        Spacer(Modifier.padding(end = 8.dp))
                    },
                )
            }
            item(key = "breaking-row") {
                ArticleRow(state.breaking, bookmarkedIds, actions, showBreaking = true)
            }
        }
        if (topStories.isNotEmpty()) {
            item(key = "top-header") {
                SectionHeader(
                    title = stringResource(R.string.section_top_stories),
                    onViewAll = { onNavigate(Routes.feed("latest")) },
                )
            }
            items(topStories, key = { "top-" + it.id }) { article ->
                ArticleCard(
                    article = article,
                    bookmarked = article.id in bookmarkedIds,
                    actions = actions,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        state.sections.forEach { sectionState ->
            val section = sectionState.section
            val articles = if (section.id == HomeSectionId.LATEST) {
                sectionState.articles.filter { it.id !in topIds }
            } else {
                sectionState.articles
            }
            if (articles.isEmpty()) return@forEach
            item(key = "header-" + section.spec.key) {
                SectionHeader(
                    title = sectionTitle(section),
                    modifier = Modifier.padding(top = 8.dp),
                    onViewAll = { onNavigate(section.viewAllRoute) },
                )
            }
            item(key = "row-" + section.spec.key) {
                ArticleRow(articles.take(10), bookmarkedIds, actions)
            }
        }
    }
}

@Composable
private fun sectionTitle(section: HomeSection): String = when (section.id) {
    HomeSectionId.LATEST -> stringResource(R.string.section_latest)
    HomeSectionId.FOR_YOU -> stringResource(R.string.section_for_you, section.title.orEmpty())
    HomeSectionId.INDIA -> stringResource(R.string.section_india)
    HomeSectionId.WORLD -> stringResource(R.string.section_world)
    HomeSectionId.BUSINESS -> stringResource(R.string.section_business)
    HomeSectionId.TECHNOLOGY -> stringResource(R.string.section_technology)
    HomeSectionId.SPORTS -> stringResource(R.string.section_sports)
    HomeSectionId.ENTERTAINMENT -> stringResource(R.string.section_entertainment)
    HomeSectionId.HEALTH -> stringResource(R.string.section_health)
}

@Composable
private fun ArticleRow(
    articles: List<Article>,
    bookmarkedIds: Set<String>,
    actions: ArticleActions,
    showBreaking: Boolean = false,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(articles, key = { it.id }) { article ->
            CompactArticleCard(article, article.id in bookmarkedIds, actions, showBreaking = showBreaking)
        }
    }
}
