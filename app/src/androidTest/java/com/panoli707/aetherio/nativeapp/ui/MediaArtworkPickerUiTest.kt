package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class MediaArtworkPickerUiTest {
    val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(ForegroundArtworkTestRule()).around(compose)
    private val item = MediaItem("90000990", MediaType.MOVIE, "Artwork fixture")
    private class Provider : ArtworkProvider {
        var chosen: String? = "not selected"
        var chosenKind: ArtworkSelectionKind? = null
        override suspend fun resolve(item: MediaItem) = CanonicalArtwork(item.title, null)
        override suspend fun options(item: MediaItem, kind: ArtworkSelectionKind) = listOf(ArtworkOption("https://fixture/cover.jpg", "Alternativa"))
        override suspend fun select(item: MediaItem, kind: ArtworkSelectionKind, url: String?) {
            chosen = url; chosenKind = kind
        }
    }

    @Test fun overflowNextToPlayOpensArtworkPicker() {
        val provider = Provider()
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            DetailScreen(AppState(), item, rememberLazyListState(), {}, {}, {}, {}, {}, { _, _, _ -> })
        } } }
        compose.onNodeWithTag("detailArtworkMenu").performScrollTo().performClick()
        compose.onNodeWithText("Elegir logo del medio").performClick()
        compose.onNodeWithTag("mediaArtworkPicker").assertExists()
        compose.onNodeWithText("Logo del medio").assertExists()
    }

    @Test fun explicitTextAndResetUseDifferentValuesAndDismissAfterSuccess() {
        val provider = Provider()
        var closed = false
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            MediaArtworkPicker(item, logos = true) { closed = true }
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("artworkUseText").performClick()
        compose.waitForIdle()
        assertEquals("", provider.chosen)
        assertEquals(ArtworkSelectionKind.LOGO, provider.chosenKind)
        assertTrue(closed)
    }

    @Test fun resetRestoresProviderDefaultInsteadOfUsingText() {
        val provider = Provider()
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            MediaArtworkPicker(item, logos = true) {}
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("artworkReset").performClick()
        compose.waitForIdle()
        assertNull(provider.chosen)
    }

    @Test fun arbitraryArtworkCanBeSelectedWithoutPolicyWarning() {
        val provider = Provider()
        var closed = false
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            MediaArtworkPicker(item, logos = true) { closed = true }
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("artworkOption:https://fixture/cover.jpg").performClick()
        compose.waitForIdle()
        assertEquals("https://fixture/cover.jpg", provider.chosen)
        assertEquals(ArtworkSelectionKind.LOGO, provider.chosenKind)
        assertTrue(closed)
        compose.onNodeWithText("Solo el nombre de la obra, sin temporada. Se comprueba la transparencia al elegir.").assertDoesNotExist()
    }
}
