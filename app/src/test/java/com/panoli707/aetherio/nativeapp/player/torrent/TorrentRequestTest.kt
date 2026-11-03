package com.panoli707.aetherio.nativeapp.player.torrent

import com.panoli707.aetherio.nativeapp.data.*
import com.panoli707.aetherio.nativeapp.player.MediaUrlPolicy
import org.junit.Assert.*
import org.junit.Test

class TorrentRequestTest {
    private val hash = "0123456789abcdef0123456789abcdef01234567"
    private fun source(info: String? = hash, url: String = "", sources: List<String> = emptyList()) =
        StreamSource("1", "hub", "Hub", "Video", "", url = url, infoHash = info, sources = sources)

    @Test fun base32HexAndCodecRoundTrip() {
        assertEquals("0".repeat(40), TorrentRequestParser.canonicalHash("A".repeat(32)))
        assertEquals("f".repeat(40), TorrentRequestParser.canonicalHash("7".repeat(32)))
        assertEquals(hash, TorrentRequestParser.canonicalHash(hash.uppercase()))
        val request = TorrentRequest(hash, 0, "folder/movie.mkv", listOf("udp://tracker.example:80/announce")).validated()
        assertEquals(request, TorrentRequest.decode(request.encode()))
        assertFalse(request.magnet().contains("tracker:"))
        assertThrows(IllegalArgumentException::class.java) { TorrentRequestParser.canonicalHash("1".repeat(32)) }
        assertThrows(IllegalArgumentException::class.java) { TorrentRequestParser.canonicalHash("a".repeat(64)) }
    }

    @Test fun magnetTrackersAndSourcesAreCombinedWithoutDhtOrArbitraryWebseeds() {
        val magnet = "magnet:?xt=urn:btih:$hash&tr=udp%3A%2F%2Ftracker.example%3A80&ws=https://untrusted.example/video"
        val request = TorrentRequestParser.fromSource(source(url = magnet, sources = listOf("tracker:udp://tracker.example:80", "dht:$hash")))
        assertEquals(listOf("udp://tracker.example:80"), request.trackers)
        assertFalse(request.magnet().contains("untrusted"))
        assertThrows(IllegalArgumentException::class.java) {
            TorrentRequestParser.fromSource(source(url = "magnet:?xt=urn:btih:${"f".repeat(40)}"))
        }
        assertThrows(IllegalArgumentException::class.java) { TorrentRequestParser.magnetHash("magnet:?xt=urn:btih:$hash&xt=urn:btih:$hash") }
    }

    @Test fun trackerValidationRejectsLocalAndMalformedDestinationsWithoutDns() {
        listOf("udp://127.0.0.1:80", "http://10.0.0.1/a", "udp://192.168.1.2:80", "udp://localhost:80",
            "udp://[::1]:80", "udp://[fc00::1]:80", "https://user:secret@tracker.example/a", "file:///tmp/a",
            "udp://127.1:80", "udp://0177.0.0.1:80", "http://tracker.example:0/a", "http://tracker.example/a#fragment",
            "http://tracker.example/%0a").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { TorrentRequestParser.validateTracker(it) }
        }
        assertEquals("udp://tracker.example:80/announce", TorrentRequestParser.validateTracker("udp://tracker.example:80/announce"))
    }

    @Test fun providerProtocolComparesCanonicalHashesAndPreservesHttpGuard() {
        val zero = "0".repeat(40)
        val result = SourceProtocol.streams("""{"streams":[{"infoHash":"${"A".repeat(32)}","url":"magnet:?xt=urn:btih:$zero"}]}""", ProviderHub.config)
        assertEquals(zero, result.sources.single().infoHash)
        assertTrue(result.sources.single().playable)
        assertThrows(IllegalArgumentException::class.java) { SourcePlayback.httpUrl(result.sources.single()) }
        assertThrows(IllegalArgumentException::class.java) { MediaUrlPolicy.validate("http://127.0.0.1:8000/stream/token") }
    }

    @Test fun requestLimitsAndFractionalIndicesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { TorrentRequest(hash, -1).validated() }
        assertThrows(IllegalArgumentException::class.java) { TorrentRequest(hash, filenameHint = "a\n.mkv").validated() }
        assertThrows(IllegalArgumentException::class.java) { TorrentRequest(hash, trackers = List(33) { "udp://tracker$it.example:80" }).validated() }
        assertThrows(IllegalArgumentException::class.java) {
            TorrentRequest.decode("""{"hash":"$hash","index":1.5,"trackers":[]}""")
        }
    }

    @Test fun fileSelectionNeverGuessesAnEpisodeInAPack() {
        val first = TorrentFile(0, "Show/E01.mkv", 100)
        val second = TorrentFile(1, "Show/E02.mkv", 200)
        val sample = TorrentFile(2, "sample.mkv", 20)
        assertNull(TorrentFileSelection.choose(listOf(first, second, sample), TorrentRequest(hash)))
        assertEquals(first, TorrentFileSelection.choose(listOf(first, sample), TorrentRequest(hash)))
        assertEquals(second, TorrentFileSelection.choose(listOf(first, second), TorrentRequest(hash, filenameHint = "E02.mkv")))
        assertEquals(second, TorrentFileSelection.choose(listOf(first, second), TorrentRequest(hash, 1)))
        assertNull(TorrentFileSelection.choose(listOf(first.copy(pathTruncated = true)), TorrentRequest(hash)))
        assertThrows(IllegalArgumentException::class.java) { TorrentFileSelection.choose(listOf(first), TorrentRequest(hash, 5)) }
        assertThrows(IllegalArgumentException::class.java) { TorrentFileSelection.choose(listOf(TorrentFile(0, "readme.txt", 10)), TorrentRequest(hash)) }
    }

    @Test fun routeCapabilityIsExactAndRevocable() {
        val token = "a".repeat(64)
        val url = "http://127.0.0.1:8765/stream/$token"
        val route = TorrentRoute(token, url, 0, 100)
        assertEquals(url, route.validate(url))
        listOf("$url?x=1", "$url#x", url.replace("127.0.0.1", "localhost"), url.replace(":8765", ":8766"),
            url.replace("/stream/", "/stream/../stream/"), url.replace("http:", "https:")).forEach {
            assertThrows(IllegalArgumentException::class.java) { route.validate(it) }
        }
        route.revoke()
        assertThrows(IllegalArgumentException::class.java) { route.validate(url) }
        assertEquals("TorrentRoute(redacted)", route.toString())
    }

    @Test fun cacheKeepsReserveAndDoesNotPretendProtectedBlocksAreAHardCap() {
        assertEquals(2L * 1024 * 1024 * 1024, TorrentCachePolicy.capacity(8L * 1024 * 1024 * 1024))
        assertEquals(100L * 1024 * 1024, TorrentCachePolicy.capacity(TorrentCachePolicy.RESERVE + 100L * 1024 * 1024))
        assertThrows(IllegalStateException::class.java) { TorrentCachePolicy.capacity(TorrentCachePolicy.RESERVE) }
    }
}
