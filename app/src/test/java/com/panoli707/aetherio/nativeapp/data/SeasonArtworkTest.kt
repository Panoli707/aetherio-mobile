package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class SeasonArtworkTest {
    private val base = MediaItem("1396", MediaType.SERIES, "Series", posterUrl = "general-poster",
        backdropUrl = "general-backdrop", portraitArtworkUrl = "general-clean", logoUrl = "general-logo")

    @Test fun seasonPosterTakesPriorityOverTheGeneralTextlessArtworkInBothOrientations() {
        val season = SeasonArtwork.apply(base, 2, poster = "season-two")
        assertNull(season.portraitArtworkUrl)
        assertNull(season.backdropUrl)
        assertEquals("season-two", ArtworkImages.headers(season, true).first().url)
        assertEquals("season-two", ArtworkImages.headers(season, false).first().url)
        assertEquals(base.key, season.key)
        assertEquals(2, season.seasonHint)
    }

    @Test fun aSeasonWithoutArtworkFallsBackToTheGeneralSeriesNotThePreviousSeason() {
        val first = SeasonArtwork.apply(base, 1, poster = "season-one")
        val detail = MediaDetail(first, artworkBase = base)
        val second = SeasonArtwork.apply(detail.artworkBase!!, 2)
        assertEquals(base.copy(seasonHint = 2), second)
        assertNotEquals(first.posterUrl, second.posterUrl)
    }

    @Test fun mappedAnimeArtworkReplacesTheWholeArtworkSetWithoutChangingTheSeriesOrProgressKey() {
        val season = SeasonArtwork.apply(base.copy(isAnime = true, seriesNumbering = true), 3,
            poster = "anime-three", backdrop = "anime-banner", logo = "anime-logo", portrait = "anime-clean")
        assertEquals("anime-clean", ArtworkImages.headers(season, true).first().url)
        assertEquals("anime-banner", ArtworkImages.headers(season, false).first().url)
        assertEquals("anime-logo", season.logoUrl)
        assertEquals(WatchTarget(base, 3, 1).key, WatchTarget(season, 3, 1).key)
    }

    @Test fun sharedTmdbPortraitNeverHidesAMappedSeasonPoster() {
        val one = SeasonArtwork.mapped(base, 1, base.copy(posterUrl = "anilist-one", portraitArtworkUrl = null),
            true, "shared-poster", "shared-logo", "shared-clean")
        val two = SeasonArtwork.mapped(base, 2, base.copy(posterUrl = "anilist-two", portraitArtworkUrl = null),
            true, "shared-poster", "shared-logo", "shared-clean")
        assertNull(one.portraitArtworkUrl); assertNull(two.portraitArtworkUrl)
        assertEquals("anilist-one", ArtworkImages.headers(one, true).first().url)
        assertEquals("anilist-two", ArtworkImages.headers(two, true).first().url)
    }

    @Test fun aMissingMappedEntryUsesTheSeriesBaseInsteadOfClaimingSharedArtBelongsToThisSeason() {
        assertEquals(base.copy(seasonHint = 3), SeasonArtwork.mapped(base, 3, null, true,
            "shared-poster", "shared-logo", "shared-clean"))
        val unique = SeasonArtwork.mapped(base, 3, null, false, "unique-poster", "unique-logo", "unique-clean")
        assertEquals("unique-clean", unique.portraitArtworkUrl)
    }
}
