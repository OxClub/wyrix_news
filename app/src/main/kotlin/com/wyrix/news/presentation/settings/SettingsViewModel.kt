package com.wyrix.news.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyrix.news.data.prefs.FavoriteKind
import com.wyrix.news.data.prefs.ThemeMode
import com.wyrix.news.data.prefs.UserPrefs
import com.wyrix.news.data.prefs.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val prefs: UserPrefs) : ViewModel() {
    val settings: StateFlow<UserSettings> =
        prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setEnglishOnly(value: Boolean) {
        viewModelScope.launch { prefs.setEnglishOnly(value) }
    }

    fun toggleFavorite(kind: FavoriteKind, id: String) {
        viewModelScope.launch { prefs.toggleFavorite(kind, id) }
    }

    fun addTopic(topic: String) {
        viewModelScope.launch { prefs.addTopic(topic) }
    }
}
