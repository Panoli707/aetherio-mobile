package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourcePreferencesUiTest {
    @get:Rule val compose = createComposeRule()
    private val query = SourceQuery(MediaItem("tt1254207", MediaType.MOVIE, "Fixture", CatalogSource.CINEMETA))
    private val first = StreamSource("one", "fixture", "Fixture", "First", "", "https://cdn.example/one.mp4",
        display = SourceDisplay(languages = listOf("Inglés"), resolutionHeight = 1080))
    private val second = first.copy(id = "two", name = "Second", url = "https://cdn.example/two.mp4",
        display = SourceDisplay(languages = listOf("Castellano"), resolutionHeight = 2160))
    private val state = mutableStateOf(AppState(addonsLoading = false, sources = listOf(first, second)))
    private var played: StreamSource? = null
    private var refreshed = 0

    private fun render() {
        compose.setContent { AetherioTheme {
            SourceSheet(state.value, query, {}, { source, _ -> played = source }, {}, { refreshed++ },
                onPreferencesChange = { state.value = state.value.copy(sourcePreferences = it) })
        } }
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureSourcePreferences") != "true") return
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun tapOpensPreferencesAndReordersWithoutHidingOrChangingTargets() {
        render()
        compose.onNodeWithContentDescription("Preferencias de fuentes").performClick()
        compose.onNodeWithText("Idioma preferido: Sin preferencia").performClick()
        compose.onNodeWithText("Español (todos)").performClick()
        compose.onNodeWithText("Calidad preferida: Sin preferencia").performClick()
        compose.onNodeWithText("4K · 2160p").performClick()
        capture("source-preferences")
        compose.onNodeWithText("Listo").performClick()
        val firstBounds = compose.onNodeWithTag("source:fixture:one").fetchSemanticsNode().boundsInRoot
        val secondBounds = compose.onNodeWithTag("source:fixture:two").fetchSemanticsNode().boundsInRoot
        assertTrue(secondBounds.top < firstBounds.top)
        capture("source-preferences-results")
        compose.onNodeWithTag("source:fixture:two").performClick()
        compose.runOnIdle { assertSame(second, played) }
    }

    @Test fun longPressHasContextMenuAndProviderFavoriteDoesNotStartPlayback() {
        render()
        compose.onNodeWithContentDescription("Fixture, 2 fuentes").performClick()
        compose.onNodeWithContentDescription("Preferencias de fuentes").performTouchInput { longClick() }
        capture("source-preferences-context")
        compose.onNodeWithText("Marcar proveedor favorito").performClick()
        compose.runOnIdle {
            assertTrue(SourcePreferenceRules.providerKey(first) in state.value.sourcePreferences.favoriteProviders)
            assertNull(played)
        }
        compose.onNodeWithContentDescription("Actualizar fuentes").performClick()
        compose.runOnIdle { assertEquals(1, refreshed) }
    }

    @Test fun starTogglesOnlyFavoriteAndDoesNotTriggerCardPlayback() {
        render()
        compose.onNodeWithTag("favorite:fixture:two", useUnmergedTree = true).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(SourcePreferenceRules.favorite(second, state.value.sourcePreferences))
            assertNull(played)
        }
        compose.onNodeWithTag("favorite:fixture:two", useUnmergedTree = true).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(state.value.sourcePreferences.favoriteSources.isEmpty()); assertNull(played) }
    }

    @Test fun resettingRankingKeepsFavoritesAndMenuWorksDuringLoading() {
        state.value = state.value.copy(sourcesLoading = true,
            sourcePreferences = SourcePreferenceRules.toggleSource(second,
                SourcePreferences(language = SourceLanguage.LATIN, quality = SourceQuality.HD)))
        render()
        compose.onNodeWithContentDescription("Preferencias de fuentes").performTouchInput { longClick() }
        compose.onNodeWithText("Restablecer preferencias").performClick()
        compose.runOnIdle {
            assertEquals(SourceLanguage.ANY, state.value.sourcePreferences.language)
            assertEquals(SourceQuality.ANY, state.value.sourcePreferences.quality)
            assertTrue(SourcePreferenceRules.favorite(second, state.value.sourcePreferences))
        }
    }

    @Test fun clearingFavoritesRequiresConfirmationAndLeavesLanguageUntouched() {
        state.value = state.value.copy(sourcePreferences = SourcePreferenceRules.toggleSource(second,
            SourcePreferences(language = SourceLanguage.JAPANESE)))
        render()
        compose.onNodeWithContentDescription("Preferencias de fuentes").performTouchInput { longClick() }
        compose.onNodeWithText("Gestionar favoritos").performClick()
        compose.onNodeWithText("Borrar todos los favoritos").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertTrue(state.value.sourcePreferences.favoriteSources.isNotEmpty()) }
        compose.onNodeWithText("Borrar todos los favoritos").performClick()
        compose.onNodeWithText("Borrar", substring = false).performClick()
        compose.runOnIdle {
            assertTrue(state.value.sourcePreferences.favoriteSources.isEmpty())
            assertEquals(SourceLanguage.JAPANESE, state.value.sourcePreferences.language)
        }
    }

    @Test fun torrentCardHasSeederStateButNoP2pWarningAndSelectsImmediately() {
        val torrent = first.copy(url = "", infoHash = "0123456789abcdef0123456789abcdef01234567", display = SourceDisplay(seeders = 1))
        state.value = state.value.copy(sources = listOf(torrent))
        render()
        compose.onNodeWithText("P2P · Requiere participantes", substring = true).assertDoesNotExist()
        compose.onNodeWithText("1 seeders", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pocos seeders"))
        compose.onNodeWithTag("source:fixture:one").performClick()
        compose.runOnIdle { assertSame(torrent, played) }
        compose.onNodeWithText("Reproducción torrent P2P").assertDoesNotExist()
    }
}
