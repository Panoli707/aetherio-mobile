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
import com.panoli707.aetherio.nativeapp.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LogoPlacementLiveTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realAnimeSeriesAndMovieLogosAreInsideTheirCoversInHomeAndDetail() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualLogoPlacement") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val repository = AetherioRepository()
        val items = runBlocking {
            listOf(MediaItem("1", MediaType.SERIES, "Cowboy Bebop", CatalogSource.ANILIST, isAnime = true),
                MediaItem("206401", MediaType.SERIES, "FX Fighter Kurumi-chan", CatalogSource.ANILIST, isAnime = true),
                MediaItem("159042", MediaType.SERIES, "Reincarnated as a Sword II", CatalogSource.ANILIST, isAnime = true),
                MediaItem("1396", MediaType.SERIES, "Breaking Bad"),
                MediaItem("129", MediaType.MOVIE, "El viaje de Chihiro", isAnime = true)).map { base ->
                val detail = repository.detail(base)
                detail.selectedSeason?.let { repository.seasonContent(detail.item, it).item } ?: detail.item
            }
        }
        val item = mutableStateOf(items.first())
        val detail = mutableStateOf(true)
        compose.setContent { AetherioTheme { Box(Modifier.width(240.dp)) {
            HeroArtwork(item.value, true, 700.dp, fullImage = detail.value)
        } } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (selected in items) for (full in listOf(true, false)) {
            compose.runOnIdle { item.value = selected; detail.value = full }
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("detailLogo", true).fetchSemanticsNodes().size == 1 &&
                compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().size == 1 }
            val photo = compose.onNodeWithTag("heroImage", true).getUnclippedBoundsInRoot()
            val logo = compose.onNodeWithTag("detailLogo", true).getUnclippedBoundsInRoot()
            val fade = compose.onNodeWithTag("detailHeroFade", true).getUnclippedBoundsInRoot()
            assertTrue("${selected.title}: logo must stay inside the lower edge", logo.top >= photo.top && logo.bottom < photo.bottom)
            assertEquals(8f, (photo.bottom - logo.bottom).value, .6f)
            assertTrue("The logo must not be pushed above the fade", logo.bottom > fade.top)
            assertEquals(photo.bottom, compose.onNodeWithTag("heroArtwork").getUnclippedBoundsInRoot().bottom)
            val file = File(context.filesDir, "logo-placement-${BuildConfig.VERSION_NAME}/${selected.id}-${if (full) "detail" else "home"}.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { compose.onNodeWithTag("heroArtwork").captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
