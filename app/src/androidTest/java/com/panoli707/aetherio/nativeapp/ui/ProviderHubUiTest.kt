package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderHubUiTest {
    @get:Rule val compose = createComposeRule()
    private val query = SourceQuery(MediaItem("tt5311514", MediaType.MOVIE, "Fixture", CatalogSource.CINEMETA))

    @Test fun freshSourceSheetNeedsNoConfigurationAndOffersAnActualRetryForEmptyResults() {
        var retried = false
        compose.setContent { AetherioTheme {
            SourceSheet(AppState(addonsLoading = false), query, {}, { _, _ -> }, {}, { retried = true })
        } }
        compose.onNodeWithText("Configura tus proveedores").assertDoesNotExist()
        compose.onNodeWithText("Activa AnimeAV1").assertDoesNotExist()
        compose.onNodeWithText("Sin resultados").assertExists()
        compose.onNodeWithText("Reintentar búsqueda").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun torrentIsVisibleButCannotBeLaunchedAndHttpKeepsItsHeaders() {
        val result = SourceProtocol.streams("""{"streams":[{"name":"HTTP fixture","url":"https://cdn.example/video.mp4","headers":{"User-Agent":"Fixture"}},
          {"name":"Torrent fixture","infoHash":"0123456789abcdef0123456789abcdef01234567","fileIdx":3,"sources":["tracker:udp://tracker.example:80"]}]}""", ProviderHub.config)
        var played: StreamSource? = null
        compose.setContent { AetherioTheme {
            SourceSheet(AppState(addonsLoading = false, sources = result.sources), query, {}, { source, _ -> played = source }, {})
        } }
        compose.onNodeWithText("Requiere motor torrent").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertNull(played) }
        compose.onNodeWithText("Reproducir").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Fixture", played?.headers?.get("User-Agent")); assertFalse(played!!.isTorrent) }
    }
}
