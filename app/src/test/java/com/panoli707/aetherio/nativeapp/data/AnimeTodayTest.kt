package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeTodayTest {
    private val start = 1793718000L // 2026-11-04 00:00 JST
    private val now = (start + 36000) * 1000
    private val item = MediaItem("1", MediaType.SERIES, "Anime", isAnime = true)

    @Test fun latestConfirmedEpisodeIncludesEarlierDaysButNeverFutureEvents() {
        assertEquals(start, AnimeToday.start(now))
        val events = listOf(AnimeAiringEpisode(item, 1, start - 1), AnimeAiringEpisode(item, 2, start),
            AnimeAiringEpisode(item, 3, start + 36000), AnimeAiringEpisode(item, 4, start + 36001),
            AnimeAiringEpisode(item, 5, start + 86400))
        assertEquals(listOf(3), AnimeToday.released(events + events, now).map { it.episode })
        assertEquals(listOf(3), AnimeToday.released(events.take(3), (start + 86400) * 1000).map { it.episode })
    }

    @Test fun allDistinctAnimeAreSelectedNewestFirstWithoutAnArtificialThirtyLimit() {
        val events = (1..45).map { id -> AnimeAiringEpisode(item.copy(id = "$id"), 1, start - id * 3600) }
        val result = AnimeToday.released((events + events + events.map { it.copy(episode = 2, airedAt = it.airedAt + 1) }).reversed(), now)
        assertEquals(45, result.size)
        assertEquals((1..45).map { "$it" }, result.map { it.item.id })
        assertTrue(result.all { it.episode == 2 })
        assertEquals(3, AnimeToday.released(events.take(3), now).size)
    }

    @Test fun relativeAgeUsesElapsedTimeNotCalendarDayOrDeviceTimezone() {
        for ((seconds, label) in listOf(0L to "Hace menos de 1 min", 60L to "Hace 1 min", 3600L to "Hace 1 h",
            46800L to "Hace 13 h", 86400L to "Hace 1 día", 172800L to "Hace 2 días")) {
            assertEquals(label, AnimeToday.relativeAge(now / 1000 - seconds, now))
        }
    }

    @Test fun thirtyDayWindowUsesJapanMidnightAndExcludesInvalidAndExpiredEvents() {
        val oldest = AnimeToday.historyStart(now)
        val events = listOf(AnimeAiringEpisode(item, 1, oldest),
            AnimeAiringEpisode(item.copy(id = "2"), 2, oldest - 1),
            AnimeAiringEpisode(item.copy(id = "3"), 0, start),
            AnimeAiringEpisode(item.copy(id = "4"), 4, now / 1000 + 1))
        assertEquals(listOf(events.first()), AnimeToday.released(events, now))
        assertTrue(AnimeToday.released(events.take(2), (start + 86400) * 1000).isEmpty())
    }

    @Test fun finalEpisodesAndOlderSeasonsAreNotFilteredOutButAdultAndInvalidEventsAre() {
        val event = JSONObject("""{"episode":24,"airingAt":$start,"media":{"id":1,"type":"ANIME","title":{"romaji":"Final"},"status":"FINISHED","seasonYear":2025,"season":"FALL"}}""")
        assertEquals(24, AnimeToday.episode(event)!!.episode)
        event.getJSONObject("media").put("isAdult", true)
        assertNull(AnimeToday.episode(event))
        event.getJSONObject("media").put("isAdult", false)
        event.put("episode", 0)
        assertNull(AnimeToday.episode(event))
    }

    @Test fun failedRefreshKeepsRecentDataAcrossMidnightButDropsExpiredHistory() = runTest {
        var time = now
        var state = AnimeTodayState(start, listOf(AnimeAiringEpisode(item, 3, start + 1)), loaded = true)
        val loader = AnimeTodayLoader(this, { throw IllegalStateException() }, { state = it(state) }, { time })
        loader.load(); runCurrent()
        assertEquals(1, state.entries.size); assertNotNull(state.error); assertFalse(state.loading)
        time += 86400000
        loader.load(); runCurrent()
        assertEquals(1, state.entries.size); assertNotNull(state.error)
        time += 30 * 86400000L
        loader.load(); runCurrent()
        assertTrue(state.entries.isEmpty()); assertNotNull(state.error)
    }

    @Test fun responseCrossingMidnightKeepsPreviousDayWithoutRelabellingItsTimestamp() = runTest {
        var time = now
        var state = AnimeTodayState()
        val pending = CompletableDeferred<List<AnimeAiringEpisode>>()
        val loader = AnimeTodayLoader(this, { pending.await() }, { state = it(state) }, { time })
        loader.load(); runCurrent(); time += 86400000
        pending.complete(listOf(AnimeAiringEpisode(item, 3, start + 1))); runCurrent()
        assertEquals(start + 1, state.entries.single().airedAt); assertTrue(state.loaded)
        assertEquals(start + 86400, state.dayStart)
    }
}
