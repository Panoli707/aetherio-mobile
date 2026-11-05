package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

/** Regression coverage for the replacement of season-scoped branding with a canonical pair. */
class SeasonArtworkTest {
    private val choice = CanonicalArtwork("Series", "https://cdn/cover.jpg", 2f / 3f, "https://cdn/name.png")
    @Test fun everySeasonUsesTheSamePairWithoutChangingProgressIdentityOrHints() {
        val base = MediaItem("1396", MediaType.SERIES, "Series", posterUrl = "old-poster", logoUrl = "old-logo")
        for (season in 1..5) {
            val item = base.copy(seasonHint = season, animeEntryId = "$season", portraitAlternatives = listOf("season-$season"))
            val result = choice.item(item)
            assertEquals(choice.cover, result.posterUrl)
            assertEquals(choice.cover, result.portraitArtworkUrl)
            assertNull(result.backdropUrl)
            assertEquals(choice.logo, result.logoUrl)
            assertTrue(result.portraitAlternatives.isEmpty()); assertTrue(result.logoAlternatives.isEmpty())
            assertEquals(item.seasonHint, result.seasonHint); assertEquals(item.animeEntryId, result.animeEntryId)
            assertEquals(WatchTarget(item, season, 1).key, WatchTarget(result, season, 1).key)
        }
    }
    @Test fun missingCanonicalLogoCannotReintroduceSeasonBrandingFromSavedMetadata() {
        val old = MediaItem("1", MediaType.MOVIE, "Series", logoUrl = "season", logoAlternatives = listOf("season-backup"))
        val item = choice.copy(logo = null).item(old)
        assertNull(item.logoUrl); assertTrue(item.logoAlternatives.isEmpty())
    }
}
