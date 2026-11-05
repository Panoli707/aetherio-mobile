package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AnimeLogoUiTest {
    @get:Rule val compose = createComposeRule()
    private val ids = listOf("213068" to "323937", "200294" to "327765", "179876" to "306565", "205909" to "320909")

    @Test fun allFourBundledLogosDecodeForBothProvidersAndDoNotStickWhenIdentityChanges() {
        val item = mutableStateOf(MediaItem("213068", MediaType.SERIES, "Dandivine", CatalogSource.ANILIST))
        compose.setContent { AetherioTheme { androidx.compose.material3.Surface(color = Ink) { Column(Modifier.fillMaxWidth().padding(18.dp)) {
            DetailTitle(item.value, Modifier.fillMaxWidth())
        } } } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((aniList, tmdb) in ids) for (source in listOf(CatalogSource.ANILIST, CatalogSource.TMDB)) {
            val id = if (source == CatalogSource.ANILIST) aniList else tmdb
            compose.runOnIdle { item.value = item.value.copy(id = id, source = source) }
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasTestTag("detailTitle") and hasStateDescription("Cargando logo"), true).fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("detailLogo").assertIsDisplayed()
            compose.onNodeWithTag("detailTitleText").assertDoesNotExist()
            val file = File(context.filesDir, "anime-logos-0163/$id.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { compose.onNodeWithTag("detailTitle").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.runOnIdle { item.value = item.value.copy(id = "1", title = "Sin logo", source = CatalogSource.ANILIST) }
        compose.onNodeWithText("Sin logo").assertIsDisplayed()
        compose.onNodeWithTag("detailLogo").assertDoesNotExist()
    }

    @Test fun bundledResourcesHaveTransparentMarginsAndNonemptyArtwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((id, _) in ids) {
            val asset = AnimeLogoOverrides.logo(MediaItem(id, MediaType.SERIES, "Anime", CatalogSource.ANILIST))!!
                .removePrefix("file:///android_asset/")
            val bitmap = context.assets.open(asset).use { android.graphics.BitmapFactory.decodeStream(it) }!!
            assertTrue(bitmap.hasAlpha())
            assertEquals(0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
            assertTrue(bitmap.width >= 600 && bitmap.height > 100)
            bitmap.recycle()
        }
    }
}
