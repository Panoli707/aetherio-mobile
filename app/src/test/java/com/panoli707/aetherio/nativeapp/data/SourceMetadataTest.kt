package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class SourceMetadataTest {
    private val hash = "0123456789abcdef0123456789abcdef01234567"
    private fun source(fields: String) = SourceProtocol.streams("""{"streams":[$fields]}""", ProviderHub.config).sources.single()

    @Test fun torrentioPayloadHasReadableMetadataAndNotOnlyRawDescription() {
        val stream = source("""{"name":"Torrentio\n1080p","title":"Batch title\nEpisode.mkv\n👤 47 💾 1.37 GB ⚙️ NyaaSi\n[ENG][SPA-LA][SPA][GER]","infoHash":"$hash","behaviorHints":{"filename":"Episode.1080p.AAC.H.264.mkv"}}""")
        assertEquals("Torrentio", stream.providerName)
        assertEquals("Episode.1080p.AAC.H.264.mkv", stream.display.title)
        assertEquals(47, stream.display.seeders)
        assertEquals("1.37 GB", stream.display.size)
        assertEquals("NyaaSi", stream.display.indexer)
        assertEquals(listOf("Latino", "Castellano", "Inglés", "Alemán"), stream.display.languages)
        assertTrue(stream.display.badges.containsAll(listOf("FHD", "AVC", "AAC")))
    }

    @Test fun structuredMetadataWinsAndOptionalMalformedFieldsCannotRejectAPlayableStream() {
        val valid = source("""{"url":"https://cdn.example/video.m3u8","size":1471026299,"duration":1440,"languages":["es","en"],"technicalMetadata":{"resolutionHeight":1080,"videoCodec":"HEVC","audioCodec":"FLAC"},"seeders":0}""")
        assertEquals("1.37 GB", valid.display.size)
        assertEquals(24, valid.display.durationMinutes)
        assertEquals(listOf("Español", "Inglés"), valid.display.languages)
        assertEquals(0, valid.display.seeders)
        assertTrue(valid.display.badges.containsAll(listOf("FHD", "HEVC", "FLAC")))
        val malformed = source("""{"url":"https://cdn.example/video.mp4","size":{},"duration":-100,"languages":[null,12],"seeders":1e50,"logo":"javascript:alert(1)","quality":[]}""")
        assertTrue(malformed.playable)
        assertNull(malformed.display.size)
        assertNull(malformed.display.durationMinutes)
        assertNull(malformed.display.seeders)
        assertTrue(malformed.display.languages.isEmpty())
    }

    @Test fun badgesNeverTreatProviderNameAsProofOfAV1OrHlsAsResolution() {
        val stream = source("""{"name":"AnimeAV1","url":"https://cdn.example/video.m3u8","title":"Sub: Castellano"}""")
        assertFalse(stream.display.badges.contains("AV1"))
        assertFalse(stream.display.badges.contains("FHD"))
        assertEquals("HLS", stream.format.label)
        assertTrue(stream.display.languages.isEmpty())
    }

    @Test fun intermediateAnd4kResolutionsAreNormalizedWithoutAffectingPlayback() {
        listOf("2160p" to 2160, "4K" to 2160, "UHD" to 2160, "1440p" to 1440, "1080p" to 1080,
            "720p" to 720, "480p" to 480, "360p" to 360).forEach { (label, height) ->
            val stream = source("""{"url":"https://cdn.example/video.mp4","quality":"$label"}""")
            assertEquals(height, stream.display.resolutionHeight)
            assertEquals("https://cdn.example/video.mp4", stream.url)
        }
    }

    @Test fun badOptionalSubtitlesAreOmittedWithoutDroppingValidManagedVideo() {
        val stream = source("""{"url":"https://cdn.example/video.mp4","subtitles":[{"lang":"es"},{"url":"javascript:alert(1)"},{"url":"https://cdn.example/es.vtt","language":"es"}]}""")
        assertTrue(stream.playable)
        assertEquals(1, stream.subtitles.size)
        assertEquals("es", stream.subtitles.single().lang)
    }

    @Test fun hiddenAggregatorGroupsDifferentServersAndQualitiesByTheRealProvider() {
        val config = ProviderHub.config.copy(payloadAttribution = true, hubManaged = true)
        val streams = SourceProtocol.streams("""{"streams":[
            {"name":"PenguPlay 4K • 4KHDHub → R2","description":"Source: 4KHDHub → R2","url":"https://cdn.example/1.mp4","behaviorHints":{"filename":"Episode.mkv|^|synthetic-pad"}},
            {"name":"PenguPlay 1080p • 4KHDHub → PixelDrain","description":"Source: 4KHDHub → PixelDrain","url":"https://cdn.example/2.mp4"}]}""", config).sources
        assertEquals(listOf("4KHDHub", "4KHDHub"), streams.map { it.providerName })
        assertEquals(streams[0].providerKey, streams[1].providerKey)
        assertEquals("Episode.mkv", streams[0].display.title)
    }

    @Test fun mergingPrefersAttributedAddonButKeepsDifferentFilesAndCredentials() {
        val hub = source("""{"infoHash":"$hash","fileIdx":0}""")
        val managed = hub.copy(addonId = "managed", addonName = "New Provider", display = SourceDisplay(logoUrl = "https://cdn.example/logo.png"))
        assertEquals(listOf(managed), SourceMetadata.merge(listOf(hub), listOf(managed)))
        assertEquals(2, SourceMetadata.merge(listOf(hub), listOf(managed.copy(fileIdx = 1))).size)
        val http = source("""{"url":"https://cdn.example/video.mp4"}""")
        assertEquals(2, SourceMetadata.merge(listOf(http), listOf(http.copy(headers = mapOf("Referer" to "https://example.org")))).size)
    }
}
