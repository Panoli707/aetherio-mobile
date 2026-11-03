package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
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
class HomeDesignTest {
    @get:Rule val compose = createComposeRule()
    private val movie = MediaItem("900000001", MediaType.MOVIE, "Primera película",
        overview = "Una aventura en el bosque.", year = "2026", score = 6.7)
    private val second = movie.copy(id = "900000002", title = "Segunda película")

    @Test fun profileDoesNotAdvanceTheCarouselOrOpenTheMovie() {
        lateinit var pager: PagerState
        var profileOpened = false
        var selected: MediaItem? = null
        compose.setContent { AetherioTheme {
            pager = rememberPagerState { 2 }
            HomeScreen(AppState(heroes = listOf(movie, second), homeLoading = false), rememberLazyListState(),
                pager, false, { selected = it }, {}, {}, onProfile = { profileOpened = true })
        } }
        compose.onNodeWithContentDescription("Siguiente destacado").assertDoesNotExist()
        compose.onNodeWithContentDescription("Abrir perfil").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(profileOpened)
            assertEquals(0, pager.currentPage)
            assertNull(selected)
        }
        compose.onNode(hasClickAction() and hasText(movie.title)).performClick()
        compose.runOnIdle { assertEquals(movie, selected) }
    }

    @Test fun swipeChangesTheFeaturedMovieAndIndicator() {
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(heroes = listOf(movie, second), homeLoading = false), rememberLazyListState(),
                rememberPagerState { 2 }, false, {}, {}, {})
        } }
        compose.onNodeWithContentDescription("Destacado 1 de 2").assertIsDisplayed()
        compose.onNodeWithTag("homeHero").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Destacado 2 de 2").assertIsDisplayed()
        compose.onNodeWithText(second.title).assertIsDisplayed()
        compose.onNodeWithText(second.overview).assertIsDisplayed()
    }

    @Test fun automaticAdvanceOnlyRunsWhileHomeIsActive() {
        lateinit var pager: PagerState
        val active = mutableStateOf(true)
        compose.setContent { AetherioTheme {
            pager = rememberPagerState { 2 }
            HomeScreen(AppState(heroes = listOf(movie, second), homeLoading = false), rememberLazyListState(),
                pager, active.value, {}, {}, {})
        } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(9_000)
        compose.runOnIdle { assertEquals(1, pager.currentPage); active.value = false }
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.advanceTimeBy(9_000)
        compose.runOnIdle { assertEquals(1, pager.currentPage) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun singleFeatureStillOffersTheProfileWithoutPageIndicators() {
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(heroes = listOf(movie), homeLoading = false), rememberLazyListState(),
                rememberPagerState { 1 }, false, {}, {}, {})
        } }
        compose.onNodeWithContentDescription("Abrir perfil").assertIsDisplayed()
        compose.onNodeWithContentDescription("Destacado 1 de 1").assertDoesNotExist()
        compose.onNodeWithText(movie.title).assertIsDisplayed()
    }

    @Test fun profileIsAvailableBeforeTheCatalogLoads() {
        var opened = false
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(), rememberLazyListState(), rememberPagerState { 0 }, false,
                {}, {}, {}, onProfile = { opened = true })
        } }
        compose.onNodeWithContentDescription("Abrir perfil").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun profileOpensExistingSettingsAndSavesTheName() {
        var renamed: String? = null
        compose.setContent {
            AetherioApp(AppState(heroes = listOf(movie), homeLoading = false), UpdateState(),
                onSelectMedia = {}, onToggleSaved = {}, onSearch = {}, onRetryHome = {}, onRetryDetail = {},
                onSelectSeason = {}, onRenameProfile = { renamed = it }, onOpenVideo = { _, _ -> },
                onAddAddon = {}, onRemoveAddon = {}, onDesktopSourceToggle = { _, _ -> },
                onRequestSources = { _, _, _ -> }, onDismissSources = {}, onPlaySource = { _, _ -> },
                onUpdateAction = {}, onAutomaticUpdatesChange = {})
        }
        compose.onNodeWithContentDescription("Abrir perfil").performClick()
        compose.onNodeWithText("Nombre del perfil").performScrollTo().performTextReplacement("Mi perfil")
        compose.onNodeWithText("Guardar nombre").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Mi perfil", renamed) }
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithContentDescription("Abrir perfil").assertIsDisplayed()
    }
}
