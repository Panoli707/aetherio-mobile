package com.panoli707.aetherio.nativeapp.player

import org.junit.Assert.*
import org.junit.Test

class YoutubeTrailerResolverTest {
    @Test fun onlyHttpsGoogleVideoMediaUrlsAreAccepted() {
        val url = "https://rr1---sn-test.googlevideo.com/videoplayback?expire=123&sig=abc"
        assertEquals(url, YoutubeTrailerResolver.validateUrl(url))
        for (bad in listOf("http://rr1.googlevideo.com/video", "https://googlevideo.com.evil.org/video",
            "https://youtube.com/watch?v=M7lc1UVf-VE", "https://127.0.0.1/video", "https://user@rr1.googlevideo.com/video",
            "https://rr1.googlevideo.com:444/video", "https://rr1.googlevideo.com/video#fragment"))
            assertThrows(IllegalArgumentException::class.java) { YoutubeTrailerResolver.validateUrl(bad) }
    }
    @Test fun parserRequiresAudioAndVideoAndDropsCookiesAndArbitraryHeaders() {
        val info = """{"url":"https://rr1.googlevideo.com/videoplayback","vcodec":"avc1","acodec":"mp4a","ext":"mp4","protocol":"m3u8_native",
            "http_headers":{"User-Agent":"Aetherio test","Cookie":"private","Authorization":"secret","X-Other":"value"}}"""
        val stream = YoutubeTrailerResolver.parse(info)
        assertEquals("application/x-mpegURL", stream.mime)
        assertEquals(mapOf("User-Agent" to "Aetherio test"), stream.headers)
        assertThrows(IllegalArgumentException::class.java) { YoutubeTrailerResolver.parse(info.replace("mp4a", "none")) }
        assertThrows(IllegalArgumentException::class.java) { YoutubeTrailerResolver.parse(info.replace("avc1", "none")) }
    }
}
