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

/** The two reported titles, with real season metadata, images and logos. Emulator-only and opt-in. */
@RunWith(AndroidJUnit4::class)
class DetailArtworkLiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dragonBallSeasonCoverDoesNotGetASecondSeriesLogo() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualLogoArtwork") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AetherioRepository()
        val detail = runBlocking { repository.detail(MediaItem("62715", MediaType.SERIES, "Dragon Ball Super")) }
        val item = runBlocking { repository.seasonContent(detail.item, 1).item }
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { HeroArtwork(item, true, 500.dp, fullImage = true) } } }
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1
        }
        val file = File(context.filesDir, "artwork-qa/dragon-ball-season-one.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onNodeWithTag("heroArtwork", true).captureToImage().asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("Seasonal poster should not acquire a duplicate main logo",
            compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().isEmpty() ||
                ArtworkImages.headers(item, true).first().kind == ArtworkImages.Kind.PORTRAIT)
    }

    @Test fun reportedSeriesHavePortraitCoversAndAnIntegratedFadedTitle() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualLogoArtwork") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = AetherioRepository()
        val items = runBlocking {
            listOf(listOf("Sasaki and Peeps", "Sasaki to Pii-chan", "Sasaki y Peeps"),
                listOf("The Laid-Off Cheat-Granting Mage", "Cheat-Granting Mage", "Tsuihou sareta Cheat")).mapIndexed { index, queries ->
                var found: MediaItem? = null
                for (query in queries) {
                    found = repository.search(query).firstOrNull { it.type == MediaType.SERIES &&
                        (index != 0 || it.title.contains("Peeps", true) || it.title.contains("Pii", true)) }
                    if (found != null) break
                }
                val detail = repository.detail(SeriesIdentity.item(requireNotNull(found) { "Reported series was not found" }))
                val preferred = if (index == 0) 2 else 1
                val number = detail.seasons.firstOrNull { it.number == preferred }?.number ?: detail.selectedSeason
                if (number != null) repository.seasonContent(detail.item, number).item else detail.item
            }
        }
        val selected = mutableStateOf(items.first())
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { HeroArtwork(selected.value, true, 500.dp, fullImage = true) } } }
        items.forEachIndexed { index, item ->
            compose.runOnIdle { selected.value = item }
            compose.waitUntil(60_000) {
                compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1 &&
                    compose.onAllNodes(hasTestTag("detailTitle") and hasStateDescription("Cargando logo"), true).fetchSemanticsNodes().isEmpty()
            }
            val photo = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
            val title = if (compose.onAllNodesWithTag("detailTitle", true).fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithTag("detailTitle", true).getUnclippedBoundsInRoot() else null
            assertEquals(240f, (photo.right - photo.left).value, 1f)
            assertTrue("The reported anime needs a real portrait, not a horizontal strip", photo.bottom - photo.top > 240.dp)
            if (title != null) assertTrue("The title must be integrated into the end of the artwork", title.top < photo.bottom && title.bottom > photo.bottom)
            else {
                assertTrue("Only a printed cover may replace the overlay", ArtworkImages.headers(item, true)
                    .any { it.titlePresence == ArtworkTitlePresence.EMBEDDED })
                compose.onNodeWithContentDescription(item.title, true).assertExists()
            }
            if (title != null && compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().isNotEmpty())
                assertEquals("A decoded logo must keep a compact title area", 112f, (title.bottom - title.top).value, 1f)
            if (title != null) assertTrue("The title must never be suppressed by an unclassified poster",
                compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText(item.title).fetchSemanticsNodes().isNotEmpty())
            val image = compose.onNodeWithTag("heroArtwork", true).captureToImage()
            val file = File(context.filesDir, "artwork-qa/reported-title-$index.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
