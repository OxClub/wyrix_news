package com.wyrix.news.presentation.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wyrix.news.R
import com.wyrix.news.domain.NewsQueryEngine
import com.wyrix.news.domain.model.Category
import com.wyrix.news.domain.model.FeedSpec
import com.wyrix.news.domain.model.IndiaRegion
import com.wyrix.news.domain.model.IndiaRegions
import com.wyrix.news.domain.model.WorldCountries
import com.wyrix.news.domain.model.WorldCountry
import com.wyrix.news.domain.model.WorldRegion
import com.wyrix.news.presentation.components.ArticleActions
import com.wyrix.news.presentation.components.NameTile
import com.wyrix.news.presentation.components.NoInsets
import com.wyrix.news.presentation.components.SectionHeader
import com.wyrix.news.presentation.feed.FeedPane
import com.wyrix.news.presentation.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(onNavigate: (String) -> Unit) {
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_explore)) },
                actions = {
                    IconButton(onClick = { onNavigate(Routes.SEARCH) }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { onNavigate(Routes.SEARCH) },
                )
            }
            item(key = "quick-breaking") {
                NameTile(stringResource(R.string.section_breaking), { onNavigate(Routes.feed("breaking")) })
            }
            item(key = "quick-india") {
                NameTile(stringResource(R.string.section_india), { onNavigate(Routes.feed("india")) })
            }
            item(key = "quick-world") {
                NameTile(stringResource(R.string.section_world), { onNavigate(Routes.feed("world")) })
            }
            item(key = "categories", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(R.string.label_categories))
            }
            items(Category.entries.toList(), key = { "cat-" + it.id }) { category ->
                NameTile(category.label, { onNavigate(Routes.feed("category", category.id)) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndiaScreen(onNavigate: (String) -> Unit) {
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_india)) }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "all", span = { GridItemSpan(maxLineSpan) }) {
                FilledTonalButton(
                    onClick = { onNavigate(Routes.feed("india")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.india_all)) }
            }
            item(key = "states-h", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(R.string.india_states))
            }
            items(IndiaRegions.states, key = { it.id }) { region ->
                NameTile(region.name, { onNavigate(Routes.state(region.id)) })
            }
            item(key = "uts-h", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(stringResource(R.string.india_uts))
            }
            items(IndiaRegions.unionTerritories, key = { it.id }) { region ->
                NameTile(region.name, { onNavigate(Routes.state(region.id)) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldScreen(onNavigate: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query) {
        WorldCountries.all.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_world)) }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.world_search_country)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isBlank()) {
                item(key = "all", span = { GridItemSpan(maxLineSpan) }) {
                    FilledTonalButton(
                        onClick = { onNavigate(Routes.feed("world")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.world_all)) }
                }
                item(key = "popular-h", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(stringResource(R.string.world_popular))
                }
                items(WorldCountries.popular, key = { "p-" + it.iso }) { country ->
                    NameTile(country.name, { onNavigate(Routes.country(country.iso)) })
                }
                WorldRegion.entries.forEach { region ->
                    val countries = WorldCountries.inRegion(region)
                    if (countries.isNotEmpty()) {
                        item(key = "h-" + region.name, span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(region.label)
                        }
                        items(countries, key = { "r-" + it.iso }) { country ->
                            NameTile(country.name, { onNavigate(Routes.country(country.iso)) })
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.world_no_match))
                }
            } else {
                items(filtered, key = { "f-" + it.iso }) { country ->
                    NameTile(country.name, { onNavigate(Routes.country(country.iso)) })
                }
            }
        }
    }
}

/** State / union territory screen with category chips. */
@Composable
fun StateScreen(
    region: IndiaRegion,
    bookmarkedIds: Set<String>,
    actions: ArticleActions,
    onBack: () -> Unit,
) {
    CategoryFeedScreen(
        title = region.name,
        categories = Category.stateCategories,
        vmPrefix = "state-" + region.id,
        bookmarkedIds = bookmarkedIds,
        actions = actions,
        onBack = onBack,
        specFor = { category, language -> NewsQueryEngine.indiaRegion(region, category, language) },
    )
}

@Composable
fun CountryScreen(
    country: WorldCountry,
    bookmarkedIds: Set<String>,
    actions: ArticleActions,
    onBack: () -> Unit,
) {
    CategoryFeedScreen(
        title = country.name,
        categories = Category.countryCategories,
        vmPrefix = "country-" + country.iso,
        bookmarkedIds = bookmarkedIds,
        actions = actions,
        onBack = onBack,
        specFor = { category, language -> NewsQueryEngine.country(country, category, language) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFeedScreen(
    title: String,
    categories: List<Category>,
    vmPrefix: String,
    bookmarkedIds: Set<String>,
    actions: ArticleActions,
    onBack: () -> Unit,
    specFor: (Category, String?) -> FeedSpec,
) {
    var selectedId by rememberSaveable { mutableStateOf(categories.first().id) }
    val selected = categories.firstOrNull { it.id == selectedId } ?: categories.first()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = category.id == selected.id,
                        onClick = { selectedId = category.id },
                        label = { Text(category.label) },
                    )
                }
            }
            key(selected.id) {
                FeedPane(
                    vmKey = vmPrefix + "-" + selected.id,
                    bookmarkedIds = bookmarkedIds,
                    actions = actions,
                    modifier = Modifier.weight(1f),
                    specBuilder = { language -> specFor(selected, language) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    kind: String,
    arg: String,
    bookmarkedIds: Set<String>,
    actions: ArticleActions,
    onBack: () -> Unit,
) {
    val title = when (kind) {
        "breaking" -> stringResource(R.string.section_breaking)
        "india" -> stringResource(R.string.section_india)
        "world" -> stringResource(R.string.section_world)
        "category" -> Category.fromId(arg)?.label ?: stringResource(R.string.section_latest)
        "topic" -> arg
        else -> stringResource(R.string.section_latest)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        FeedPane(
            vmKey = "feed-$kind-$arg",
            bookmarkedIds = bookmarkedIds,
            actions = actions,
            modifier = Modifier.padding(padding).fillMaxSize(),
            breakingOnly = kind == "breaking",
            specBuilder = { language -> NewsQueryEngine.forRoute(kind, arg, language) },
        )
    }
}
