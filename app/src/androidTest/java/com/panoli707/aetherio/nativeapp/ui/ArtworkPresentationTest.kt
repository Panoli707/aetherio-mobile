package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArtworkPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    @After fun cleanup() { files.forEach { it.delete() } }
    private fun image(color: Int): String {
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val file = File.createTempFile("artwork-presentation-", ".png", context.cacheDir)
        files.add(file)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        return file.toURI().toString()
    }
    private fun ready() { compose.waitUntil(30_000) { compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() } }
    private fun provider(item: MediaItem, clean: String? = null): ArtworkProvider {
        val resolver = CanonicalArtworkResolver({ item }, { url, logo, _ ->
            ArtworkInspection(2f / 3f, clean = !logo && url == clean)
        })
        return ArtworkProvider { resolver.resolve(it) }
    }

    @Test fun bottomPrintedTitleSelectsACleanSameScopePortraitBeforeShowingIt() {
        val printed = image(Color.RED)
        val clean = image(Color.GREEN)
        val item = MediaItem("1", MediaType.SERIES, "Título", posterUrl = printed, portraitAlternatives = listOf(clean))
        val provider = provider(item, clean)
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            Box(Modifier.width(300.dp).verticalScroll(rememberScrollState())) { HeroArtwork(item, true, 800.dp, fullImage = true) }
        } } }
        ready()
        val pixels = compose.onNodeWithTag("heroImage").captureToImage().toPixelMap()
        assertTrue(pixels[pixels.width / 2, pixels.height / 3].green > .8f)
        compose.onNodeWithTag("detailTitleText").assertExists()
        compose.onNodeWithTag("detailHeroFade").assertExists()
    }

    @Test fun topPrintedTitlesAreRejectedRatherThanDuplicatedWithAnotherLogo() {
        val portrait = image(Color.RED)
        val logo = image(Color.WHITE)
        val item = MediaItem("1", MediaType.SERIES, "Título", posterUrl = portrait, logoUrl = logo,
            posterTitlePresence = ArtworkTitlePresence.TOP)
        val provider = provider(item)
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            Box(Modifier.width(300.dp).verticalScroll(rememberScrollState())) { HeroArtwork(item, true, 800.dp, fullImage = true) }
        } } }
        ready()
        compose.onAllNodesWithTag("detailLogo", true).assertCountEquals(0)
        compose.onAllNodesWithTag("heroImage", true).assertCountEquals(0)
        compose.onNodeWithText(item.title).assertExists()
        compose.onNodeWithTag("detailHeroFade").assertExists()
        val title = compose.onNodeWithTag("detailTitle").getUnclippedBoundsInRoot()
        val photo = compose.onNodeWithTag("heroArtwork").getUnclippedBoundsInRoot()
        assertTrue(title.top < photo.bottom)
    }

    @Test fun withoutACleanAlternativeOrLogoBottomTextUsesAnUnobscuredWrittenTitle() {
        val item = MediaItem("1", MediaType.MOVIE, "Título de reserva", posterUrl = image(Color.WHITE),
            posterTitlePresence = ArtworkTitlePresence.BOTTOM)
        val provider = provider(item)
        compose.setContent { AetherioTheme { CompositionLocalProvider(LocalArtworkProvider provides provider) {
            Box(Modifier.width(300.dp).verticalScroll(rememberScrollState())) { HeroArtwork(item, true, 800.dp, fullImage = true) }
        } } }
        ready()
        val photo = compose.onNodeWithTag("heroArtwork").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag("detailTitle").getUnclippedBoundsInRoot()
        val fade = compose.onNodeWithTag("detailHeroFade").getUnclippedBoundsInRoot()
        assertTrue(title.top >= photo.top && title.bottom < photo.bottom && fade.bottom == photo.bottom)
        compose.onNodeWithText(item.title).assertExists()
        compose.onNodeWithTag("detailHeroFade").assertExists()
    }

    @Test fun bundledRecognizerDetectsLatinTitlePositionWithoutDownloadingAModel() = runBlocking {
        for ((y, expected) in listOf(150f to ArtworkTitlePresence.TOP, 820f to ArtworkTitlePresence.BOTTOM)) {
            val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            Canvas(bitmap).drawText("MODERN FAMILY", 30f, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 56f })
            assertEquals(expected, ArtworkTextRecognizer.analyze(context, "fixture-$y", bitmap.asImageBitmap()))
            bitmap.recycle()
        }
    }
}
