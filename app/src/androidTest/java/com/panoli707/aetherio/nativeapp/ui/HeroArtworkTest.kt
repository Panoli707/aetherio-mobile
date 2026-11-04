package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HeroArtworkTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtures = mutableListOf<File>()

    @After fun cleanup() { fixtures.forEach { it.delete() } }

    private fun fixture(width: Int, height: Int): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEachIndexed { index, color ->
            paint.color = color
            val x = if (index % 2 == 0) 0f else width / 2f
            val y = if (index < 2) 0f else height / 2f
            canvas.drawRect(x, y, x + width / 2f, y + height / 2f, paint)
        }
        val file = File.createTempFile("hero-fixture-", ".png", context.cacheDir)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        fixtures.add(file)
        return file.toURI().toString()
    }

    private fun waitForImage() {
        // Coil is asynchronous. Wait for actual pixels, not only the composable placeholder.
        compose.waitUntil(15_000) {
            runCatching {
                val pixels = compose.onNodeWithTag("heroImage", useUnmergedTree = true).captureToImage().toPixelMap()
                val sample = pixels[pixels.width / 2, pixels.height / 2]
                sample.red + sample.green + sample.blue > .8f
            }.getOrDefault(false)
        }
    }

    private fun assertArtworkFillsFrame() {
        val pixels = compose.onNodeWithTag("heroImage", useUnmergedTree = true).captureToImage().toPixelMap()
        // Check the actual frame edges, NOT a calculated centred image rectangle: that masked letterboxing.
        val red = pixels[(pixels.width * .03f).toInt(), (pixels.height * .30f).toInt()]
        val green = pixels[(pixels.width * .97f).toInt(), (pixels.height * .30f).toInt()]
        val blue = pixels[(pixels.width * .03f).toInt(), (pixels.height * .70f).toInt()]
        val yellow = pixels[(pixels.width * .97f).toInt(), (pixels.height * .70f).toInt()]
        assertTrue("Top left of artwork must remain visible", red.red > .45f && red.green < .2f)
        assertTrue("Top right of artwork must remain visible", green.green > .45f && green.red < .2f)
        assertTrue("Bottom left of artwork must remain visible", blue.blue > .45f && blue.red < .2f)
        assertTrue("Bottom right of artwork must remain visible", yellow.red > .45f && yellow.green > .45f)
    }

    @Test fun portraitPosterShowsEveryCornerAndDoesNotRepeatItsEmbeddedTitle() {
        val item = MediaItem("550", MediaType.MOVIE, "Embedded title", posterUrl = fixture(1200, 1800))
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) { HeroArtwork(item, true, 800.dp) }
        } }
        waitForImage()
        assertArtworkFillsFrame()
        compose.onNodeWithText(item.title).assertDoesNotExist()
        compose.onNodeWithContentDescription(item.title).assertIsDisplayed()
        val bounds = compose.onNodeWithTag("heroImage").getUnclippedBoundsInRoot()
        assertEquals(heroArtworkHeight(360.dp, 800.dp, 2f / 3f).value, (bounds.bottom - bounds.top).value, 1f)
    }

    @Test fun bannerFallbackKeepsItsLandscapeProportionsInAPhoneHeader() {
        val item = MediaItem("550", MediaType.MOVIE, "Banner title", backdropUrl = fixture(1920, 1080))
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) { HeroArtwork(item, true, 800.dp) }
        } }
        waitForImage()
        assertArtworkFillsFrame()
        val bounds = compose.onNodeWithTag("heroImage").getUnclippedBoundsInRoot()
        assertEquals(202.5f, (bounds.bottom - bounds.top).value, 1f)
        compose.onNodeWithText(item.title).assertIsDisplayed()
    }

    @Test fun textlessPortraitGetsASeparateTitleAndMissingArtworkFallsBackToThePoster() {
        val poster = fixture(1200, 1800)
        val item = mutableStateOf(MediaItem("1396", MediaType.SERIES, "Series title", posterUrl = poster,
            portraitArtworkUrl = fixture(1200, 1800)))
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) { HeroArtwork(item.value, true, 800.dp) }
        } }
        waitForImage()
        compose.onNodeWithText("Series title").assertIsDisplayed()
        compose.runOnIdle { item.value = item.value.copy(portraitArtworkUrl = "file:///does-not-exist-hero.png") }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Series title").fetchSemanticsNodes().isNotEmpty()
        }
        waitForImage()
        assertArtworkFillsFrame()
        compose.onNodeWithText("Series title").assertDoesNotExist()
    }

    @Test fun lastAvailableLowResolutionSourceStillFillsTheFrameWithoutBlackMargins() {
        val item = MediaItem("550", MediaType.MOVIE, "Small image", posterUrl = fixture(60, 90))
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) { HeroArtwork(item, true, 800.dp) }
        } }
        waitForImage()
        assertArtworkFillsFrame()
    }

    @Test fun lowResolutionPosterUsesAnAvailableSharperBannerWithoutZoomingIt() {
        val item = MediaItem("550", MediaType.MOVIE, "Better artwork", posterUrl = fixture(60, 90),
            backdropUrl = fixture(1920, 1080))
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) { HeroArtwork(item, true, 800.dp) }
        } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText(item.title).fetchSemanticsNodes().isNotEmpty() }
        waitForImage()
        assertArtworkFillsFrame()
        compose.onNodeWithText(item.title).assertIsDisplayed()
    }

    @Test fun narrowWindowAndLargeTextKeepTheCoverSeparateFromMetadataAndActions() {
        val movie = MediaItem("550", MediaType.MOVIE, "Long title", posterUrl = fixture(1200, 1800),
            year = "2026", score = 7.1, genres = listOf("Ciencia ficción"))
        compose.setContent { AetherioTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                Box(Modifier.width(300.dp).height(600.dp)) {
                    DetailScreen(AppState(detail = MediaDetail(movie)), movie, rememberLazyListState(),
                        {}, {}, {}, {}, {}, { _, _, _ -> })
                }
            }
        } }
        waitForImage()
        compose.onNodeWithTag("detailPrimaryPlay").assertIsDisplayed()
        compose.onNodeWithText("Ciencia ficción", substring = true).assertIsDisplayed()
        val image = compose.onNodeWithTag("heroImage").getUnclippedBoundsInRoot()
        val action = compose.onNodeWithTag("detailPrimaryPlay").getUnclippedBoundsInRoot()
        assertTrue(image.bottom < action.top)
        assertEquals(heroArtworkHeight(300.dp, 600.dp, 2f / 3f).value, (image.bottom - image.top).value, 1f)
    }

    @Test fun landscapeHeaderPrefersTheBannerInsteadOfCroppingThePortraitPoster() {
        val item = MediaItem("550", MediaType.MOVIE, "Landscape title", posterUrl = fixture(1200, 1800),
            backdropUrl = fixture(1920, 1080))
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) { HeroArtwork(item, false, 400.dp) }
        } }
        waitForImage()
        assertArtworkFillsFrame()
        compose.onNodeWithText(item.title).assertIsDisplayed()
    }

    @Test fun movieAndSeriesDetailsKeepMetadataAndPrimaryActionVisibleBelowABoundedCover() {
        val item = mutableStateOf(MediaItem("550", MediaType.MOVIE, "Detail title",
            posterUrl = fixture(1200, 1800), year = "2026", score = 6.9, genres = listOf("Animación")))
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) {
                val media = item.value
                DetailScreen(AppState(detail = MediaDetail(media)), media, rememberLazyListState(),
                    {}, {}, {}, {}, {}, { _, _, _ -> })
            }
        } }
        waitForImage()
        compose.onNodeWithTag("detailPrimaryPlay").assertIsDisplayed()
        compose.onNodeWithText("2026", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").assertIsDisplayed()
        saveScreenshot("movie-cover.png")
        compose.runOnIdle { item.value = item.value.copy(type = MediaType.SERIES, isAnime = true) }
        compose.onNodeWithTag("detailPrimaryPlay").assertIsDisplayed()
        compose.onNodeWithText("Animación", substring = true).assertIsDisplayed()
        saveScreenshot("series-cover.png")
    }

    @Test fun anilistSizedPosterFillsThePhoneWidthInsteadOfFloatingInABlackSlot() {
        val item = MediaItem("123", MediaType.SERIES, "AniList-sized cover", posterUrl = fixture(425, 600))
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) { HeroArtwork(item, true, 800.dp) }
        } }
        waitForImage()
        assertArtworkFillsFrame()
        saveScreenshot("anilist-width-fit.png")
    }

    @Test fun sampledLargeArtworkFillsFrameAtDifferentPhoneDensities() {
        val density = mutableStateOf(1.5f)
        val item = MediaItem("550", MediaType.MOVIE, "Sampled artwork", posterUrl = fixture(2160, 3240))
        compose.setContent { AetherioTheme {
            CompositionLocalProvider(LocalDensity provides Density(density.value)) {
                Box(Modifier.width(320.dp)) { HeroArtwork(item, true, 800.dp) }
            }
        } }
        waitForImage()
        assertArtworkFillsFrame()
        compose.runOnIdle { density.value = 3f }
        waitForImage()
        assertArtworkFillsFrame()
    }

    @Test fun homeArtworkAndMetadataHaveNoReservedEmptySlotBetweenThem() {
        val item = MediaItem("550", MediaType.MOVIE, "Home artwork", posterUrl = fixture(425, 600), year = "2026")
        compose.setContent { AetherioTheme {
            Box(Modifier.width(360.dp)) {
                HomeScreen(AppState(heroes = listOf(item), homeLoading = false), rememberLazyListState(),
                    rememberPagerState { 1 }, false, {}, {}, {})
            }
        } }
        waitForImage()
        assertArtworkFillsFrame()
        val image = compose.onNodeWithTag("heroImage", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val metadata = compose.onNodeWithTag("homeHeroMetadata", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(metadata.top - image.bottom < 20.dp)
        saveScreenshot("home-width-fit.png")
    }

    private fun saveScreenshot(name: String) {
        val file = File(context.filesDir, "artwork-qa/$name")
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
