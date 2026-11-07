package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.ErrorResult
import coil.request.SuccessResult
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in network QA. Does not read or change library, tracking, downloads or playback data. */
class EpisodeArtworkLiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportedEpisodeRendersAnImageEvenWhenMetahubRejectsTheThumbnail() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveEpisodeArtwork") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = ImageLoader.Builder(context).okHttpClient(metadataHttpClient()).build()
        try {
            val item = AetherioRepository().detail(MediaItem("333622", MediaType.SERIES, "Reported anime")).item
            val url = "https://episodes.metahub.space/tt43650419/1/7/w780.jpg"
            val started = System.nanoTime()
            val result = loader.execute(ImageRequest.Builder(context).data(url).size(500, 281).build())
            val status = when (result) {
                is SuccessResult -> "success"
                is ErrorResult -> (result.throwable as? coil.network.HttpException)?.response?.code?.toString() ?: "network_error"
            }
            println("EPISODE_LIVE primary_status=$status elapsed_ms=${(System.nanoTime() - started) / 1_000_000}")
            val provider = CanonicalArtworkProvider(context) { loader }
            compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
                EpisodeArtwork(url, item, Modifier.size(300.dp, 169.dp).testTag("liveEpisodeArt"), loader)
            } } }
            compose.waitUntil(60_000) {
                runCatching {
                    val image = compose.onNodeWithTag("liveEpisodeArt", true).captureToImage().asAndroidBitmap()
                    val colors = (1..8).flatMap { y -> (1..8).map { x -> image.getPixel(image.width * x / 10, image.height * y / 10) } }.distinct()
                    colors.size > 8
                }.getOrDefault(false)
            }
            val screenshot = compose.onNodeWithTag("liveEpisodeArt", true).captureToImage().asAndroidBitmap()
            val file = File(context.getExternalFilesDir(null), "qa-episode-artwork/reported-episode.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (status != "success") compose.onNodeWithContentDescription("Imagen de la serie", true).assertExists()
        } finally { loader.shutdown() }
    }
}
