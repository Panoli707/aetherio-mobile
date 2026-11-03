package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import com.panoli707.aetherio.nativeapp.player.VideoFormat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Deterministic screenshots: no network, playback, or changes to saved user data. */
@RunWith(AndroidJUnit4::class)
class PaletteVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movieUsesSolidPrimaryActionOnNeutralSurfaces() {
        val movie = MediaItem("palette", MediaType.MOVIE, "Aetherio · Película de prueba",
            overview = "Una interfaz neutra permite que las imágenes del catálogo destaquen.", year = "2026")
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = MediaDetail(movie)), movie, rememberLazyListState(),
                {}, {}, {}, {}, {}, { _, _, _ -> })
        } }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("detailPrimaryPlay"))
        compose.onNodeWithTag("detailPrimaryPlay").assertIsDisplayed()
        save("palette-detail.png", compose.onRoot())
    }

    @Test fun sourceFiltersAndCardsKeepTheNeutralPalette() {
        val open = mutableStateOf(true)
        val movie = MediaItem("palette", MediaType.MOVIE, "Aetherio · Película de prueba")
        val sources = listOf(
            StreamSource("one", "fixture", "Proveedor de prueba", "Fuente Full HD", "Metadatos proporcionados por la fuente.",
                "https://example.invalid/video.m3u8", VideoFormat.HLS,
                subtitles = listOf(StreamSubtitle("es", "https://example.invalid/es.vtt", "es"))),
            StreamSource("two", "fixture", "Proveedor de prueba", "Fuente alternativa", "Sin información adicional")
        )
        compose.setContent { AetherioTheme {
            Box(Modifier.fillMaxSize().background(Ink))
            if (open.value) SourceSheet(AppState(addonsLoading = false, sources = sources), SourceQuery(movie),
                { open.value = false }, { _, _ -> }, {})
        } }
        compose.onNodeWithContentDescription("Todas las fuentes, 2").assertIsDisplayed()
        compose.onNodeWithTag("source:fixture:one").assertIsEnabled()
        save("palette-sources.png", compose.onNodeWithTag("sourceGlassPanel"))
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("sourceGlassPanel").assertDoesNotExist()
    }

    private fun save(name: String, node: SemanticsNodeInteraction) {
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        val file = File(output, name)
        file.parentFile?.mkdirs()
        file.outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
