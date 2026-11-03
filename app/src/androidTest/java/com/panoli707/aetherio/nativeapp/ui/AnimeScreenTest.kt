package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
class AnimeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val anime = MediaItem("123", MediaType.SERIES, "Anime local", isAnime = true, episodeCount = 12, animeSeasonLabel = "Otoño 2026")

    @Test fun dedicatedAnimePagePlacesPendingEpisodesAboveTrackedPostersAndKeepsWatchedCounts() {
        val completed = WatchProgress(WatchTarget(anime, 1, 1), watched = true, updatedAt = 1)
        val progress = WatchProgress(WatchTarget(anime, 1, 2, "Capítulo pendiente"), 754321, 1440000, updatedAt = 2)
        var resumed: WatchProgress? = null
        compose.setContent { AetherioTheme {
            AnimeScreen(AppState(watchingAnime = listOf(anime), watchProgress = listOf(completed, progress),
                animeSeason = AnimeSeasonState(entries = listOf(SeasonalAnime(anime, AnimeWeekday.MONDAY, 3, 1793973600)), loaded = true)),
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
            var day by remember { mutableStateOf("MONDAY") }
            SeasonalAnimeScreen(AnimeSeasonState(AnimeSeason(2026, "FALL"), listOf(monday, friday, unknown), loaded = true),
                rememberLazyListState(), {}, {}, {}, day, { day = it })
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

    @Test fun failedRefreshDoesNotShowAnEmptyFilterAndRetryCanRestoreTheSeason() {
        val state = mutableStateOf(AnimeSeasonState(AnimeSeason(2026, "FALL"), loaded = true, error = "AniList está tardando demasiado"))
        val entry = SeasonalAnime(anime, AnimeWeekday.MONDAY, 3, 1793973600)
        var retried = false
        compose.setContent { AetherioTheme {
            SeasonalAnimeScreen(state.value, rememberLazyListState(), {}, {}, {
                retried = true
                state.value = state.value.copy(entries = listOf(entry), error = null, loaded = true)
            }, "MONDAY", {})
        } }
        compose.onNodeWithText("Sin emisiones para este filtro").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(retried) }
        compose.onNodeWithText("No se pudo actualizar la temporada").assertDoesNotExist()
        compose.onNodeWithText(anime.title).performScrollTo().assertIsDisplayed()
    }

    @Test fun updateFailureKeepsPreviousPostersAccessibleBelowTheWarning() {
        val state = AnimeSeasonState(AnimeSeason(2026, "FALL"),
            entries = listOf(SeasonalAnime(anime, AnimeWeekday.MONDAY, 3, 1793973600)),
            loaded = true, error = "Sin conexión")
        compose.setContent { AetherioTheme {
            SeasonalAnimeScreen(state, rememberLazyListState(), {}, {}, {}, "all", {})
        } }
        compose.onNodeWithText("No se pudo actualizar la temporada").assertExists()
        compose.onNodeWithText("Sin emisiones para este filtro").assertDoesNotExist()
        compose.onNodeWithText(anime.title).performScrollTo().assertIsDisplayed()
    }

    @Test fun calendarStartsOnMondayAndKeepsTheChosenDayAfterOpeningAndClosingADetail() {
        val monday = anime.copy(id = "900000001", title = "Anime lunes")
        val friday = anime.copy(id = "900000002", title = "Anime viernes")
        val state = mutableStateOf(AppState(homeLoading = false, animeSeason = AnimeSeasonState(entries = listOf(
            SeasonalAnime(monday, AnimeWeekday.MONDAY, 2, 1793973600), SeasonalAnime(friday, AnimeWeekday.FRIDAY, 2, 1793973600)), loaded = true)))
        compose.setContent {
            AetherioApp(state.value, UpdateState(),
                onSelectMedia = { state.value = state.value.copy(selectedItem = it, detail = MediaDetail(it)) },
                onToggleSaved = {}, onSearch = {}, onRetryHome = {}, onRetryDetail = {}, onSelectSeason = {},
                onRenameProfile = {}, onOpenVideo = { _, _ -> }, onAddAddon = {}, onRemoveAddon = {},
                onDesktopSourceToggle = { _, _ -> }, onRequestSources = { _, _, _ -> }, onDismissSources = {},
                onPlaySource = { _, _ -> }, onUpdateAction = {}, onAutomaticUpdatesChange = {})
        }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasText("Anime emitiéndose ahora"))
        compose.onNodeWithText("Anime emitiéndose ahora").performClick()
        compose.onNode(hasText("Lunes") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("Anime lunes").performClick()
        compose.onNodeWithText("ANIME").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNode(hasText("Lunes") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("Viernes") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Anime viernes").performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNode(hasText("Viernes") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("Anime viernes").assertExists()
        compose.onNodeWithText("Anime lunes").assertDoesNotExist()
    }

    @Test fun libraryShowsWatchedAndPendingTitlesEvenWhenTheyWereNeverFavorited() {
        val movie = MediaItem("900000003", MediaType.MOVIE, "Película terminada")
        val series = anime.copy(id = "900000004", title = "Serie en curso")
        compose.setContent { AetherioTheme {
            LibraryScreen(AppState(watchProgress = listOf(WatchProgress(WatchTarget(movie), watched = true),
                WatchProgress(WatchTarget(series, 1, 1), 10000, 1440000))),
                androidx.compose.foundation.lazy.grid.rememberLazyGridState(), "all", {}, {}, {})
        } }
        compose.onNodeWithText("Película terminada").assertExists()
        compose.onNodeWithText("Serie en curso").assertExists()
    }
}
