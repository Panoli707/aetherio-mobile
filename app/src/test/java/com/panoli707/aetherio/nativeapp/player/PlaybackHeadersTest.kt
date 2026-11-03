package com.panoli707.aetherio.nativeapp.player

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class PlaybackHeadersTest {
    @Test fun providerCredentialsStayOnTheVideoOriginIncludingForSegmentsKeysAndSubtitles() {
        val headers = mapOf("Authorization" to "Bearer fixture", "Cookie" to "session=fixture",
            "Referer" to "https://player.example/watch", "Origin" to "https://player.example", "User-Agent" to "Fixture")
        val media = "https://cdn.example/video.m3u8"
        assertEquals(headers, PlaybackHeaders.forDestination(headers, media, "https://cdn.example:443/segment.ts".toHttpUrl()))
        val public = headers - setOf("Authorization", "Cookie")
        listOf("https://other.example/es.vtt", "https://cdn.example:8443/key", "https://sub.cdn.example/segment.ts").forEach {
            assertEquals(public, PlaybackHeaders.forDestination(headers, media, it.toHttpUrl()))
        }
        assertEquals(public, PlaybackHeaders.forDestination(headers, null, media.toHttpUrl()))
        assertEquals(mapOf("User-Agent" to "Fixture"),
            PlaybackHeaders.forDestination(headers, "http://shegu.net/video.mp4", "http://shegu.net/segment".toHttpUrl()))
        assertEquals(emptyMap<String, String>(), PlaybackHeaders.forDestination(
            mapOf("authorization" to "fixture", "COOKIE" to "fixture", "REFERER" to "fixture", "origin" to "fixture"), media,
            "http://shegu.net/video.mp4".toHttpUrl()))
    }

    @Test fun acceptsZillaFetchMetadataButRejectsUnrecognizedValues() {
        val headers = mapOf("Referer" to "https://player.zilla-networks.com/",
            "Origin" to "https://player.zilla-networks.com", "User-Agent" to "Player/1.0",
            "Sec-Fetch-Dest" to "empty", "Sec-Fetch-Mode" to "cors", "Sec-Fetch-Site" to "same-origin")
        assertEquals(headers, PlaybackHeaders.validate(headers))
        listOf("Sec-Fetch-Dest", "Sec-Fetch-Mode", "Sec-Fetch-Site").forEach { key ->
            assertRejected(headers + (key to "untrusted"))
            assertRejected(headers + (key to "cors\r\nCookie: test-value"))
        }
        assertRejected(headers + ("Authorization" to "test-value"))
    }

    @Test fun acceptsOnlyCanonicalAllowedKeysAndPreservesValidValues() {
        val headers = linkedMapOf(
            "rEfErEr" to "https://www.example.org/watch/episode%20one?quality=hd&signature=a%2Fb",
            "ORIGIN" to "https://example.org:8443/",
            "user-agent" to "Example Player/1.0 (Android)"
        )
        assertEquals(mapOf("Referer" to headers["rEfErEr"], "Origin" to headers["ORIGIN"],
            "User-Agent" to headers["user-agent"]), PlaybackHeaders.validate(headers))
        assertEquals(emptyMap<String, String>(), PlaybackHeaders.validate(emptyMap()))
        assertEquals("https://example.org", PlaybackHeaders.validate(mapOf("Origin" to "https://example.org"))["Origin"])
    }

    @Test fun rejectsInjectionControlsBidiNonAsciiAndInvalidValueLengths() {
        val forbidden = (0..31).toList() + (127..159).toList() + listOf(0x202e, 0x2066, 0xe9)
        forbidden.forEach { code ->
            assertRejected(mapOf("User-Agent" to "Player${code.toChar()}example"))
        }
        assertRejected(mapOf("User-Agent" to ""))
        assertRejected(mapOf("User-Agent" to "x".repeat(4097)))
        assertEquals("x".repeat(4096), PlaybackHeaders.validate(mapOf("User-Agent" to "x".repeat(4096)))["User-Agent"])
        assertEquals("~", PlaybackHeaders.validate(mapOf("User-Agent" to "~"))["User-Agent"])
        assertRejected(mapOf("User-Agent\r\nCookie" to "test-value"))
    }

    @Test fun rejectsInvalidOriginsRatherThanReducingThemToOrigins() {
        listOf(
            "http://example.org", "null", "https://example.org/path", "https://example.org//",
            "https://example.org/../", "https://example.org/%2f", "https://example.org?",
            "https://example.org/?quality=hd", "https://example.org/#fragment",
            "https://user@example.org", "https://@example.org", "https://example.org:",
            "https://example.org:0", "https://example.org:65536", " https://example.org",
            "https://example.org/%0a", "https://example.org\\other"
        ).forEach { assertRejected(mapOf("Origin" to it)) }
    }

    @Test fun rejectsNonPublicOrUnsafeReferersButAllowsPathsAndQueries() {
        listOf(
            "http://example.org/watch", "https://user@example.org/watch", "https://@example.org/watch",
            "https://example.org/watch#fragment", "https://example.org/watch?x=%0d%0aInjected",
            "https://example.org/bad%escape", "https://example.org/a b", "https://example.org:0/watch",
            "https://localhost/watch", "https://localhost./watch", "https://player.local/watch",
            "https://127.0.0.1/watch", "https://10.0.0.1/watch", "https://172.16.0.1/watch",
            "https://192.168.0.1/watch", "https://169.254.169.254/watch", "https://100.64.0.1/watch",
            "https://999.999.999.999/watch", "https://127.1/watch",
            "https://[::1]/watch", "https://[fc00::1]/watch", "https://[fe80::1]/watch"
        ).forEach { assertRejected(mapOf("Referer" to it)) }
        val value = "https://example.org/watch/episode?quality=hd&start=120"
        assertEquals(value, PlaybackHeaders.validate(mapOf("Referer" to value))["Referer"])
    }

    @Test fun rejectsCaseDuplicatesAndSecretOrSessionHeadersWithoutLeakingValues() {
        assertRejected(linkedMapOf("User-Agent" to "test-value", "user-agent" to "test-value"))
        listOf("Cookie", "Authorization", "Proxy-Authorization", "Host", "Set-Cookie",
            "X-Session-Id", "X-Api-Key", "Accept", "Referer ").forEach { key ->
            assertRejected(mapOf(key to "test-value"))
        }
        assertRejected(mapOf("Referer" to "https://example.org", "Origin" to "https://example.org",
            "User-Agent" to "test-value", "Cookie" to "test-value"))
    }

    private fun assertRejected(headers: Map<String, String>) {
        val error = assertThrows(IllegalArgumentException::class.java) { PlaybackHeaders.validate(headers) }
        assertEquals("Invalid playback headers", error.message)
        assertNull(error.cause)
        assertTrue(error.suppressed.isEmpty())
        assertFalse(error.toString().contains("example.org"))
        assertFalse(error.toString().contains("test-value"))
    }
}
