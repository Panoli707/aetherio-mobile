package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDesignSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingContentModeFiltersEveryHeroAndClampsThePagerWhenTheListShrinks() {
        val movie = MediaItem("900000001", MediaType.MOVIE, "Película destacada")
        val anime = movie.copy(id = "900000002", title = "Anime destacado", isAnime = true)
        val series = movie.copy(id = "900000003", type = MediaType.SERIES, title = "Serie destacada")
        val state = mutableStateOf(AppState(homeLoading = false, heroes = listOf(movie, series, anime),
            homePreferences = HomePreferences(content = HomeContentPreference.ANIME)))
        lateinit var pager: androidx.compose.foundation.pager.PagerState
        compose.setContent { AetherioTheme {
            pager = rememberPagerState { state.value.homePreferences.visibleHeroes(state.value.heroes).size }
            HomeScreen(state.value, rememberLazyListState(), pager, false, {}, {}, {})
        } }
        compose.runOnIdle { assertEquals(1, pager.pageCount) }
        compose.onNode(hasClickAction() and hasText("Anime destacado")).assertExists()
        compose.onNode(hasClickAction() and hasText("Película destacada")).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(homePreferences = HomePreferences(content = HomeContentPreference.MOVIES_SERIES)) }
        compose.runOnIdle { assertEquals(2, pager.pageCount) }
        compose.onNodeWithTag("homeHero").performTouchInput { swipeLeft() }
        compose.runOnIdle { state.value = state.value.copy(homePreferences = HomePreferences(content = HomeContentPreference.ANIME)) }
        compose.runOnIdle { assertEquals(1, pager.pageCount); assertEquals(0, pager.currentPage) }
        compose.runOnIdle { state.value = state.value.copy(homePreferences = HomePreferences(content = HomeContentPreference.BOTH)) }
        compose.runOnIdle { assertEquals(3, pager.pageCount) }
    }

    @Test fun preferencesChangeImmediatelyAndResetWithoutFakingDesktopIntegrations() {
        val state = mutableStateOf(AppState(homePreferences = HomePreferences()))
        compose.setContent { AetherioTheme {
            Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState())) {
                HomeDesignSettings(state.value) { state.value = state.value.copy(homePreferences = it) }
            }
        } }
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        if (output != null) {
            File(output, "home-design-settings.png").apply { parentFile?.mkdirs() }.outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("designContent:ANIME").performClick()
        compose.runOnIdle { assertEquals(HomeContentPreference.ANIME, state.value.homePreferences.content) }
        compose.onNodeWithTag("designPosters").performClick()
        compose.runOnIdle { assertTrue(state.value.homePreferences.horizontalPosters) }
        compose.onNodeWithTag("designVisible:tmdb.top_series").performScrollTo().performClick()
        compose.runOnIdle { assertTrue("tmdb.top_series" in state.value.homePreferences.hiddenCatalogIds) }
        compose.onNodeWithTag("designReset").performScrollTo().performClick()
        compose.onNodeWithText("Restablecer").performClick()
        compose.runOnIdle { assertEquals(HomePreferences(), state.value.homePreferences) }
        compose.onNodeWithText("SpatialPosters").assertDoesNotExist()
    }

    @Test fun movingRowsAndCancellingResetPreservesPreferences() {
        val state = mutableStateOf(AppState(homePreferences = HomePreferences()))
        compose.setContent { AetherioTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                HomeDesignSettings(state.value) { state.value = state.value.copy(homePreferences = it) }
            }
        } }
        compose.onNodeWithTag("designDown:tmdb.top_series").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("tmdb.trending_movie", state.value.homePreferences.catalogOrder.first()) }
        compose.onNodeWithTag("designVisible:tmdb.top_series").performScrollTo().performClick()
        compose.onNodeWithTag("designReset").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle {
            assertTrue("tmdb.top_series" in state.value.homePreferences.hiddenCatalogIds)
            assertTrue(state.value.homePreferences.catalogOrder.isNotEmpty())
        }
    }

    @Test fun hidingHomeAnchorsKeepsGenreNavigationAndLibraryIndependent() {
        val movie = MediaItem("fixture", MediaType.MOVIE, "Título de prueba")
        val sections = listOf(CatalogSection("tmdb.top_movie", "Películas populares", listOf(movie)),
            CatalogSection("jikan.recommendations", "Anime recomendado", listOf(movie.copy(isAnime = true))))
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(homeLoading = false, sections = sections, savedItems = listOf(movie),
                homePreferences = HomePreferences(hiddenCatalogIds = sections.map { it.id }.toSet() + "mal.airing_anime")),
                rememberLazyListState(), rememberPagerState { 0 }, false, {}, {}, {})
        } }
        compose.onNodeWithTag("homeSection:tmdb.top_movie").assertDoesNotExist()
        compose.onNodeWithTag("homeSection:jikan.recommendations").assertDoesNotExist()
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("movieSeriesGenres"))
        compose.onNodeWithText("Explorar géneros - Series y películas").assertIsDisplayed()
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("animeGenres"))
        compose.onNodeWithText("Explorar géneros - Anime").assertIsDisplayed()
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasText("Catálogos ocultos"))
        compose.onNodeWithText("Catálogos ocultos").assertIsDisplayed()
    }
}
