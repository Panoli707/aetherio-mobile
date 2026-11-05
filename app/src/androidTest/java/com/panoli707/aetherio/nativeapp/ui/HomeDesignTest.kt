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

    @Test fun aSlowBackgroundRowDoesNotKeepTheWholeCatalogLoadingMessage() {
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(homeLoading = true, sections = listOf(
                CatalogSection("tmdb.top_series", "Series populares", listOf(movie)),
                CatalogSection("later", "Otra fila", emptyList(), loading = true))),
                rememberLazyListState(), rememberPagerState { 0 }, false, {}, {}, {})
        } }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasText("Series populares"))
        compose.onNodeWithText("Series populares").assertIsDisplayed()
        compose.onNodeWithText("Cargando catálogo…").assertDoesNotExist()
    }

    @Test fun featuredMetadataShowsRatingVotesYearGenreAndAgeRatingUnderTheTitle() {
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(heroes = listOf(movie.copy(voteCount = 58, genres = listOf("Terror"),
                certification = "16 (ES)")), homeLoading = false), rememberLazyListState(),
                rememberPagerState { 1 }, false, {}, {}, {})
        } }
        compose.onNodeWithTag("homeHeroMetadata", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("6,7", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("58 votos", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2026", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Terror", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("16 (ES)", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(movie.overview, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun missingFeaturedMetadataDoesNotShowFakeRatingsOrVotes() {
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(heroes = listOf(movie.copy(score = 0.0, year = "")), homeLoading = false),
                rememberLazyListState(), rememberPagerState { 1 }, false, {}, {}, {})
        } }
        compose.onNodeWithText("0,0", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("0 votos", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("NR", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(movie.title).assertIsDisplayed()
    }

    @Test fun aNewPosterHeroShowsItsTitleBeforeAnyLogoOrDescriptionLoads() {
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(heroes = listOf(movie.copy(posterUrl = "https://example.org/poster.jpg", overview = "", logoUrl = null)),
                homeLoading = true), rememberLazyListState(), rememberPagerState { 1 }, false, {}, {}, {})
        } }
        compose.onNodeWithText(movie.title, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun cachedProviderSubtitlesAreNotShownUnderCatalogRowTitles() {
        val row = CatalogSection("mal.airing_anime", "Anime en emisión", listOf(movie), subtitle = "AniList")
        compose.setContent { AetherioTheme { HomeCatalogSection(row, {}, {}, {}) } }
        compose.onNodeWithText("Anime en emisión").assertIsDisplayed()
        compose.onNodeWithText("AniList").assertDoesNotExist()
    }

    @Test fun aProgressiveHeroRefreshCanShrinkPastTheCurrentPagerPage() {
        val heroes = mutableStateOf((1..10).map { movie.copy(id = it.toString(), title = "Destacado $it") })
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(heroes = heroes.value, homeLoading = false), rememberLazyListState(),
                rememberPagerState(initialPage = 8) { heroes.value.size }, false, {}, {}, {})
        } }
        compose.onNodeWithTag("homeHero").assertExists()
        compose.runOnIdle { heroes.value = heroes.value.take(5) }
        compose.waitForIdle()
        compose.onNodeWithTag("homeHero").assertExists()
        compose.onNodeWithContentDescription("Destacado 5 de 5").assertExists()
    }

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

    @Test fun automaticAdvanceStopsWhenHeroIsOffScreen() {
        lateinit var pager: PagerState
        val sections = (1..8).map { index -> CatalogSection("fixture$index", "Sección $index", listOf(movie, second)) }
        compose.setContent { AetherioTheme {
            pager = rememberPagerState { 2 }
            HomeScreen(AppState(heroes = listOf(movie, second), sections = sections, homeLoading = false),
                rememberLazyListState(), pager, true, {}, {}, {})
        } }
        compose.onNodeWithTag("homeScroll").performScrollToIndex(2)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(20_000)
        compose.runOnIdle { assertEquals(0, pager.currentPage) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun holdingATouchPausesTheTimerWithoutConsumingTheGesture() {
        lateinit var pager: PagerState
        compose.setContent { AetherioTheme {
            pager = rememberPagerState { 2 }
            HomeScreen(AppState(heroes = listOf(movie, second), homeLoading = false),
                rememberLazyListState(), pager, true, {}, {}, {})
        } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("homeHero").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(10_000)
        compose.runOnIdle { assertEquals(0, pager.currentPage) }
        compose.onNodeWithTag("homeHero").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(7_000)
        compose.runOnIdle { assertEquals(0, pager.currentPage) }
        compose.mainClock.advanceTimeBy(2_000)
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
        compose.onNodeWithText("Perfil local").performClick()
        compose.onNodeWithText("Nombre del perfil").performScrollTo().performTextReplacement("Mi perfil")
        compose.onNodeWithText("Guardar nombre").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Mi perfil", renamed) }
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithContentDescription("Abrir perfil").assertIsDisplayed()
    }
}
