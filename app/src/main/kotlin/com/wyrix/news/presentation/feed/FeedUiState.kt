package com.wyrix.news.presentation.feed

import com.wyrix.news.data.repository.FailureReason
import com.wyrix.news.domain.model.Article

data class FeedUiState(
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val failure: FailureReason? = null,
    val lastUpdated: Long? = null,
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
)
