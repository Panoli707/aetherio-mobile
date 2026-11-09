package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.RuleChain

@RunWith(AndroidJUnit4::class)
class WatchingScreenTest {
    val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(ForegroundArtworkTestRule()).around(compose)

    @Test fun categoriesKeepIndependentPeriodAndOnlyAnimeExposesTracking() {
        val state = mutableStateOf(AppState())
        var opened = false
        compose.setContent { AetherioTheme {
            WatchingScreen(state.value, {}, { opened = true }, { request, _ ->
                val calendar = WatchingCalendarState(request, loaded = true)
                state.value = if (request.category == WatchingCategory.ANIME) state.value.copy(animeCalendar = calendar)
                    else state.value.copy(movieTvCalendar = calendar)
            })
        } }
        compose.onNodeWithTag("openWatchingAnime").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(opened) }
        compose.onNodeWithText("Mes", substring = false).performClick()
        compose.onNodeWithTag("watchingCategory:MOVIES_TV").performClick()
        compose.onNodeWithTag("openWatchingAnime").assertDoesNotExist()
        compose.onNodeWithText("Semana", substring = false).assertIsSelected()
        compose.onNodeWithTag("watchingCategory:ANIME").performClick()
        compose.onNodeWithText("Mes", substring = false).assertIsSelected()
        compose.onNodeWithTag("openWatchingAnime").assertIsDisplayed()
    }

    @Test fun dateOnlyPremieresOpenTheirOwnDetailsAndErrorsCanRetry() {
        val today = WatchingDates.date(System.currentTimeMillis(), AnimeSeason.japan)
        val media = MediaItem("900000001", MediaType.SERIES, "Anime futuro", CatalogSource.ANILIST, isAnime = true)
        val state = mutableStateOf(AppState())
        var selected: MediaItem? = null
        var retried = false
        compose.setContent { AetherioTheme {
            WatchingScreen(state.value, { selected = it }, {}, { request, force ->
                retried = force
                state.value = state.value.copy(animeCalendar = WatchingCalendarState(request,
                    listOf(WatchingEvent(media, today, episode = 1, premiere = true)), loaded = true,
                    error = if (force) null else "Sin conexión"))
            })
        } }
        compose.onNodeWithTag("watchingAgenda").performScrollToNode(hasText("Reintentar"))
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertTrue(retried) }
        compose.onNodeWithTag("watchingAgenda").performScrollToNode(hasText(media.title))
        compose.onNodeWithText(media.title).performClick()
        compose.runOnIdle { assertEquals(media, selected) }
        compose.onNodeWithText("Episodio 1 · Estreno").assertExists()
    }

    @Test fun navigationKeepsTrackingProgressAndReturnsToTheCalendarAfterDetails() {
        val today = WatchingDates.date(System.currentTimeMillis(), AnimeSeason.japan)
        val anime = MediaItem("900000002", MediaType.SERIES, "Anime seguido", CatalogSource.ANILIST, isAnime = true, episodeCount = 12)
        val progress = WatchProgress(WatchTarget(anime, 1, 2, "Capítulo pendiente"), 754321, 1440000)
        val state = mutableStateOf(AppState(homeLoading = false, watchingAnime = listOf(anime),
            watchProgress = listOf(progress, WatchProgress(WatchTarget(anime, 1, 1), watched = true)),
            animeSeason = AnimeSeasonState(entries = listOf(SeasonalAnime(anime, AnimeWeekday.MONDAY, 3, 1793973600)), loaded = true)))
        compose.setContent {
            AetherioApp(state.value, UpdateState(),
                onSelectMedia = { state.value = state.value.copy(selectedItem = it, detail = MediaDetail(it)) },
                onToggleSaved = {}, onSearch = {}, onRetryHome = {}, onRetryDetail = {}, onSelectSeason = {},
                onRenameProfile = {}, onOpenVideo = { _, _ -> }, onAddAddon = {}, onRemoveAddon = {},
                onDesktopSourceToggle = { _, _ -> }, onRequestSources = { _, _, _ -> }, onDismissSources = {},
                onPlaySource = { _, _ -> }, onUpdateAction = {}, onAutomaticUpdatesChange = {},
                onLoadWatchingCalendar = { request, _ -> state.value = state.value.copy(animeCalendar =
                    WatchingCalendarState(request, listOf(WatchingEvent(anime, today, episode = 3)), loaded = true)) })
        }
        compose.onNode(hasText("Calendario") and hasClickAction()).performClick()
        compose.onNodeWithTag("openWatchingAnime").performClick()
        compose.onNodeWithText("12:34 / 24:00").assertIsDisplayed()
        compose.onNodeWithText("1/12 vistos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Temporada y días de emisión").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("watchingScreen").assertExists()
        compose.onNodeWithTag("watchingAgenda").performScrollToNode(hasText(anime.title))
        compose.onNodeWithText(anime.title).performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("watchingScreen").assertExists()
        compose.onNodeWithText(anime.title).assertExists()
    }

    @Test fun partialMovieCalendarsOfferMoreResultsInsteadOfClaimingThereAreNoAirings() {
        val today = WatchingDates.date(System.currentTimeMillis())
        val movie = MediaItem("900000003", MediaType.MOVIE, "Estreno de película")
        val state = mutableStateOf(AppState())
        compose.setContent { AetherioTheme {
            WatchingScreen(state.value, {}, {}, { request, _ ->
                val calendar = WatchingCalendarState(request.copy(page = 1),
                    if (request.page == 1) emptyList() else listOf(WatchingEvent(movie, today)),
                    loaded = true, page = request.page, hasMore = request.page == 1)
                state.value = if (request.category == WatchingCategory.ANIME) state.value.copy(animeCalendar = calendar.copy(hasMore = false))
                    else state.value.copy(movieTvCalendar = calendar)
            })
        } }
        compose.onNodeWithTag("watchingCategory:MOVIES_TV").performClick()
        compose.onNodeWithText("Títulos por popularidad", substring = true).assertDoesNotExist()
        compose.onNodeWithText("No indica disponibilidad en tu país", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Hay más títulos por consultar", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("watchingAgenda").performScrollToNode(hasTestTag("watchingLoadMore"))
        compose.onNodeWithText("Sin emisiones confirmadas").assertDoesNotExist()
        compose.onNodeWithTag("watchingLoadMore").performClick()
        compose.onNodeWithTag("watchingAgenda").performScrollToNode(hasText(movie.title))
        compose.onNodeWithText(movie.title).assertIsDisplayed()
        compose.onNodeWithTag("watchingLoadMore").assertDoesNotExist()
    }

    @Test fun failedInitialLoadsDoNotInventZeroAiringCounts() {
        val state = mutableStateOf(AppState())
        compose.setContent { AetherioTheme {
            WatchingScreen(state.value, {}, {}, { request, _ -> state.value = state.value.copy(
                animeCalendar = WatchingCalendarState(request, error = "Sin conexión")) })
        } }
        compose.onNodeWithTag("watchingAgenda").performScrollToNode(hasText("Agenda no disponible", substring = true))
        compose.onNodeWithText("0 estrenos y emisiones", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Sin emisiones confirmadas").assertDoesNotExist()
    }

    @Test fun expandedMovieListsResetAfterDetailsAndAfterSwitchingCategories() {
        val today = WatchingDates.date(System.currentTimeMillis())
        val first = (1..18).map { WatchingEvent(MediaItem("900000$it", MediaType.MOVIE, "Película $it"), today) }
        val extra = WatchingEvent(MediaItem("90000099", MediaType.MOVIE, "Película adicional"), today)
        val state = mutableStateOf(AppState())
        val visible = mutableStateOf(true)
        var resets = 0
        compose.setContent { AetherioTheme {
            val scope = rememberCoroutineScope()
            val holder = rememberSaveableStateHolder()
            val loader = remember { WatchingCalendarLoader(scope, { request ->
                WatchingPage(if (request.page == 1) first else listOf(extra), request.page == 1)
            }, { calendar -> state.value = state.value.copy(movieTvCalendar = calendar) }) }
            if (visible.value) holder.SaveableStateProvider("calendar") {
                WatchingScreen(state.value, { visible.value = false }, {}, { request, force ->
                    if (request.category == WatchingCategory.MOVIES_TV) loader.load(request, force)
                    else state.value = state.value.copy(animeCalendar = WatchingCalendarState(request, loaded = true))
                }, { resets++; loader.resetToFirstPage() })
            } else TextButton(onClick = { visible.value = true }) { Text("Volver al calendario") }
        } }
        compose.onNodeWithTag("watchingCategory:MOVIES_TV").performClick()
        compose.onNodeWithTag("watchingAgenda").performScrollToNode(hasTestTag("watchingLoadMore"))
        compose.onNodeWithTag("watchingLoadMore").performClick()
        compose.onNodeWithTag("watchingAgenda").performScrollToNode(hasText(extra.item.title))
        compose.onNodeWithText(extra.item.title).performClick()
        compose.runOnIdle { assertEquals(1, resets); assertEquals(1, state.value.movieTvCalendar.page); assertEquals(first.size, state.value.movieTvCalendar.events.size) }
        compose.onNodeWithText("Volver al calendario").performClick()
        compose.onNodeWithTag("watchingDay:$today").assertIsDisplayed()
        compose.onNodeWithTag("watchingAgenda").performScrollToNode(hasTestTag("watchingLoadMore"))
        compose.onNodeWithTag("watchingLoadMore").performClick()
        compose.runOnIdle { assertEquals(2, state.value.movieTvCalendar.page) }
        compose.onNodeWithTag("watchingCategory:ANIME").performClick()
        compose.onNodeWithTag("watchingCategory:MOVIES_TV").performClick()
        compose.onNodeWithTag("watchingDay:$today").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, resets); assertEquals(1, state.value.movieTvCalendar.page); assertEquals(first.size, state.value.movieTvCalendar.events.size) }
    }
}
