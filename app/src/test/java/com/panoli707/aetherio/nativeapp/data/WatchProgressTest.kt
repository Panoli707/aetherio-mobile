package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WatchProgressTest {
    private val anime = MediaItem("20", MediaType.SERIES, "Anime de prueba", isAnime = true, imdbId = "tt0409591")
    private val target = WatchTarget(anime, 2, 3, "Episodio de prueba", "https://example.org/still.jpg")

    @Test fun episodeIdentityDoesNotDependOnProviderImdbOrMetadata() {
        assertEquals(target.key, target.copy(item = anime.copy(imdbId = null, title = "Otro idioma")).key)
        assertNotEquals(target.key, target.copy(season = 1).key)
        assertNotEquals(target.key, target.copy(episode = 4).key)
        assertTrue(WatchProgressRules.valid(target))
        assertFalse(WatchProgressRules.valid(target.copy(episode = null)))
        assertFalse(WatchProgressRules.valid(target.copy(season = -1)))
    }

    @Test fun preservesExactSecondsAndMovesBackwardWhenTheViewerSeeksBackward() {
        val first = WatchProgressRules.sample(null, target, 754321, 1440000, false, 1)!!
        assertEquals(754321L, first.resumePositionMs)
        val second = WatchProgressRules.sample(first, target, 60123, 1440000, false, 2)!!
        assertEquals(60123L, second.resumePositionMs)
        assertFalse(second.watched)
    }

    @Test fun completionAt95PercentOrEndPreservesActualPositionButReplaysFromBeginning() {
        assertFalse(WatchProgressRules.sample(null, target, 94999, 100000, false, 1)!!.watched)
        val completed = WatchProgressRules.sample(null, target, 95000, 100000, false, 1)!!
        assertTrue(completed.watched)
        assertEquals(95000L, completed.positionMs)
        assertEquals(0L, completed.resumePositionMs)
        assertTrue(WatchProgressRules.sample(null, target, 90000, 100000, true, 1)!!.watched)
        assertTrue(WatchProgressRules.sample(completed, target, 1000, 100000, false, 2)!!.watched)
    }

    @Test fun invalidSamplesCannotReplacePreviouslySavedProgress() {
        listOf(-1L to 100L, 100L to -1L, 100L to 0L, 100L to Long.MAX_VALUE).forEach { (position, duration) ->
            assertNull(WatchProgressRules.sample(null, target, position, duration, false, 1))
        }
        assertNull(WatchProgressRules.sample(null, target, 0, 1000, false, 1))
        assertEquals(1000L, WatchProgressRules.sample(null, target, 10000, 1000, false, 1)!!.positionMs)
    }

    @Test fun continueWatchingShowsLatestEpisodePerTitleInNewestOrderAndNoCompletedMovies() {
        val movie = WatchTarget(MediaItem("30", MediaType.MOVIE, "Pelicula"))
        val entries = listOf(WatchProgress(target, 500, 10000, updatedAt = 1),
            WatchProgress(target.copy(episode = 4), 900, 10000, updatedAt = 3),
            WatchProgress(movie, 100, 10000, updatedAt = 2))
        assertEquals(listOf(4, null), WatchProgressRules.continuing(entries).map { it.target.episode })
        assertEquals(listOf(movie), WatchProgressRules.continuing(entries + WatchProgress(target.copy(episode = 5), 10000, 10000, true, 4)).map { it.target })
    }

    @Test fun codecRoundTripsEpisodeAndMovieWithoutInventingWatchedTime() {
        val manual = WatchProgress(target, watched = true, updatedAt = 1)
        val movie = WatchProgress(WatchTarget(MediaItem("123", MediaType.MOVIE, "Pelicula")), 123456, 600000, updatedAt = 2)
        assertEquals(listOf(movie, manual), WatchProgressCodec.decode(WatchProgressCodec.encode(listOf(manual, movie))))
        assertEquals(target, WatchProgressCodec.decodeTarget(WatchProgressCodec.encodeTarget(target)))
    }

    @Test fun corruptEntriesAreIsolatedAndUnknownSchemasAreIgnored() {
        val valid = WatchProgressCodec.encode(listOf(WatchProgress(target, 60000, 120000, updatedAt = 1)))
        val json = JSONObject(valid)
        json.getJSONArray("entries").put(JSONObject().put("duration", -1))
        assertEquals(1, WatchProgressCodec.decode(json.toString()).size)
        assertTrue(WatchProgressCodec.decode(json.put("schema", 2).toString()).isEmpty())
        assertTrue(WatchProgressCodec.decode("not json").isEmpty())
        assertNull(WatchProgressCodec.decodeTarget("{}"))
    }

    @Test fun formattedPositionIncludesHoursAndSeconds() {
        assertEquals("0:00", watchTime(-1))
        assertEquals("12:34", watchTime(754321))
        assertEquals("1:02:03", watchTime(3723000))
    }

    @Test fun nextPlaybackSkipsWatchedEpisodesAndAdvancesToTheNextSeason() {
        val episodes = listOf(EpisodeItem(1, 1, "Uno"), EpisodeItem(2, 1, "Dos"))
        val seasons = listOf(SeasonInfo(1, "T1", 2), SeasonInfo(2, "T2", 3))
        assertEquals(1 to 1, WatchProgressRules.nextEpisode(1, episodes, seasons, emptySet()))
        assertEquals(1 to 2, WatchProgressRules.nextEpisode(1, episodes, seasons, setOf(1 to 1)))
        assertEquals(2 to 1, WatchProgressRules.nextEpisode(1, episodes, seasons, setOf(1 to 1, 1 to 2)))
        assertEquals(1 to 1, WatchProgressRules.nextEpisode(1, episodes, listOf(seasons.first()), setOf(1 to 1, 1 to 2)))
    }
}
