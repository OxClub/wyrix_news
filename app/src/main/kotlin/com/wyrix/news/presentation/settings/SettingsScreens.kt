package com.wyrix.news.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wyrix.news.BuildConfig
import com.wyrix.news.R
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.wyrix.news.data.prefs.FavoriteKind
import com.wyrix.news.data.prefs.NewsLanguage
import com.wyrix.news.data.prefs.ThemeMode
import com.wyrix.news.domain.model.Category
import com.wyrix.news.domain.model.IndiaRegions
import com.wyrix.news.domain.model.WorldCountries
import com.wyrix.news.presentation.components.SectionHeader
import com.wyrix.news.presentation.components.containerViewModel
import com.wyrix.news.presentation.components.isHindiUi
import com.wyrix.news.presentation.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) { content() }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val vm = containerViewModel<SettingsViewModel> { SettingsViewModel(it.prefs) }
    val settings by vm.settings.collectAsStateWithLifecycle()

    SubScreen(stringResource(R.string.action_settings), onBack) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_appearance))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(ThemeMode.SYSTEM, R.string.settings_theme_system, settings.themeMode, vm::setTheme)
                ThemeChip(ThemeMode.LIGHT, R.string.settings_theme_light, settings.themeMode, vm::setTheme)
                ThemeChip(ThemeMode.DARK, R.string.settings_theme_dark, settings.themeMode, vm::setTheme)
            }
            SectionHeader(stringResource(R.string.settings_language))
            val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageChip("", stringResource(R.string.language_system), currentTag, vm::onAppLanguageChosen)
                LanguageChip("en", stringResource(R.string.lang_name_en), currentTag, vm::onAppLanguageChosen)
                LanguageChip("hi", stringResource(R.string.lang_name_hi), currentTag, vm::onAppLanguageChosen)
            }
            SectionHeader(stringResource(R.string.settings_news_language))
            Text(
                text = stringResource(R.string.settings_news_language_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NewsLanguageChip(NewsLanguage.ENGLISH, stringResource(R.string.lang_name_en), settings.newsLanguage, vm::setNewsLanguage)
                NewsLanguageChip(NewsLanguage.HINDI, stringResource(R.string.lang_name_hi), settings.newsLanguage, vm::setNewsLanguage)
                NewsLanguageChip(NewsLanguage.ALL, stringResource(R.string.news_lang_all), settings.newsLanguage, vm::setNewsLanguage)
            }
            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_favorites)) },
                supportingContent = { Text(stringResource(R.string.settings_favorites_desc)) },
                modifier = Modifier.clickable { onNavigate(Routes.FAVORITES) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_sources)) },
                modifier = Modifier.clickable { onNavigate(Routes.info("sources")) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy)) },
                modifier = Modifier.clickable { onNavigate(Routes.info("privacy")) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_licenses)) },
                modifier = Modifier.clickable { onNavigate(Routes.info("licenses")) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                modifier = Modifier.clickable { onNavigate(Routes.info("about")) },
            )
        }
    }
}

@Composable
private fun LanguageChip(tag: String, label: String, currentTag: String, onChosen: (String) -> Unit) {
    val selected = if (tag.isEmpty()) currentTag.isEmpty() else currentTag.substringBefore('-') == tag
    FilterChip(
        selected = selected,
        onClick = {
            onChosen(tag)
            AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
            )
        },
        label = { Text(label) },
    )
}

@Composable
private fun NewsLanguageChip(
    language: NewsLanguage,
    label: String,
    current: NewsLanguage,
    onSelect: (NewsLanguage) -> Unit,
) {
    FilterChip(selected = language == current, onClick = { onSelect(language) }, label = { Text(label) })
}

@Composable
private fun ThemeChip(mode: ThemeMode, label: Int, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    FilterChip(selected = mode == current, onClick = { onSelect(mode) }, label = { Text(stringResource(label)) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesScreen(onBack: () -> Unit) {
    val vm = containerViewModel<SettingsViewModel> { SettingsViewModel(it.prefs) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var topic by rememberSaveable { mutableStateOf("") }
    val hindi = isHindiUi()

    SubScreen(stringResource(R.string.settings_favorites), onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionHeader(stringResource(R.string.favorites_states))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IndiaRegions.all.forEach { region ->
                    FilterChip(
                        selected = region.id in settings.favoriteStates,
                        onClick = { vm.toggleFavorite(FavoriteKind.STATE, region.id) },
                        label = { Text(region.nameFor(hindi)) },
                    )
                }
            }
            SectionHeader(stringResource(R.string.favorites_countries))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WorldCountries.all.forEach { country ->
                    FilterChip(
                        selected = country.iso in settings.favoriteCountries,
                        onClick = { vm.toggleFavorite(FavoriteKind.COUNTRY, country.iso) },
                        label = { Text(country.nameFor(hindi)) },
                    )
                }
            }
            SectionHeader(stringResource(R.string.label_categories))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Category.entries.filter { it != Category.LATEST }.forEach { category ->
                    FilterChip(
                        selected = category.id in settings.favoriteCategories,
                        onClick = { vm.toggleFavorite(FavoriteKind.CATEGORY, category.id) },
                        label = { Text(category.labelFor(hindi)) },
                    )
                }
            }
            SectionHeader(stringResource(R.string.favorites_topics))
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                settings.favoriteTopics.sorted().forEach { saved ->
                    FilterChip(
                        selected = true,
                        onClick = { vm.toggleFavorite(FavoriteKind.TOPIC, saved) },
                        label = { Text(saved) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it.take(50) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.favorites_topic_hint)) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        vm.addTopic(topic)
                        topic = ""
                    },
                    enabled = topic.trim().length >= 2,
                ) { Text(stringResource(R.string.action_add)) }
            }
        }
    }
}

@Composable
fun InfoScreen(type: String, onBack: () -> Unit) {
    val (titleRes, bodyRes) = when (type) {
        "sources" -> R.string.settings_sources to R.string.sources_body
        "privacy" -> R.string.settings_privacy to R.string.privacy_body
        "licenses" -> R.string.settings_licenses to R.string.licenses_body
        else -> R.string.settings_about to R.string.about_body
    }
    SubScreen(stringResource(titleRes), onBack) {
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        )
    }
}
