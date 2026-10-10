package com.wyrix.news.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wyrix.news.data.prefs.UserPrefs
import com.wyrix.news.data.prefs.UserSettings
import com.wyrix.news.data.repository.FailureReason
import com.wyrix.news.data.repository.NewsRepository
import com.wyrix.news.data.repository.RefreshResult
import com.wyrix.news.domain.ArticleRanker
import com.wyrix.news.domain.BreakingPolicy
import com.wyrix.news.domain.NewsQueryEngine
import com.wyrix.news.domain.model.Article
import com.wyrix.news.domain.model.Category
import com.wyrix.news.domain.model.FeedSpec
import com.wyrix.news.domain.model.IndiaRegions
import com.wyrix.news.domain.model.WorldCountries
import com.wyrix.news.presentation.navigation.Routes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HomeSectionId { LATEST, FOR_YOU, INDIA, WORLD, BUSINESS, TECHNOLOGY, SPORTS, ENTERTAINMENT, HEALTH }

data class HomeSection(
    val id: HomeSectionId,
    val spec: FeedSpec,
    val title: String? = null,
    val viewAllRoute: String,
)

data class HomeSectionState(val section: HomeSection, val articles: List<Article>)

data class HomeUiState(
    val breaking: List<Article> = emptyList(),
    val topStories: List<Article> = emptyList(),
    val sections: List<HomeSectionState> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val failure: FailureReason? = null,
    val lastUpdated: Long? = null,
) {
    val hasContent: Boolean
        get() = breaking.isNotEmpty() || topStories.isNotEmpty() || sections.any { it.articles.isNotEmpty() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: NewsRepository,
    prefs: UserPrefs,
    private val ranker: ArticleRanker = ArticleRanker(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private data class Status(
        val initialLoading: Boolean = true,
        val refreshing: Boolean = false,
        val failure: FailureReason? = null,
        val lastUpdated: Long? = null,
    )

    private data class Plan(val sections: List<HomeSection>, val preferenceTerms: Set<String>)

    private val status = MutableStateFlow(Status())

    @Volatile
    private var currentSections: List<HomeSection> = emptyList()

    private val planFlow: Flow<Plan> = prefs.settings
        .map { Plan(buildSections(it), preferenceTerms(it)) }
        .distinctUntilChanged()

    val state: StateFlow<HomeUiState> = planFlow.flatMapLatest { plan ->
        val sectionFlows = plan.sections.map { section ->
            repository.observe(section.spec).map { HomeSectionState(section, it) }
        }
        combine(combine(sectionFlows) { it.toList() }, status) { sections, st ->
            val latest = sections.firstOrNull { it.section.id == HomeSectionId.LATEST }?.articles.orEmpty()
            val now = clock()
            HomeUiState(
                breaking = latest.filter { BreakingPolicy.isBreaking(it, now) }.take(5),
                topStories = ranker.rank(latest, plan.preferenceTerms).take(6),
                sections = sections.filter { it.articles.isNotEmpty() },
                isLoading = st.initialLoading,
                isRefreshing = st.refreshing,
                failure = st.failure,
                lastUpdated = st.lastUpdated,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            planFlow.collectLatest { plan ->
                currentSections = plan.sections
                refreshAll(plan.sections, force = false)
            }
        }
    }

    fun refresh() {
        val sections = currentSections
        if (sections.isEmpty()) return
        viewModelScope.launch { refreshAll(sections, force = true) }
    }

    private suspend fun refreshAll(sections: List<HomeSection>, force: Boolean) {
        status.update { it.copy(refreshing = true) }
        val results = coroutineScope {
            sections.map { section -> async { repository.refresh(section.spec, force) } }.awaitAll()
        }
        val attempted = results.filter { it !is RefreshResult.Throttled }
        val failures = attempted.filterIsInstance<RefreshResult.Failed>().map { it.reason }
        val updated = sections.firstOrNull()?.let { repository.lastUpdated(it.spec) }
        status.update { st ->
            val failure = when {
                attempted.isEmpty() -> st.failure
                failures.size == attempted.size ->
                    failures.firstOrNull { it == FailureReason.OFFLINE } ?: failures.first()
                else -> null
            }
            st.copy(initialLoading = false, refreshing = false, failure = failure, lastUpdated = updated)
        }
    }

    private fun buildSections(settings: UserSettings): List<HomeSection> {
        val lang = if (settings.englishOnly) "en" else null
        val sections = ArrayList<HomeSection>()
        sections += HomeSection(HomeSectionId.LATEST, NewsQueryEngine.latest(lang), null, Routes.feed("latest"))

        // Up to two personalised sections, in this order: state, country, category, topic.
        val forYou = ArrayList<HomeSection>()
        settings.favoriteStates.sorted().firstNotNullOfOrNull { IndiaRegions.byId(it) }?.let { region ->
            forYou += HomeSection(
                HomeSectionId.FOR_YOU,
                NewsQueryEngine.indiaRegion(region, Category.LATEST, lang),
                region.name,
                Routes.state(region.id),
            )
        }
        settings.favoriteCountries.sorted().firstNotNullOfOrNull { WorldCountries.byIso(it) }?.let { country ->
            forYou += HomeSection(
                HomeSectionId.FOR_YOU,
                NewsQueryEngine.country(country, Category.LATEST, lang),
                country.name,
                Routes.country(country.iso),
            )
        }
        settings.favoriteCategories.sorted().firstNotNullOfOrNull { Category.fromId(it) }?.let { category ->
            forYou += HomeSection(
                HomeSectionId.FOR_YOU,
                NewsQueryEngine.category(category, lang),
                category.label,
                Routes.feed("category", category.id),
            )
        }
        settings.favoriteTopics.sorted().firstOrNull()?.let { topic ->
            forYou += HomeSection(
                HomeSectionId.FOR_YOU,
                NewsQueryEngine.topic(topic, lang),
                topic,
                Routes.feed("topic", topic),
            )
        }
        sections += forYou.take(2)

        sections += HomeSection(HomeSectionId.INDIA, NewsQueryEngine.india(lang), null, Routes.feed("india"))
        sections += HomeSection(HomeSectionId.WORLD, NewsQueryEngine.world(lang), null, Routes.feed("world"))
        fun category(id: HomeSectionId, c: Category) =
            HomeSection(id, NewsQueryEngine.category(c, lang), null, Routes.feed("category", c.id))
        sections += category(HomeSectionId.BUSINESS, Category.BUSINESS)
        sections += category(HomeSectionId.TECHNOLOGY, Category.TECHNOLOGY)
        sections += category(HomeSectionId.SPORTS, Category.SPORTS)
        sections += category(HomeSectionId.ENTERTAINMENT, Category.ENTERTAINMENT)
        sections += category(HomeSectionId.HEALTH, Category.HEALTH)
        return sections
    }

    private fun preferenceTerms(settings: UserSettings): Set<String> {
        val terms = HashSet<String>()
        settings.favoriteStates.mapNotNull { IndiaRegions.byId(it) }.forEach { terms += it.queryTerm.lowercase() }
        settings.favoriteCountries.mapNotNull { WorldCountries.byIso(it) }.forEach { terms += it.name.lowercase() }
        settings.favoriteCategories.mapNotNull { Category.fromId(it)?.keyword }.forEach { terms += it.lowercase() }
        settings.favoriteTopics.forEach { terms += it.lowercase() }
        return terms
    }
}
