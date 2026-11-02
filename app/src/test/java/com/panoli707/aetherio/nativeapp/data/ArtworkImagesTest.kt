package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class ArtworkImagesTest {
    @Test fun mainImagesUseOriginalTmdbResolutionWithoutRewritingUnrelatedOrSignedUrls() {
        assertEquals("https://image.tmdb.org/t/p/original/poster.jpg", ArtworkImages.highResolution("https://image.tmdb.org/t/p/w500/poster.jpg"))
        assertEquals("https://image.tmdb.org/t/p/original/backdrop.jpg", ArtworkImages.highResolution("https://image.tmdb.org/t/p/w1280/backdrop.jpg"))
        val unchanged = listOf("https://cdn.example/t/p/w500/poster.jpg", "https://image.tmdb.org/t/p/w500/poster.jpg?signature=123",
            "https://image.tmdb.org.evil.org/t/p/w500/image.jpg", "https://user@image.tmdb.org/t/p/w500/image.jpg",
            "http://image.tmdb.org/t/p/w500/image.jpg", "https://image.tmdb.org/t/p/original/image.jpg")
        unchanged.forEach { assertEquals(it, ArtworkImages.highResolution(it)) }
        assertNull(ArtworkImages.highResolution(null))
    }

    @Test fun portraitHeroDoesNotEnlargeACroppedLandscapeWhenAPosterIsAvailable() {
        val item = MediaItem("1", MediaType.MOVIE, "Title", posterUrl = "poster", backdropUrl = "backdrop", logoUrl = "logo")
        assertEquals("poster", ArtworkImages.hero(item, portrait = true))
        assertEquals("backdrop", ArtworkImages.hero(item, portrait = false))
        assertEquals("backdrop", ArtworkImages.hero(item.copy(posterUrl = null), portrait = true))
        assertEquals("poster", ArtworkImages.hero(item.copy(backdropUrl = null), portrait = false))
    }
}
