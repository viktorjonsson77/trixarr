package org.jellyfin.androidtv.ui.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import java.util.UUID
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.data.repository.ItemMutationRepository
import org.jellyfin.androidtv.seerr.SeerrClientProvider
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.itemdetail.versionLabel
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher
import org.jellyfin.androidtv.util.PlaybackHelper
import org.jellyfin.androidtv.util.sdk.TrailerUtils
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.koin.android.ext.android.inject

internal enum class CatalogScreen(val label: String) {
    Home("Hem"),
    Discover("Upptäck"),
    Movies("Filmer"),
    Series("Serier"),
    Search("Sök"),
    Collection(""),
    Details(""),
    Information(""),
    Episodes(""),
}

internal class CatalogPage(val screen: CatalogScreen, title: CatalogTitle? = null) {
    var heading by mutableStateOf("")
    var title by mutableStateOf(title)
    var items by mutableStateOf(emptyList<CatalogTitle>())
    var rows by mutableStateOf(emptyList<CatalogRow>())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var presentedHero by mutableStateOf<CatalogTitle?>(null)
    var selected by mutableStateOf<String?>(null)
    var selectedIndex by mutableIntStateOf(0)
    var heroIndex by mutableIntStateOf(0)
    var detailsExpanded by mutableStateOf(true)
    var detailTab by mutableStateOf("content")
    var focusToken by mutableStateOf("initial")
    var restoring by mutableStateOf(true)
    var restore by mutableIntStateOf(0)

    fun resetNavigation() {
        if (screen == CatalogScreen.Search) {
            query = ""
            items = emptyList()
            error = null
            loading = false
        }
        focusToken = "initial"
        selected = null
        selectedIndex = 0
        heroIndex = 0
        rowColumns.clear()
        rowStates.clear()
        list.requestScrollToItem(0)
        grid.requestScrollToItem(0)
        restoreFocus()
    }

    fun restoreFocus() {
        restoring = true
        restore++
    }

    var type by
        mutableStateOf(
            when (screen) {
                CatalogScreen.Movies -> TitleKind.Movie
                CatalogScreen.Series -> TitleKind.Series
                else -> null
            }
        )
    var query by mutableStateOf("")
    var season by mutableStateOf<TitleSeason?>(null)
    val grid = LazyGridState()
    val list = LazyListState()
    val rowColumns = mutableMapOf<String, Int>()
    val rowStates = mutableMapOf<String, LazyListState>()
}

