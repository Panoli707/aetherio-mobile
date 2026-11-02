package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val anime = MediaItem("123", MediaType.SERIES, "Anime local", isAnime = true, episodeCount = 12, animeSeasonLabel = "Otoño 2026")

    @Test fun dedicatedAnimePagePlacesPendingEpisodesAboveTrackedPostersAndKeepsWatchedCounts() {
        val completed = WatchProgress(WatchTarget(anime, 1, 1), watched = true, updatedAt = 1)
        val progress = WatchProgress(WatchTarget(anime, 1, 2, "Capítulo pendiente"), 754321, 1440000, updatedAt = 2)
        var resumed: WatchProgress? = null
        compose.setContent { AetherioTheme {
            AnimeScreen(AppState(watchingAnime = listOf(anime), watchProgress = listOf(completed, progress)),
                rememberLazyListState(), {}, { resumed = it }, {})
        } }
        compose.onNodeWithText("12:34 / 24:00").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(progress, resumed) }
        compose.onNodeWithText("Estás viendo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1/12 vistos").performScrollTo().assertIsDisplayed()
    }

    @Test fun weekdayFilterChangesTheActualSeasonResultsAndUnknownSchedulesHaveTheirOwnFilter() {
        val monday = SeasonalAnime(anime.copy(id = "1", title = "Anime lunes"), AnimeWeekday.MONDAY, 3, 1790000000)
        val friday = SeasonalAnime(anime.copy(id = "2", title = "Anime viernes"), AnimeWeekday.FRIDAY, 6, 1793973600)
        val unknown = SeasonalAnime(anime.copy(id = "3", title = "Anime sin horario"), null, null, null)
        compose.setContent { AetherioTheme {
            SeasonalAnimeScreen(AnimeSeasonState(AnimeSeason(2026, "FALL"), listOf(monday, friday, unknown), loaded = true),
                rememberLazyListState(), {}, {}, {})
        } }
        compose.onNode(hasText("Viernes") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Anime viernes").assertExists()
        compose.onNodeWithText("Anime lunes").assertDoesNotExist()
        compose.onNodeWithText("Anime sin horario").assertDoesNotExist()
        compose.onNode(hasText("Sin horario") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Anime sin horario").assertExists()
        compose.onNodeWithText("Anime viernes").assertDoesNotExist()
    }

    @Test fun seasonMenuUsesAnExplicitBulkActionAndDoesNotPlayOrMarkOnOpeningTheSheet() {
        val episode = EpisodeItem(2, 1, "Capítulo dos")
        var marked: Pair<EpisodeWatchScope, Boolean>? = null
        var played = false
        compose.setContent { AetherioTheme {
            EpisodeActionsSheet(anime, episode, listOf(EpisodeItem(1, 1, "Uno"), episode), emptyList(), {},
                { scope, watched -> marked = scope to watched }, { played = true }, { played = true })
        } }
        compose.onNodeWithText("Marcar como visto").assertIsDisplayed()
        compose.onNodeWithText("Marcar anteriores como vistos").assertIsDisplayed()
        compose.runOnIdle { assertNull(marked); assertFalse(played) }
        compose.onNodeWithText("Marcar temporada 1 como vista").performClick()
        compose.runOnIdle { assertEquals(EpisodeWatchScope.SEASON to true, marked); assertFalse(played) }
    }
}
