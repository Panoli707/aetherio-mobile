package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class CanonicalArtworkUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    private val gate = CompletableDeferred<Unit>()
    @After fun cleanup() { gate.complete(Unit); files.forEach { it.delete() } }
    private fun image(color: Int): String {
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File.createTempFile("canonical-qa-", ".png", context.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle(); files += file
        return file.toURI().toString()
    }

    @Test fun rejectedPixelsAreNeverPaintedAndTheResolvedCoverDoesNotChangeWithSeasonOrOrientation() {
        val dirty = image(Color.RED)
        val clean = image(Color.GREEN)
        val started = AtomicBoolean(false)
        val item = mutableStateOf(MediaItem("90000000", MediaType.SERIES, "Canonical title", posterUrl = dirty,
            portraitAlternatives = listOf(clean)))
        val portrait = mutableStateOf(true)
        val resolver = CanonicalArtworkResolver({ item.value }, { url, logo, _ ->
            assertFalse(logo)
            started.set(true); gate.await()
            ArtworkInspection(2f / 3f, clean = url == clean)
        })
        val provider = ArtworkProvider { resolver.resolve(it) }
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            Box(Modifier.width(360.dp)) { HeroArtwork(item.value, portrait.value, 800.dp, fullImage = true) }
        } } }
        compose.waitUntil(15_000) { started.get() }
        compose.onAllNodesWithTag("heroImage", true).assertCountEquals(0)
        gate.complete(Unit)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
        fun green() {
            val pixels = compose.onNodeWithTag("heroImage", true).captureToImage().toPixelMap()
            val sample = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue(sample.green > .9f && sample.red < .1f)
        }
        green()
        val before = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
        compose.runOnIdle { item.value = item.value.copy(seasonHint = 3, posterUrl = dirty, portraitAlternatives = emptyList()); portrait.value = false }
        compose.waitForIdle(); green()
        assertEquals(before, compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot())
        compose.onNodeWithText("Canonical title").assertExists()
        val title = compose.onNodeWithTag("detailTitle", true).getUnclippedBoundsInRoot()
        assertEquals(8f, (before.bottom - title.bottom).value, 1f)
    }

    @Test fun noSafeCoverUsesNeutralFrameInsteadOfAPrintedPosterAndUnvalidatedLogo() {
        val item = MediaItem("90000001", MediaType.MOVIE, "Neutral title", posterUrl = image(Color.RED), logoUrl = "https://invalid/season.png")
        val resolver = CanonicalArtworkResolver({ item }, { _, _, _ -> ArtworkInspection(.7f) })
        val provider = ArtworkProvider { resolver.resolve(it) }
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            Box(Modifier.width(360.dp)) { HeroArtwork(item, true, 800.dp) }
        } } }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() }
        compose.onAllNodesWithTag("heroImage", true).assertCountEquals(0)
        compose.onAllNodesWithTag("detailLogo", true).assertCountEquals(0)
        compose.onNodeWithText("Neutral title").assertExists()
    }

    @Test fun bundledOcrDetectsARealSeasonLabelAndAcceptsABlankIllustration() = runBlocking {
        fun bitmap(text: String): Bitmap = Bitmap.createBitmap(960, 480, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            Canvas(this).drawText(text, 30f, 220f, Paint().apply { color = Color.BLACK; textSize = 90f; isAntiAlias = true })
        }
        val season = bitmap("SERIES SEASON 2")
        val blank = bitmap("")
        try {
            val reading = ArtworkTextRecognizer.read(context, "qa-season-${System.nanoTime()}", season.asImageBitmap())
            assertTrue("OCR must complete for this fixture", reading.complete)
            assertTrue(reading.text.contains("SEASON", true))
            assertFalse(CanonicalArtworkPolicy.genericLogo(reading.text, "SERIES", reading.complete, true))
            val clear = ArtworkTextRecognizer.read(context, "qa-clean-${System.nanoTime()}", blank.asImageBitmap())
            assertTrue(CanonicalArtworkPolicy.clean(clear.text, clear.complete))
        } finally { season.recycle(); blank.recycle() }
    }
}
