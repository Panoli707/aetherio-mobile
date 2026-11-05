package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
class LogoPlacementUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtures = mutableListOf<File>()
    @After fun cleanup() { fixtures.forEach { it.delete() } }

    private fun image(width: Int, height: Int, color: Int): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File.createTempFile("logo-placement-", ".png", context.cacheDir).also(fixtures::add)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.toURI().toString()
    }

    @Test fun allContentTypesAndArtworkOrientationsKeepTheWholeLogoBeforeTheFadeInHomeAndDetails() {
        val portrait = image(600, 900, android.graphics.Color.BLUE)
        val banner = image(1600, 400, android.graphics.Color.BLUE)
        val logo = image(600, 200, android.graphics.Color.WHITE)
        val item = mutableStateOf(MediaItem("999999999", MediaType.MOVIE, "Una película", logoUrl = logo))
        val fullImage = mutableStateOf(false)
        val usePortrait = mutableStateOf(true)
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) {
            HeroArtwork(item.value, usePortrait.value, 600.dp, fullImage = fullImage.value)
        } } }
        for (detail in listOf(false, true)) for (vertical in listOf(true, false))
            for ((type, anime) in listOf(MediaType.MOVIE to false, MediaType.SERIES to false, MediaType.SERIES to true)) {
                compose.runOnIdle {
                    fullImage.value = detail; usePortrait.value = vertical
                    item.value = item.value.copy(type = type, isAnime = anime,
                        posterUrl = if (vertical) portrait else null, backdropUrl = if (vertical) null else banner)
                }
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
                        compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().size == 1
                }
                val photo = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
                val title = compose.onNodeWithTag("detailLogo", true).getUnclippedBoundsInRoot()
                val fade = compose.onNodeWithTag("detailHeroFade", true).getUnclippedBoundsInRoot()
                val hero = compose.onNodeWithTag("heroArtwork", true).getUnclippedBoundsInRoot()
                assertTrue("$type anime=$anime detail=$detail portrait=$vertical", title.top >= photo.top && title.bottom < fade.top)
                assertTrue(title.left >= photo.left && title.right <= photo.right)
                assertEquals(photo.bottom, fade.bottom)
                assertEquals(photo.bottom, hero.bottom)
            }
        compose.runOnIdle { item.value = item.value.copy(logoUrl = "file:///missing-logo-placement.png") }
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("detailTitle") and hasStateDescription("Título preparado"), true).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("detailTitleText").assertIsDisplayed()
        val fallback = compose.onNodeWithTag("detailTitle").getUnclippedBoundsInRoot()
        assertTrue(fallback.bottom < compose.onNodeWithTag("detailHeroFade").getUnclippedBoundsInRoot().top)
    }

    @Test fun everyNewBundledLogoDecodesAndHasTransparentMargins() {
        for (entry in AnimeLogoCatalog.entries) {
            val bitmap = context.assets.open("anime-logos/${entry.asset}").use { android.graphics.BitmapFactory.decodeStream(it) }
            assertNotNull(entry.asset, bitmap)
            assertTrue(entry.asset, bitmap.hasAlpha())
            assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
            assertTrue(bitmap.width > 200 && bitmap.height > 30)
            bitmap.recycle()
        }
    }

    @Test fun realBundledLogoStaysInsideItsActualCoverAndExportsAReference() {
        val cover = image(600, 900, android.graphics.Color.rgb(45, 60, 90))
        val item = MediaItem("213068", MediaType.SERIES, "Dandivine", CatalogSource.ANILIST,
            posterUrl = cover, posterTitlePresence = ArtworkTitlePresence.ABSENT)
        compose.setContent { AetherioTheme { Box(Modifier.width(300.dp)) { HeroArtwork(item, true, 600.dp, fullImage = true) } } }
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().size == 1 }
        val file = File(context.filesDir, "logo-placement-0164/bundled-reference.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onNodeWithTag("heroArtwork").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
