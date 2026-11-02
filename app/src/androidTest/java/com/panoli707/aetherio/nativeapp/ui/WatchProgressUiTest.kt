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

    @Test fun episodeRowShowsProgressAndWatchedActionDoesNotStartPlayback() {
        var marked: EpisodeItem? = null
        var played = false
        val detail = MediaDetail(anime, seasons = listOf(SeasonInfo(2, "Temporada 2", 1)), episodes = listOf(episode), selectedSeason = 2)
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = detail, watchProgress = listOf(progress)), anime, rememberLazyListState(), {}, {}, {}, {}, {},
                { _, _, _ -> played = true }, { _, ep -> marked = ep })
        } }
        compose.onNodeWithText("12:34 / 24:00", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Marcar como visto: T2 E3").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(episode, marked); assertFalse(played) }
    }

    @Test fun watchedEpisodeHasAnUndoAction() {
        val detail = MediaDetail(anime, seasons = listOf(SeasonInfo(2, "Temporada 2", 1)), episodes = listOf(episode), selectedSeason = 2)
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = detail, watchProgress = listOf(progress.copy(watched = true))), anime,
                rememberLazyListState(), {}, {}, {}, {}, {}, { _, _, _ -> })
        } }
        compose.onNodeWithContentDescription("Marcar como no visto: T2 E3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Visto", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Continuar · T2 E3 · 12:34").assertDoesNotExist()
    }
}
