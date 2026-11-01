package com.panoli707.aetherio.nativeapp.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaUrlPolicyTest {
    @Test
    fun acceptsDirectFormatsHttpsVariantsPortsAndPunycode() {
        val urls = listOf(
            "https://example.com/video.mp4",
            "https://example.com/master.m3u8?token=sample%2Bvalue&quality=hd",
            "https://example.com/manifest.mpd",
            "HTTPS://EXAMPLE.COM/VIDEO.MP4",
            "https://example.com:443/video.mp4",
            "https://example.com:8443/live/index.m3u8",
            "https://example.com:1/manifest.mpd",
            "https://example.com:65535/video%20name.mp4",
            "https://xn--bcher-kva.example/video.mp4",
            "https://[2001:db8::1]:8443/video.mp4"
        )
        urls.forEach { assertEquals(it, MediaUrlPolicy.validate(it)) }
        assertEquals(urls.first(), MediaUrlPolicy.validate("  ${urls.first()}  "))
    }

    @Test
    fun rejectsAllC0AndC1ControlsBeforeTrimming() {
        val url = "https://example.com/video.mp4"
        val controls = (0..31).toList() + (127..159).toList()
        controls.forEach { code ->
            val control = code.toChar()
            assertRejected("$control$url")
            assertRejected("$url$control")
            assertRejected("https://example.com/vid${control}eo.mp4")
        }
    }

    @Test
    fun rejectsOtherSchemesMissingHostsAndMalformedUris() {
        listOf(
            "", "   ",
            "http://example.com/video.mp4",
            "file:///video.mp4",
            "content://example.com/video.mp4",
            "data:video/mp4;base64,AAAA",
            "javascript:alert(1)",
            "rtsp://example.com/live",
            "//example.com/video.mp4",
            "https:video.mp4",
            "https:///video.mp4",
            "https://exa mple.com/video.mp4",
            "https://example.com/invalid%escape.mp4",
            "https://example.com\\@other.example/video.mp4",
            "https://example.com%2f.other.example/video.mp4"
        ).forEach(::assertRejected)
    }

    @Test
    fun rejectsCredentialsIncludingEmptyAndEncodedUserInfo() {
        listOf(
            "https://user:password@example.com/video.mp4",
            "https://user@example.com/master.m3u8",
            "https://@example.com/manifest.mpd",
            "https://user%3Apassword@example.com/video.mp4",
            "https://example.com@other.example/video.mp4"
        ).forEach(::assertRejected)
    }

    @Test
    fun rejectsZeroOutOfRangeEmptyAndMalformedPorts() {
        listOf("0", "000", "65536", "2147483648", "-1", "abc", "443.0", "").forEach {
            assertRejected("https://example.com:$it/video.mp4")
        }
    }

    @Test
    fun enforcesLengthBoundaryAndDoesNotExposeInputOrCauses() {
        val prefix = "https://example.com/video.mp4?token="
        val maximum = prefix + "a".repeat(8192 - prefix.length)
        assertEquals(maximum, MediaUrlPolicy.validate(maximum))
        assertRejected(maximum + "a")
        assertRejected(" ".repeat(8193) + prefix)

        val secret = "private-token-123"
        val failure = assertRejected("https://user:$secret@example.com/video.mp4")
        assertFalse(failure.toString().contains(secret))
        assertFalse(failure.toString().contains("example.com"))
    }

    private fun assertRejected(value: String): IllegalArgumentException {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            MediaUrlPolicy.validate(value)
        }
        assertEquals("URL no válida. Usa una URL HTTPS directa válida.", failure.message)
        assertNull(failure.cause)
        return failure
    }
}
