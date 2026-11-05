package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class ArtworkTextPlacementTest {
    private fun line(top: Float, bottom: Float) = ArtworkTextBox(.1f, top, .9f, bottom, 12)
    @Test fun topTitlesCanCoexistWithTheLogoButBottomAndCentralTextCannotOverlapIt() {
        assertEquals(ArtworkTitlePresence.TOP, ArtworkTextPlacement.classify(listOf(line(.05f, .25f))))
        assertEquals(ArtworkTitlePresence.BOTTOM, ArtworkTextPlacement.classify(listOf(line(.76f, .88f))))
        assertEquals(ArtworkTitlePresence.CENTER, ArtworkTextPlacement.classify(listOf(line(.4f, .6f))))
        assertEquals(ArtworkTitlePresence.BOTTOM, ArtworkTextPlacement.classify(listOf(line(.05f, .25f), line(.8f, .9f))))
        assertEquals(ArtworkTitlePresence.ABSENT, ArtworkTextPlacement.classify(emptyList()))
    }
    @Test fun oldAndUnknownClassificationNeverHidesTheTitleAndUsesTheNonOverlappingFallback() {
        for (presence in ArtworkTitlePresence.entries) {
            val header = ArtworkImages.Header("image", 2f / 3f, ArtworkImages.Kind.POSTER, presence)
            assertTrue(ArtworkImages.separateTitle(header, true))
            assertEquals(presence in setOf(ArtworkTitlePresence.TOP, ArtworkTitlePresence.ABSENT), ArtworkImages.overlayTitle(header))
        }
    }
    @Test fun discoveryCandidatesSurviveLegacyCacheButCanonicalPresentationClearsThem() {
        val base = MediaItem("1", MediaType.SERIES, "Title", portraitAlternatives = listOf("https://example.org/base.jpg"))
        assertEquals(base, CatalogJson.decode(CatalogJson.encode(listOf(base))).single())
        val season = CanonicalArtwork("Title", "https://example.org/clean.jpg").item(base.copy(seasonHint = 2))
        assertTrue(season.portraitAlternatives.isEmpty())
        assertEquals(ArtworkTitlePresence.ABSENT, season.posterTitlePresence)
    }
}
