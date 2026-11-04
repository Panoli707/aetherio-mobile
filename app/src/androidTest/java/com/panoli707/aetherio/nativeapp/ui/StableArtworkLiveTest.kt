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

    @Test fun boticariaRealSeasonImagesChangeVisiblePixelsAndReturnToTheSameFirstCover() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualSeriesArtwork") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = SeriesIdentity.item(MediaItem("220542", MediaType.SERIES, "Los diarios de la boticaria", isAnime = true))
        val repository = AetherioRepository()
        val seasons = runBlocking { (1..3).map { repository.seasonContent(base, it).item } }
        assertEquals(3, seasons.map { ArtworkImages.headers(it, true).first().url }.distinct().size)
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
        assertNotEquals(signatures[0], signatures[1]); assertNotEquals(signatures[1], signatures[2])
        assertEquals(signatures[0], signatures[3])
    }
}
