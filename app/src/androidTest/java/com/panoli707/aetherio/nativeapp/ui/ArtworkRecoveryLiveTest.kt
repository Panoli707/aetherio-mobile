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
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercises real automatic selection, deliberately outside the seven hand-reviewed exceptions. */
class ArtworkRecoveryLiveTest {
    @get:Rule val compose = createComposeRule()
    @Test fun uncuratedMovieSeriesAndAnimeLoadWithoutTheOcrAdmissionGate() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveArtworkRecovery") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AetherioRepository()
        val items = listOf("Inception" to "27205", "Stranger Things" to "66732", "One Piece" to "37854").map { (query, id) ->
            repository.search(query).first { it.id == id && it.source == CatalogSource.TMDB }
        }
        val provider = CanonicalArtworkProvider(context) { context.coilImageLoader() }
        val choices = items.map { item ->
            assertFalse(CanonicalArtworkCatalog.entries.containsKey(item.key))
            val start = System.nanoTime()
            provider.resolve(item).also {
                assertNotNull(it.cover)
                assertTrue("Seed selection must not perform OCR/network", (System.nanoTime() - start) / 1_000_000 < 1500)
            }
        }
        val current = mutableStateOf(items.first())
        compose.setContent { AetherioTheme { Box(Modifier.width(360.dp)) {
            HeroArtwork(current.value, true, 800.dp, fullImage = true)
        } } }
        for (index in items.indices) {
            compose.runOnIdle { current.value = items[index] }
            compose.waitUntil(45_000) {
                compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1
            }
            assertEquals(choices[index].cover, provider.resolve(items[index].copy(seasonHint = 2)).cover)
            val image = compose.onNodeWithTag("heroImage", true).captureToImage().asAndroidBitmap()
            val colors = (1..8).flatMap { y -> (1..8).map { x -> image.getPixel(image.width * x / 10, image.height * y / 10) } }.distinct()
            assertTrue("A real cover, not a neutral frame", colors.size > 8)
            val file = File(context.getExternalFilesDir(null), "qa0201/${items[index].id}.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

private fun android.content.Context.coilImageLoader() = coil.Coil.imageLoader(this)
