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
import coil.imageLoader

/** Current provider artwork is sampled explicitly; no claim of perfect OCR over the entire catalog. */
@RunWith(AndroidJUnit4::class)
class ReportedVisualLiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dailyFeedUsesRealAiredEventsAndCanRenderTheirEpisodeNumbers() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualReportedVisuals") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val now = System.currentTimeMillis()
        val entries = AnimeSeasonRepository().today(now)
        assertEquals(entries, AnimeToday.released(entries, now))
        assertTrue(entries.all { it.item.isAnime && it.episode > 0 })
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Warm only the visible sample so the exported reference shows decoded provider covers.
        for (entry in entries.take(8)) entry.item.posterUrl?.let { url ->
            context.imageLoader.execute(coil.request.ImageRequest.Builder(context).data(url).size(600, 900).build())
        }
        compose.setContent { AetherioTheme { androidx.compose.material3.Surface(color = Ink) {
            AiringAnimeSection(AnimeTodayState(AnimeToday.start(now), entries, loaded = true), {}, {}, {}, now)
        } } }
        compose.onNodeWithText("Emitidos recientemente · Japón (JST)", useUnmergedTree = true).assertExists()
        android.util.Log.i("RecentAnimeQA", "Complete recent feed: ${entries.size} distinct anime")
        entries.firstOrNull()?.let { entry ->
            assertTrue(compose.onAllNodesWithText("Ep. ${entry.episode} / ${entry.item.episodeCount ?: "—"}", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty())
        }
        val file = File(context.filesDir, "anime-logos-0163/recent-row.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onNodeWithTag("airingAnimeSection").captureToImage().asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun reportedShowsAlwaysHaveOnePortraitAFadeAndAnIndependentTitle() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualReportedVisuals") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val repository = AetherioRepository()
        val items = runBlocking {
            val known = listOf(MediaItem("1421", MediaType.SERIES, "Modern Family"),
                MediaItem("1100", MediaType.SERIES, "How I Met Your Mother"),
                MediaItem("220542", MediaType.SERIES, "The Apothecary Diaries", isAnime = true))
            val sword = repository.search("Reincarnated as a Sword").first { it.type == MediaType.SERIES }
            (known + sword.copy(isAnime = true)).map { selected ->
                val detail = repository.detail(SeriesIdentity.item(selected))
                detail.selectedSeason?.let { repository.seasonContent(detail.item, it).item } ?: detail.item
            }
        }
        val item = mutableStateOf(items.first())
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { HeroArtwork(item.value, true, 700.dp, fullImage = true) } } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (selected in items) {
            compose.runOnIdle { item.value = selected }
            compose.waitUntil(90_000) {
                compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1 &&
                    compose.onAllNodes(hasTestTag("detailTitle") and hasStateDescription("Cargando logo"), true).fetchSemanticsNodes().isEmpty()
            }
            compose.onAllNodesWithTag("heroImage", true).assertCountEquals(1)
            compose.onNodeWithTag("detailHeroFade").assertExists()
            compose.onNodeWithTag("detailTitle").assertExists()
            val photo = compose.onNodeWithTag("heroImage").getUnclippedBoundsInRoot()
            assertEquals(240f, (photo.right - photo.left).value, 1f)
            assertTrue(photo.bottom - photo.top > 240.dp)
            assertTrue(compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().size == 1 ||
                compose.onAllNodesWithText(selected.title).fetchSemanticsNodes().isNotEmpty())
            val file = File(context.filesDir, "reported-visuals-0161/${selected.id}.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { compose.onNodeWithTag("heroArtwork", true).captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
