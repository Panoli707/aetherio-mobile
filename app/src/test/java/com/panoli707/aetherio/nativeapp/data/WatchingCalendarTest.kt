package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class WatchingCalendarTest {
    private val anime = MediaItem("900000001", MediaType.SERIES, "Prueba", CatalogSource.ANILIST, isAnime = true)
    private val request = WatchingRequest(WatchingCategory.ANIME, "2026-11-02", "2026-11-09")

    @Test fun rangesHandleLeapYearsYearChangesAndMondayWeeks() {
        assertEquals("2026-11-02", WatchingDates.request(WatchingCategory.ANIME, "2026-11-08", false).start)
        val leap = WatchingDates.request(WatchingCategory.ANIME, "2028-02-29", true)
        assertEquals(29, WatchingDates.days(leap.start, leap.end).size)
        assertEquals("2027-01-01", WatchingDates.shift("2026-12-01", 1, java.util.Calendar.MONTH))
        assertFalse(WatchingDates.valid("2026-02-29"))
        assertFalse(WatchingDates.valid("2026-01-01junk"))
        assertEquals("2026-11-09", WatchingDates.date(WatchingDates.millis("2026-11-09", AnimeSeason.japan), AnimeSeason.japan))
        assertEquals("2026-11-08", WatchingDates.date(WatchingDates.millis("2026-11-09", AnimeSeason.japan), TimeZone.getTimeZone("UTC")))
    }

    @Test(expected = IllegalArgumentException::class) fun oversizedRangesAreRejected() {
        WatchingRequest(WatchingCategory.ANIME, "2026-01-01", "2027-01-01")
    }

    @Test fun normalizationKeepsMultipleEpisodesButRejectsDuplicatesOtherCategoriesAndOutOfRange() {
        val a = WatchingEvent(anime, "2026-11-03", at = 2, episode = 1)
        val b = a.copy(at = 3, episode = 2)
        assertEquals(listOf(a, b), WatchingDates.normalize(listOf(b, a, a,
            a.copy(date = request.end), a.copy(item = anime.copy(isAnime = false))), request))
    }

    @Test fun dateOnlyMoviesNeverGetATimeAndAnimeIsNotMixedIntoMovies() {
        val movie = JSONObject("""{"id":1,"title":"Película","release_date":"2026-11-03","genre_ids":[18]}""")
        val event = requireNotNull(WatchingDates.movie(movie))
        assertNull(event.at); assertEquals(MediaType.MOVIE, event.item.type)
        assertNull(WatchingDates.movie(movie.put("release_date", "2026-02-30")))
        assertNull(WatchingDates.movie(movie.put("release_date", "2026-11-03").put("original_language", "ja").put("genre_ids", org.json.JSONArray("[16]"))))
    }

    @Test fun animeMoviesKeepTheirMovieIdentityInDetailsWithoutInventingASeason() {
        val json = JSONObject("""{"id":123,"format":"MOVIE","title":{"romaji":"Película anime"},"status":"FINISHED","episodes":1}""")
        val detail = AnimeSeasonProtocol.detail(json, 1793610000)
        assertEquals(MediaType.MOVIE, detail.item.type)
        assertTrue(detail.item.isAnime)
        assertTrue(detail.seasons.isEmpty()); assertTrue(detail.episodes.isEmpty()); assertNull(detail.selectedSeason)
    }

    @Test fun cacheRetryAndFailurePreserveOnlyTheMatchingRangesData() = runTest {
        var state = WatchingCalendarState()
        var calls = 0
        var fail = false
        val event = WatchingEvent(anime, "2026-11-03", episode = 1)
        val loader = WatchingCalendarLoader(this, { calls++; if (fail) error("offline"); WatchingPage(listOf(event)) }, { state = it }, { 1000 })
        loader.load(request); runCurrent()
        assertEquals(listOf(event), state.events)
        loader.load(request); runCurrent(); assertEquals(1, calls)
        fail = true; loader.load(request, true); runCurrent()
        assertNotNull(state.error); assertEquals(listOf(event), state.events)
        val other = request.copy(start = "2026-11-09", end = "2026-11-16")
        loader.load(other); runCurrent(); assertTrue(state.events.isEmpty()); assertNotNull(state.error)
        fail = false; loader.load(other, true); runCurrent(); assertNull(state.error); assertTrue(state.loaded)
    }

    @Test fun cancelledResponsesCannotOverwriteANewerRange() = runTest {
        var state = WatchingCalendarState()
        val pending = CompletableDeferred<WatchingPage>()
        val loader = WatchingCalendarLoader(this, { range ->
            if (range == request) withContext(NonCancellable) { pending.await() } else WatchingPage(emptyList())
        }, { state = it })
        loader.load(request); runCurrent()
        val next = request.copy(start = "2026-11-09", end = "2026-11-16")
        loader.load(next); runCurrent()
        pending.complete(WatchingPage(listOf(WatchingEvent(anime, "2026-11-03")))); runCurrent()
        assertEquals(next, state.request); assertTrue(state.events.isEmpty()); assertFalse(state.loading)
    }

    @Test fun additionalPagesMergeWithoutDuplicatesAndRetryTheSamePageAfterFailure() = runTest {
        var state = WatchingCalendarState()
        var fail = true
        val pages = mutableListOf<Int>()
        val event = WatchingEvent(anime, "2026-11-03", episode = 1)
        val loader = WatchingCalendarLoader(this, { range ->
            pages += range.page
            if (range.page == 2 && fail) error("offline")
            WatchingPage(if (range.page == 1) listOf(event) else listOf(event, event.copy(episode = 2)), range.page == 1)
        }, { state = it })
        loader.load(request); runCurrent()
        loader.load(request.copy(page = 2)); runCurrent()
        assertNotNull(state.error); assertEquals(1, state.page); assertTrue(state.hasMore)
        fail = false; loader.load(request.copy(page = 2)); runCurrent()
        assertEquals(listOf(1, 2, 2), pages); assertEquals(2, state.events.size); assertFalse(state.hasMore)
        loader.load(request); runCurrent(); assertEquals(2, state.events.size)
    }

    @Test fun leavingRestoresTheCachedFirstPageForEveryVisitedRange() = runTest {
        var state = WatchingCalendarState()
        val pages = mutableListOf<Int>()
        val movie = MediaItem("900000002", MediaType.MOVIE, "Película")
        val range = request.copy(category = WatchingCategory.MOVIES_TV)
        val loader = WatchingCalendarLoader(this, { range ->
            pages += range.page
            WatchingPage(listOf(WatchingEvent(movie.copy(id = range.page.toString()), range.start)), range.page == 1)
        }, { state = it })
        loader.load(range); runCurrent()
        loader.load(range.copy(page = 2)); runCurrent(); assertEquals(2, state.events.size)
        val other = range.copy(start = "2026-11-09", end = "2026-11-16")
        loader.load(other); runCurrent(); loader.load(other.copy(page = 2)); runCurrent()
        loader.resetToFirstPage()
        assertEquals(1, state.page); assertEquals(1, state.events.size); assertTrue(state.hasMore)
        loader.load(range); runCurrent()
        assertEquals(1, state.page); assertEquals(1, state.events.size)
        assertEquals(listOf(1, 2, 1, 2), pages)
    }

    @Test fun leavingCancelsAnInFlightExtraPageAndRejectsItsLateResponse() = runTest {
        var state = WatchingCalendarState()
        val pending = CompletableDeferred<WatchingPage>()
        val movie = MediaItem("900000002", MediaType.MOVIE, "Película")
        val event = WatchingEvent(movie, request.start)
        val range = request.copy(category = WatchingCategory.MOVIES_TV)
        val loader = WatchingCalendarLoader(this, { range ->
            if (range.page == 1) WatchingPage(listOf(event), true)
            else withContext(NonCancellable) { pending.await() }
        }, { state = it })
        loader.load(range); runCurrent()
        loader.load(range.copy(page = 2)); runCurrent(); assertTrue(state.loading)
        loader.resetToFirstPage()
        pending.complete(WatchingPage(listOf(event.copy(item = movie.copy(id = "extra"))))); runCurrent()
        assertEquals(listOf(event), state.events); assertEquals(1, state.page); assertFalse(state.loading)
    }
}
