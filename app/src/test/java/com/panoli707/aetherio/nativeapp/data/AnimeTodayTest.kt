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

    @Test fun onlyEventsAlreadyAiredDuringTheJapaneseDayAreIncluded() {
        assertEquals(start, AnimeToday.start(now))
        val events = listOf(AnimeAiringEpisode(item, 1, start - 1), AnimeAiringEpisode(item, 2, start),
            AnimeAiringEpisode(item, 3, start + 36000), AnimeAiringEpisode(item, 4, start + 36001),
            AnimeAiringEpisode(item, 5, start + 86400))
        assertEquals(listOf(3, 2), AnimeToday.released(events + events, now).map { it.episode })
        assertTrue(AnimeToday.released(events.take(3), (start + 86400) * 1000).isEmpty())
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

    @Test fun failedRefreshKeepsTodaysDataButNeverYesterdayAndDoesNotInventEpisodes() = runTest {
        var time = now
        var state = AnimeTodayState(start, listOf(AnimeAiringEpisode(item, 3, start + 1)), loaded = true)
        val loader = AnimeTodayLoader(this, { throw IllegalStateException() }, { state = it(state) }, { time })
        loader.load(); runCurrent()
        assertEquals(1, state.entries.size); assertNotNull(state.error); assertFalse(state.loading)
        time += 86400000
        loader.load(); runCurrent()
        assertTrue(state.entries.isEmpty()); assertNotNull(state.error)
    }

    @Test fun responseCrossingMidnightCannotPublishYesterdaysEpisodesAsToday() = runTest {
        var time = now
        var state = AnimeTodayState()
        val pending = CompletableDeferred<List<AnimeAiringEpisode>>()
        val loader = AnimeTodayLoader(this, { pending.await() }, { state = it(state) }, { time })
        loader.load(); runCurrent(); time += 86400000
        pending.complete(listOf(AnimeAiringEpisode(item, 3, start + 1))); runCurrent()
        assertTrue(state.entries.isEmpty()); assertFalse(state.loaded)
        assertEquals(start + 86400, state.dayStart)
    }
}
