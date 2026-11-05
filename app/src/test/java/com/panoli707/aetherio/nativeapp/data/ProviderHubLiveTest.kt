package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in public metadata/stream listings only; never fetches media bytes, scripts or torrents. */
class ProviderHubLiveTest {
    @Test fun productionDirectoryLoadsAutomaticallyAndUsesPublicStreamContracts() = runBlocking {
        assumeTrue(System.getenv("AETHERIO_HUB_LIVE_TESTS") == "true")
        val client = OkHttpClient()
        try {
            val result = ProviderHubDirectoryRepository(SourceRepository(client, requestTimeoutMs = 12_000)).refresh()
            assertFalse("El directorio público no pudo validarse", result.stale)
            println("Hub directory: ${result.providers.size} administrados, ${result.providers.count { it.config != null }} compatibles con IMDb")
            val query = SourceQuery(MediaItem("tt5311514", MediaType.MOVIE, "Movie", CatalogSource.CINEMETA))
            // Directory entries are selected dynamically, including IDs unknown to the bundled adapters.
            for (provider in result.providers.filter { it.enabled && it.config != null }) {
                try {
                    val streams = SourceRepository(client, requestTimeoutMs = provider.timeoutMs, attempts = provider.attempts)
                        .sources(provider.config!!, query)
                    println("Hub addon ${provider.name}: retenidos=${streams.sources.size}, rechazados=${streams.unsupportedCount}; reproducción NO probada")
                } catch (_: Exception) { println("Hub addon ${provider.name}: no respondió o contrato de streams no válido; no se ocultan los otros proveedores") }
            }
        } finally { client.dispatcher.cancelAll(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun productionExamplesReturnProtocolResponsesWithoutClaimingDevicePlayback() = runBlocking {
        assumeTrue(System.getenv("AETHERIO_HUB_LIVE_TESTS") == "true")
        val client = OkHttpClient()
        try {
            val repository = SourceRepository(client, requestTimeoutMs = 45_000, attempts = 2)
            val manifest = repository.configure("${ProviderHub.BASE}/manifest.json")
            assertEquals(ProviderHub.MANIFEST_ID, manifest.manifestId)
            assertEquals(setOf(MediaType.MOVIE, MediaType.SERIES), manifest.types)
            val queries = listOf(SourceQuery(MediaItem("tt5311514", MediaType.MOVIE, "Movie", CatalogSource.CINEMETA)),
                SourceQuery(MediaItem("tt2560140", MediaType.SERIES, "Series", CatalogSource.CINEMETA), 1, 1))
            for (query in queries) {
                val result = repository.sources(ProviderHub.config, query)
                result.sources.filter { !it.isTorrent }.forEach { assertEquals(it.url, SourcePlayback.httpUrl(it)) }
                println("Provider Hub ${query.item.id}: respuesta válida; retenidos=${result.sources.size}, torrents=${result.sources.count { it.isTorrent }}, HTTP=${result.sources.count { !it.isTorrent }}, rechazados=${result.unsupportedCount}; reproducción en dispositivo NO probada")
            }
        } finally { client.dispatcher.cancelAll(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }
}
