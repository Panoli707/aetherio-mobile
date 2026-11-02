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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.panoli707.aetherio.nativeapp.data.EpisodeWatchScope
import com.panoli707.aetherio.nativeapp.player.VideoFormat
import kotlinx.coroutines.launch

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
    onOpenExternal: (String) -> Unit = {}
) {
    var startupComplete by rememberSaveable { mutableStateOf(false) }
    val startupReady = !state.homeLoading || state.sections.isNotEmpty() || state.homeError != null
    LaunchedEffect(startupReady) { if (startupReady) startupComplete = true }
    if (!startupComplete) {
        AetherioTheme { StartupScreen() }
        return
    }

    var tab by rememberSaveable { mutableStateOf("home") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchNeedsFocus by rememberSaveable { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var linkOpen by rememberSaveable { mutableStateOf(false) }
    var libraryFilter by rememberSaveable { mutableStateOf("all") }
    val homeScroll = rememberLazyListState()
    val animeScroll = rememberLazyListState()
    val seasonScroll = rememberLazyListState()
    val libraryScroll = rememberLazyGridState()
    val searchScroll = rememberLazyGridState()
    val categoryScroll = rememberSaveable(categoryId, saver = LazyGridState.Saver) { LazyGridState() }
    val detailScroll = rememberSaveable(detailKey, saver = LazyListState.Saver) { LazyListState() }
    val pager = rememberPagerState { state.heroes.size }
    val screenState = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val collapseOffset = with(LocalDensity.current) { 160.dp.roundToPx() }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val collapsed by remember(collapseOffset) {
        derivedStateOf { homeScroll.firstVisibleItemIndex > 0 || homeScroll.firstVisibleItemScrollOffset > collapseOffset }
    }
    val openSearch: () -> Unit = { focus.clearFocus(); searchNeedsFocus = true; searchOpen = true }
    LaunchedEffect(tab, categoryId) { if (tab == "anime" || categoryId == "airing") onLoadAnimeSeason() }
    val select: (MediaItem) -> Unit = { item ->
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
            categoryId != null -> categoryId = null
            tab != "home" -> tab = "home"
        }
    }
    BackHandler(enabled = state.sourceQuery != null || linkOpen || detailKey != null || searchOpen || categoryId != null || tab != "home", onBack = closeTop)

    AetherioTheme {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            // Only the visible screen is composed: hidden text fields cannot retain IME focus.
            Box(Modifier.fillMaxSize().background(Ink).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                when {
                    detailKey != null -> {
                         val selected = state.selectedItem?.takeIf { it.key == detailKey }
                            ?: state.detail?.item?.takeIf { it.key == detailKey }
                              ?: (state.heroes.asSequence() + state.savedItems.asSequence() + state.searchResults.asSequence() + state.watchProgress.asSequence().map { it.target.item } +
                                  state.watchingAnime.asSequence() + state.animeSeason.entries.asSequence().map { it.item } +
                                 state.sections.asSequence().flatMap { it.items.asSequence() }).firstOrNull { it.key == detailKey }
                         if (selected != null) key(detailKey) {
                             LaunchedEffect(selected.key, state.selectedItem?.key) {
                                 if (state.selectedItem?.key != selected.key) onSelectMedia(selected)
                             }
                             DetailScreen(state, selected, detailScroll, closeTop, onToggleSaved, onRetryDetail, onSelectSeason,
                                   { focus.clearFocus(); linkOpen = true }, onRequestSources, onEpisodeWatchAction, onToggleWatching, onOpenExternal, select)
                        } else Column {
                            ScreenHeader("Ficha no disponible", closeTop)
                             if (state.homeLoading) LoadingRow("Recuperando ficha…")
                             else StatusMessage("No se encontró el título", "Vuelve al catálogo para abrir su ficha.", "Volver", closeTop)
                        }
                    }
                    searchOpen -> SearchScreen(state, searchScroll, searchNeedsFocus, { searchNeedsFocus = false }, onSearch, select, closeTop)
                    categoryId == "airing" -> SeasonalAnimeScreen(state.animeSeason, seasonScroll, closeTop, select, onLoadAnimeSeason)
                    categoryId != null -> {
                        val section = state.sections.firstOrNull { it.id == categoryId }
                        Column(Modifier.fillMaxSize()) {
                            ScreenHeader(section?.title ?: "Categoría", closeTop)
                             if (section == null && state.homeLoading) LoadingRow("Cargando categoría…")
                             else if (section == null || section.items.isEmpty()) StatusMessage("No hay títulos disponibles", "Vuelve al catálogo para ver otras categorías.")
                            else CatalogGrid(section.items, categoryScroll, select, Modifier.weight(1f), ranked = section.style == SectionStyle.RANKED)
                        }
                    }
                    else -> {
                        screenState.SaveableStateProvider(tab) {
                            when (tab) {
                                "anime" -> AnimeScreen(state, animeScroll, select, onContinue, { categoryId = "airing" })
                                "library" -> LibraryScreen(state, libraryScroll, libraryFilter, { libraryFilter = it }, select, openSearch)
                                "settings" -> SettingsScreen(state, onRenameProfile, { focus.clearFocus(); linkOpen = true }, onAddAddon, onRemoveAddon, onDesktopSourceToggle,
                                    updates, onUpdateAction, onAutomaticUpdatesChange)
                                else -> HomeScreen(state, homeScroll, pager, !linkOpen && state.sourceQuery == null && !updates.noticeVisible,
                                    select, { categoryId = it }, onRetryHome, onContinue)
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
                    SourceSheet(state, query, onDismissSources, onPlaySource, onSettings = {
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
            if (!linkOpen && state.sourceQuery == null && !imeVisible && updates.noticeVisible) {
                UpdateDialog(updates, onUpdateAction)
            }
        }
    }
}
