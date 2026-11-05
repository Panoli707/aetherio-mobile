package com.panoli707.aetherio.nativeapp.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real providers plus the actual hero renderer, never enabled in offline/CI runs or on a personal phone. */
@RunWith(AndroidJUnit4::class)
class StableArtworkLiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allReviewedPairsReallyLoadAndRetainTheExistingBottomLogoPosition() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualCanonicalArtwork") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reviewed = CanonicalArtworkCatalog.entries.toList()
        fun item(index: Int): MediaItem {
            val (key, art) = reviewed[index]
            val parts = key.split(':')
            return MediaItem(parts[2], if (parts[1] == "movie") MediaType.MOVIE else MediaType.SERIES, art.title)
        }
        val selected = mutableStateOf(item(0))
        compose.setContent { AetherioTheme { Box(Modifier.width(360.dp)) {
            HeroArtwork(selected.value, true, 800.dp, fullImage = true)
        } } }
        for (index in reviewed.indices) {
            compose.runOnIdle { selected.value = item(index) }
            try {
                compose.waitUntil(60_000) {
                    compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
                        compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1 &&
                        compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().size == 1
                }
            } catch (error: Throwable) {
                throw AssertionError("Pair ${reviewed[index].first} did not load: ${compose.onRoot(useUnmergedTree = true).printToString()}", error)
            }
            val cover = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
            val title = compose.onNodeWithTag("detailTitle", true).getUnclippedBoundsInRoot()
            assertEquals(8f, (cover.bottom - title.bottom).value, 1f)
            val file = File(context.getExternalFilesDir(null), "qa0200/${reviewed[index].first.replace(':','-')}.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun boticariaRealSeasonsKeepExactlyTheSameCanonicalPixels() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualSeriesArtwork") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = SeriesIdentity.item(MediaItem("220542", MediaType.SERIES, "Los diarios de la boticaria", isAnime = true))
        val repository = AetherioRepository()
        val seasons = runBlocking { (1..3).map { repository.seasonContent(base, it).item } }
        assertEquals(1, seasons.map { CanonicalArtworkIdentity.key(it) }.distinct().size)
        val selected = mutableStateOf(seasons[0])
        compose.setContent { AetherioTheme { Box(Modifier.width(360.dp)) { HeroArtwork(selected.value, true, 800.dp, fullImage = true) } } }
        val signatures = mutableListOf<List<Int>>()
        for (number in listOf(1, 2, 3, 1)) {
            compose.runOnIdle { selected.value = seasons[number - 1] }
            compose.waitUntil(60_000) {
                compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1
            }
            val node = compose.onNodeWithTag("heroImage", true)
            val bounds = node.getUnclippedBoundsInRoot()
            assertEquals(360f, (bounds.right - bounds.left).value, 1f)
            assertTrue("A seasonal portrait must not be replaced by a short banner", bounds.bottom - bounds.top >= 360.dp)
            val screenshot = node.captureToImage()
            val pixels = screenshot.toPixelMap()
            signatures.add((1..8).flatMap { y -> (1..8).map { x ->
                val color = pixels[pixels.width * x / 9, pixels.height * y / 9]
                (color.red * 255).toInt() * 65536 + (color.green * 255).toInt() * 256 + (color.blue * 255).toInt()
            } })
            val file = File(context.filesDir, "artwork-qa/boticaria-season-$number.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { screenshot.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertEquals(signatures[0], signatures[1]); assertEquals(signatures[1], signatures[2])
        assertEquals(signatures[0], signatures[3])
    }
}
