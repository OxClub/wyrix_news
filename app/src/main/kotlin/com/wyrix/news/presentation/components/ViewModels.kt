package com.wyrix.news.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wyrix.news.di.AppContainer
import com.wyrix.news.di.appContainer

@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline builder: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.appContainer()
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = builder(container) as T
        },
    )
}
