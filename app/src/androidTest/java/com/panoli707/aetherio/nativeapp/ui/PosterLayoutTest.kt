package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosterLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allPosterSurfacesShareWidthAndPortraitRatioWithoutStretchingIncompleteRows() {
        val anime = MediaItem("fixture-anime", MediaType.SERIES, "Anime de prueba", isAnime = true)
        val movie = MediaItem("fixture-movie", MediaType.MOVIE, "Película de prueba")
        val state = AppState(homeLoading = false,
            animeToday = AnimeTodayState(entries = listOf(AnimeAiringEpisode(anime, 1, System.currentTimeMillis() / 1000 - 60)), loaded = true),
            animeSeason = AnimeSeasonState(entries = listOf(SeasonalAnime(anime, null, null, null)), loaded = true),
            sections = listOf(CatalogSection("movies", "Películas", listOf(movie))))
        val page = mutableStateOf("home")
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) {
                when (page.value) {
                    "grid" -> CatalogGrid(listOf(movie), rememberLazyGridState(), {})
                    "anime" -> AnimeScreen(state.copy(watchingAnime = listOf(anime)), rememberLazyListState(), {}, {}, {})
                    "season" -> SeasonalAnimeScreen(state.animeSeason, rememberLazyListState(), {}, {}, {}, "all", {})
                    "detail" -> DetailScreen(AppState(detail = MediaDetail(anime, related = listOf(movie))), anime,
                        rememberLazyListState(), {}, {}, {}, {}, {}, { _, _, _ -> })
                    else -> HomeScreen(state, rememberLazyListState(), rememberPagerState { 0 }, false, {}, {}, {})
                }
            }
        } }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("airingPoster:${anime.key}"))
        val airing = compose.onNodeWithTag("airingArt:${anime.key}", useUnmergedTree = true).getUnclippedBoundsInRoot()
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("homePoster:movies:${movie.key}"))
        val poster = compose.onNodeWithTag("posterArt:${movie.key}", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val airingWidth = (airing.right - airing.left).value
        val posterWidth = (poster.right - poster.left).value
        // Recent emissions intentionally use wider portrait cards; ordinary grids still share 100 dp.
        assertEquals((360f - 36f) / 2.6f, airingWidth, 1f)
        assertEquals(100f, posterWidth, 1f)
        assertEquals(airingWidth * 1.5f, (airing.bottom - airing.top).value, 1f)
        assertEquals(posterWidth * 1.5f, (poster.bottom - poster.top).value, 1f)
        compose.runOnIdle { page.value = "grid" }
        assertPortrait("posterArt:${movie.key}", posterWidth)
        compose.runOnIdle { page.value = "anime" }
        compose.onNodeWithTag("animeScroll").performScrollToNode(hasTestTag("watchingArt:${anime.key}"))
        assertPortrait("watchingArt:${anime.key}", posterWidth)
        compose.runOnIdle { page.value = "season" }
        assertPortrait("posterArt:${anime.key}", posterWidth)
        compose.runOnIdle { page.value = "detail" }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("relatedPoster:${movie.key}"))
        assertPortrait("posterArt:${movie.key}", posterWidth)
    }

    private fun assertPortrait(tag: String, expectedWidth: Float) {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val width = (bounds.right - bounds.left).value
        assertEquals(expectedWidth, width, 1f)
        assertEquals(width * 1.5f, (bounds.bottom - bounds.top).value, 1f)
    }
}
