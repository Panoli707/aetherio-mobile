package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
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
    private val anime = MediaItem("123", MediaType.SERIES, "Anime en emisión", source = CatalogSource.ANILIST,
        isAnime = true, episodeCount = 12, animeSeasonLabel = "Otoño 2026")

    @Test fun homeShowsCoverEpisodeAndTitleAndHeadingOpensCalendarWhileCardOpensDetail() {
        var category: String? = null
        var selected: MediaItem? = null
        val season = AnimeSeasonState(AnimeSeason(2026, "FALL"), listOf(SeasonalAnime(anime, AnimeWeekday.FRIDAY, 6, 1793973600,
            latestEpisode = 4, latestAiringAt = 1791554400)), loaded = true)
        compose.setContent { AetherioTheme {
            HomeScreen(AppState(homeLoading = false, animeSeason = season), rememberLazyListState(), rememberPagerState { 0 }, false,
                { selected = it }, { category = it }, {})
        } }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("airingAnimeSection"))
        compose.onNodeWithText("Ep. 4 / 12", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Anime en emisión").performClick()
        compose.runOnIdle { assertEquals(anime, selected); assertNull(category) }
        compose.onNodeWithText("Anime emitiéndose ahora").performClick()
        compose.runOnIdle { assertEquals("airing", category) }
    }

    @Test fun unknownAiredEpisodeIsNotInventedFromTheNextEpisode() {
        compose.setContent { AetherioTheme {
            AiringAnimeSection(AnimeSeasonState(entries = listOf(SeasonalAnime(anime, null, 6, 1793973600)), loaded = true), {}, {}, {})
        } }
        compose.onNodeWithText("En emisión", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Ep. 5 / 12", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun errorOffersAnActualRetryRatherThanLeavingAnEmptyHeading() {
        var retried = false
        compose.setContent { AetherioTheme {
            AiringAnimeSection(AnimeSeasonState(error = "Sin conexión"), {}, {}, { retried = true })
        } }
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun sharedThemeUsesTheApprovedDetailPaletteForAllSurfacesAndControls() {
        var colors: List<Color> = emptyList()
        compose.setContent { AetherioTheme {
            val scheme = MaterialTheme.colorScheme
            colors = listOf(scheme.background, scheme.surface, scheme.primary, scheme.secondary, scheme.tertiary)
            LibraryScreen(AppState(), androidx.compose.foundation.lazy.grid.rememberLazyGridState(), "all", {}, {}, {})
        } }
        compose.onNodeWithText("Tus títulos guardados").assertExists()
        compose.runOnIdle { assertEquals(listOf(Ink, Panel, Aqua, Violet, Coral), colors) }
    }
}
