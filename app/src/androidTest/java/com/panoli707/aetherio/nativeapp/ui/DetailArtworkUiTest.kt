package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.EventListener
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ImageRequest
import coil.request.ImageResult
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DetailArtworkUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    private val loaders = mutableListOf<ImageLoader>()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()
    @After fun cleanup() { gates.forEach { it.complete(Unit) }; loaders.forEach { it.shutdown() }; files.forEach { it.delete() } }

    private fun image(width: Int, height: Int, solid: Int? = null): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        if (solid != null) canvas.drawColor(solid)
        else listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEachIndexed { index, color ->
            paint.color = color
            val x = if (index % 2 == 0) 0f else width / 2f
            val y = if (index < 2) 0f else height / 2f
            canvas.drawRect(x, y, x + width / 2f, y + height / 2f, paint)
        }
        val file = File.createTempFile("detail-art-", ".png", context.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle(); files.add(file)
        return file.toURI().toString()
    }

    private fun waitFor(tag: String) { compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size == 1 } }

    private fun assertAllEdges(tag: String, sourceRatio: Float, faded: Boolean = false, bottomAligned: Boolean = false) {
        val pixels = compose.onNodeWithTag(tag, true).captureToImage().toPixelMap()
        val w = minOf(pixels.width.toFloat(), pixels.height * sourceRatio)
        val h = w / sourceRatio
        val left = (pixels.width - w) / 2f
        val top = if (bottomAligned) pixels.height - h else (pixels.height - h) / 2f
        val bottom = if (faded) .64f else .985f
        val samples = listOf(.015f to .015f, .985f to .015f, .015f to bottom, .985f to bottom).map { (x, y) ->
            pixels[(left + w * x).toInt().coerceIn(0, pixels.width - 1), (top + h * y).toInt().coerceIn(0, pixels.height - 1)]
        }
        assertTrue("Top-left edge must not be cropped or darkened", samples[0].red > .9f && samples[0].green < .1f)
        assertTrue("Top-right edge must remain visible", samples[1].green > .9f && samples[1].red < .1f)
        assertTrue("Bottom-left edge must remain visible", samples[2].blue > .9f && samples[2].red < .1f)
        assertTrue("Bottom-right edge must remain visible", samples[3].red > .9f && samples[3].green > .9f)
    }

    @Test fun completePosterIncludesEveryEdgeAndKeepsTheTitleForAnUnverifiedLegacyFlag() {
        val item = MediaItem("1", MediaType.SERIES, "Integrated title", posterUrl = image(1200, 1800),
            logoUrl = image(600, 200), posterTitlePresence = ArtworkTitlePresence.EMBEDDED)
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { HeroArtwork(item, true, 400.dp, fullImage = true) } } }
        waitFor("heroImage")
        assertAllEdges("heroImage", 2f / 3f, faded = true)
        val bounds = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
        assertEquals(240f, (bounds.right - bounds.left).value, 1f)
        assertEquals(360f, (bounds.bottom - bounds.top).value, 1f)
        compose.onNodeWithTag("detailTitle").assertExists()
    }

    @Test fun unknownPosterAndCompleteLogoShareOneFadedHeader() {
        val item = MediaItem("1", MediaType.SERIES, "Title", posterUrl = image(1200, 1800), logoUrl = image(1200, 240))
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { HeroArtwork(item, true, 400.dp, fullImage = true) } } }
        waitFor("heroImage"); waitFor("detailLogo")
        assertAllEdges("heroImage", 2f / 3f, faded = true); assertAllEdges("detailLogo", 5f, bottomAligned = true)
        val picture = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
        val logo = compose.onNodeWithTag("detailLogo", true).getUnclippedBoundsInRoot()
        val fade = compose.onNodeWithTag("detailHeroFade", true).getUnclippedBoundsInRoot()
        assertTrue("The whole logo must stay inside the lower picture edge", logo.top >= picture.top && logo.bottom < picture.bottom && fade.bottom == picture.bottom)
        compose.onNodeWithTag("detailHeroFade").assertExists()
    }

    @Test fun aWideBannerAlsoKeepsItsEdgesInsteadOfBeingZoomedIntoAPortrait() {
        val item = MediaItem("1", MediaType.SERIES, "Banner", backdropUrl = image(1920, 480))
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { HeroArtwork(item, true, 400.dp, fullImage = true) } } }
        waitFor("heroImage")
        assertAllEdges("heroImage", 4f, faded = true)
        assertEquals(60f, compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }, 1f)
        compose.onNodeWithText("Banner").assertExists()
    }

    @Test fun missingLogosKeepTheWholeWrittenTitleAndSafeFrameForLargeText() {
        val item = MediaItem("1", MediaType.SERIES, "Un título largo que debe seguir completo aunque no exista ningún archivo de logo disponible")
        compose.setContent { AetherioTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Box(Modifier.width(240.dp)) { DetailTitle(item) }
            }
        } }
        compose.onNodeWithText(item.title).assertIsDisplayed()
        compose.onNodeWithTag("detailLogo").assertDoesNotExist()
        val text = compose.onNodeWithTag("detailTitleText").getUnclippedBoundsInRoot()
        val frame = compose.onNodeWithTag("detailTitle").getUnclippedBoundsInRoot()
        assertTrue(text.top >= frame.top && text.bottom <= frame.bottom)
    }

    @Test fun failedLogoUsesTheNextVariantAndTallLogosAreNotCropped() {
        val item = MediaItem("1", MediaType.SERIES, "Fallback logo", logoUrl = "file:///missing-detail-logo.png",
            logoAlternatives = listOf(image(240, 1200)))
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { DetailTitle(item) } } }
        waitFor("detailLogo")
        assertAllEdges("detailLogo", .2f)
    }

    @Test fun delayedLogoDoesNotMoveTheTitleFrameOrReloadTheCover() {
        val poster = image(1200, 1800)
        val logo = image(1200, 240)
        val gate = CompletableDeferred<Unit>().also(gates::add)
        val covers = AtomicInteger()
        val loader = ImageLoader.Builder(context).memoryCache(null).eventListener(object : EventListener {
            override fun onStart(request: ImageRequest) { if (request.data == poster) covers.incrementAndGet() }
        }).components { add(object : Interceptor {
            override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                if (chain.request.data == logo) gate.await()
                return chain.proceed(chain.request)
            }
        }) }.build().also(loaders::add)
        val item = mutableStateOf(MediaItem("1", MediaType.SERIES, "Stable title", posterUrl = poster, logoUrl = logo))
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { HeroArtwork(item.value, true, 400.dp, imageLoader = loader, fullImage = true) } } }
        waitFor("heroImage")
        val before = compose.onNodeWithTag("detailTitle").getUnclippedBoundsInRoot()
        gate.complete(Unit); waitFor("detailLogo")
        assertEquals(before, compose.onNodeWithTag("detailTitle").getUnclippedBoundsInRoot())
        compose.runOnIdle { item.value = item.value.copy(logoUrl = "file:///other-missing-logo.png", logoAlternatives = listOf(logo), score = 8.0) }
        waitFor("detailLogo")
        assertEquals(1, covers.get())
        assertAllEdges("heroImage", 2f / 3f, faded = true)
    }

    @Test fun everyLogoFailureStillLeavesTheWrittenTitleVisible() {
        val item = MediaItem("1", MediaType.SERIES, "Título siempre visible", logoUrl = "file:///missing-logo-one.png",
            logoAlternatives = listOf("file:///missing-logo-two.png"))
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { DetailTitle(item) } } }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("detailTitle") and hasStateDescription("Título preparado"), true).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithText(item.title).assertIsDisplayed()
        compose.onNodeWithTag("detailLogo").assertDoesNotExist()
    }

    @Test fun longNamesDoNotCreateAHugeEmptySlotAroundAValidLogo() {
        val item = MediaItem("1", MediaType.SERIES, "Un nombre muy largo para esta serie. ".repeat(8), logoUrl = image(1200, 240))
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { DetailTitle(item) } } }
        waitFor("detailLogo")
        val frame = compose.onNodeWithTag("detailTitle").getUnclippedBoundsInRoot()
        assertEquals(112f, (frame.bottom - frame.top).value, 1f)
        assertAllEdges("detailLogo", 5f)
    }

    @Test fun aLandscapeFileMislabelledAsAPosterCannotHideAnAvailableVerticalImage() {
        val item = MediaItem("1", MediaType.SERIES, "Orientation", portraitArtworkUrl = image(1600, 400, Color.RED),
            posterUrl = image(1200, 1800, Color.BLUE), backdropUrl = image(1920, 1080, Color.GREEN))
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { HeroArtwork(item, true, 400.dp, fullImage = true) } } }
        waitFor("heroImage")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() }
        val bounds = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
        assertEquals(360f, (bounds.bottom - bounds.top).value, 1f)
        val pixels = compose.onNodeWithTag("heroImage", true).captureToImage().toPixelMap()
        assertTrue(pixels[pixels.width / 2, pixels.height / 2].blue > .9f)
    }

    @Test fun theFadeHasNoHardBoundaryAtThePhotographEndAndDoesNotDarkenItsUpperPart() {
        val item = MediaItem("1", MediaType.SERIES, "Blend", posterUrl = image(1200, 1800, Color.WHITE),
            logoUrl = image(600, 200, Color.GREEN))
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { HeroArtwork(item, true, 400.dp, fullImage = true) } } }
        waitFor("heroImage"); waitFor("detailLogo")
        val pixels = compose.onNodeWithTag("heroArtwork", true).captureToImage().toPixelMap()
        val imageHeight = (pixels.width * 1.5f).toInt()
        val x = 2 // Outside the title ink, compare the fade itself.
        assertTrue(pixels[x, imageHeight / 2].red > .95f)
        assertTrue(pixels[x, (imageHeight * .97f).toInt()].red < .4f)
        assertTrue(pixels[x, imageHeight - 2].red < .1f)
        assertEquals(imageHeight, pixels.height)
        val frame = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
        assertEquals(360f, (frame.bottom - frame.top).value, 1f)
    }

    @Test fun aDarkLogoGetsAnAlphaShapedContrastOutlineNotARectangularPanel() {
        val bitmap = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(120f, 60f, 480f, 140f, Paint().apply { color = Color.BLACK })
        val file = File.createTempFile("dark-logo-", ".png", context.cacheDir).also(files::add)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val item = MediaItem("1", MediaType.SERIES, "Dark logo", logoUrl = file.toURI().toString())
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp).background(Ink)) { DetailTitle(item) } } }
        waitFor("detailLogo")
        val pixels = compose.onNodeWithTag("detailLogo", true).captureToImage().toPixelMap()
        val bright = (0 until pixels.width).count { pixels[it, pixels.height / 2].red > .6f }
        assertTrue("Dark logo edges must remain legible", bright > 0)
        assertTrue("The outline must not become a panel", bright < pixels.width / 10)
        assertTrue(pixels[pixels.width / 2, pixels.height / 2].red < .1f)
    }
}
