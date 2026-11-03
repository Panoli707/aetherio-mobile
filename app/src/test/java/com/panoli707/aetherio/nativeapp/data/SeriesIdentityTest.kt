package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class SeriesIdentityTest {
    private val root = MediaItem("220542", MediaType.SERIES, "Los diarios de la boticaria", isAnime = true)
    private val third = MediaItem("195516", MediaType.SERIES, "Kusuriya no Hitorigoto 3rd Season", CatalogSource.ANILIST, isAnime = true)

    @Test fun bundledFactsUnifyAllBoticariaPartsByExplicitIdsNotByTitle() {
        assertEquals(listOf(1, 2, 3, 4), SeriesIdentity.index.parts(root).map { it.season })
        val unified = SeriesIdentity.item(third)
        assertEquals(root.key, unified.key)
        assertEquals(3, unified.seasonHint)
        assertTrue(unified.seriesNumbering)
        assertEquals(1, SeriesIdentity.items(listOf(root, third)).size)
        assertEquals("ANILIST:tv:999999999", SeriesIdentity.item(third.copy(id = "999999999")).key)
    }

    @Test fun oldContinuousAndSeparateSeasonProgressBecomeTheSameEpisodeIdempotently() {
        val old = WatchTarget(root, 1, 49)
        val separate = WatchTarget(third, 1, 1)
        val mapped = SeriesIdentity.index.target(old)
        val other = SeriesIdentity.index.target(separate)
        assertEquals(mapped.key, other.key)
        assertEquals(3, mapped.season)
        assertEquals(1, mapped.episode)
        assertEquals(mapped, SeriesIdentity.index.target(mapped))
        assertEquals(other, SeriesIdentity.index.target(other))
        val entries = SeriesIdentity.progress(listOf(WatchProgress(old, 12345, 1440000, updatedAt = 1),
            WatchProgress(separate, 54321, 1440000, updatedAt = 2)))
        assertEquals(1, entries.size)
        assertEquals(54321L, entries.single().positionMs)
        val roundTrip = WatchProgressCodec.decode(WatchProgressCodec.encode(entries))
        assertEquals(entries, SeriesIdentity.progress(roundTrip))
        assertEquals(1, SeriesIdentity.externalTarget(mapped).season)
        assertEquals(49, SeriesIdentity.externalTarget(mapped).episode)
    }

    @Test fun ambiguousOrIncompleteCrosswalksAreRejectedAndMoviesNeverMerge() {
        val a = SeriesPart("1", "100", 1, 0, 1)
        assertTrue(SeriesIndex(listOf(a, a.copy(animeId = "2"))).groups.isEmpty())
        assertTrue(SeriesIndex(listOf(a, a.copy(tmdbId = "101"))).groups.isEmpty())
        assertTrue(SeriesIndex(listOf(a.copy(offset = 24))).groups.isEmpty())
        assertEquals(third.copy(type = MediaType.MOVIE), SeriesIdentity.item(third.copy(type = MediaType.MOVIE)))
        assertEquals(WatchTarget(third, 2, 1), SeriesIdentity.index.target(WatchTarget(third, 2, 1)))
    }

    @Test fun splitCoursWithExplicitOffsetsKeepTheirSeasonAndEpisodeNumbers() {
        val index = SeriesIndex(listOf(SeriesPart("1", "100", 1, 0, 1), SeriesPart("2", "100", 1, 12, 1),
            SeriesPart("3", "100", 1, 24, 2)))
        val partTwo = MediaItem("2", MediaType.SERIES, "Parte dos", CatalogSource.ANILIST)
        assertEquals(13, index.target(WatchTarget(partTwo, 1, 1)).episode)
        assertEquals(1, index.target(WatchTarget(partTwo, 1, 1)).season)
    }
}
