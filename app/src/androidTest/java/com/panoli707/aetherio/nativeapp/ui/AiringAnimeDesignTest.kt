package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiringAnimeDesignTest {
    @get:Rule val compose = createComposeRule()
    private val anime = MediaItem("123", MediaType.SERIES, "Anime de prueba", source = CatalogSource.ANILIST,
        isAnime = true, episodeCount = 12, animeSeasonLabel = "Otoño 2026")

    @Test fun homeShowsCoverEpisodeAndTitleAndHeadingOpensCalendarWhileCardOpensDetail() {
        var category: String? = null
        var selected: MediaItem? = null
        val now = System.currentTimeMillis()
        val today = AnimeTodayState(AnimeToday.start(now), listOf(AnimeAiringEpisode(anime, 4, now / 1000 - 1)), loaded = true)
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(homeLoading = false, animeToday = today), rememberLazyListState(), rememberPagerState { 0 }, false,
                { selected = it }, { category = it }, {})
        } }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("airingAnimeSection"))
        compose.onNodeWithText("Ep. 4 / 12", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(anime.title).performClick()
        compose.runOnIdle { assertEquals(anime, selected); assertNull(category) }
        compose.onNodeWithText("Anime en emisión").performClick()
        compose.runOnIdle { assertEquals("airing", category) }
    }

    @Test fun unknownAiredEpisodeIsNotInventedFromTheNextEpisode() {
        compose.setContent { AetherioTheme {
            AiringAnimeSection(AnimeTodayState(loaded = true), {}, {}, {})
        } }
        compose.onNodeWithText("No hay emisiones confirmadas en los últimos 30 días. Consulta el calendario para ver las próximas.").assertExists()
        compose.onNodeWithText("Ep. 5 / 12", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun errorOffersAnActualRetryRatherThanLeavingAnEmptyHeading() {
        var retried = false
        compose.setContent { AetherioTheme {
            AiringAnimeSection(AnimeTodayState(error = "Sin conexión"), {}, {}, { retried = true })
        } }
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun retryRestoresHomeCoversAndRemovesTheFailureMessage() {
        val state = mutableStateOf(AnimeTodayState(error = "Sin conexión"))
        compose.setContent { AetherioTheme {
            AiringAnimeSection(state.value, {}, {}, {
                state.value = AnimeTodayState(entries = listOf(AnimeAiringEpisode(anime, 4, System.currentTimeMillis() / 1000)), loaded = true)
            })
        } }
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("No se pudieron actualizar las emisiones").assertDoesNotExist()
        compose.onNodeWithText(anime.title).assertExists()
    }

    @Test fun earlierDaysAreIncludedButFutureAndExpiredEventsAreNot() {
        val now = System.currentTimeMillis()
        val start = AnimeToday.start(now)
        compose.setContent { AetherioTheme {
            AiringAnimeSection(AnimeTodayState(entries = listOf(AnimeAiringEpisode(anime.copy(id = "1", title = "Ayer"), 2, start - 1),
                AnimeAiringEpisode(anime.copy(id = "2", title = "Futuro"), 3, now / 1000 + 3600),
                AnimeAiringEpisode(anime.copy(id = "3", title = "Hoy"), 4, start),
                AnimeAiringEpisode(anime.copy(id = "4", title = "Antiguo"), 1, AnimeToday.historyStart(now) - 1)), loaded = true), {}, {}, {}, now)
        } }
        compose.onNodeWithText("Hoy").assertExists()
        compose.onNodeWithText("Ayer").assertExists()
        compose.onNodeWithText("Futuro").assertDoesNotExist()
        compose.onNodeWithText("Antiguo").assertDoesNotExist()
        compose.onNodeWithText("Emitidos recientemente · Japón (JST)").assertExists()
    }

    @Test fun horizontalPreferenceKeepsSpecialRecentRowPortraitWithoutChangingItsEpisodeOrNavigation() {
        val now = System.currentTimeMillis()
        val state = mutableStateOf(AppState(homeLoading = false,
            animeToday = AnimeTodayState(entries = listOf(AnimeAiringEpisode(anime, 4, now / 1000)), loaded = true)))
        var selected: MediaItem? = null
        compose.setContent { AetherioTheme {
            HomeScreen(state.value, rememberLazyListState(), rememberPagerState { 0 }, false, { selected = it }, {}, {})
        } }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("airingAnimeSection"))
        fun ratio(): Float {
            val bounds = compose.onNodeWithTag("airingArt:${anime.key}", true).getUnclippedBoundsInRoot()
            return (bounds.right - bounds.left).value / (bounds.bottom - bounds.top).value
        }
        assertEquals(2f / 3f, ratio(), .02f)
        compose.runOnIdle { state.value = state.value.copy(homePreferences = HomePreferences(horizontalPosters = true)) }
        assertEquals(2f / 3f, ratio(), .02f)
        compose.onNodeWithText("Ep. 4 / 12", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(anime.title).performClick()
        compose.runOnIdle { assertEquals(anime, selected) }
        compose.runOnIdle { state.value = state.value.copy(homePreferences = HomePreferences()) }
        assertEquals(2f / 3f, ratio(), .02f)
    }

    @Test fun recentRowHasRelativeAgeSeasonAndScrollableCardsBeyondThirty() {
        val now = System.currentTimeMillis()
        val entries = (1..45).map { AnimeAiringEpisode(anime.copy(id = "$it", title = "Anime $it"), 4, now / 1000 - it * 3600) }
        var selected: MediaItem? = null
        compose.setContent { AetherioTheme {
            AiringAnimeSection(AnimeTodayState(entries = entries, loaded = true), { selected = it }, {}, {}, now)
        } }
        compose.onNodeWithText("Hace 1 h", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Otoño 2026", useUnmergedTree = true).onFirst().assertExists()
        val first = compose.onNodeWithTag("airingArt:${entries.first().item.key}", true).getUnclippedBoundsInRoot()
        val row = compose.onNodeWithTag("airingAnimeCards").getUnclippedBoundsInRoot()
        assertTrue(first.right - first.left < (row.right - row.left) / 2)
        compose.onNodeWithContentDescription("Más emisiones recientes").performClick()
        compose.onNodeWithContentDescription("Emisiones anteriores en la fila").assertIsEnabled()
        compose.onNodeWithTag("airingAnimeCards").performScrollToIndex(44)
        compose.onNodeWithText("Anime 45").performClick()
        compose.runOnIdle { assertEquals(entries.last().item, selected) }
    }

    @Test fun sharedThemeUsesTheApprovedDetailPaletteForAllSurfacesAndControls() {
        var colors: List<Color> = emptyList()
        compose.setContent { AetherioTheme {
            val scheme = MaterialTheme.colorScheme
            colors = listOf(scheme.background, scheme.surface, scheme.primary, scheme.secondary, scheme.tertiary)
            LibraryScreen(AppState(), androidx.compose.foundation.lazy.grid.rememberLazyGridState(), "all", {}, {}, {})
        } }
        compose.onNodeWithText("En curso, vistos y guardados").assertExists()
        compose.runOnIdle { assertEquals(listOf(Ink, Panel, Accent, Muted, Cream), colors) }
    }
}
