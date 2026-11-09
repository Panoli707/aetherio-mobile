package com.panoli707.aetherio.nativeapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.AppState
import com.panoli707.aetherio.nativeapp.data.UpdateAction
import com.panoli707.aetherio.nativeapp.data.UpdateState
import com.panoli707.aetherio.nativeapp.data.MediaItem
import com.panoli707.aetherio.nativeapp.data.SectionStyle
import com.panoli707.aetherio.nativeapp.data.StreamSource
import com.panoli707.aetherio.nativeapp.data.EpisodeItem
import com.panoli707.aetherio.nativeapp.data.WatchProgress
import com.panoli707.aetherio.nativeapp.data.MediaTrailer
import com.panoli707.aetherio.nativeapp.data.EpisodeWatchScope
import com.panoli707.aetherio.nativeapp.data.SeriesIdentity
import com.panoli707.aetherio.nativeapp.data.DesktopHomeCatalog
import com.panoli707.aetherio.nativeapp.data.HomePreferences
import com.panoli707.aetherio.nativeapp.data.SourcePreferences
import com.panoli707.aetherio.nativeapp.player.VideoFormat
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun AetherioApp(
    state: AppState,
    updates: UpdateState,
    onSelectMedia: (MediaItem) -> Unit,
    onToggleSaved: (MediaItem) -> Unit,
    onSearch: (String) -> Unit,
    onRetryHome: () -> Unit,
    onRetryDetail: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onRenameProfile: (String) -> Unit,
    onOpenVideo: (String, VideoFormat) -> Unit,
    onAddAddon: (String) -> Unit,
    onRemoveAddon: (String) -> Unit,
    onDesktopSourceToggle: (String, Boolean) -> Unit,
    onRequestSources: (MediaItem, Int?, Int?) -> Unit,
    onDismissSources: () -> Unit,
    onPlaySource: (StreamSource, VideoFormat) -> Unit,
    onUpdateAction: (UpdateAction) -> Unit,
    onAutomaticUpdatesChange: (Boolean) -> Unit,
    onEpisodeWatchAction: (MediaItem, EpisodeItem, EpisodeWatchScope, Boolean) -> Unit = { _, _, _, _ -> },
    onContinue: (WatchProgress) -> Unit = {},
    onToggleWatching: (MediaItem) -> Unit = {},
    onLoadAnimeSeason: () -> Unit = {},
    onOpenExternal: (String) -> Unit = {},
    onPlayTrailer: (MediaTrailer) -> Unit = {},
    onBrowse: (String, Boolean) -> Unit = { _, _ -> },
    onHomePreferencesChange: (HomePreferences) -> Unit = {},
    onSourcePreferencesChange: (SourcePreferences) -> Unit = {},
    personalActions: PersonalActions = PersonalActions(),
    onPrepareDownloads: (MediaItem, List<EpisodeItem>) -> Unit = { _, _ -> },
    onDownloadSource: (StreamSource) -> Unit = {}, onSkipDownload: () -> Unit = {},
    profileRequest: Int = 0, downloadsRequest: Int = 0,
    onLibraryFilters: (com.panoli707.aetherio.nativeapp.data.LibraryFilters) -> Unit = {},
    onDiscover: (com.panoli707.aetherio.nativeapp.data.DiscoverSelection, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onLoadWatchingCalendar: (com.panoli707.aetherio.nativeapp.data.WatchingRequest, Boolean) -> Unit = { _, _ -> },
    onLeaveMovieTvCalendar: () -> Unit = {}
) {
    var startupComplete by rememberSaveable { mutableStateOf(false) }
    val startupReady = !state.homeLoading || state.sections.isNotEmpty() || state.homeError != null ||
        state.downloads.isNotEmpty() || profileRequest > 0 || downloadsRequest > 0
    LaunchedEffect(startupReady) { if (startupReady) startupComplete = true }
    if (!startupComplete) {
        AetherioTheme { StartupScreen() }
        return
    }

    var tab by rememberSaveable { mutableStateOf("home") }
    var directDownloads by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchNeedsFocus by rememberSaveable { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var parentCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var linkOpen by rememberSaveable { mutableStateOf(false) }
    var handledProfileRequest by rememberSaveable { mutableStateOf(0) }
    var handledDownloadsRequest by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(profileRequest, downloadsRequest) {
        if (profileRequest != handledProfileRequest || downloadsRequest != handledDownloadsRequest) {
            onDismissSources(); detailKey = null; searchOpen = false; categoryId = null; linkOpen = false
            tab = "profile"; directDownloads = downloadsRequest > profileRequest
            handledProfileRequest = profileRequest; handledDownloadsRequest = downloadsRequest
        }
    }
    var airingDay by rememberSaveable(state.animeSeason.season.year, state.animeSeason.season.code) { mutableStateOf("MONDAY") }
    val homeScroll = rememberLazyListState()
    val animeScroll = rememberLazyListState()
    val seasonScroll = rememberLazyListState()
    val libraryScroll = rememberLazyGridState()
    val searchScroll = rememberLazyGridState()
    val categoryScroll = rememberSaveable(categoryId, saver = LazyGridState.Saver) { LazyGridState() }
    val browseScroll = rememberSaveable(categoryId, saver = LazyListState.Saver) { LazyListState() }
    val detailScroll = rememberSaveable(detailKey, saver = LazyListState.Saver) { LazyListState() }
    val pager = rememberPagerState { state.homePreferences.visibleHeroes(state.heroes).size }
    LaunchedEffect(state.homePreferences.content, state.homePreferences.animeFirst) { pager.scrollToPage(0) }
    val screenState = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val collapseOffset = with(LocalDensity.current) { 160.dp.roundToPx() }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val collapsed by remember(collapseOffset) {
        derivedStateOf { homeScroll.firstVisibleItemIndex > 0 || homeScroll.firstVisibleItemScrollOffset > collapseOffset }
    }
    val openSearch: () -> Unit = { focus.clearFocus(); searchNeedsFocus = true; searchOpen = true }
    LaunchedEffect(tab, categoryId) { if (tab == "home" || categoryId == "watchingAnime" || categoryId == "airing") onLoadAnimeSeason() }
    val homeLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(homeLifecycle, tab) {
        if (tab == "home") homeLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) { kotlinx.coroutines.delay(60_000); onLoadAnimeSeason() }
        }
    }
    LaunchedEffect(categoryId, parentCategoryId) {
        val id = parentCategoryId ?: categoryId
        if (id != null && DesktopHomeCatalog.browseTitle(id) != null) onBrowse(id, false)
    }
    val select: (MediaItem) -> Unit = { original ->
        val item = SeriesIdentity.detailItem(original)
        focus.clearFocus()
        onSelectMedia(item)
        detailKey = item.key
    }
    val closeTop: () -> Unit = {
        focus.clearFocus()
        when {
            state.sourceQuery != null -> onDismissSources()
            linkOpen -> linkOpen = false
            detailKey != null -> detailKey = null
            searchOpen -> { searchOpen = false; searchNeedsFocus = false }
            categoryId != null -> { categoryId = parentCategoryId; parentCategoryId = null }
            tab != "home" -> tab = "home"
        }
    }
    BackHandler(enabled = state.sourceQuery != null || linkOpen || detailKey != null || searchOpen || categoryId != null || tab != "home", onBack = closeTop)

    AetherioTheme {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            // Only the visible screen is composed: hidden text fields cannot retain IME focus.
            // blur(0.dp) still creates a clipped graphics layer in Compose. Do not wrap ordinary scrolling in it.
            Box(Modifier.fillMaxSize().background(Ink).then(if (state.sourceQuery != null) Modifier.blur(18.dp) else Modifier)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                when {
                    detailKey != null -> {
                         val selected = state.selectedItem?.takeIf { it.key == detailKey }
                            ?: state.detail?.item?.takeIf { it.key == detailKey }
                              ?: (state.heroes.asSequence() + state.savedItems.asSequence() + state.searchResults.asSequence() + state.watchProgress.asSequence().map { it.target.item } +
                                   state.watchingAnime.asSequence() + state.animeSeason.entries.asSequence().map { it.item } +
                                   state.animeCalendar.events.asSequence().map { it.item } + state.movieTvCalendar.events.asSequence().map { it.item } +
                                 state.sections.asSequence().flatMap { it.items.asSequence() }).firstOrNull { it.key == detailKey }
                         if (selected != null) key(detailKey) {
                             LaunchedEffect(selected.key, state.selectedItem?.key) {
                                 if (state.selectedItem?.key != selected.key) onSelectMedia(selected)
                             }
                             DetailScreen(state, selected, detailScroll, closeTop, onToggleSaved, onRetryDetail, onSelectSeason,
                                   { focus.clearFocus(); linkOpen = true }, onRequestSources, onEpisodeWatchAction, onToggleWatching, onOpenExternal, select,
                                     onPlayTrailer, onPrepareDownloads,
                                     onSaveMediaState = { media, season, value -> personalActions.saveMediaState(media, season, value) },
                                     onRemoveMedia = { media, complete -> personalActions.removeMedia(media, complete) })
                        } else Column {
                            ScreenHeader("Ficha no disponible", closeTop)
                             if (state.homeLoading) LoadingRow("Recuperando ficha…")
                             else StatusMessage("No se encontró el título", "Vuelve al catálogo para abrir su ficha.", "Volver", closeTop)
                        }
                    }
                    searchOpen -> SearchScreen(state, searchScroll, searchNeedsFocus, { searchNeedsFocus = false }, onSearch, select, closeTop)
                    categoryId == "watchingAnime" -> screenState.SaveableStateProvider("watchingAnime") {
                        AnimeScreen(state, animeScroll, select, onContinue,
                            { parentCategoryId = "watchingAnime"; categoryId = "airing" }, closeTop)
                    }
                    categoryId == "airing" -> screenState.SaveableStateProvider("airing") {
                        SeasonalAnimeScreen(state.animeSeason, seasonScroll, closeTop, select, onLoadAnimeSeason,
                            airingDay, { airingDay = it; scope.launch { seasonScroll.scrollToItem(0) } })
                    }
                    categoryId != null -> {
                         val browseTitle = categoryId?.let(DesktopHomeCatalog::browseTitle)
                         val section = (state.sections + state.browse.sections).firstOrNull { it.id == categoryId }
                         if (browseTitle != null) {
                             val id = categoryId!!
                             val browse = if (state.browse.id == id) state.browse else com.panoli707.aetherio.nativeapp.data.CatalogBrowseState(id, browseTitle, loading = true)
                             CatalogBrowseScreen(browse, categoryScroll, browseScroll, closeTop, select,
                                 { child -> parentCategoryId = id; categoryId = child }, { onBrowse(id, true) })
                         } else {
                         Column(Modifier.fillMaxSize()) {
                             ScreenHeader(section?.title ?: "Categoría", closeTop)
                              if (section == null && (state.homeLoading || state.browse.loading)) LoadingRow("Cargando categoría…")
                              else if (section == null || section.items.isEmpty()) StatusMessage("No hay títulos disponibles", "Vuelve al catálogo para ver otras categorías.")
                             else CatalogGrid(section.items, categoryScroll, select, Modifier.weight(1f), ranked = section.style == SectionStyle.RANKED)
                         }
                         }
                    }
                    else -> {
                        screenState.SaveableStateProvider(tab) {
                            when (tab) {
                                "anime" -> WatchingScreen(state, select, { parentCategoryId = null; categoryId = "watchingAnime" },
                                    onLoadWatchingCalendar, onLeaveMovieTvCalendar)
                                "library" -> LibraryScreen(state, libraryScroll, state.libraryFilters.type, {}, select, openSearch,
                                    onFilters = onLibraryFilters, onDiscover = onDiscover, personalActions = personalActions)
                                 "settings" -> SettingsScreen(state, onRenameProfile, { focus.clearFocus(); linkOpen = true }, onAddAddon, onRemoveAddon, onDesktopSourceToggle,
                                     updates, onUpdateAction, onAutomaticUpdatesChange, onHomePreferencesChange, personalActions, select)
                                  "profile" -> ProfileSpace(state, onRenameProfile, personalActions, { tab = "home" },
                                      select, initialDownloads = directDownloads, navigationRequest = maxOf(profileRequest, downloadsRequest))
                                else -> HomeScreen(state, homeScroll, pager, !linkOpen && state.sourceQuery == null && !updates.noticeVisible,
                                     select, { parentCategoryId = null; categoryId = it }, onRetryHome, onContinue, onLoadAnimeSeason,
                                     onProfile = { focus.clearFocus(); directDownloads = false; tab = "profile" })
                            }
                        }
                        if (!imeVisible) FloatingNavigation(tab, tab == "home" && collapsed, { target ->
                            focus.clearFocus()
                            if (target == "home" && tab == "home") scope.launch { homeScroll.animateScrollToItem(0) }
                            tab = target
                        }, openSearch, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
            if (linkOpen && state.sourceQuery == null) VideoLinkDialog(onDismiss = { focus.clearFocus(); linkOpen = false }, onOpenVideo = onOpenVideo)
            state.sourceQuery?.let { query ->
                key(query.key) {
                    SourceSheet(state, query, onDismissSources, { source, format ->
                        if (state.downloadSelection.isEmpty()) onPlaySource(source, format)
                        else if (!state.downloadEnqueueing) {
                            onDownloadSource(source)
                        }
                    },
                        onSkipDownload = onSkipDownload,
                        onPreferencesChange = onSourcePreferencesChange,
                        onRetry = { onRequestSources(query.item, query.season, query.episode) }, onSettings = {
                        focus.clearFocus()
                        onDismissSources()
                        linkOpen = false
                        detailKey = null
                        searchOpen = false
                        searchNeedsFocus = false
                        categoryId = null
                        tab = "settings"
                    })
                }
            }
            state.animeTracking.draft?.let { draft -> AnimeEntryEditor(draft.service, draft.entry, personalActions, personalActions.dismissAnime) }
            if (!linkOpen && state.sourceQuery == null && !imeVisible && updates.noticeVisible) {
                UpdateDialog(updates, onUpdateAction)
            }
        }
    }
}
