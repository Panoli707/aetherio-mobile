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
}
