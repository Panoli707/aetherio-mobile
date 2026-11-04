package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SeasonArtworkUiTest {
    @get:Rule val compose = createComposeRule()
    private val files = mutableListOf<File>()
    @After fun cleanup() { files.forEach { it.delete() } }

    private fun image(color: Int): String {
        val bitmap = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File.createTempFile("season-art-", ".png", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle(); files.add(file)
        return file.toURI().toString()
    }

    private fun expectColor(channel: Int) {
        compose.waitUntil(15_000) {
            runCatching {
                val pixels = compose.onNodeWithTag("heroImage", useUnmergedTree = true).captureToImage().toPixelMap()
                val color = pixels[pixels.width / 2, pixels.height / 2]
                when (channel) { 0 -> color.red > .9f && color.green < .1f; 1 -> color.green > .9f && color.blue < .1f
                    else -> color.blue > .9f && color.red < .1f }
            }.getOrDefault(false)
        }
    }

    @Test fun selectingSeasonsReplacesGeneralArtworkAndMissingSeasonArtRestoresTheBase() {
        val base = MediaItem("1396", MediaType.SERIES, "Series", portraitArtworkUrl = image(Color.GREEN))
        val first = image(Color.RED)
        val second = image(Color.BLUE)
        val current = mutableStateOf(base)
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) { HeroArtwork(current.value, true, 800.dp) }
        } }
        expectColor(1)
        compose.runOnIdle { current.value = SeasonArtwork.apply(base, 1, poster = first) }
        expectColor(0)
        compose.runOnIdle { current.value = SeasonArtwork.apply(base, 2, poster = second) }
        expectColor(2)
        compose.runOnIdle { current.value = SeasonArtwork.apply(base, 3) }
        expectColor(1)
    }
}
