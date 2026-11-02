package com.panoli707.aetherio.nativeapp.data

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class DesktopSourcesTest {
    private val expectedProviders = listOf(
        DesktopSourceProvider("penguplay", "PenguPlay", "https://trkll.aetherio.workers.dev/api/penguplay/manifest.json"),
        DesktopSourceProvider("hdhub", "HdHub", "https://hdhub.thevolecitor.qzz.io/eyJ0b3Jib3giOiJ1bnNldCIsInF1YWxpdGllcyI6IjIxNjBwLDEwODBwLDcyMHAiLCJzb3J0IjoiZGVzYyIsImNvbnRlbnQiOiJsYXRpbiIsImNhdGFsb2dzIjoiIn0/manifest.json"),
        DesktopSourceProvider("animes", "AnimES", "https://pigamer37.alwaysdata.net/onAirCatalogs=animeflv%2Canimeav1%2Chenaojara%2Ctioanime%2Canimejara%2Cjkanime/manifest.json"),
        DesktopSourceProvider("deepflix", "DeepFlix", "https://mg.homelabx.qzz.io/manifest.json")
    )
    private val movie = SourceQuery(MediaItem("movie", MediaType.MOVIE, "Synthetic movie", imdbId = "tt0137523"))
    private val series = SourceQuery(
        MediaItem("tt0903747", MediaType.SERIES, "Synthetic series", source = CatalogSource.CINEMETA),
        season = 2, episode = 3
    )

    private class Fixture(body: (Request) -> String = {
        if (it.url.pathSegments.last() == "manifest.json") MANIFEST else STREAMS
    }) : Closeable {
        val requests = CopyOnWriteArrayList<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertTrue(request.url.isHttps)
                assertTrue(request.url.host in setOf(
                    "trkll.aetherio.workers.dev", "hdhub.thevolecitor.qzz.io",
                    "pigamer37.alwaysdata.net", "mg.homelabx.qzz.io"
                ))
                assertEquals("GET", request.method)
                requests.add(request)
                // An application interceptor returns every response before DNS, TLS, or sockets.
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(body(request).toResponseBody("application/json".toMediaType())).build()
            }.build()
        val repository = DesktopSourceRepository(SourceRepository(client))

        override fun close() {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    @Test fun providersMatchTheFourPublicDesktopConfigurationsAndValidateAsManifestUrls() {
        assertEquals(expectedProviders, DesktopSources.providers)
        assertEquals(setOf("penguplay", "hdhub", "animes", "deepflix"), DesktopSources.defaultIds)
        DesktopSources.providers.forEach { provider ->
            assertEquals(provider.manifestUrl, SourceProtocol.manifestUrl(provider.manifestUrl).toString())
        }
    }

    @Test fun constructionIsLazyAndEachManifestIsConfiguredOnceAcrossMovieAndExplicitSeriesQueries() = runBlocking {
        Fixture().use { fixture ->
            assertTrue(fixture.requests.isEmpty())
            expectedProviders.forEach { provider ->
                listOf(movie, series, movie, series).forEach { query ->
                    val result = fixture.repository.sources(provider.id, query)
                    assertEquals("https://video.example/bunny.mp4", result.sources.single().url)
                    assertEquals("Synthetic public provider", result.sources.single().addonName)
                    assertEquals(0, result.unsupportedCount)
                }
                assertEquals(1, fixture.requests.count { it.url.toString() == provider.manifestUrl })
            }
            assertEquals(20, fixture.requests.size)
        }
    }

    @Test fun streamRequestsPreservePublicConfigurationPathsIncludingHdHubAndEncodedAnimES() = runBlocking {
        Fixture().use { fixture ->
            expectedProviders.forEach { provider ->
                val start = fixture.requests.size
                fixture.repository.sources(provider.id, movie)
                fixture.repository.sources(provider.id, series)
                val base = provider.manifestUrl.removeSuffix("manifest.json")
                assertEquals(listOf(
                    provider.manifestUrl,
                    "${base}stream/movie/tt0137523.json",
                    "${base}stream/series/tt0903747:2:3.json"
                ), fixture.requests.drop(start).map { it.url.toString() })
            }
        }
    }

    @Test fun failedManifestIsRetriedRatherThanCachedAndUnknownProviderNeverFetches() = runBlocking {
        var failManifest = true
        Fixture { request ->
            if (request.url.pathSegments.last() == "manifest.json") {
                if (failManifest) "{}" else MANIFEST
            } else STREAMS
        }.use { fixture ->
            suspend fun rejectUnknown() {
                val count = fixture.requests.size
                val error = try {
                    fixture.repository.sources("unknown-provider", movie)
                    throw AssertionError("Expected an invalid provider failure")
                } catch (error: IOException) { error }
                assertEquals("Proveedor de escritorio no valido", error.message)
                assertEquals(count, fixture.requests.size)
            }

            rejectUnknown()
            val error = try {
                fixture.repository.sources("deepflix", movie)
                throw AssertionError("Expected a failed manifest")
            } catch (error: IOException) { error }
            assertEquals("No se pudo consultar la fuente", error.message)
            assertEquals(1, fixture.requests.size)
            failManifest = false
            assertEquals("https://video.example/bunny.mp4", fixture.repository.sources("deepflix", movie).sources.single().url)
            assertEquals("https://video.example/bunny.mp4", fixture.repository.sources("deepflix", series).sources.single().url)
            assertEquals(2, fixture.requests.count { it.url.toString() == expectedProviders.last().manifestUrl })
            assertEquals(4, fixture.requests.size)
            rejectUnknown()
        }
    }

    private companion object {
        const val MANIFEST = """{"id":"org.example.public","name":"Synthetic public provider","types":["movie","series"],"resources":[{"name":"stream","types":["movie","series"],"idPrefixes":["tt"]}],"idPrefixes":["tt"]}"""
        const val STREAMS = """{"streams":[{"name":"Synthetic stream","url":"https://video.example/bunny.mp4"}]}"""
    }
}
