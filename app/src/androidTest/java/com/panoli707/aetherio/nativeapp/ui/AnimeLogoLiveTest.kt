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

/** Explicit opt-in, real first-season metadata; the artwork selection itself is unchanged. */
@RunWith(AndroidJUnit4::class)
class AnimeLogoLiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fourRequestedShowsKeepTheirRealCoversAndDisplayTheBundledLogo() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualAnimeLogos") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val repository = AetherioRepository()
        val items = runBlocking {
            listOf("323937" to "Dandivine", "327765" to "Chojo", "306565" to "Dark Machine", "320909" to "Firefly Wedding")
                .map { (id, name) ->
                    val detail = repository.detail(MediaItem(id, MediaType.SERIES, name, isAnime = true))
                    detail.selectedSeason?.let { repository.seasonContent(detail.item, it).item } ?: detail.item
                }
        }
        val item = mutableStateOf(items.first())
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) { HeroArtwork(item.value, true, 700.dp, fullImage = true) } } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (selected in items) {
            assertNotNull(AnimeLogoOverrides.logo(selected))
            compose.runOnIdle { item.value = selected }
            compose.waitUntil(90_000) {
                compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().size == 1 &&
                    compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1
            }
            compose.onNodeWithTag("detailHeroFade").assertExists()
            val file = File(context.filesDir, "anime-logos-0163/hero-${selected.id}.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { compose.onNodeWithTag("heroArtwork", true).captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
