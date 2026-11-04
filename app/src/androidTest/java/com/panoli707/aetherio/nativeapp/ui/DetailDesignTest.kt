package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.lazy.rememberLazyListState
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
class DetailDesignTest {
    @get:Rule val compose = createComposeRule()
    private val movie = MediaItem("550", MediaType.MOVIE, "Película de prueba", overview = "Una sinopsis suficientemente larga. ".repeat(35))

    @Test fun movieUsesOnePrimarySourceActionAndCompactControlsWithoutInventedEpisodes() {
        var played = false
        var saved = false
        var linked = false
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = MediaDetail(movie)), movie, rememberLazyListState(), {}, { saved = true }, {}, {}, { linked = true },
                { item, season, episode -> assertEquals(movie.key, item.key); assertNull(season); assertNull(episode); played = true })
        } }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("detailPrimaryPlay"))
        compose.onNodeWithText("Reproducir").performClick()
        compose.onNodeWithContentDescription("Guardar en biblioteca").performClick()
        compose.onNodeWithContentDescription("Reproducir por enlace propio").performClick()
        compose.onNodeWithTag("detailEpisodes").assertDoesNotExist()
        compose.runOnIdle { assertTrue(played && saved && linked) }
    }

    @Test fun synopsisCanExpandAndCollapseWithoutPushingAllOtherSectionsBehindFullText() {
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = MediaDetail(movie)), movie, rememberLazyListState(), {}, {}, {}, {}, {}, { _, _, _ -> })
        } }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("detailSynopsis"))
        compose.onNodeWithText("Leer sinopsis completa").performScrollTo().performClick()
        compose.onNodeWithText("Ver menos").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Leer sinopsis completa").assertExists()
    }

    @Test fun horizontalEpisodesKeepTheirOwnSourcesAndSeasonSelectorDispatchesTheRealNumber() {
        val series = movie.copy(type = MediaType.SERIES, title = "Serie de prueba")
        var played: Int? = null
        var selected: Int? = null
        val detail = MediaDetail(series, seasons = listOf(SeasonInfo(1, "Temporada 1", 8), SeasonInfo(2, "Temporada 2", 8)),
            episodes = (1..8).map { EpisodeItem(it, 1, "Capítulo $it") }, selectedSeason = 1)
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = detail), series, rememberLazyListState(), {}, {}, {}, { selected = it }, {}, { _, _, ep -> played = ep })
        } }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("detailEpisodes"))
        compose.onNodeWithTag("detailEpisodes").performScrollToNode(hasText("Capítulo 7"))
        compose.onNodeWithText("Capítulo 7").performClick()
        compose.runOnIdle { assertEquals(7, played) }
        compose.onNodeWithText("Temporada 1").performClick()
        compose.onNodeWithText("Temporada 2 · 8 episodios").performClick()
        compose.runOnIdle { assertEquals(2, selected) }
    }

    @Test fun seriesPrimaryActionAlsoSaysReproducirAndKeepsTheSelectedEpisode() {
        val series = movie.copy(type = MediaType.SERIES, title = "Serie de prueba")
        val detail = MediaDetail(series, seasons = listOf(SeasonInfo(2, "Temporada 2", 1)),
            episodes = listOf(EpisodeItem(3, 2, "Capítulo tres")), selectedSeason = 2)
        var chosen: Pair<Int?, Int?>? = null
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = detail), series, rememberLazyListState(), {}, {}, {}, {}, {},
                { _, season, episode -> chosen = season to episode })
        } }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("detailPrimaryPlay"))
        compose.onNodeWithText("Ver fuentes", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Reproducir · T2 E3").performClick()
        compose.runOnIdle { assertEquals(2 to 3, chosen) }
    }

    @Test fun reproducirIsDisabledUntilASeriesHasAnEpisodeInsteadOfInventingOne() {
        val series = movie.copy(type = MediaType.SERIES)
        var played = false
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = MediaDetail(series)), series, rememberLazyListState(), {}, {}, {}, {}, {},
                { _, _, _ -> played = true })
        } }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("detailPrimaryPlay"))
        compose.onNodeWithText("Reproducir").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertFalse(played) }
    }

    @Test fun trailerRelatedAndReviewActionsUseRealMetadataAndMissingSectionsStayAbsent() {
        val trailer = DetailLinks.trailer("abcDE123_-x", "Tráiler de prueba")!!
        val related = movie.copy(id = "551", title = "Película relacionada")
        val detail = MediaDetail(movie, trailers = listOf(trailer), related = listOf(related))
        var external: String? = null
        var playedTrailer: MediaTrailer? = null
        var picked: MediaItem? = null
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = detail), movie, rememberLazyListState(), {}, {}, {}, {}, {}, { _, _, _ -> },
                onOpenExternal = { external = it }, onSelectRelated = { picked = it }, onPlayTrailer = { playedTrailer = it })
        } }
        compose.onNodeWithContentDescription("Ver tráiler").assertDoesNotExist()
        compose.onNodeWithContentDescription("Abrir fuentes").assertDoesNotExist()
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("detailTrailers"))
        compose.onNodeWithContentDescription("Abrir tráiler Tráiler de prueba").performClick()
        compose.runOnIdle { assertEquals(trailer, playedTrailer); assertNull(external) }
        compose.onNodeWithText("YouTube · vídeo externo").assertDoesNotExist()
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasText("Película relacionada"))
        compose.onNodeWithText("Película relacionada").performClick()
        compose.runOnIdle { assertEquals(related, picked) }
        compose.onNodeWithText("Comentarios").assertDoesNotExist()
        compose.onNodeWithText("Dónde ver").assertDoesNotExist()
    }

    @Test fun backStaysAvailableAfterScrollingPastTheHero() {
        var closed = false
        val detail = MediaDetail(movie, facts = listOf(DetailFact("Estado", "Estrenada")))
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = detail), movie, rememberLazyListState(), { closed = true }, {}, {}, {}, {}, { _, _, _ -> })
        } }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasText("Acerca de"))
        compose.onNodeWithContentDescription("Volver").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
}
