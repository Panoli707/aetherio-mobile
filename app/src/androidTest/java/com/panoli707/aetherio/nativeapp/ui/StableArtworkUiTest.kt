package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.ErrorResult
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class StableArtworkUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    private val loaders = mutableListOf<ImageLoader>()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @After fun cleanup() { gates.forEach { it.complete(Unit) }; scope.cancel(); loaders.forEach { it.shutdown() }; files.forEach { it.delete() } }

    private fun image(color: Int, width: Int = 1200, height: Int = 1800): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File.createTempFile("stable-art-", ".png", context.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle(); files.add(file)
        return file.toURI().toString()
    }

    private fun expectColor(channel: Int) {
        compose.waitUntil(15_000) {
            runCatching {
                val pixels = compose.onNodeWithTag("heroImage", true).captureToImage().toPixelMap()
                val color = pixels[pixels.width / 2, pixels.height / 2]
                when (channel) { 0 -> color.red > .9f && color.green < .1f
                    1 -> color.green > .9f && color.blue < .1f
                    else -> color.blue > .9f && color.red < .1f }
            }.getOrDefault(false)
        }
        compose.onAllNodesWithTag("heroImage", true).assertCountEquals(1)
    }

    private fun delayed(url: String, gate: CompletableDeferred<Unit>, starts: AtomicInteger): ImageLoader =
        ImageLoader.Builder(context).memoryCache(null).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    starts.incrementAndGet()
                    if (chain.request.data == url) withContext(NonCancellable) { gate.await() }
                    return chain.proceed(chain.request)
                }
            })
        }.build().also { loaders.add(it); gates.add(gate) }

    @Test fun initialDetailNeverShowsCatalogOrGeneralArtworkWhileTheSelectedSeasonLoads() {
        val catalog = MediaItem("220542", MediaType.SERIES, "Los diarios de la boticaria", posterUrl = image(Color.RED))
        val general = catalog.copy(portraitArtworkUrl = image(Color.GREEN))
        val selected = SeasonArtwork.apply(general, 2, poster = image(Color.BLUE))
        val pending = CompletableDeferred<SeasonContent>()
        val state = mutableStateOf(AppState())
        val controller = DetailLoader(scope, { MediaDetail(general, seasons = listOf(SeasonInfo(1, "T1", 24), SeasonInfo(2, "T2", 24)), selectedSeason = 1) },
            { _, _ -> pending.await() }, { 2 }, { state.value }, { state.value = it(state.value) })
        compose.runOnIdle { controller.open(catalog) }
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) { DetailScreen(state.value, catalog, rememberLazyListState(), {}, {}, {}, {}, {}, { _, _, _ -> }) }
        } }
        compose.onNodeWithTag("detailArtworkPlaceholder").assertExists()
        compose.onAllNodesWithTag("heroImage", true).assertCountEquals(0)
        pending.complete(SeasonContent(selected, listOf(EpisodeItem(1, 2, "Episode"))))
        expectColor(2)
        compose.onNodeWithTag("detailArtworkPlaceholder").assertDoesNotExist()
    }

    @Test fun lowResolutionCandidateIsNotPaintedBeforeItsSharperAlternativeIsValidated() {
        val red = image(Color.RED, 60, 90)
        val blue = image(Color.BLUE, 1920, 1080)
        val gate = CompletableDeferred<Unit>()
        val starts = AtomicInteger()
        val loader = delayed(blue, gate, starts)
        val item = MediaItem("1", MediaType.SERIES, "Quality", posterUrl = red, backdropUrl = blue)
        compose.setContent { AetherioTheme { Box(Modifier.width(360.dp)) { HeroArtwork(item, true, 800.dp, imageLoader = loader) } } }
        compose.waitUntil(15_000) { starts.get() == 2 }
        compose.onAllNodesWithTag("heroImage", true).assertCountEquals(0)
        gate.complete(Unit)
        expectColor(2)
        val bounds = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
        assertEquals(360f, (bounds.right - bounds.left).value, 1f)
        assertEquals(202.5f, (bounds.bottom - bounds.top).value, 1f)
    }

    @Test fun delayedReplacementKeepsOnlyTheOldCoverAndLateResultsCannotOverwriteTheNewSelection() {
        val red = image(Color.RED)
        val blue = image(Color.BLUE)
        val green = image(Color.GREEN)
        val gate = CompletableDeferred<Unit>()
        val starts = AtomicInteger()
        val loader = delayed(blue, gate, starts)
        val item = mutableStateOf(MediaItem("1", MediaType.SERIES, "Season", posterUrl = red))
        compose.setContent { AetherioTheme { Box(Modifier.width(360.dp)) { HeroArtwork(item.value, true, 800.dp, imageLoader = loader) } } }
        expectColor(0)
        compose.runOnIdle { item.value = item.value.copy(posterUrl = blue, seasonHint = 2) }
        compose.waitUntil(15_000) { starts.get() == 2 }
        expectColor(0)
        compose.runOnIdle { item.value = item.value.copy(posterUrl = green, seasonHint = 3) }
        expectColor(1)
        gate.complete(Unit)
        compose.waitForIdle()
        expectColor(1)
        val count = starts.get()
        val bounds = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
        compose.runOnIdle { item.value = item.value.copy(score = 9.0, overview = "Metadata refreshed") }
        compose.waitForIdle()
        assertEquals(count, starts.get())
        assertEquals(bounds, compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot())
    }

    @Test fun boticariaMappedSeasonsShowTheirOwnPixelsInsteadOfTheSharedPortrait() {
        val base = MediaItem("220542", MediaType.SERIES, "Los diarios de la boticaria", portraitArtworkUrl = image(Color.YELLOW))
        val entries = listOf(image(Color.RED), image(Color.GREEN), image(Color.BLUE))
        val item = mutableStateOf(SeasonArtwork.mapped(base, 1, base.copy(posterUrl = entries[0], portraitArtworkUrl = null),
            true, "shared", null, base.portraitArtworkUrl))
        compose.setContent { AetherioTheme { Box(Modifier.width(360.dp)) { HeroArtwork(item.value, true, 800.dp) } } }
        for (number in listOf(1, 2, 3, 1)) {
            compose.runOnIdle { item.value = SeasonArtwork.mapped(base, number,
                base.copy(posterUrl = entries[number - 1], portraitArtworkUrl = null), true, "shared", null, base.portraitArtworkUrl) }
            expectColor(number - 1)
        }
    }

    @Test fun failedAlternativesDoNotDiscardTheOnlyAvailableOriginalOrShrinkItsWidth() {
        val item = MediaItem("1", MediaType.SERIES, "Only available", posterUrl = image(Color.RED, 60, 90),
            backdropUrl = "file:///aetherio-missing-alternative.png")
        compose.setContent { AetherioTheme { Box(Modifier.width(360.dp)) { HeroArtwork(item, true, 800.dp) } } }
        expectColor(0)
        assertEquals(360f, compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot().let { (it.right - it.left).value }, 1f)
    }

    @Test fun aDifferentSeriesWithIdenticalImageUrlsStillLoadsItsOwnHeroState() {
        val item = mutableStateOf(MediaItem("1", MediaType.SERIES, "First", posterUrl = image(Color.RED)))
        compose.setContent { AetherioTheme { Box(Modifier.width(360.dp)) { HeroArtwork(item.value, true, 800.dp) } } }
        expectColor(0)
        compose.runOnIdle { item.value = item.value.copy(id = "2", title = "Second") }
        expectColor(0)
        compose.onNodeWithText("Second").assertExists()
    }

    @Test fun nativeAnilistCoverKeepsItsPortraitSizeEvenWhenAWiderBannerIsAvailable() {
        val poster = image(Color.RED, 425, 600)
        val banner = image(Color.BLUE, 1920, 400)
        val gate = CompletableDeferred<Unit>()
        val starts = AtomicInteger()
        val loader = delayed(banner, gate, starts)
        val item = MediaItem("1", MediaType.SERIES, "Native extraLarge", posterUrl = poster, backdropUrl = banner)
        compose.setContent { AetherioTheme { Box(Modifier.width(360.dp)) { HeroArtwork(item, true, 800.dp, imageLoader = loader) } } }
        expectColor(0)
        assertEquals(1, starts.get())
        val bounds = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
        assertEquals(360f, (bounds.right - bounds.left).value, 1f)
        assertEquals(heroArtworkHeight(360.dp, 800.dp, 425f / 600f).value, (bounds.bottom - bounds.top).value, 1f)
    }

    @Test fun scrollingBackAfterAnImageFailureDoesNotChooseADifferentIllustration() {
        val poster = image(Color.RED, 425, 600)
        val banner = image(Color.BLUE, 1920, 400)
        val fail = AtomicBoolean(false)
        val bannerRequests = AtomicInteger()
        val requests = AtomicInteger()
        val loader = ImageLoader.Builder(context).memoryCache(null).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    requests.incrementAndGet()
                    if (chain.request.data == banner) bannerRequests.incrementAndGet()
                    if (fail.get() && chain.request.data == poster)
                        return ErrorResult(null, chain.request, java.io.IOException("Fixture offline"))
                    return chain.proceed(chain.request)
                }
            })
        }.build().also(loaders::add)
        val visible = mutableStateOf(true)
        val item = MediaItem("1", MediaType.SERIES, "Pinned", posterUrl = poster, backdropUrl = banner)
        compose.setContent { AetherioTheme {
            val saved = rememberSaveableStateHolder()
            if (visible.value) saved.SaveableStateProvider("hero") {
                Box(Modifier.width(360.dp)) { HeroArtwork(item, true, 800.dp, imageLoader = loader) }
            }
        } }
        expectColor(0)
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        fail.set(true)
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(15_000) { requests.get() == 2 && compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() }
        assertEquals(0, bannerRequests.get())
        compose.onAllNodesWithTag("heroImage", true).assertCountEquals(0)
        fail.set(false)
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        expectColor(0)
    }
}
