package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeSeasonLoaderTest {
    private val season = AnimeSeason(2026, "FALL")
    private val entry = SeasonalAnime(MediaItem("123", MediaType.SERIES, "Fixture", CatalogSource.ANILIST), null, null, null)

    @Test fun initialErrorCanBeRetriedAndSuccessfulResultsClearTheError() = runTest {
        var state = AnimeSeasonState(season)
        var fail = true
        val loader = AnimeSeasonLoader(this, {
            if (fail) throw AnimeCalendarException(AnimeCalendarFailure.TIMEOUT)
            listOf(entry)
        }, { state = it(state) })
        loader.load(season); runCurrent()
        assertFalse(state.loading); assertFalse(state.loaded); assertTrue(state.error!!.contains("tardando"))
        fail = false
        loader.load(season); runCurrent()
        assertTrue(state.loaded); assertFalse(state.loading); assertNull(state.error); assertEquals(listOf(entry), state.entries)
    }

    @Test fun refreshPreservesCurrentEntriesButChangingQuarterDoesNotReuseThem() = runTest {
        var state = AnimeSeasonState(season, listOf(entry), loaded = true)
        val loader = AnimeSeasonLoader(this, { throw AnimeCalendarException(AnimeCalendarFailure.CONNECTION) }, { state = it(state) })
        loader.load(season); runCurrent()
        assertEquals(listOf(entry), state.entries); assertTrue(state.loaded); assertNotNull(state.error)
        loader.load(AnimeSeason(2027, "WINTER")); runCurrent()
        assertTrue(state.entries.isEmpty()); assertFalse(state.loaded); assertEquals(2027, state.season.year)
    }

    @Test fun activeLoadsAreNotDuplicatedAndCancellationDoesNotDisplayAnError() = runTest {
        var state = AnimeSeasonState(season, error = "Old failure")
        val pending = CompletableDeferred<List<SeasonalAnime>>()
        var count = 0
        val loader = AnimeSeasonLoader(this, { count++; pending.await() }, { state = it(state) })
        loader.load(season); runCurrent()
        assertTrue(state.loading); assertNull(state.error)
        loader.load(season); runCurrent()
        assertEquals(1, count)
        coroutineContext.cancelChildren(); runCurrent()
        assertFalse(state.loading); assertNull(state.error)
    }
}
