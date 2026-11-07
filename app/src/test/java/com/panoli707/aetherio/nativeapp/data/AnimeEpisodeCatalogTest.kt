package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class AnimeEpisodeCatalogTest {
    private val parts = listOf(SeriesPart("186497", "283428", 1, 0, 1), SeriesPart("213805", "283428", 2, 0, 2))
    private val item = MediaItem("283428", MediaType.SERIES, "Rompiendo el hielo", imdbId = "tt39123061", seriesNumbering = true)
    private val tmdb = MediaDetail(item, seasons = listOf(SeasonInfo(1, "Temporada 1", 28)), selectedSeason = 1)
    private val imdb = MediaDetail(item.copy(source = CatalogSource.CINEMETA, id = "tt39123061"),
        seasons = listOf(SeasonInfo(1, "Temporada 1", 14), SeasonInfo(2, "Temporada 2", 14)))

    @Test fun reportedAnimeKeepsBothRealImdbSeasonsEvenWhenTmdbCombinesThem() {
        val detail = AnimeEpisodeCatalog.preferImdb(tmdb, imdb, parts)
        assertEquals(listOf(1, 2), detail.seasons.map { it.number })
        assertEquals(item.key, detail.item.key)
        assertEquals(item.posterUrl, detail.item.posterUrl)
        assertTrue(detail.item.imdbNumbering)
        assertEquals(2, AnimeEpisodeCatalog.preferImdb(tmdb.copy(item = item.copy(seasonHint = 2)), imdb, parts).selectedSeason)
    }
    @Test fun mismatchedIdentityIncompleteInventoryAndUnknownMappingsNeverReplaceTheCatalog() {
        assertEquals(tmdb, AnimeEpisodeCatalog.preferImdb(tmdb, imdb.copy(item = imdb.item.copy(id = "tt1234567")), parts))
        assertEquals(tmdb, AnimeEpisodeCatalog.preferImdb(tmdb, imdb.copy(seasons = listOf(imdb.seasons.first())), parts))
        assertEquals(tmdb, AnimeEpisodeCatalog.preferImdb(tmdb, imdb, emptyList()))
    }
    @Test fun dateConversionNeedsOneExactMatchNeverAnEpisodeNumberGuess() {
        val tmdbEpisode = EpisodeItem(15, 1, "Clouds and Rain", airedOn = "2026-10-01")
        val imdbEpisode = EpisodeItem(1, 2, "Clouds and Rain", airedOn = "2026-10-01")
        assertEquals(imdbEpisode, AnimeEpisodeCatalog.matchingEpisode(tmdbEpisode, listOf(imdbEpisode)))
        assertNull(AnimeEpisodeCatalog.matchingEpisode(tmdbEpisode, listOf(imdbEpisode, imdbEpisode.copy(number = 2))))
        assertNull(AnimeEpisodeCatalog.matchingEpisode(tmdbEpisode.copy(airedOn = ""), listOf(imdbEpisode)))
    }
    @Test fun factualCrosswalkResolvesBothAnimeEntriesToTheSameRealImdbWithoutNumericIdConversion() {
        for (id in listOf("186497", "213805")) {
            val resolved = SeriesIdentity.item(MediaItem(id, MediaType.SERIES, "Any title", CatalogSource.ANILIST))
            assertEquals("283428", resolved.id)
            assertEquals("tt39123061", resolved.imdbId)
        }
        assertNull(SeriesIdentity.item(MediaItem("999999999", MediaType.SERIES, item.title, CatalogSource.ANILIST)).imdbId)
    }
    @Test fun oldCombinedHistoryMigratesOnceAndPreservesSeparateSeasonTwoAndAniListProgress() {
        val old = WatchTarget(item.copy(imdbNumbering = false), 1, 15)
        val fixed = SeriesIdentity.index.target(old)
        assertEquals(2, fixed.season); assertEquals(1, fixed.episode)
        assertEquals(fixed, SeriesIdentity.index.target(fixed))
        val separate = SeriesIdentity.index.target(old.copy(season = 2, episode = 1))
        assertEquals(2, separate.season); assertEquals(1, separate.episode)
        assertEquals(separate, SeriesIdentity.index.target(separate))
        val anime = WatchTarget(MediaItem("213805", MediaType.SERIES, "Second entry", CatalogSource.ANILIST), 1, 1)
        val mapped = SeriesIdentity.index.target(anime)
        assertEquals(2, mapped.season); assertEquals(1, mapped.episode)
        assertEquals(213805 to 1, AnimeSyncRules.episode(mapped, SeriesIdentity.index))
        assertEquals(mapped, SeriesIdentity.index.target(mapped))
    }
}
