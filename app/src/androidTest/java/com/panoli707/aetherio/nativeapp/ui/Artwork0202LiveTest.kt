package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class Artwork0202LiveTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun reportedTitlesAndMovieSeriesHaveRealCleanCoversLogosAndLandscapeImages() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveArtwork0202") == "true")
        val items = listOf(
            MediaItem("20", MediaType.SERIES, "Naruto", CatalogSource.ANILIST),
            MediaItem("148116", MediaType.SERIES, "Hyouken", CatalogSource.ANILIST),
            MediaItem("154587", MediaType.SERIES, "Frieren", CatalogSource.ANILIST),
            MediaItem("550", MediaType.MOVIE, "Fight Club"),
            MediaItem("66732", MediaType.SERIES, "Stranger Things"),
            MediaItem("27205", MediaType.MOVIE, "Inception"))
        val current = mutableStateOf(items.first())
        val portrait = mutableStateOf(true)
        val provider = CanonicalArtworkProvider(context) { coil.Coil.imageLoader(context) }
        compose.setContent { AetherioTheme { Box(Modifier.width(300.dp)) {
            HeroArtwork(current.value, portrait.value, 800.dp, fullImage = true)
        } } }
        for (item in items) for (vertical in listOf(true, false)) {
            val selected = provider.resolve(item, vertical)
            assertNotNull(selected.cover); assertNotNull(selected.logo)
            assertEquals(ArtworkTitlePresence.ABSENT, selected.titlePresence)
            assertTrue(if (vertical) selected.ratio < 1f else selected.ratio > 1.3f)
            compose.runOnIdle { current.value = item; portrait.value = vertical }
            compose.waitUntil(45_000) {
                compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1 &&
                    compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().size == 1 &&
                    compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty()
            }
            val bounds = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
            assertEquals(selected.ratio, (bounds.right - bounds.left).value / (bounds.bottom - bounds.top).value, .025f)
            val title = compose.onNodeWithTag("detailTitle", true).getUnclippedBoundsInRoot()
            assertEquals(8f, (bounds.bottom - title.bottom).value, 1f)
            assertEquals(selected, provider.resolve(item.copy(seasonHint = 3), vertical))
            val image = compose.onNodeWithTag("heroArtwork", true).captureToImage().asAndroidBitmap()
            val file = File(context.getExternalFilesDir(null), "qa0202/${item.id}-${if (vertical) "portrait" else "landscape"}.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val colors = (1..19).flatMap { y -> (1..19).map { x -> image.getPixel(image.width * x / 20, image.height * y / 20) } }.distinct()
            assertTrue("Real art ${item.id}, portrait=$vertical: ${colors.size} sampled colours", colors.size > 16)
        }
    }

    @Test fun automaticLogoSelectionWorksOutsideTheManualRegistryForMovieSeriesAndAnime() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveArtwork0202") == "true")
        val provider = CanonicalArtworkProvider(context) { coil.Coil.imageLoader(context) }
        val missing = mutableListOf<String>()
        for (item in listOf(MediaItem("13", MediaType.MOVIE, "Forrest Gump"),
            MediaItem("1399", MediaType.SERIES, "Game of Thrones"),
            MediaItem("1429", MediaType.SERIES, "Attack on Titan"))) {
            assertFalse(CanonicalArtworkCatalog.entries.containsKey(item.key))
            val logo = provider.logo(item)
            if (logo == null) {
                val source = AetherioRepository().artwork(item)
                for (url in ArtworkImages.logos(source).take(4)) {
                    val loaded = coil.Coil.imageLoader(context).execute(coil.request.ImageRequest.Builder(context)
                        .data(url).size(960, 1440).allowHardware(false).build()) as? coil.request.SuccessResult ?: continue
                    val bitmap = loaded.drawable.toBitmap()
                    val mask = logoOcrMask(bitmap)
                    val reading = try { ArtworkTextRecognizer.read(context, "diagnostic-mask:$url", mask.asImageBitmap()) }
                        finally { mask.recycle() }
                    InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                        putString("stream", "Logo ${source.title}; aliases=${source.artworkTitles}; size=${bitmap.width}x${bitmap.height}; complete=${reading.complete}; OCR=${reading.text}\n")
                    })
                }
            }
            if (logo == null) missing += item.title
        }
        assertTrue("Automatic logos missing: $missing", missing.isEmpty())
    }
}
