package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import com.panoli707.aetherio.nativeapp.player.VideoFormat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderHubUiTest {
    @get:Rule val compose = createComposeRule()
    private val query = SourceQuery(MediaItem("tt5311514", MediaType.MOVIE, "Fixture", CatalogSource.CINEMETA))

    @Test fun freshSourceSheetNeedsNoConfigurationAndOffersAnActualRetryForEmptyResults() {
        var retried = false
        compose.setContent { AetherioTheme {
            SourceSheet(AppState(addonsLoading = false), query, {}, { _, _ -> }, {}, { retried = true })
        } }
        compose.onNodeWithText("Configura tus proveedores").assertDoesNotExist()
        compose.onNodeWithText("Activa AnimeAV1").assertDoesNotExist()
        compose.onNodeWithText("Sin resultados").assertExists()
        compose.onNodeWithContentDescription("Actualizar fuentes").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun torrentCanBeSelectedAndHttpKeepsItsHeaders() {
        val result = SourceProtocol.streams("""{"streams":[{"name":"HTTP fixture","url":"https://cdn.example/video.mp4","headers":{"User-Agent":"Fixture"}},
          {"name":"Torrent fixture","infoHash":"0123456789abcdef0123456789abcdef01234567","fileIdx":3,"sources":["tracker:udp://tracker.example:80"]}]}""", ProviderHub.config)
        var played: StreamSource? = null
        compose.setContent { AetherioTheme {
            SourceSheet(AppState(addonsLoading = false, sources = result.sources), query, {}, { source, _ -> played = source }, {})
        } }
        compose.onNodeWithText("Reproducir torrent").assertDoesNotExist()
        compose.onNodeWithText("Reproducir").assertDoesNotExist()
        compose.onNodeWithTag("source:${result.sources[1].addonId}:${result.sources[1].id}").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(played!!.isTorrent); assertEquals(3, played!!.fileIdx) }
        compose.onNodeWithTag("source:${result.sources[0].addonId}:${result.sources[0].id}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Fixture", played?.headers?.get("User-Agent")); assertFalse(played!!.isTorrent) }
    }

    @Test fun providerFiltersShowCountsAndRestoreAllSources() {
        val sources = listOf(
            StreamSource("alpha-1", "alpha", "AnimeAV1", "Alpha uno", "Primer resultado", "https://cdn.example/1.m3u8"),
            StreamSource("alpha-2", "alpha", "AnimeAV1", "Alpha dos", "Segundo resultado", "https://cdn.example/2.m3u8",
                format = com.panoli707.aetherio.nativeapp.player.VideoFormat.HLS,
                subtitles = listOf(StreamSubtitle("es", "https://cdn.example/es.vtt", "es"))),
            StreamSource("beta-1", "beta", "Proveedor Beta", "Beta uno", "Tercer resultado", "https://cdn.example/3.m3u8")
        )
        compose.setContent { AetherioTheme {
            SourceSheet(AppState(addonsLoading = false, sources = sources), query, {}, { _, _ -> }, {})
        } }

        compose.onNodeWithContentDescription("Todas las fuentes, 3").assertExists()
        compose.onNodeWithContentDescription("AnimeAV1, 2 fuentes").performClick()
        compose.onNodeWithText("Alpha uno").assertExists()
        compose.onNodeWithText("Alpha dos").assertExists()
        compose.onNodeWithText("HLS", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Sub: es").assertExists()
        compose.onNodeWithText("Beta uno").assertDoesNotExist()

        compose.onNodeWithContentDescription("Proveedor Beta, 1 fuente").performClick()
        compose.onNodeWithText("Beta uno").assertExists()
        compose.onNodeWithText("Alpha uno").assertDoesNotExist()

        compose.onNodeWithContentDescription("Todas las fuentes, 3").performClick()
        compose.onNodeWithText("Alpha uno").assertExists()
        compose.onNodeWithTag("sourceResults").performScrollToNode(hasText("Beta uno"))
        compose.onNodeWithText("Beta uno").assertExists()
    }

    @Test fun refreshIsTopRightAndStaysAvailableAfterScrollingWithoutFormatControls() {
        var refreshes = 0
        val sources = (1..20).map { StreamSource("$it", "fixture", "Fixture", "Fuente $it", "", "https://cdn.example/$it.mp4") }
        compose.setContent { AetherioTheme {
            SourceSheet(AppState(addonsLoading = false, sources = sources), query, {}, { _, _ -> }, {}, { refreshes++ })
        } }
        compose.onNodeWithTag("sourceGlassPanel").assertExists()
        compose.onNodeWithText("Formato de reproducción").assertDoesNotExist()
        compose.onNodeWithText("Auto").assertDoesNotExist()
        val refresh = compose.onNodeWithContentDescription("Actualizar fuentes")
        val before = refresh.fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("sourceGlassPanel").fetchSemanticsNode().boundsInRoot
        assertTrue(before.center.x > panel.center.x)
        compose.onNodeWithTag("sourceResults").performScrollToNode(hasText("Fuente 20"))
        refresh.assertIsDisplayed().performClick()
        assertEquals(before, refresh.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun loadingDisablesDuplicateRefreshAndErrorAndEmptyStatesCanRefreshAgain() {
        val state = mutableStateOf(AppState(addonsLoading = false, sourcesLoading = true))
        var refreshes = 0
        compose.setContent { AetherioTheme {
            SourceSheet(state.value, query, {}, { _, _ -> }, {}, { refreshes++ })
        } }
        compose.onNodeWithContentDescription("Actualizar fuentes").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, refreshes); state.value = state.value.copy(sourcesLoading = false, sourceError = "Sin conexión") }
        compose.onNodeWithText("Sin conexión").assertExists()
        compose.onNodeWithContentDescription("Actualizar fuentes").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, refreshes); state.value = state.value.copy(sourceError = null) }
        compose.onNodeWithText("Sin resultados").assertExists()
        compose.onNodeWithContentDescription("Actualizar fuentes").performClick()
        compose.runOnIdle { assertEquals(2, refreshes) }
    }

    @Test fun removingFormatPickerKeepsDetectedHttpFormatAndTorrentMetadata() {
        val source = StreamSource("fixture", "fixture", "Fixture", "HLS fixture", "", "https://cdn.example/video.m3u8", format = VideoFormat.HLS)
        var chosen: Pair<StreamSource, VideoFormat>? = null
        compose.setContent { AetherioTheme {
            SourceSheet(AppState(addonsLoading = false, sources = listOf(source)), query, {}, { item, format -> chosen = item to format }, {})
        } }
        compose.onNodeWithText("Formato de reproducción").assertDoesNotExist()
        compose.onNode(hasText("HLS") and hasClickAction(), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Reproducir").assertDoesNotExist()
        compose.onNodeWithTag("source:fixture:fixture").performClick()
        compose.runOnIdle { assertEquals(source to VideoFormat.HLS, chosen) }
    }

    @Test fun partialProviderFailuresDoNotInterruptPlayableResultsOrReturnWhenFiltering() {
        val source = StreamSource("available", "fixture", "Fixture", "Fuente disponible", "", "https://cdn.example/video.mp4")
        val message = "Provider Hub o un proveedor opcional no respondió."
        val state = mutableStateOf(AppState(addonsLoading = false, sourcesLoading = true, sourceError = message))
        compose.setContent { AetherioTheme {
            SourceSheet(state.value, query, {}, { _, _ -> }, {})
        } }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(sourcesLoading = false, sources = listOf(source)) }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithContentDescription("Fixture, 1 fuente").performClick()
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.onNodeWithTag("source:fixture:available").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(sources = emptyList()) }
        compose.onNodeWithTag("sourceFailure").assertIsDisplayed()
        compose.onNodeWithText(message).assertIsDisplayed()
        compose.onNodeWithContentDescription("Actualizar fuentes").assertIsEnabled()
    }

    @Test fun sourceCardWithNoPlayableTargetStaysDisabled() {
        val source = StreamSource("unavailable", "fixture", "Fixture", "Sin destino", "Conservado para diagnóstico")
        var selected = false
        compose.setContent { AetherioTheme {
            SourceSheet(AppState(addonsLoading = false, sources = listOf(source)), query, {}, { _, _ -> selected = true }, {})
        } }
        compose.onNodeWithTag("source:fixture:unavailable").assertIsNotEnabled().performClick()
        compose.onNodeWithText("Fuente no compatible", useUnmergedTree = true).assertExists()
        compose.runOnIdle { assertFalse(selected) }
    }

    @Test fun glassKeepsMetadataAndFiltersOverArtwork() {
        val sources = listOf(
            StreamSource("one", "alpha", "AnimeAV1", "AnimeAV1 · Episodio 5", "Duración: 24 min · Sub: Castellano",
                "https://cdn.example/one.m3u8", format = VideoFormat.HLS),
            StreamSource("two", "torrent", "Torrentio", "1080p · Episodio 5", "Tamaño: 1.37 GB · 47 seeders",
                infoHash = "0123456789abcdef0123456789abcdef01234567")
        )
        compose.setContent { AetherioTheme {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF436C80), Color(0xFFAE6587), Color(0xFF718B52)))))
            SourceSheet(AppState(addonsLoading = false, sources = sources, sourceError = "Proveedor opcional no respondió"),
                query, {}, { _, _ -> }, {})
        } }
        compose.onNodeWithText("Presentado por AnimeAV1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Todas las fuentes, 2").assertIsDisplayed()
        compose.onNodeWithTag("sourceFailure").assertDoesNotExist()
        if (InstrumentationRegistry.getArguments().getString("capturePlayer") == "true") {
            compose.waitForIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val image = instrumentation.uiAutomation.takeScreenshot()
            File(instrumentation.targetContext.getExternalFilesDir(null), "source-glass.png")
                .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