class CatalogFragment : Fragment() {
    private val api by inject<ApiClient>()
    private val clients by inject<SeerrClientProvider>()
    private val sessions by inject<SessionRepository>()
    private val users by inject<UserRepository>()
    private val playback by inject<PlaybackHelper>()
    private val playbackLauncher by inject<PlaybackLauncher>()
    private val mutation by inject<ItemMutationRepository>()
    private lateinit var repository: CatalogRepository
    private val stack = mutableStateListOf<CatalogPage>()
    private val sections = mutableMapOf<CatalogScreen, CatalogPage>()
    private var menuVisible by mutableStateOf(false)
    private var menuRestore by mutableIntStateOf(0)
    private var menu by mutableStateOf(false)
    private var exitConfirmation by mutableStateOf(false)
    private var pendingSection: CatalogScreen? = null
    private var pendingSettings = false
    private var requestTitle by mutableStateOf<CatalogTitle?>(null)
    private var versions by mutableStateOf<CatalogTitle?>(null)
    private var sending by mutableStateOf(false)
    private var requestError by mutableStateOf<String?>(null)
    private lateinit var searchCoordinator: SearchCoordinator<List<CatalogTitle>>
    private val preferences = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra("catalog-section")?.let { name ->
            menuSections.firstOrNull { it.name == name }?.let(::switch)
        }
    }
    private var playStarted = 0L
    private var history by mutableStateOf(emptyList<String>())
    private val current
        get() = stack.last()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = sessions.currentSession.value?.let { "${it.serverId}:${it.userId}" }.orEmpty()
        repository = CatalogRepository(api, clients, profile)
        searchCoordinator = SearchCoordinator(lifecycleScope, repository::search)
        history =
            requireContext()
                .getSharedPreferences("catalog-search-${profile.hashCode()}", 0)
                .getString("history", "")
                .orEmpty()
                .split('\n')
                .filter(String::isNotBlank)
        val section =
            runCatching { CatalogScreen.valueOf(arguments?.getString("section") ?: "Home") }
                .getOrDefault(CatalogScreen.Home)
        val first = CatalogPage(section)
        sections[section] = first
        stack += first
        lifecycleScope.launch {
            try {
                repository.connect()
                repository.library()
                load(first)
                if (section == CatalogScreen.Search)
                    arguments?.getString("query")?.let { search(first, it) }
                arguments?.getString("localId")?.let {
                    open(CatalogTitle("local:$it", TitleKind.Movie, "", ids = TitleIds(local = it)))
                }
                arguments
                    ?.getInt("remoteId")
                    ?.takeIf { it > 0 }
                    ?.let { id ->
                        val kind =
                            if (arguments?.getString("kind") == "Series") TitleKind.Series
                            else TitleKind.Movie
                        open(CatalogTitle("$kind:tmdb:$id", kind, "", ids = TitleIds(tmdb = id)))
                    }
            } catch (_: Exception) {
                ensureActive()
                first.error = "Innehållet kunde inte hämtas. Försök igen."
                first.loading = false
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = content {
        JellyfinTheme {
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides
                    object : BringIntoViewSpec {
                        override fun calculateScrollDistance(
                            offset: Float,
                            size: Float,
                            containerSize: Float,
                        ): Float =
                            when {
                                offset < 6f -> offset - 6f
                                offset + size > containerSize - 6f ->
                                    offset + size - containerSize + 6f
                                else -> 0f
                            }
                    }
            ) {
                CatalogApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (playStarted == 0L || stack.isEmpty()) return
        val page = current
        val title = page.title ?: return
        lifecycleScope.launch {
            try {
                page.title = repository.details(title)
                if (page.screen == CatalogScreen.Episodes && page.season != null) {
                    page.items =
                        repository.episodes(requireNotNull(page.title), requireNotNull(page.season))
                }
                sections[CatalogScreen.Home]?.let { it.rows = repository.home() }
                page.restoreFocus()
            } catch (_: Exception) {
                ensureActive()
            }
        }
    }

    private suspend fun load(page: CatalogPage) {
        page.error = null
        try {
            when (page.screen) {
                CatalogScreen.Home -> page.rows = repository.home()
                CatalogScreen.Discover, CatalogScreen.Movies, CatalogScreen.Series -> {
                    page.rows = repository.sections(page.type)
                    page.items = mergeTitles(page.rows.flatMap { it.titles })
                }
                CatalogScreen.Search -> Unit
                else -> page.items = repository.browse(1)
            }
        } catch (_: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            page.error = "Innehållet kunde inte hämtas. Försök igen."
        }
        if (page.focusToken == "initial")
            page.focusToken =
                when (page.screen) {
                    CatalogScreen.Home ->
                        if (page.rows.any { it.titles.isNotEmpty() }) "hero" else "empty"
                    CatalogScreen.Search -> "query"
                    else ->
                        page.items
                            .firstOrNull { page.type == null || it.kind == page.type }
                            ?.let { "card:${it.key}" } ?: "filter:Alla"
                }
        page.loading = false
    }

    private fun switch(screen: CatalogScreen) {
        menu = false
        val page =
            sections.getOrPut(screen) {
                CatalogPage(screen).also { lifecycleScope.launch { load(it) } }
            }
        if (current.screen != screen) {
            searchCoordinator.cancel()
            page.resetNavigation()
        }
        visitSection(stack, page)
        page.restoreFocus()
    }

    private fun openMenu() {
        pendingSection = null
        pendingSettings = false
        menuFocusIndex = menuSections.indexOf(stack.lastOrNull { it.screen in menuSections }?.screen).coerceAtLeast(0)
        menuVisible = true
        menu = true
    }

    private var menuFocusIndex by mutableIntStateOf(1)
    private var menuCloseEpoch by mutableIntStateOf(0)

    private fun closeMenu() { menu = false; menuCloseEpoch++ }

    private fun chooseMenu(section: CatalogScreen?) {
        if (section == null) pendingSettings = true
        else pendingSection = section
        closeMenu()
    }

    private fun previousScreen() {
        if (popScreen(stack)) current.restoreFocus()
    }

    private fun back() {
        if (!menu && returnToTitleTop(current)) return
        when (catalogBackAction(menu, nested = current.screen in setOf(CatalogScreen.Details, CatalogScreen.Information, CatalogScreen.Episodes, CatalogScreen.Collection))) {
            CatalogBackAction.ConfirmExit -> exitConfirmation = true
            CatalogBackAction.OpenMenu -> openMenu()
            CatalogBackAction.DismissOverlay -> Unit
            CatalogBackAction.PreviousScreen -> previousScreen()
        }
    }

    private fun open(title: CatalogTitle) {
        if (
            stack.lastOrNull()?.let {
                it.screen == CatalogScreen.Details && it.title?.key == title.key
            } == true
        )
            return
        val page = CatalogPage(CatalogScreen.Details, title)
        stack += page
        loadDetails(page)
    }

    private fun loadDetails(page: CatalogPage) {
        val title = page.title ?: return
        page.error = null
        lifecycleScope.launch {
            try {
                page.title = repository.details(title)
                val detailed = requireNotNull(page.title)
                if (detailed.seasons.isNotEmpty() && detailed.ids.local != null) {
                    page.season =
                        detailed.seasons.firstOrNull { it.number > 0 } ?: detailed.seasons.first()
                    page.items = repository.episodes(detailed, requireNotNull(page.season)).take(12)
                } else page.items = repository.similar(detailed)
            } catch (_: Exception) {
                ensureActive()
                page.error = "All information kunde inte hämtas."
            }
            page.loading = false
        }
    }

    private fun showInformation(page: CatalogPage) {
        stack += CatalogPage(CatalogScreen.Information, page.title).apply { loading = false }
    }

    private fun showEpisodes(page: CatalogPage) {
        val next = CatalogPage(CatalogScreen.Episodes, page.title)
        stack += next
        val season = page.season ?: page.title?.seasons?.firstOrNull()
        if (season != null) selectSeason(next, season) else next.loading = false
    }

    private fun selectSeason(page: CatalogPage, season: TitleSeason) {
        page.season = season
        page.loading = true
        lifecycleScope.launch {
            try {
                val episodes = repository.episodes(requireNotNull(page.title), season)
                if (page.season != season) return@launch
                page.items = episodes
                page.selected = relevantEpisode(episodes)?.key
                page.selectedIndex =
                    episodes.indexOfFirst { it.key == page.selected }.coerceAtLeast(0)
                page.focusToken = page.selected?.let { "card:$it" } ?: "season:${season.key}"
                page.loading = false
                page.grid.scrollToItem(page.selectedIndex)
                page.restoreFocus()
                page.error = null
            } catch (_: Exception) {
                ensureActive()
                if (page.season == season) {
                    page.items = emptyList()
                    page.error = "Avsnitten kunde inte hämtas."
                }
            }
            if (page.season == season) page.loading = false
        }
    }

    private fun play(title: CatalogTitle) {
        val start = playbackStart(title) ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - playStarted < 1500) return
        playStarted = now
        playback.retrieveAndPlay(
            UUID.fromString(start.itemId),
            false,
            start.positionMillis,
            requireContext(),
        )
    }

    private fun primary(page: CatalogPage) {
        val title = page.title ?: return
        when (title.primary) {
            TitleAction.Play,
            TitleAction.Resume -> play(title)
            TitleAction.Request -> {
                requestError = null
                requestTitle = title
            }
            TitleAction.Episodes -> showEpisodes(page)
            TitleAction.Information -> showInformation(page)
        }
    }

    private fun search(page: CatalogPage, query: String) {
        page.query = query.take(120)
        if (query.isBlank()) {
            searchCoordinator.cancel()
            page.items = emptyList()
            page.loading = false
            return
        }
        searchCoordinator.submit(page.query, loading = { page.loading = true }) { outcome ->
            outcome.fold(
                onSuccess = { result ->
                    val index =
                        retainedIndex(result.map { it.key }, page.selected, page.selectedIndex)
                    page.items = result
                    page.selectedIndex = index
                    page.selected = result.getOrNull(index)?.key
                    if (page.focusToken.startsWith("card:")) {
                        page.focusToken = page.selected?.let { "card:$it" } ?: "query"
                        page.restoreFocus()
                    }
                    page.error = null
                },
                onFailure = { page.error = "Sökningen kunde inte slutföras. Försök igen." },
            )
            page.loading = false
        }
    }

    private fun saveSearch(query: String) {
        if (query.isBlank()) return
        history = (listOf(query.trim()) + history).distinct().take(5)
        val profile = sessions.currentSession.value?.let { "${it.serverId}:${it.userId}" }.orEmpty()
        requireContext()
            .getSharedPreferences("catalog-search-${profile.hashCode()}", 0)
            .edit()
            .putString("history", history.joinToString("\n"))
            .apply()
    }

    @Composable
    private fun CatalogApp() {
        CatalogInputBoundary(blocked = { menuVisible && !menu }, ready = { menu || !current.restoring }, onBack = ::back) {
        CatalogAppContent()
        }
    }

    @Composable
    private fun CatalogAppContent() {
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                if (
                    event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU
                ) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        if (menu) closeMenu() else openMenu()
                    }
                    true
                } else if (menu && event.key in setOf(Key.DirectionDown, Key.DirectionUp, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                    when (event.key) {
                        Key.DirectionDown, Key.DirectionUp -> if (event.type == KeyEventType.KeyDown)
                            menuFocusIndex = (menuFocusIndex + if (event.key == Key.DirectionDown) 1 else -1).coerceIn(0, menuSections.size)
                        Key.DirectionRight -> if (event.type == KeyEventType.KeyDown) closeMenu()
                        else -> if (event.type == KeyEventType.KeyUp) chooseMenu((menuSections + listOf(null))[menuFocusIndex])
                    }
                    true
                } else false
            }
        ) {
            val page = current
            val active =
                stack
                    .lastOrNull {
                        it.screen !in
                            setOf(
                                CatalogScreen.Details,
                                CatalogScreen.Information,
                                CatalogScreen.Episodes,
                                CatalogScreen.Collection,
                            )
                    }
                    ?.screen ?: CatalogScreen.Home
            LaunchedEffect(page.loading, page.error) {
                if (!page.loading && (page.error != null || (page.items.isEmpty() && page.rows.isEmpty()))) {
                    withFrameNanos { }
                    view?.let { (activity as? org.jellyfin.androidtv.ui.browsing.MainActivity)?.catalogFrameReady(it) }
                }
            }
            val restore = remember(page) { FocusRequester() }
            val displayed = page.items.filter { page.type == null || it.kind == page.type }
            val baseHero =
                when (page.screen) {
                    CatalogScreen.Details,
                    CatalogScreen.Information,
                    CatalogScreen.Episodes -> page.title
                    CatalogScreen.Home -> homeHero(page)
                    else -> displayed.firstOrNull { it.key == page.selected } ?: displayed.firstOrNull()
                }
            val heroScope = androidx.compose.runtime.rememberCoroutineScope()
            val metadataLoader = remember(page) { LatestFanart<CatalogTitle>(heroScope) }
            var detailedHero by remember(page) { mutableStateOf<CatalogTitle?>(null) }
            androidx.compose.runtime.DisposableEffect(metadataLoader) { onDispose { metadataLoader.cancel() } }
            LaunchedEffect(page, baseHero?.key) {
                detailedHero = null
                metadataLoader.cancel()
                if (page.screen in setOf(CatalogScreen.Discover, CatalogScreen.Movies, CatalogScreen.Series) && baseHero != null) {
                    metadataLoader.submit(load = { runCatching { repository.details(baseHero) }.getOrNull() }, publish = { loaded ->
                        detailedHero = loaded
                    })
                }
            }
            val hero = detailedHero?.takeIf { it.key == baseHero?.key } ?: baseHero
            SyncHeroMetadata(page, detailedHero?.takeIf { it.key == baseHero?.key })
            LaunchedEffect(page, page.loading, page.restore) {
                if (!page.loading && !menuVisible && page.screen !in setOf(CatalogScreen.Home, CatalogScreen.Discover, CatalogScreen.Movies, CatalogScreen.Series)) {
                    withFrameNanos {}
                    runCatching { restore.requestFocus() }
                    page.restoring = false
                }
            }
            fun target(token: String, initial: Boolean = false): FocusRequester? =
                restore.takeIf { page.focusToken == token || page.focusToken == "initial" && initial }
            fun focus(token: String) {
                if (!page.restoring && !menuVisible && !exitConfirmation) page.focusToken = token
            }
            CompositionLocalProvider(LocalContentFocusEnabled provides !menuVisible) {
            Box(Modifier.fillMaxSize()) {
                Fanart(
                    hero?.backdrop,
                    page.screen in
                        setOf(CatalogScreen.Search, CatalogScreen.Information, CatalogScreen.Episodes),
                    identity = hero?.key,
                    onReady = { identity ->
                        if (identity == hero?.key) page.presentedHero = hero
                        if (!page.loading) view?.let { (activity as? org.jellyfin.androidtv.ui.browsing.MainActivity)?.catalogFrameReady(it) }
                    },
                    soft =
                        page.screen == CatalogScreen.Information ||
                            page.screen == CatalogScreen.Episodes,
                )
                Box(
                    Modifier.fillMaxSize()
                        .padding(
                            start = 80.dp,
                            end = 36.dp,
                            top = 26.dp,
                            bottom = if (page.screen in setOf(CatalogScreen.Home, CatalogScreen.Discover, CatalogScreen.Movies, CatalogScreen.Series)) 0.dp else 26.dp,
                        )
                ) {
                    when (page.screen) {
                        CatalogScreen.Home ->
                            if (!page.loading || page.rows.isNotEmpty())
                                Home(page, hero, restore, ::target, ::focus)
                            else HomeLoadingSurface()
                        CatalogScreen.Discover, CatalogScreen.Movies, CatalogScreen.Series ->
                            CatalogRows(page, hero, ::open, ::openCategory, ::openMenu)
                        CatalogScreen.Collection -> Column {
                            Text(page.heading, color = Color.White, fontSize = 24.sp)
                            TitleHero(hero, compact = true)
                            Grid(page, displayed, restore, ::target, ::focus, Modifier.weight(1f))
                        }
                        CatalogScreen.Search -> Search(page, displayed, restore, ::target, ::focus)
                        CatalogScreen.Details -> Details(page, ::target, ::focus)
                        CatalogScreen.Information -> Information(page, ::target, ::focus)
                        CatalogScreen.Episodes -> Episodes(page, ::target, ::focus)
                    }
                    if (page.loading && page.items.isEmpty() && page.rows.isEmpty())
                        Text(
                            "Hämtar …",
                            color = Muted,
                            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 28.dp),
                        )
                    page.error?.let { message ->
                        TvButton(
                            message + "  Försök igen",
                            Modifier.align(Alignment.BottomStart),
                            onClick = {
                                when (page.screen) {
                                    CatalogScreen.Details -> loadDetails(page)
                                    CatalogScreen.Episodes ->
                                        page.season?.let { selectSeason(page, it) }
                                    CatalogScreen.Search -> search(page, page.query)
                                    else ->
                                        lifecycleScope.launch {
                                            repository.connect()
                                            repository.library()
                                            load(page)
                                        }
                                }
                            },
                        )
                    }
                }
            }
            }
            CatalogMenu(
                focusEpoch = menuRestore,
                focusIndex = menuFocusIndex,
                closeEpoch = menuCloseEpoch,
                expanded = menu,
                active = active,
                onClose = ::closeMenu,
                onClosed = {
                    menuVisible = false
                    val destination = pendingSection
                    pendingSection = null
                    if (destination != null) switch(destination) else current.restoreFocus()
                    if (pendingSettings) {
                        pendingSettings = false
                        preferences.launch(ActivityDestinations.userPreferences(requireContext()).putExtra("catalog-menu", true))
                    }
                },
                onSelect = ::chooseMenu,
            )
            if (exitConfirmation) CatalogExitConfirmation(
                onStay = { exitConfirmation = false; menuRestore++ },
                onExit = { requireActivity().finish() },
            )
            versions?.let { title ->
                val dto = repository.dto(title)
                val sources = dto?.mediaSources.orEmpty()
                ChoiceDialog(
                    "Version",
                    sources.mapIndexed { index, source -> "${index + 1}. ${versionLabel(source)}" },
                    onDismiss = {
                        versions = null
                        page.restoreFocus()
                    },
                ) { choice ->
                    val source =
                        sources.getOrNull(choice.substringBefore('.').toIntOrNull()?.minus(1) ?: -1)
                    versions = null
                    if (source != null && dto != null)
                        playbackLauncher.launch(
                            requireContext(),
                            listOf(dto),
                            mediaSourceId = source.id,
                        )
                }
            }
            requestTitle?.let {
                RequestConfirmation(
                    it,
                    onDismiss = {
                        if (!sending) {
                            requestTitle = null
                            page.restoreFocus()
                        }
                    },
                )
            }
        }
    }

    @Composable
    private fun Home(
        page: CatalogPage,
        hero: CatalogTitle?,
        restore: FocusRequester,
        target: (String, Boolean) -> FocusRequester?,
        focus: (String) -> Unit,
    ) {
        HomeRows(page, hero, ::openMenu, ::open)
    }

    private fun openCategory(category: CatalogCategory) {
        stack += CatalogPage(CatalogScreen.Collection).apply {
            heading = category.name
            items = category.titles
            loading = false
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Grid(
        page: CatalogPage,
        values: List<CatalogTitle>,
        restore: FocusRequester,
        target: (String, Boolean) -> FocusRequester?,
        focus: (String) -> Unit,
        modifier: Modifier = Modifier,
        columns: Int = 6,
        search: Boolean = false,
    ) {
        if (values.isEmpty() && !page.loading) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (page.screen == CatalogScreen.Search)
                        if (page.query.isBlank()) "Skriv en titel för att söka" else "Inga träffar"
                    else "Inga titlar med dessa val bland hämtade titlar",
                    color = Muted,
                )

            }
            return
        }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val safe = with(density) { 8.dp.toPx() }
        var pendingActivation by remember(page) { mutableStateOf<CatalogTitle?>(null) }
        val enabled = LocalContentFocusEnabled.current
        LaunchedEffect(page.selectedIndex, page.focusToken, enabled, page.loading, values.size) {
            if (enabled && !page.loading && values.isNotEmpty() && page.focusToken.startsWith("card:")) {
                page.grid.animateTitleGrid(page.selectedIndex, columns, safe)
                withFrameNanos { }
                restore.requestFocus()
                page.restoring = false
            }
        }
        androidx.compose.runtime.DisposableEffect(page, enabled) {
            onDispose {
                pendingActivation = null
                if (page.restoring && page.focusToken.startsWith("card:"))
                    page.grid.finishTitleGrid(page.selectedIndex, columns, safe)
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
            }
        }) {
        LazyVerticalGrid(
            GridCells.Fixed(columns),
            state = page.grid,
            modifier = modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                if (values.isEmpty()) return@onPreviewKeyEvent false
                val index = page.selectedIndex.coerceIn(values.indices)
                if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                    if (event.type == KeyEventType.KeyDown && pendingActivation == null) pendingActivation = values[index]
                    if (event.type == KeyEventType.KeyUp) {
                        val title = pendingActivation ?: values[index]
                        pendingActivation = null
                        if (search) saveSearch(page.query)
                        open(title)
                    }
                    true
                } else {
                    val next = when (event.key) {
                        Key.DirectionRight -> if (index % columns < columns - 1) index + 1 else index
                        Key.DirectionLeft -> if (index % columns > 0) index - 1 else -1
                        Key.DirectionDown -> if (index / columns < values.lastIndex / columns)
                            (index + columns).coerceAtMost(values.lastIndex) else index
                        Key.DirectionUp -> if (index >= columns) index - columns else -1
                        else -> -1
                    }
                    if (next in values.indices) {
                        if (event.type == KeyEventType.KeyDown) {
                            page.selectedIndex = next
                            page.selected = values[next].key
                            page.focusToken = "card:${values[next].key}"
                            page.restoring = true
                        }
                        true
                    } else if (event.key == Key.DirectionLeft) {
                        if (event.type == KeyEventType.KeyDown) {
                            openMenu()
                        }
                        true
                    } else if (search && event.key == Key.DirectionUp && index < columns) {
                        if (event.type == KeyEventType.KeyDown) { page.focusToken = "query"; page.restoreFocus() }
                        true
                    } else false
                }
            },
            contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 20.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            itemsIndexed(values, key = { _, title -> title.key }) { index, title ->
                val token = "card:${title.key}"
                TitleCard(
                    title,
                    Modifier.fillMaxWidth()
                        .aspectRatio(if (search) 1.6f else .90f)
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionLeft &&
                                    index % columns == 0
                            ) {
                                openMenu()
                                true
                            } else false
                        },
                    search = search,
                    landscape = search,
                    requester = target(token, !search && index == 0),
                    logicalFocus = if (page.focusToken.startsWith("card:")) enabled && page.focusToken == token else null,
                    onFocus = {
                        if (!page.restoring) {
                            page.selected = title.key
                            page.selectedIndex = index
                            focus(token)
                        }

                    },
                    onClick = {
                        if (search) saveSearch(page.query)
                        open(title)
                    },
                )
            }
        }
        }
    }

    @Composable
    private fun Search(
        page: CatalogPage,
        values: List<CatalogTitle>,
        restore: FocusRequester,
        target: (String, Boolean) -> FocusRequester?,
        focus: (String) -> Unit,
    ) {
        Column(Modifier.fillMaxSize().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            CatalogSearchField(page.query, target("query", true),
                onFocus = { focus("query") }, onQuery = { search(page, it) }, onMenu = ::openMenu,
                onResults = {
                    if (values.isNotEmpty()) {
                        page.selectedIndex = page.selectedIndex.coerceIn(values.indices)
                        page.focusToken = "card:${values[page.selectedIndex].key}"
                        page.restoreFocus()
                    }
                })
            Text(if (page.loading) "Sökresultat  ·  Hämtar …" else "Sökresultat", color = Color.White, fontSize = 20.sp)
            Grid(page, values, restore, target, focus, Modifier.weight(1f), 4, true)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Details(
        page: CatalogPage,
        target: (String, Boolean) -> FocusRequester?,
        focus: (String) -> Unit,
    ) {
        val title = page.title ?: return
        if (title.status == TitleStatus.Requestable) {
            RequestableTitleContent(title, target("primary", true), { focus("primary") },
                onRequest = { requestError = null; requestTitle = title }, onBack = ::previousScreen)
            return
        }

        CatalogDetailLayout(page, title, focus, actions = { actionKeys ->
            LazyRow(
                state = page.rowStates.getOrPut("actions") { LazyListState() },
                modifier = actionKeys,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(3.dp),
            ) {
                item {
                    TvButton(
                        when (title.primary) {
                            TitleAction.Play,
                            TitleAction.Resume -> "▶  ${title.primary.label}"
                            TitleAction.Request -> "+  Begär"
                            else -> title.primary.label
                        },
                        Modifier.onPreviewKeyEvent { e ->
                            e.key == Key.DirectionLeft
                        },
                        requester = target("primary", true),
                        primary = true,
                        onFocus = { focus("primary") },
                        onClick = { primary(page) },
                    )
                }
                val dto = repository.dto(title)
                if (dto != null && TrailerUtils.hasPlayableTrailers(requireContext(), dto))
                    item {
                        TvButton(
                            "▶  Trailer",
                            requester = target("trailer", false),
                            onFocus = { focus("trailer") },
                            onClick = {
                                if ((dto.localTrailerCount ?: 0) > 0)
                                    lifecycleScope.launch {
                                        val trailers =
                                            api.userLibraryApi.getLocalTrailers(dto.id).content
                                        playbackLauncher.launch(requireContext(), trailers)
                                    }
                                else
                                    TrailerUtils.getExternalTrailerIntent(requireContext(), dto)
                                        ?.let(::startActivity)
                            },
                        )
                    }
                if (dto?.mediaSources.orEmpty().size > 1)
                    item {
                        TvButton(
                            "Version",
                            requester = target("version", false),
                            onFocus = { focus("version") },
                            onClick = { versions = title },
                        )
                    }
                if (title.ids.local != null)
                    item {
                        var busy by remember { mutableStateOf(false) }
                        TvButton(
                            if (title.favorite) "✓  Min lista" else "+  Min lista",
                            requester = target("favorite", false),
                            onFocus = { focus("favorite") },
                            onClick = {
                                if (!busy) {
                                    busy = true
                                    lifecycleScope.launch {
                                        try {
                                            mutation.setFavorite(
                                                UUID.fromString(title.ids.local),
                                                !title.favorite,
                                            )
                                            page.title = title.copy(favorite = !title.favorite)
                                            repository.updateFavorite(title, !title.favorite)
                                        } catch (_: Exception) {
                                            ensureActive()
                                            page.error = "Listan kunde inte uppdateras."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            },
                        )
                    }
                if (title.seasons.isNotEmpty() && title.ids.local != null)
                    item {
                        TvButton(
                            "Säsonger och avsnitt",
                            requester = target("episodes", false),
                            onFocus = { focus("episodes") },
                            onClick = { showEpisodes(page) },
                        )
                    }
            }
        }, related = {
            if (page.items.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (title.kind == TitleKind.Series && title.ids.local != null) "Avsnitt"
                        else "Liknande titlar",
                        color = Color.White,
                        fontSize = 20.sp,
                    )
                    page.season?.let { Text(it.name, color = Muted, fontSize = 12.sp) }
                }
                val relatedState = page.rowStates.getOrPut("related") { LazyListState() }
                var pendingActivation by remember(page) { mutableStateOf<CatalogTitle?>(null) }
                val density = androidx.compose.ui.platform.LocalDensity.current
                val enabled = LocalContentFocusEnabled.current
                LaunchedEffect(page.focusToken, enabled) {
                    if (enabled && page.focusToken.startsWith("related:")) {
                        withFrameNanos { }
                        with(density) { relatedState.animateTitleCard(page.rowColumns["related"] ?: 0,
                            209.dp.toPx(), 200.dp.toPx(), 8.dp.toPx()) }
                        withFrameNanos { }
                        target(page.focusToken, false)?.requestFocus()
                        page.restoring = false
                    }
                }
                androidx.compose.runtime.DisposableEffect(page, enabled) {
                    onDispose {
                        pendingActivation = null
                        if (page.restoring && page.focusToken.startsWith("related:")) with(density) {
                            relatedState.finishTitleCard(page.rowColumns["related"] ?: 0,
                                209.dp.toPx(), 200.dp.toPx(), 8.dp.toPx())
                        }
                    }
                }
                CompositionLocalProvider(LocalBringIntoViewSpec provides remember {
                    object : BringIntoViewSpec {
                        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
                    }
                }) {
                LazyRow(
                    state = relatedState,
                    modifier = Modifier.onPreviewKeyEvent { event ->
                        val column = page.rowColumns["related"] ?: 0
                        if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                            if (event.type == KeyEventType.KeyDown && pendingActivation == null)
                                pendingActivation = page.items.getOrNull(column)
                            if (event.type == KeyEventType.KeyUp) {
                                val title = pendingActivation ?: page.items.getOrNull(column)
                                pendingActivation = null
                                title?.let(::open)
                            }
                            true
                        } else if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                            val next = column + if (event.key == Key.DirectionRight) 1 else -1
                            if (next in page.items.indices && event.type == KeyEventType.KeyDown) {
                                page.rowColumns["related"] = next
                                page.focusToken = "related:${page.items[next].key}"
                                page.restoring = true
                            }
                            true
                        } else false
                    },
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    itemsIndexed(page.items, key = { _, t -> t.key }) { index, entry ->
                        val token = "related:${entry.key}"
                        Column(
                            Modifier.width(200.dp).zIndex(if (page.focusToken == token) 1f else 0f),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            TitleCard(
                                entry,
                                Modifier.fillMaxWidth().height(103.dp),
                                landscape = true,
                                logicalFocus = if (page.focusToken.startsWith("related:")) enabled && page.focusToken == token else null,
                                requester = target(token, false),
                                onFocus = { page.rowColumns["related"] = index; focus(token) },
                                onClick = { open(entry) },
                            )
                            Text(
                                listOfNotNull(
                                        entry.episodeNumber?.toString()?.plus("."),
                                        entry.name,
                                    )
                                    .joinToString(" "),
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                }
            }
        })
    }

    @Composable
    private fun Information(
        page: CatalogPage,
        target: (String, Boolean) -> FocusRequester?,
        focus: (String) -> Unit,
    ) {
        val title = page.title ?: return
        InformationContent(title, onBack = ::previousScreen)
    }

    @Composable
    private fun Episodes(
        page: CatalogPage,
        target: (String, Boolean) -> FocusRequester?,
        focus: (String) -> Unit,
    ) {
        val title = page.title ?: return
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(
                "‹  Tillbaka",
                compact = true,
                requester = target("back", false),
                onFocus = { focus("back") },
                onClick = ::previousScreen,
            )
            Text(
                title.name,
                color = Muted,
                fontSize = 29.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(title.seasons) { _, season ->
                        TvButton(
                            season.name,
                            requester = target("season:${season.key}", false),
                            compact = true,
                            selected = page.season == season,
                            onFocus = { focus("season:${season.key}") },
                            onClick = { if (page.season != season) selectSeason(page, season) },
                        )
                    }
                }
                Text("${page.items.size} avsnitt", color = Muted, fontSize = 13.sp)
            }
            if (!page.loading)
                LazyVerticalGrid(
                    GridCells.Fixed(2),
                    state = page.grid,
                    contentPadding = PaddingValues(3.dp, 3.dp, 3.dp, 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(page.items, key = { _, episode -> episode.key }) { index, episode
                        ->
                        TvTarget(
                            Modifier.fillMaxWidth().height(128.dp),
                            requester = target("card:${episode.key}", index == 0),
                            onFocus = {
                                page.selected = episode.key
                                focus("card:${episode.key}")
                            },
                            onClick = { play(episode) },
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(13.dp),
                            ) {
                                Box(Modifier.weight(1f).fillMaxHeight().background(Ink)) {
                                    (episode.poster ?: episode.backdrop)?.let {
                                        AsyncImage(
                                            it,
                                            null,
                                            Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                    if (episode.watched)
                                        Text(
                                            "✓",
                                            color = Accent,
                                            fontSize = 18.sp,
                                            modifier = Modifier.padding(5.dp),
                                        )
                                }
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        "${episode.episodeNumber?.let { "$it. " }.orEmpty()}${episode.name}",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    episode.runtimeTicks?.let {
                                        Text(
                                            "${it / 600_000_000L} min",
                                            color = Muted,
                                            fontSize = 11.sp,
                                        )
                                    }
                                    Text(
                                        episode.overview,
                                        color = Muted,
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (episode.progress > 0) {
                                        Progress(episode)
                                        Text(
                                            "Fortsätt titta${episode.minutesRemaining?.let { " · $it min kvar" }.orEmpty()}",
                                            color = Accent,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    @Composable
    private fun ChoiceDialog(
        name: String,
        options: List<String>,
        onDismiss: () -> Unit,
        selected: (String) -> Unit,
    ) {
        val initial = remember { FocusRequester() }
        Dialog(onDismissRequest = onDismiss) {
            LaunchedEffect(Unit) {
                withFrameNanos {}
                initial.requestFocus()
            }
            LazyColumn(
                Modifier.width(340.dp).heightIn(max = 450.dp).background(Ink).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Text(name, color = Color.White, fontSize = 22.sp) }
                itemsIndexed(options) { index, option ->
                    TvButton(
                        option,
                        Modifier.fillMaxWidth(),
                        requester = initial.takeIf { index == 0 },
                        onClick = { selected(option) },
                    )
                }
            }
        }
    }

    @Composable
    private fun RequestConfirmation(title: CatalogTitle, onDismiss: () -> Unit) {
        val initial = remember { FocusRequester() }
        var seasons by remember(title.key) { mutableStateOf(emptySet<Int>()) }
        Dialog(onDismissRequest = onDismiss) {
            LaunchedEffect(Unit) {
                withFrameNanos {}
                initial.requestFocus()
            }
            LazyColumn(
                Modifier.width(480.dp).heightIn(max = 450.dp).background(Ink).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Begär ${title.name}", color = Color.White, fontSize = 24.sp)
                    Text(
                        if (title.kind == TitleKind.Series) "Välj säsonger att begära."
                        else "Bekräfta att du vill begära filmen.",
                        color = Muted,
                        fontSize = 14.sp,
                    )
                }
                itemsIndexed(title.seasons.filter { it.requestable }) { _, season ->
                    TvButton(
                        (if (season.number in seasons) "✓  " else "") + season.name,
                        Modifier.fillMaxWidth(),
                        onClick = {
                            if (!sending)
                                seasons =
                                    if (season.number in seasons) seasons - season.number
                                    else seasons + season.number
                        },
                    )
                }
                item {
                    requestError?.let { Text(it, color = Accent, fontSize = 13.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton("Avbryt", requester = initial, onClick = onDismiss)
                        if (title.kind == TitleKind.Movie || seasons.isNotEmpty())
                            TvButton(
                                if (sending) "Begäran skickas …" else "Bekräfta begäran",
                                onClick = {
                                    if (!sending) {
                                        sending = true
                                        lifecycleScope.launch {
                                            repository
                                                .request(title, seasons)
                                                .fold(
                                                    onSuccess = {
                                                        current.title =
                                                            title.copy(
                                                                status = TitleStatus.Requested
                                                            )
                                                        requestTitle = null
                                                        current.restoreFocus()
                                                    },
                                                    onFailure = {
                                                        requestError =
                                                            "Begäran kunde inte skickas. Försök igen."
                                                    },
                                                )
                                            sending = false
                                        }
                                    }
                                },
                            )
                    }
                }
            }
        }
    }
}
