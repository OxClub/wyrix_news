package com.wyrix.news.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.oxDataStore: DataStore<Preferences> by preferencesDataStore(name = "wyrix_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class NewsLanguage(val code: String?) { ENGLISH("en"), HINDI("hi"), ALL(null) }

enum class FavoriteKind { STATE, COUNTRY, CATEGORY, TOPIC }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val newsLanguage: NewsLanguage = NewsLanguage.ENGLISH,
    val favoriteStates: Set<String> = emptySet(),
    val favoriteCountries: Set<String> = emptySet(),
    val favoriteCategories: Set<String> = emptySet(),
    val favoriteTopics: Set<String> = emptySet(),
    val recentSearches: List<String> = emptyList(),
) {
    /** Value for the provider's lang filter; null means all languages. */
    val languageCode: String? get() = newsLanguage.code
}

class UserPrefs(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val ENGLISH_ONLY = booleanPreferencesKey("english_only")
        val NEWS_LANGUAGE = stringPreferencesKey("news_language")
        val FAV_STATES = stringSetPreferencesKey("fav_states")
        val FAV_COUNTRIES = stringSetPreferencesKey("fav_countries")
        val FAV_CATEGORIES = stringSetPreferencesKey("fav_categories")
        val FAV_TOPICS = stringSetPreferencesKey("fav_topics")
        val RECENT = stringPreferencesKey("recent_searches")
    }

    val settings: Flow<UserSettings> = context.oxDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            UserSettings(
                themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                newsLanguage = p[Keys.NEWS_LANGUAGE]?.let { runCatching { NewsLanguage.valueOf(it) }.getOrNull() }
                    ?: when (p[Keys.ENGLISH_ONLY]) {
                        false -> NewsLanguage.ALL
                        else -> if (Locale.getDefault().language == "hi") NewsLanguage.HINDI else NewsLanguage.ENGLISH
                    },
                favoriteStates = p[Keys.FAV_STATES].orEmpty(),
                favoriteCountries = p[Keys.FAV_COUNTRIES].orEmpty(),
                favoriteCategories = p[Keys.FAV_CATEGORIES].orEmpty(),
                favoriteTopics = p[Keys.FAV_TOPICS].orEmpty(),
                recentSearches = p[Keys.RECENT]?.split('\n')?.filter { it.isNotBlank() }.orEmpty(),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.oxDataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setNewsLanguage(language: NewsLanguage) {
        context.oxDataStore.edit { it[Keys.NEWS_LANGUAGE] = language.name }
    }

    suspend fun toggleFavorite(kind: FavoriteKind, id: String) {
        val key = when (kind) {
            FavoriteKind.STATE -> Keys.FAV_STATES
            FavoriteKind.COUNTRY -> Keys.FAV_COUNTRIES
            FavoriteKind.CATEGORY -> Keys.FAV_CATEGORIES
            FavoriteKind.TOPIC -> Keys.FAV_TOPICS
        }
        context.oxDataStore.edit { p ->
            val current = p[key].orEmpty()
            p[key] = if (id in current) current - id else current + id
        }
    }

    suspend fun addTopic(topic: String) {
        val clean = topic.trim().take(50)
        if (clean.length < 2) return
        context.oxDataStore.edit { p -> p[Keys.FAV_TOPICS] = p[Keys.FAV_TOPICS].orEmpty() + clean }
    }

    suspend fun addRecentSearch(query: String) {
        val clean = query.trim().take(100)
        if (clean.isEmpty()) return
        context.oxDataStore.edit { p ->
            val current = p[Keys.RECENT]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
            val updated = (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) }).take(8)
            p[Keys.RECENT] = updated.joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() {
        context.oxDataStore.edit { it.remove(Keys.RECENT) }
    }
}
