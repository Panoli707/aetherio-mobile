package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class EpisodeArtworkUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    private val loaders = mutableListOf<ImageLoader>()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()
    private val requests = CopyOnWriteArrayList<String>()
    private val resolves = AtomicInteger()
    private val media = MediaItem("333622", MediaType.SERIES, "Fixture series")

    @After fun cleanup() {
        gates.forEach { it.complete(Unit) }
        loaders.forEach { it.shutdown() }
        files.forEach { it.delete() }
    }

    private fun image(color: Int, width: Int = 480, height: Int = 270): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File.createTempFile("episode-art-", ".png", context.cacheDir).also(files::add)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.toURI().toString()
    }

    private fun loader(gate: CompletableDeferred<Unit>? = null): ImageLoader = ImageLoader.Builder(context)
        .memoryCache(null).components { add(object : Interceptor {
            override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                val url = chain.request.data.toString()
                requests.add(url)
                gate?.await()
                return if (url.startsWith("https://fixture.invalid/"))
                    ErrorResult(null, chain.request, IOException("Fixture image unavailable"))
                else chain.proceed(chain.request)
            }
        }) }.build().also { loaders.add(it); if (gate != null) gates.add(gate) }

    private fun provider(landscape: String?, portraitCover: String? = landscape) = object : ArtworkProvider {
        override suspend fun resolve(item: MediaItem): CanonicalArtwork = resolve(item, true)
        override suspend fun resolve(item: MediaItem, portrait: Boolean): CanonicalArtwork {
            resolves.incrementAndGet()
            return CanonicalArtwork(item.title, if (portrait) portraitCover else landscape)
        }
    }

    private fun expectColor(red: Boolean) {
        compose.waitUntil(10_000) {
            runCatching {
                val pixels = compose.onNodeWithTag("episodeTest", true).captureToImage().toPixelMap()
                val color = pixels[pixels.width / 2, pixels.height / 2]
                if (red) color.red > .9f && color.green < .1f else color.green > .9f && color.red < .1f
            }.getOrDefault(false)
        }
    }

    @Test fun successDoesNotResolveOrDownloadFallbackAndRecompositionDoesNotReload() {
        val red = image(Color.RED)
        val green = image(Color.GREEN)
        val title = mutableStateOf(media)
        val loader = loader()
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider(green)) {
            EpisodeArtwork(red, title.value, Modifier.size(240.dp, 135.dp).testTag("episodeTest"), loader)
        } } }
        expectColor(true)
        compose.runOnIdle { title.value = media.copy(title = "Updated metadata") }
        compose.waitForIdle()
        assertEquals(listOf(red), requests.toList())
        assertEquals(0, resolves.get())
    }

    @Test fun failureRecoversToCanonicalSeriesImageWithoutRetryLoop() {
        val green = image(Color.GREEN)
        val loader = loader()
        val art = provider(green)
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides art) {
            EpisodeArtwork("https://fixture.invalid/403.jpg", media, Modifier.size(240.dp, 135.dp).testTag("episodeTest"), loader)
        } } }
        expectColor(false)
        compose.onNodeWithContentDescription("Imagen de la serie", true).assertExists()
        assertEquals(listOf("https://fixture.invalid/403.jpg", green), requests.toList())
        assertEquals(1, resolves.get())
    }

    @Test fun missingLandscapeUsesCanonicalPortraitInsteadOfLeavingAnEmptyCard() {
        val green = image(Color.GREEN)
        val loader = loader()
        val art = provider(null, green)
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides art) {
            EpisodeArtwork(null, media, Modifier.size(240.dp, 135.dp).testTag("episodeTest"), loader)
        } } }
        expectColor(false)
        assertEquals(listOf(green), requests.toList())
        assertEquals(2, resolves.get())
    }

    @Test fun failedLandscapeAlsoFallsBackToTheCanonicalPortrait() {
        val green = image(Color.GREEN)
        val loader = loader()
        val art = provider("https://fixture.invalid/landscape.jpg", green)
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides art) {
            EpisodeArtwork("", media, Modifier.size(240.dp, 135.dp).testTag("episodeTest"), loader)
        } } }
        expectColor(false)
        assertEquals(listOf("https://fixture.invalid/landscape.jpg", green), requests.toList())
    }

    @Test fun noArtworkShowsAnAccessiblePlaceholderWithoutRequests() {
        val loader = loader()
        val art = provider(null)
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides art) {
            EpisodeArtwork(null, media, Modifier.size(240.dp, 135.dp).testTag("episodeTest"), loader)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("episodeArtworkUnavailable", true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Sin miniatura disponible", true).assertExists()
        assertTrue(requests.isEmpty())
    }

    @Test fun sameFailedFallbackIsNotRequestedTwice() {
        val loader = loader()
        val art = provider("https://fixture.invalid/same.jpg")
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides art) {
            EpisodeArtwork(null, media, Modifier.size(240.dp, 135.dp), loader)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("episodeArtworkUnavailable", true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("https://fixture.invalid/same.jpg"), requests.toList())
    }

    @Test fun changingTheEpisodeUrlResetsFailureState() {
        val red = image(Color.RED)
        val green = image(Color.GREEN)
        val url = mutableStateOf("https://fixture.invalid/episode.jpg")
        val loader = loader()
        val art = provider(green)
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides art) {
            EpisodeArtwork(url.value, media, Modifier.size(240.dp, 135.dp).testTag("episodeTest"), loader)
        } } }
        expectColor(false)
        compose.runOnIdle { url.value = red }
        expectColor(true)
        assertEquals(listOf("https://fixture.invalid/episode.jpg", green, red), requests.toList())
    }

    @Test fun primaryAndFallbackSharingTheSameBrokenUrlAreNotRetried() {
        val url = "https://fixture.invalid/shared.jpg"
        val loader = loader()
        val art = provider(url)
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides art) {
            EpisodeArtwork(url, media, Modifier.size(240.dp, 135.dp), loader)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("episodeArtworkUnavailable", true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(url), requests.toList())
    }

    @Test fun allThreeDifferentFailuresStopAtThePlaceholder() {
        val loader = loader()
        val art = provider("https://fixture.invalid/landscape.jpg", "https://fixture.invalid/portrait.jpg")
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides art) {
            EpisodeArtwork("https://fixture.invalid/episode.jpg", media, Modifier.size(240.dp, 135.dp), loader)
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("episodeArtworkUnavailable", true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf("https://fixture.invalid/episode.jpg", "https://fixture.invalid/landscape.jpg",
            "https://fixture.invalid/portrait.jpg"), requests.toList())
    }

    @Test fun slowThumbnailDoesNotStartSpeculativeFallbackWork() {
        val red = image(Color.RED)
        val gate = CompletableDeferred<Unit>()
        val loader = loader(gate)
        val art = provider(image(Color.GREEN))
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides art) {
            EpisodeArtwork(red, media, Modifier.size(240.dp, 135.dp).testTag("episodeTest"), loader)
        } } }
        compose.waitUntil(10_000) { requests.isNotEmpty() }
        assertEquals(0, resolves.get())
        gate.complete(Unit)
        expectColor(true)
        assertEquals(listOf(red), requests.toList())
    }

    @Test fun longSeasonsOnlyLoadVisibleCardsAndDecodeToDisplaySize() {
        val red = image(Color.RED, 1920, 1080)
        val widths = CopyOnWriteArrayList<Int>()
        val loader = loader().newBuilder().eventListener(object : coil.EventListener {
            override fun onSuccess(request: coil.request.ImageRequest, result: coil.request.SuccessResult) {
                widths.add(result.drawable.intrinsicWidth)
            }
        }).build().also(loaders::add)
        val art = provider(null)
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides art) {
            LazyRow(Modifier.width(300.dp).testTag("longSeason")) {
                items((1..120).toList(), key = { it }) {
                    EpisodeArtwork(red, media, Modifier.size(240.dp, 135.dp), loader)
                }
            }
        } } }
        compose.waitUntil(10_000) { widths.isNotEmpty() }
        compose.waitForIdle()
        val initial = requests.size
        assertTrue("Do not load all 120 episodes", initial in 1..12)
        compose.onNodeWithTag("longSeason").performScrollToIndex(119)
        compose.waitForIdle()
        compose.onNodeWithTag("longSeason").performScrollToIndex(0)
        compose.waitForIdle()
        assertTrue("Scrolling must not prefetch the entire season", requests.size < 24)
        assertTrue("Decode measured card sizes, not originals", widths.all { it in 1 until 1920 })
        assertEquals(0, resolves.get())
        println("EPISODE_LAZY season_size=120 initial_requests=$initial total_requests=${requests.size} decoded_widths=${widths.distinct()}")
    }
}
