package com.panoli707.aetherio.nativeapp.ui

import android.os.Build
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** An explicit twelve-series sample, not a claim to have scanned the entire catalogue. */
@RunWith(AndroidJUnit4::class)
class ArtworkCatalogLiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun twelveRealSeriesUseVerticalCoversAndOneContinuousHeader() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualArtworkCatalog") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AetherioRepository()
        val items = runBlocking {
            val reported = listOf("Sasaki and Peeps", "The Laid-Off Cheat-Granting Mage").map { query ->
                repository.search(query).first { it.type == MediaType.SERIES &&
                    (if (query.startsWith("Sasaki")) it.title.contains("Peeps", true) || it.title.contains("Pii", true)
                    else it.title.contains("Cheat", true) || it.title.contains("Fuyo", true)) }
            }
            val known = listOf("220542", "1396", "1399", "66732", "37854", "1429", "85937", "95479", "94605", "60059")
                .map { MediaItem(it, MediaType.SERIES, "Fixture $it") }
            (reported + known).map { selected ->
                val detail = repository.detail(SeriesIdentity.item(selected))
                detail.selectedSeason?.let { repository.seasonContent(detail.item, it).item } ?: detail.item
            }
        }
        assertEquals(12, items.map { it.key }.distinct().size)
        val current = mutableStateOf(items.first())
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) {
            HeroArtwork(current.value, true, 600.dp, fullImage = true)
        } } }
        val report = mutableListOf<String>()
        items.forEach { item ->
            compose.runOnIdle { current.value = item }
            compose.waitUntil(60_000) {
                compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1 &&
                    compose.onAllNodes(hasTestTag("detailTitle") and hasStateDescription("Título preparado"), true).fetchSemanticsNodes().size == 1
            }
            val photo = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
            val title = compose.onNodeWithTag("detailTitle", true).getUnclippedBoundsInRoot()
            assertEquals(item.title, 240f, (photo.right - photo.left).value, 1f)
            assertTrue("${item.title} must not resolve to a horizontal strip", photo.bottom - photo.top > 240.dp)
            assertTrue("${item.title} title must bridge the image and the background", title.top < photo.bottom && title.bottom > photo.bottom)
            compose.onAllNodesWithTag("heroImage", true).assertCountEquals(1)
            val file = File(context.filesDir, "artwork-qa/catalog-${item.id}.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { compose.onNodeWithTag("heroArtwork", true).captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            report.add("${item.key}\t${item.title}\t${photo.right - photo.left} x ${photo.bottom - photo.top}\t" +
                ArtworkImages.headers(item, true).joinToString { "${it.kind}:${it.url}" })
        }
        File(context.filesDir, "artwork-qa/catalog-audit.tsv").writeText(report.joinToString("\n"))
    }
}
