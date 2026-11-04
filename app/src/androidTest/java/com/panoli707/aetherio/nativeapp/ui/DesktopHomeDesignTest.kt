package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopHomeDesignTest {
    @get:Rule val compose = createComposeRule()
    private val movie = MediaItem("900000001", MediaType.MOVIE, "Película de prueba", year = "1999")
    private fun rows() = DesktopHomeCatalog.rails().mapIndexed { index, spec ->
        spec.section(listOf(movie.copy(id = (900000001 + index).toString(), type = spec.type, title = "Título $index", isAnime = spec.anime)))
    }

    @Test fun refreshDispatchesOnceAndIsDisabledWhileTheCatalogIsLoading() {
        val state = mutableStateOf(AppState(homeLoading = false))
        var requests = 0
        compose.setContent { AetherioTheme {
            HomeScreen(state.value, rememberLazyListState(), rememberPagerState { 0 }, false, {}, {}, {
                requests++; state.value = state.value.copy(homeLoading = true)
            })
        } }
        compose.onNodeWithContentDescription("Actualizar catálogo").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Actualizar catálogo").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun all44RailsAndBothGenreExplorersRemainReachableOnPhone() {
        lateinit var scroll: LazyListState
        compose.setContent { AetherioTheme {
            scroll = rememberLazyListState()
            HomeScreen(AppState(sections = rows(), homeLoading = false), scroll, rememberPagerState { 0 }, false, {}, {}, {})
        } }
        compose.runOnIdle { assertEquals(48, scroll.layoutInfo.totalItemsCount) }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("movieSeriesGenres"))
        compose.onNodeWithText("Explorar géneros - Series y películas").assertIsDisplayed()
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("animeGenres"))
        compose.onNodeWithText("Explorar géneros - Anime").assertIsDisplayed()
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("homeSection:jikan.top_movies"))
        compose.onNodeWithText("Películas anime más populares").assertIsDisplayed()
    }
    @Test fun everyPlatformCardDispatchesItsOwnCatalogRoute() {
        val opened = mutableListOf<String>()
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(homeLoading = false), rememberLazyListState(), rememberPagerState { 0 }, false, {}, { opened += it }, {})
        } }
        for (platform in DesktopHomeCatalog.platforms) {
            val id = "platform:${platform.id}"
            compose.onNodeWithTag("streamingPlatforms:cards").performScrollToNode(hasTestTag("catalogLink:$id"))
            compose.onNodeWithTag("catalogLink:$id").assertIsDisplayed().performClick()
        }
        compose.runOnIdle { assertEquals(DesktopHomeCatalog.platforms.map { "platform:${it.id}" }, opened) }
    }
    @Test fun genresDispatchDifferentMovieAndAnimeRoutes() {
        var opened: String? = null
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(sections = rows(), homeLoading = false), rememberLazyListState(), rememberPagerState { 0 }, false, {}, { opened = it }, {})
        } }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("movieSeriesGenres"))
        compose.onNodeWithTag("catalogLink:genre:action-adventure").performClick()
        compose.runOnIdle { assertEquals("genre:action-adventure", opened) }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("animeGenres"))
        compose.onNodeWithTag("catalogLink:anime-genre:Action").performClick()
        compose.runOnIdle { assertEquals("anime-genre:Action", opened) }
    }
    @Test fun rankedRailKeepsItsTwentiethTitleAndRealRank() {
        var selected: MediaItem? = null
        val items = (1..20).map { movie.copy(id = (900000000 + it).toString(), title = "Película $it") }
        val section = CatalogSection("tmdb.trending_movie", "Top Películas", items, SectionStyle.RANKED)
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(sections = listOf(section), homeLoading = false), rememberLazyListState(), rememberPagerState { 0 }, false, { selected = it }, {}, {})
        } }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("homeSection:tmdb.trending_movie"))
        compose.onNodeWithTag("catalogItems:tmdb.trending_movie").performScrollToIndex(19)
        compose.onNodeWithText("Película 20").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(items.last().key, selected?.key) }
    }
    @Test fun failedRowDoesNotRemoveHealthyRowsOrTheirCatalogTitles() {
        val failed = DesktopHomeCatalog.rails().first { it.id == "tmdb.home.movie.horror" }.section(error = "Sin conexión")
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(sections = listOf(CatalogSection("healthy", "Catálogo disponible", listOf(movie)), failed), homeLoading = false),
                rememberLazyListState(), rememberPagerState { 0 }, false, {}, {}, {})
        } }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("homeSection:healthy"))
        compose.onNodeWithText("Catálogo disponible").assertIsDisplayed()
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("homeSection:tmdb.home.movie.horror"))
        compose.onNodeWithText("Sin conexión").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").assertExists()
    }
    @Test fun platformBrowseAndItsSeeAllGridReturnToTheCorrectParent() {
        val state = mutableStateOf(AppState(homeLoading = false))
        val platformRow = CatalogSection("streaming.netflix.movie.popular", "Películas populares", listOf(movie))
        compose.setContent {
            AetherioApp(state.value, UpdateState(), onSelectMedia = {}, onToggleSaved = {}, onSearch = {}, onRetryHome = {},
                onRetryDetail = {}, onSelectSeason = {}, onRenameProfile = {}, onOpenVideo = { _, _ -> }, onAddAddon = {},
                onRemoveAddon = {}, onDesktopSourceToggle = { _, _ -> }, onRequestSources = { _, _, _ -> }, onDismissSources = {},
                onPlaySource = { _, _ -> }, onUpdateAction = {}, onAutomaticUpdatesChange = {}, onBrowse = { id, _ ->
                    state.value = state.value.copy(browse = CatalogBrowseState(id, "Netflix", listOf(platformRow)))
                })
        }
        compose.onNodeWithTag("catalogLink:platform:netflix").performClick()
        compose.onNodeWithTag("catalogBrowse").assertIsDisplayed()
        compose.onNodeWithText("Netflix").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ver Películas populares").performClick()
        compose.onNodeWithText(movie.title).assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("catalogBrowse").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("homeScroll").assertExists()
    }
}
