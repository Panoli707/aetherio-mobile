package com.panoli707.aetherio.nativeapp.player

import org.junit.Assert.*
import org.junit.Test

class TrailerEmbedTest {
    @Test fun onlyYoutubeVideoIdsCanEnterTheEmbeddedDocument() {
        assertEquals("M7lc1UVf-VE", TrailerEmbed.validId("M7lc1UVf-VE"))
        listOf(null, "", "https://youtube.com/watch?v=M7lc1UVf-VE", "../file.mp4", "'onload=alert(1)", "abcdefghijk/")
            .forEach { assertNull(TrailerEmbed.validId(it)) }
        assertTrue(runCatching { TrailerEmbed.document("</script><script>alert(1)</script>") }.isFailure)
    }

    @Test fun officialPlayerIdentifiesTheInstalledApplicationAndKeepsBrandingAndControls() {
        val html = TrailerEmbed.document("M7lc1UVf-VE")
        assertTrue(html.contains("https://www.youtube.com/iframe_api"))
        assertTrue(html.contains("origin:'${TrailerEmbed.ORIGIN}'"))
        assertTrue(html.contains("strict-origin-when-cross-origin"))
        assertTrue(html.contains("controls:1,fs:1"))
        assertTrue(html.contains("pauseTrailer"))
        assertTrue(html.contains("visibilitychange"))
        assertFalse(html.contains("modestbranding"))
        assertFalse(html.contains("get_video_info"))
    }

    @Test fun externalNavigationAndUnsafeSchemesNeverReplaceTheLocalPlayerPage() {
        assertTrue(TrailerEmbed.allowedMainFrame(TrailerEmbed.BASE_URL))
        assertTrue(TrailerEmbed.allowedMainFrame("about:blank"))
        listOf("intent://youtube.com", "javascript:alert(1)", "file:///etc/passwd", "https://www.youtube.com/watch?v=M7lc1UVf-VE",
            "https://evil.example/", "${TrailerEmbed.BASE_URL}redirect").forEach { assertFalse(TrailerEmbed.allowedMainFrame(it)) }
    }

    @Test fun restrictedAndUnavailableVideosHaveAnInAppErrorInsteadOfAnExternalRedirect() {
        assertTrue(TrailerEmbed.error(101).contains("propietario"))
        assertEquals(TrailerEmbed.error(101), TrailerEmbed.error(150))
        assertTrue(TrailerEmbed.error(100).contains("privado"))
        assertTrue(TrailerEmbed.error(153).contains("verificar"))
    }
}
