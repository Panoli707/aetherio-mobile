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
import org.junit.rules.RuleChain
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class CanonicalArtworkUiTest {
    val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(ForegroundArtworkTestRule()).around(compose)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    private val gate = CompletableDeferred<Unit>()
    @After fun cleanup() { gate.complete(Unit); files.forEach { it.delete() } }
    private fun image(color: Int, width: Int = 600, height: Int = 900): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File.createTempFile("canonical-qa-", ".png", context.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle(); files += file
        return file.toURI().toString()
    }

    @Test fun selectedCoverDoesNotChangeWithSeasonOrOrientation() {
        val dirty = image(Color.RED)
        val clean = image(Color.GREEN)
        val started = AtomicBoolean(false)
        val item = mutableStateOf(MediaItem("90000000", MediaType.SERIES, "Canonical title", posterUrl = dirty,
            portraitAlternatives = listOf(clean)))
        val portrait = mutableStateOf(true)
        val provider = ArtworkProvider {
            started.set(true); gate.await()
            CanonicalArtwork(it.title, clean)
        }
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
        val item = MediaItem("90000001", MediaType.MOVIE, "Neutral title", logoUrl = "https://invalid/season.png")
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

    @Test fun unknownCoverLoadsTheLogoAtTheInnerBottomEdgeWithoutAddingHeight() {
        val cover = image(Color.BLUE)
        val logo = image(Color.CYAN, 600, 150)
        val item = MediaItem("90000002", MediaType.MOVIE, "Visible title")
        val provider = object : ArtworkProvider {
            override suspend fun resolve(item: MediaItem) = CanonicalArtwork(item.title, cover,
                titlePresence = ArtworkTitlePresence.UNKNOWN)
            override suspend fun logo(item: MediaItem) = logo
        }
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            Box(Modifier.width(300.dp)) { HeroArtwork(item, true, 800.dp) }
        } } }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().size == 1 }
        val photo = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag("detailTitle", true).getUnclippedBoundsInRoot()
        val frame = compose.onNodeWithTag("heroArtwork", true).getUnclippedBoundsInRoot()
        assertTrue(title.top >= photo.top)
        assertEquals(8f, (photo.bottom - title.bottom).value, 1f)
        assertEquals(photo.bottom, frame.bottom)
        compose.onAllNodesWithTag("detailTitle", true).assertCountEquals(1)
    }

    @Test fun printedArtworkAndFailedLogosKeepTheWrittenFallbackInsideTheSameImageHeight() {
        val cover = image(Color.BLUE)
        val full = mutableStateOf(true)
        val portrait = mutableStateOf(true)
        val item = MediaItem("90000005", MediaType.MOVIE, "Written fallback")
        val provider = ArtworkProvider { CanonicalArtwork(it.title, cover, titlePresence = ArtworkTitlePresence.EMBEDDED,
            logo = "file:///missing-qa-logo.png") }
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            Box(Modifier.width(300.dp)) { HeroArtwork(item, portrait.value, 800.dp, fullImage = full.value) }
        } } }
        for ((isFull, isPortrait) in listOf(true to true, false to true, false to false)) {
            compose.runOnIdle { full.value = isFull; portrait.value = isPortrait }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1 }
            compose.waitForIdle()
            val photo = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
            val title = compose.onNodeWithTag("detailTitle", true).getUnclippedBoundsInRoot()
            val frame = compose.onNodeWithTag("heroArtwork", true).getUnclippedBoundsInRoot()
            compose.onNodeWithText(item.title).assertExists()
            assertTrue(title.top >= photo.top)
            assertEquals(8f, (photo.bottom - title.bottom).value, 1f)
            assertEquals(photo.bottom, frame.bottom)
        }
    }

    @Test fun cleanUpgradeSwapsOnlyAfterDecodeAndKeepsLogoInsideTheImage() {
        val dirty = image(Color.RED)
        val clean = image(Color.GREEN)
        val logo = image(Color.CYAN, 600, 150)
        val item = MediaItem("90000003", MediaType.MOVIE, "Clean title")
        val provider = object : ArtworkProvider {
            override suspend fun resolve(item: MediaItem) = CanonicalArtwork(item.title, dirty,
                titlePresence = ArtworkTitlePresence.UNKNOWN)
            override suspend fun refine(item: MediaItem, current: CanonicalArtwork, portrait: Boolean): CanonicalArtwork {
                gate.await()
                return CanonicalArtwork(item.title, clean, logo = logo)
            }
        }
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            Box(Modifier.width(300.dp)) { HeroArtwork(item, true, 800.dp) }
        } } }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1 }
        gate.complete(Unit)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().size == 1 }
        val pixels = compose.onNodeWithTag("heroImage", true).captureToImage().toPixelMap()
        assertTrue(pixels[pixels.width / 2, pixels.height / 2].green > .9f)
        val photo = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag("detailTitle", true).getUnclippedBoundsInRoot()
        assertEquals(8f, (photo.bottom - title.bottom).value, 1f)
    }

    @Test fun horizontalCardRequestsLandscapeArtworkInsteadOfFittingAVerticalPoster() {
        val vertical = image(Color.RED)
        val wide = image(Color.GREEN, 960, 540)
        val item = MediaItem("90000004", MediaType.SERIES, "Landscape example")
        val requested = AtomicBoolean(false)
        val provider = object : ArtworkProvider {
            override suspend fun resolve(item: MediaItem) = CanonicalArtwork(item.title, vertical)
            override suspend fun resolve(item: MediaItem, portrait: Boolean): CanonicalArtwork {
                requested.set(!portrait)
                return if (portrait) resolve(item) else CanonicalArtwork(item.title, wide, 16f / 9f)
            }
        }
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            HorizontalPoster(item, {}, Modifier.width(300.dp))
        } } }
        compose.waitUntil(15_000) { requested.get() }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription(item.title, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val node = compose.onNodeWithContentDescription(item.title, useUnmergedTree = true)
        // AsyncImage can enter semantics before the decoder completes.
        compose.waitUntil(15_000) {
            val pixels = node.captureToImage().toPixelMap()
            pixels[1, pixels.height / 2].green > .9f && pixels[pixels.width - 2, pixels.height / 2].green > .9f
        }
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

    @Test fun transparentLogoInspectionUsesContrastWithoutChangingTheDisplayedOriginal() = runBlocking {
        for (color in listOf(Color.BLACK, Color.WHITE, Color.RED)) {
            val original = Bitmap.createBitmap(960, 240, Bitmap.Config.ARGB_8888)
            Canvas(original).drawText("FORREST GUMP", 20f, 155f, Paint().apply {
                this.color = color; textSize = 105f; isAntiAlias = true
            })
            val mask = logoOcrMask(original)
            try {
                assertEquals(0, Color.alpha(original.getPixel(0, 0)))
                assertEquals(Color.WHITE, mask.getPixel(0, 0))
                val reading = ArtworkTextRecognizer.read(context, "qa-transparent-$color-${System.nanoTime()}", mask.asImageBitmap())
                assertTrue("OCR: ${reading.text}", CanonicalArtworkPolicy.genericLogo(reading.text, "Forrest Gump", reading.complete, true))
            } finally { original.recycle(); mask.recycle() }
        }
    }
}
