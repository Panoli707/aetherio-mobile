package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchProgressUiTest {
    @get:Rule val compose = createComposeRule()
    private val anime = MediaItem("20", MediaType.SERIES, "Anime de prueba", isAnime = true)
    private val episode = EpisodeItem(3, 2, "Capitulo tres")
    private val progress = WatchProgress(WatchTarget(anime, 2, 3, episode.title), 754321, 1440000, updatedAt = 1)

    @Test fun continueWatchingShowsExactTimeAndDispatchesTheSameEpisode() {
        var resumed: WatchProgress? = null
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(homeLoading = false, watchProgress = listOf(progress)), rememberLazyListState(),
                rememberPagerState { 0 }, false, {}, {}, {}, { resumed = it })
        } }
        compose.onNodeWithText("Continuar viendo").assertIsDisplayed()
        compose.onNodeWithText("12:34 / 24:00").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(progress, resumed) }
    }

    @Test fun primaryReproducirActionStillResumesTheSavedEpisode() {
        val detail = MediaDetail(anime, seasons = listOf(SeasonInfo(2, "Temporada 2", 1)), episodes = listOf(episode), selectedSeason = 2)
        var selected: Pair<Int?, Int?>? = null
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = detail, watchProgress = listOf(progress)), anime, rememberLazyListState(), {}, {}, {}, {}, {},
                { _, season, number -> selected = season to number })
        } }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("detailPrimaryPlay"))
        compose.onNodeWithText("Reproducir · T2 E3 · 12:34").performClick()
        compose.runOnIdle { assertEquals(2 to 3, selected) }
    }

    @Test fun episodeRowShowsProgressAndWatchedActionDoesNotStartPlayback() {
        var marked: EpisodeItem? = null
        var played = false
        val detail = MediaDetail(anime, seasons = listOf(SeasonInfo(2, "Temporada 2", 1)), episodes = listOf(episode), selectedSeason = 2)
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = detail, watchProgress = listOf(progress)), anime, rememberLazyListState(), {}, {}, {}, {}, {},
                { _, _, _ -> played = true }, { _, ep, scope, watched ->
                    assertEquals(EpisodeWatchScope.EPISODE, scope); assertTrue(watched); marked = ep })
        } }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("detailEpisodes"))
        compose.onNodeWithTag("detailEpisodes").performScrollToNode(hasContentDescription("Opciones del episodio T2 E3"))
        compose.onNodeWithText("12:34 / 24:00", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Opciones del episodio T2 E3").performClick()
        compose.onNodeWithText("Marcar como visto").performClick()
        compose.runOnIdle { assertEquals(episode, marked); assertFalse(played) }
    }

    @Test fun watchedEpisodeHasAnUndoAction() {
        val detail = MediaDetail(anime, seasons = listOf(SeasonInfo(2, "Temporada 2", 1)), episodes = listOf(episode), selectedSeason = 2)
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = detail, watchProgress = listOf(progress.copy(watched = true))), anime,
                rememberLazyListState(), {}, {}, {}, {}, {}, { _, _, _ -> })
        } }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("detailEpisodes"))
        compose.onNodeWithTag("detailEpisodes").performScrollToNode(hasContentDescription("Opciones del episodio T2 E3"))
        compose.onNodeWithText("Visto", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Opciones del episodio T2 E3").performClick()
        compose.onNodeWithText("Marcar como no visto").assertIsDisplayed()
        compose.onNodeWithText("Reproducir · T2 E3 · 12:34").assertDoesNotExist()
    }

    @Test fun unreleasedEpisodeCardAndOptionsDoNothingUntilRelease() {
        var played = false
        var openedActions = false
        val futureEpisode = EpisodeItem(7, 1, "Episode 7", released = false, airedOn = "2099-11-14")
        val detail = MediaDetail(anime, seasons = listOf(SeasonInfo(1, "Temporada 1", 1)),
            episodes = listOf(futureEpisode), selectedSeason = 1)
        compose.setContent { AetherioTheme {
            DetailScreen(state = AppState(detail = detail), item = anime, scroll = rememberLazyListState(),
                onBack = {}, onToggleSaved = {}, onRetry = {}, onSeason = {}, onLink = {},
                onRequestSources = { _, _, _ -> played = true },
                onEpisodeWatchAction = { _, _, _, _ -> openedActions = true })
        } }

        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("detailEpisodes"))
        compose.onNodeWithTag("detailEpisodes").performScrollToNode(hasContentDescription("Episodio T1 E7"))
        compose.onNodeWithContentDescription("Episodio T1 E7").assertIsNotEnabled()
            .performTouchInput { click(); longClick() }
        compose.onNodeWithContentDescription("Opciones del episodio T1 E7").assertIsNotEnabled()
            .performTouchInput { click() }
        compose.onNodeWithTag("detailPrimaryPlay").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertFalse(played); assertFalse(openedActions) }
    }
}
