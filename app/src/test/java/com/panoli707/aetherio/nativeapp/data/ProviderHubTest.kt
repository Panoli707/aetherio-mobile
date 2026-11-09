package com.panoli707.aetherio.nativeapp.data

import com.panoli707.aetherio.nativeapp.player.PlaybackHeaders
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderHubTest {
    private val movie = SourceQuery(MediaItem("5311514", MediaType.MOVIE, "Movie", imdbId = "tt5311514"))
    private val series = SourceQuery(MediaItem("1429", MediaType.SERIES, "Series", imdbId = "tt2560140"), 1, 1)
    private val hash = "0123456789abcdef0123456789abcdef01234567"

    @Test fun cleanAndRepeatedInitializationHaveExactlyOneHubAndBundledDesktopDefaults() {
        val fresh = ProviderHub.withDefault(emptyList())
        assertEquals(listOf(ProviderHub.config), fresh)
        assertEquals(fresh, ProviderHub.withDefault(ProviderHub.withDefault(fresh)))
        assertEquals(DesktopSources.knownIds, DesktopSources.defaultIds)
        assertEquals(fresh, AppState().addons)
        assertEquals(DesktopSources.knownIds, AppState().enabledDesktopSources)
        assertEquals("com.aetherio.hub", fresh.single().manifestId)
    }

    @Test fun migrationPreservesAllExplicitCustomAddonsEvenIfTheyMatchOldBundledUrls() {
        val custom = SourceProtocol.manifest("""{"id":"custom","name":"User","resources":["stream"],"types":["movie"]}""",
            SourceProtocol.manifestUrl(DesktopSources.providers.first().manifestUrl!!))
        val other = custom.copy(id = SourceProtocol.addonId(SourceProtocol.manifestUrl("https://example.org/private/manifest.json")),
            manifestUrl = "https://example.org/private/manifest.json")
        val migrated = ProviderHub.withDefault(listOf(custom, ProviderHub.config, other))
        assertEquals(listOf(ProviderHub.config, custom, other), migrated)
        assertEquals(migrated, ProviderHub.withDefault(migrated))
    }

    @Test fun endpointsUseFullImdbAndExplicitEpisodeAndNeverTreatOtherIdsAsImdb() {
        assertEquals("${ProviderHub.BASE}/stream/movie/tt5311514.json", SourceProtocol.endpoint(ProviderHub.config, movie).toString())
        assertEquals("${ProviderHub.BASE}/stream/series/tt2560140:1:1.json", SourceProtocol.endpoint(ProviderHub.config, series).toString())
        for (source in listOf(CatalogSource.TMDB, CatalogSource.ANILIST)) {
            assertNull(SourceProtocol.endpoint(ProviderHub.config, movie.copy(item = movie.item.copy(imdbId = null, source = source))))
        }
        assertNull(SourceProtocol.endpoint(ProviderHub.config, series.copy(season = null)))
        assertNull(SourceProtocol.endpoint(ProviderHub.config, series.copy(episode = 0)))
        assertNull(SourceProtocol.endpoint(ProviderHub.config, movie.copy(item = movie.item.copy(imdbId = "5311514"))))
    }

    @Test fun httpTorrentAndMagnetKeepFileSelectionTrackersSubtitlesHeadersAndHints() {
        val raw = """{"streams":[
          {"url":"https://cdn.example/video.m3u8","headers":{"User-Agent":"Player"},"subtitles":[{"id":"es","url":"https://cdn.example/es.vtt","lang":"es"}],
           "behaviorHints":{"notWebReady":true,"bingeGroup":"group","proxyHeaders":{"request":{"Referer":"https://example.org/watch","Authorization":"Bearer fixture"},"response":{"X-Test":"kept"}}}},
          {"infoHash":"$hash","fileIdx":7,"sources":["tracker:udp://tracker.example:80/announce","dht:$hash"],"behaviorHints":{"filename":"episode.mkv"}},
          {"url":"magnet:?xt=urn:btih:$hash&tr=udp%3A%2F%2Ftracker.example%3A80%2Fannounce","fileIdx":2}] }"""
        val result = SourceProtocol.streams(raw, ProviderHub.config)
        assertEquals(0, result.unsupportedCount)
        val http = result.sources.first()
        assertTrue(http.playable)
        assertEquals("Player", http.headers["User-Agent"])
        assertEquals("https://example.org/watch", http.headers["Referer"])
        assertEquals("Bearer fixture", http.headers["Authorization"])
        assertEquals("text/vtt", http.subtitles.single().mimeType)
        assertEquals("es", http.subtitles.single().lang)
        assertEquals("kept", JSONObject(http.behaviorHints).getJSONObject("proxyHeaders").getJSONObject("response").getString("X-Test"))
        assertTrue(JSONObject(http.rawStream).has("subtitles"))
        val torrent = result.sources[1]
        assertEquals(hash, torrent.infoHash)
        assertEquals(7, torrent.fileIdx)
        assertEquals(listOf("tracker:udp://tracker.example:80/announce", "dht:$hash"), torrent.sources)
        assertEquals("episode.mkv", JSONObject(torrent.behaviorHints).getString("filename"))
        assertTrue(torrent.playable)
        assertEquals(2, result.sources[2].fileIdx)
        assertTrue(result.sources[2].url.startsWith("magnet:"))
        for (source in result.sources.drop(1)) {
            assertTrue(source.isTorrent)
            assertThrows(IllegalArgumentException::class.java) { SourcePlayback.httpUrl(source) }
        }
        assertEquals(http.url, SourcePlayback.httpUrl(http))
    }

    @Test fun malformedEncryptedUrlsAreNeverRepairedAndMalformedTorrentShapesAreRejected() {
        val result = SourceProtocol.streams("""{"streams":[{"url":"U2FsdGVkX1ciphertext"},{"url":"cdn.example/movie.mp4"},
          {"url":"javascript:alert(1)"},{"url":"https://cdn.example/video bad.mp4"},{"url":"https://user:pass@cdn.example/a"},
          {"infoHash":"bad"},{"infoHash":"$hash","fileIdx":-1},{"infoHash":"$hash","fileIdx":1.5},{"url":"magnet:invalid"}]}""", ProviderHub.config)
        assertTrue(result.sources.isEmpty())
        assertEquals(9, result.unsupportedCount)
        assertThrows(IllegalArgumentException::class.java) { SourceProtocol.streams("{}", ProviderHub.config) }
        assertThrows(IllegalArgumentException::class.java) { SourceProtocol.streams("{" , ProviderHub.config) }
        assertThrows(IllegalArgumentException::class.java) { SourceProtocol.streams("""{"streams":null}""", ProviderHub.config) }
    }

    @Test fun onlyTheHubCanSupplyValidatedProviderCredentialsAndControlsRemainForbidden() {
        val headers = mapOf("Authorization" to "Bearer fixture", "Cookie" to "session=fixture", "Accept" to "*/*")
        assertEquals(headers, PlaybackHeaders.validate(headers, true))
        assertThrows(IllegalArgumentException::class.java) { PlaybackHeaders.validate(headers) }
        assertThrows(IllegalArgumentException::class.java) { PlaybackHeaders.validate(mapOf("Host" to "evil.example"), true) }
        assertThrows(IllegalArgumentException::class.java) { PlaybackHeaders.validate(mapOf("Cookie" to "a=b\r\nX-Evil: yes"), true) }
    }

    @Test fun noScriptsDirectoryOrChildProviderFanOutAndEmptyResponsesAreNotCached() = runBlocking {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request().url.encodedPath)
            assertNull(chain.request().header("Authorization"))
            val body = if (requests.size == 1) """{"streams":[]}""" else """{"streams":[{"url":"https://cdn.example/video.mp4"}]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body(body.toResponseBody()).build()
        }.build()
        try {
            val repository = SourceRepository(client, attempts = 2)
            assertTrue(repository.sources(ProviderHub.config, movie).sources.isEmpty())
            assertTrue(repository.sources(ProviderHub.config, series).sources.single().playable)
            assertEquals(listOf("/stream/movie/tt5311514.json", "/stream/series/tt2560140:1:1.json"), requests)
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun retryIsBoundedAndOnlyTransientFailuresAreRetried() = runBlocking {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request().url.encodedPath)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (requests.size == 1) 503 else 200)
                .message("Fixture").body("""{"streams":[]}""".toResponseBody()).build()
        }.build()
        try {
            assertTrue(SourceRepository(client, attempts = 2).sources(ProviderHub.config, movie).sources.isEmpty())
            assertEquals(2, requests.size)
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun scraperFieldsInManifestAreInformationalAndNeverFetched() = runBlocking {
        val paths = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            paths.add(chain.request().url.encodedPath)
            val body = if (paths.size == 1) """{"id":"com.aetherio.hub","name":"Aetherio Provider Hub","resources":["stream"],"types":["movie","series"],"idPrefixes":["tt"],
                "scrapers":[{"id":"torrentio","filename":"/scripts/torrentio.js","enabled":true}]}""" else """{"streams":[]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body(body.toResponseBody()).build()
        }.build()
        try {
            val repository = SourceRepository(client)
            val configured = repository.configure("${ProviderHub.BASE}/manifest.json")
            assertEquals(ProviderHub.config, configured)
            repository.sources(configured, movie)
            assertEquals(listOf("/manifest.json", "/stream/movie/tt5311514.json"), paths)
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }
}
