package com.panoli707.aetherio.nativeapp.downloads

import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Test

class DownloadCodecTest {
    @Test fun commonHlsHttpAliasesMustNotBecomeAProgressiveTextFileDownload() {
        listOf("application/vnd.apple.mpegurl", "application/x-mpegURL", "Application/Vnd.Apple.MpegUrl; charset=utf-8", "audio/mpegurl").forEach {
            assertEquals(com.panoli707.aetherio.nativeapp.player.VideoFormat.HLS.mimeType, adaptiveDownloadMime(it))
        }
        assertEquals(com.panoli707.aetherio.nativeapp.player.VideoFormat.DASH.mimeType, adaptiveDownloadMime("application/dash+xml"))
        assertNull(adaptiveDownloadMime("video/mp4"))
    }
    @Test fun taskIndexContainsMetadataButNeverSourceUrlsOrHeaders() {
        val task = MediaDownload("12345678-1234-1234-1234-123456789012", WatchTarget(MediaItem("1", MediaType.SERIES, "Test"), 2, 4),
            "Provider", DownloadStage.PAUSED, 40, 100)
        val raw = DownloadCodec.encode(listOf(task))
        assertEquals(listOf(task), DownloadCodec.decode(raw))
        listOf("Authorization", "Cookie", "magnet:", "access_token", "signedUrl").forEach { assertFalse(raw.contains(it)) }
    }
    @Test fun corruptedIndexCannotCreateAnArbitraryFilesystemPath() {
        assertTrue(DownloadCodec.decode("""[{"id":"../../other","target":"{}"}]""").isEmpty())
        assertTrue(DownloadCodec.decode("broken").isEmpty())
    }
    @Test fun unknownLengthsAreNotShownAsZeroPercentOrDivideByZero() {
        val task = MediaDownload("12345678-1234-1234-1234-123456789012", WatchTarget(MediaItem("1", MediaType.MOVIE, "Movie")), "Provider")
        assertNull(task.fraction)
        assertEquals(0.5f, task.copy(bytes = 50, total = 100).fraction)
        assertEquals(1f, task.copy(bytes = 500, total = 100).fraction)
    }
}
