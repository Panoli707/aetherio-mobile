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

    @Test fun phoneHeadersPreferCleanPortraitsThenPostersAndOnlyThenLandscape() {
        MediaType.entries.forEach { type ->
            val item = MediaItem("1", type, "Title", posterUrl = "poster", backdropUrl = "backdrop", portraitArtworkUrl = "clean")
            val candidates = ArtworkImages.headers(item, portrait = true)
            assertEquals(listOf("clean", "poster", "backdrop"), candidates.map { it.url })
            assertEquals(2f / 3f, candidates.first().aspectRatio)
            assertEquals(ArtworkTitlePresence.UNKNOWN, candidates.first().titlePresence)
            assertEquals(ArtworkTitlePresence.UNKNOWN, candidates[1].titlePresence)
            assertTrue(ArtworkImages.separateTitle(candidates[1], detail = true))
            assertTrue(ArtworkImages.separateTitle(candidates[1], detail = false)) // Unknown posters still have an accessible title.
            assertEquals(listOf("backdrop", "clean", "poster"), ArtworkImages.headers(item, portrait = false).map { it.url })
            assertEquals("poster", ArtworkImages.headers(item.copy(portraitArtworkUrl = null), true).first().url)
            assertEquals(16f / 9f, ArtworkImages.headers(item.copy(portraitArtworkUrl = null, posterUrl = null), true).single().aspectRatio)
        }
    }

    @Test fun headerFallbacksIgnoreBlankImagesAndDuplicateUrls() {
        val item = MediaItem("1", MediaType.MOVIE, "Title", posterUrl = "", backdropUrl = " ")
        assertTrue(ArtworkImages.headers(item, true).isEmpty())
        assertEquals(1, ArtworkImages.headers(item.copy(posterUrl = "same", backdropUrl = "same"), true).size)
    }

    @Test fun onlyConfirmedEmbeddedTitlesCanHideTheIndependentDetailTitle() {
        val item = MediaItem("1", MediaType.SERIES, "Title", posterUrl = "poster")
        ArtworkTitlePresence.entries.forEach { presence ->
            val header = ArtworkImages.headers(item.copy(posterTitlePresence = presence), true).single()
            assertEquals(presence != ArtworkTitlePresence.EMBEDDED, ArtworkImages.separateTitle(header, true))
        }
        assertTrue(ArtworkImages.separateTitle(null, true))
        assertTrue(ArtworkImages.separateTitle(ArtworkImages.headers(item, true).single(), false))
        assertFalse(ArtworkImages.separateTitle(ArtworkImages.headers(item.copy(posterTitlePresence = ArtworkTitlePresence.EMBEDDED), true).single(), false))
    }

    @Test fun logoVariantsKeepProviderLanguageOrderAndNeverRewriteSignedOrForeignUrls() {
        val item = MediaItem("1", MediaType.SERIES, "Title", logoUrl = "https://image.tmdb.org/t/p/w500/es.png",
            logoAlternatives = listOf("https://cdn.example/logo.png?signature=x", "https://image.tmdb.org/t/p/w500/es.png"))
        assertEquals(listOf("https://image.tmdb.org/t/p/original/es.png", item.logoUrl,
            "https://cdn.example/logo.png?signature=x"), ArtworkImages.logos(item))
    }
}
