package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDiscoverUiTest {
    @get:Rule val compose = createComposeRule()
    private val movie = MediaItem("900000001", MediaType.MOVIE, "Película biblioteca", genres = listOf("Terror"))
    private val series = MediaItem("900000002", MediaType.SERIES, "Serie biblioteca", genres = listOf("Comedia"))

    @Test fun librarySelectorsCombineTypeGenreAndSortAndReset() {
        var state by mutableStateOf(AppState(savedItems = listOf(movie, series)))
        compose.setContent { AetherioTheme {
            LibraryScreen(state, rememberLazyGridState(), state.libraryFilters.type, {}, {}, {},
                onFilters = { state = state.copy(libraryFilters = it) })
        } }
        compose.onNodeWithTag("filter:Tipo").performClick()
        compose.onNodeWithText("Películas (1)").performClick()
        compose.onNodeWithText(movie.title).assertExists()
        compose.onNodeWithText(series.title).assertDoesNotExist()
        compose.onNodeWithTag("libraryFilters").performScrollToNode(hasTestTag("filter:Género"))
        compose.onNodeWithTag("filter:Género").performClick()
        compose.onNodeWithText("Comedia (0)").performClick()
        compose.onNodeWithText("No hay títulos con estos filtros").assertExists()
        compose.onAllNodesWithText("Restablecer filtros").onFirst().performClick()
        compose.onNodeWithTag("libraryFilters").performScrollToNode(hasTestTag("filter:Orden"))
        compose.onNodeWithTag("filter:Orden").performClick()
        compose.onNodeWithText("Título Z–A").performClick()
        compose.runOnIdle { assertEquals("title_desc", state.libraryFilters.sort) }
    }

    @Test fun discoveringChangesTypeAndGenreWithoutChangingPersonalLibrary() {
        var state by mutableStateOf(AppState(savedItems = listOf(movie)))
        compose.setContent { AetherioTheme {
            LibraryScreen(state, rememberLazyGridState(), "all", {}, {}, {}, onDiscover = { selection, _, _ ->
                state = state.copy(discover = state.discover.copy(selection = DesktopDiscover.normalize(selection, state.discover.catalogs)))
            })
        } }
        compose.onNodeWithTag("discoverTab").performClick()
        compose.onNodeWithTag("filter:Tipo").performClick()
        compose.onNodeWithText("Anime").performClick()
        compose.onNodeWithTag("discoverFilters").performScrollToNode(hasTestTag("filter:Género"))
        compose.onNodeWithTag("filter:Género").performClick()
        compose.onNodeWithText("Romance").performClick()
        compose.runOnIdle {
            assertEquals("anime", state.discover.selection.type)
            assertEquals("Romance", state.discover.selection.genre)
            assertEquals(listOf(movie), state.savedItems)
        }
    }

    @Test fun discoverErrorRetryAndPaginationDispatchTheCorrectActions() {
        var state by mutableStateOf(AppState(discover = DiscoverState(error = "Sin conexión")))
        var request: Triple<DiscoverSelection, Boolean, Boolean>? = null
        var selected: MediaItem? = null
        compose.setContent { AetherioTheme {
            LibraryScreen(state, rememberLazyGridState(), "all", {}, { selected = it }, {},
                onDiscover = { selection, more, force -> request = Triple(selection, more, force) })
        } }
        compose.onNodeWithTag("discoverTab").performClick()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle {
            assertEquals(Triple(state.discover.selection, false, true), request)
            state = state.copy(discover = state.discover.copy(items = listOf(movie), page = 1, error = null, hasMore = true))
        }
        compose.onNodeWithText(movie.title).performClick()
        compose.runOnIdle { assertEquals(movie, selected) }
        compose.onNodeWithTag("discoverGrid").performScrollToNode(hasTestTag("discoverMore"))
        compose.onNodeWithTag("discoverMore").performClick()
        compose.runOnIdle { assertEquals(Triple(state.discover.selection, true, false), request) }
    }

    @Test fun discoverTabSurvivesStateRestorationAndLoadingDoesNotShowStaleCards() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { AetherioTheme {
            LibraryScreen(AppState(discover = DiscoverState(loading = true, items = listOf(movie))), rememberLazyGridState(), "all", {}, {}, {})
        } }
        compose.onNodeWithTag("discoverTab").performClick()
        compose.onNodeWithText("Cargando catálogo…").assertExists()
        compose.onNodeWithText(movie.title).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("discoverGrid").assertExists()
    }
}
