package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class SeriesNavigationTest {
    @Test fun steelBallRunSplitDeliveryOpensGeneralJojoWithoutInventingEpisodeOffsets() {
        val delivery = MediaItem("210482", MediaType.SERIES, "Steel Ball Run - 2nd & 3rd STAGE", CatalogSource.ANILIST, isAnime = true)
        val target = SeriesIdentity.detailItem(delivery)
        assertEquals(CatalogSource.TMDB, target.source)
        assertEquals("45790", target.id)
        assertEquals(6, target.seasonHint)
        assertEquals(delivery, SeriesIdentity.item(delivery))
        assertEquals(WatchTarget(delivery, 1, 1), SeriesIdentity.index.target(WatchTarget(delivery, 1, 1)))
    }
    @Test fun unknownSeriesAndMoviesAreNotMergedByTitle() {
        val unknown = MediaItem("999999999", MediaType.SERIES, "Steel Ball Run", CatalogSource.ANILIST)
        assertEquals(unknown, SeriesIdentity.detailItem(unknown))
        val movie = unknown.copy(id = "210482", type = MediaType.MOVIE)
        assertEquals(movie, SeriesIdentity.detailItem(movie))
    }
}
