package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailLoaderTest {
    private val base = MediaItem("220542", MediaType.SERIES, "Los diarios de la boticaria", posterUrl = "general")
    private val detail = MediaDetail(base, seasons = (1..3).map { SeasonInfo(it, "Temporada $it", 24) }, selectedSeason = 1)
    private fun season(number: Int) = SeasonContent(base.copy(seasonHint = number, posterUrl = "season-$number"),
        listOf(EpisodeItem(1, number, "Episodio")))

    @Test fun metadataIsPublishedBeforeTheHistorySeasonWithoutEnablingOldEpisodes() = runTest {
        var state = AppState()
        val seen = mutableListOf<AppState>()
        val pending = CompletableDeferred<SeasonContent>()
        val loader = DetailLoader(this, { detail }, { _, number -> assertEquals(2, number); pending.await() }, { 2 },
            { state }, { state = it(state); seen.add(state) })
        loader.open(base.copy(posterUrl = "catalog")); runCurrent()
        assertFalse(state.detailLoading); assertNotNull(state.detail)
        assertTrue(state.episodesLoading); assertEquals(2, state.pendingSeason)
        assertTrue(state.detail!!.episodes.isEmpty())
        pending.complete(season(2)); runCurrent()
        assertEquals(listOf("general"), seen.mapNotNull { it.detail?.item?.posterUrl }.distinct())
        assertEquals(2, state.detail!!.selectedSeason); assertEquals(base, state.detail!!.artworkBase)
        assertFalse(state.detailLoading)
    }

    @Test fun controlledLatencyRemovesFiveSecondsOfSeasonWaitFromTimeToUsableMetadata() = runTest {
        var state = AppState()
        var usableAt: Long? = null
        var episodesAt: Long? = null
        val loader = DetailLoader(this, { delay(100); detail }, { _, n -> delay(5_000); season(n) }, { null },
            { state }, { state = it(state)
                if (state.detail != null && usableAt == null) usableAt = testScheduler.currentTime
                if (state.detail?.episodes?.isNotEmpty() == true) episodesAt = testScheduler.currentTime
            })
        loader.open(base)
        advanceTimeBy(101); runCurrent()
        assertEquals(100L, usableAt)
        assertTrue(state.episodesLoading)
        assertNull(episodesAt)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(5_100L, episodesAt)
        println("CONTROLLED_LOAD metadata_ready_ms=$usableAt season_ready_ms=$episodesAt previous_sequential_gate_ms=5100")
    }

    @Test fun failingInitialSeasonDoesNotDiscardMetadataAndRetryOnlyReloadsSeason() = runTest {
        var state = AppState()
        var detailCalls = 0
        var fail = true
        val loader = DetailLoader(this, { detailCalls++; detail }, { _, n -> if (fail) error("offline") else season(n) },
            { 2 }, { state }, { state = it(state) })
        loader.open(base); runCurrent()
        assertNotNull(state.detail); assertFalse(state.detailLoading); assertFalse(state.episodesLoading)
        assertTrue(state.detail!!.episodes.isEmpty()); assertNotNull(state.detailError)
        fail = false; loader.retry(); runCurrent()
        assertEquals(1, detailCalls)
        assertEquals(2, state.detail!!.selectedSeason); assertNull(state.detailError)
    }

    @Test fun explicitSeasonHintWinsOverHistoryAndInvalidHintsUseTheAvailableSeason() = runTest {
        var state = AppState()
        val requested = mutableListOf<Int>()
        val loader = DetailLoader(this, { detail }, { _, number -> requested.add(number); season(number) }, { 2 },
            { state }, { state = it(state) })
        loader.open(base.copy(seasonHint = 3)); runCurrent()
        assertEquals(3, state.detail!!.selectedSeason)
        loader.open(base.copy(seasonHint = 999)); runCurrent()
        assertEquals(1, state.detail!!.selectedSeason)
        assertEquals(listOf(3, 1), requested)
    }

    @Test fun changingSeasonKeepsTheCommittedCoverUntilTheReplacementIsReadyAndSameSeasonIsANoOp() = runTest {
        var state = AppState()
        val pending = CompletableDeferred<SeasonContent>()
        var requests = 0
        val loader = DetailLoader(this, { detail }, { _, n -> requests++; if (n == 2) pending.await() else season(n) }, { null },
            { state }, { state = it(state) })
        loader.open(base); runCurrent()
        loader.select(1); runCurrent(); assertEquals(1, requests)
        loader.select(2); runCurrent()
        assertEquals("general", state.detail!!.item.posterUrl)
        assertEquals(1, state.detail!!.selectedSeason); assertEquals(2, state.pendingSeason); assertTrue(state.episodesLoading)
        loader.select(2); runCurrent(); assertEquals(2, requests)
        pending.complete(season(2)); runCurrent()
        assertEquals("general", state.detail!!.item.posterUrl)
        assertEquals(2, state.detail!!.episodes.single().season); assertNull(state.pendingSeason)
        loader.select(1); runCurrent(); assertEquals(2, requests) // Session cache, no image/metadata roulette.
    }

    @Test fun lateNonCooperativeSeasonResponsesCannotReplaceTheNewestSelection() = runTest {
        var state = AppState()
        val pending = CompletableDeferred<SeasonContent>()
        val loader = DetailLoader(this, { detail }, { _, n ->
            if (n == 2) withContext(NonCancellable) { pending.await() } else season(n)
        }, { null }, { state }, { state = it(state) })
        loader.open(base); runCurrent()
        loader.select(2); runCurrent(); loader.select(3); runCurrent()
        pending.complete(season(2)); runCurrent()
        assertEquals(3, state.detail!!.selectedSeason); assertEquals("general", state.detail!!.item.posterUrl)
    }

    @Test fun openingAnotherSeriesDiscardsPendingResultsEvenWhenTheirTransportCannotBeCancelled() = runTest {
        var state = AppState()
        val pending = CompletableDeferred<MediaDetail>()
        val movie = base.copy(id = "550", type = MediaType.MOVIE, posterUrl = "movie")
        val loader = DetailLoader(this, { if (it.key == base.key) withContext(NonCancellable) { pending.await() } else MediaDetail(movie) },
            { _, n -> season(n) }, { null }, { state }, { state = it(state) })
        loader.open(base); runCurrent(); loader.open(movie); runCurrent()
        pending.complete(detail); runCurrent()
        assertEquals(movie, state.detail!!.item)
    }

    @Test fun seasonFailureKeepsOldArtworkAndEpisodesAndRetryTargetsTheFailedSeason() = runTest {
        var state = AppState()
        var fail = true
        val loader = DetailLoader(this, { detail }, { _, n -> if (n == 2 && fail) error("offline") else season(n) }, { null },
            { state }, { state = it(state) })
        loader.open(base); runCurrent(); loader.select(2); runCurrent()
        assertEquals(1, state.detail!!.selectedSeason); assertEquals("general", state.detail!!.item.posterUrl)
        assertNotNull(state.detailError); assertFalse(state.episodesLoading); assertNull(state.pendingSeason)
        fail = false; loader.retry(); runCurrent()
        assertEquals(2, state.detail!!.selectedSeason); assertNull(state.detailError)
    }

    @Test fun initialFailureShowsNoProvisionalCoverAndCanBeRetried() = runTest {
        var state = AppState()
        var fail = true
        val loader = DetailLoader(this, { if (fail) error("offline") else detail }, { _, n -> season(n) }, { null },
            { state }, { state = it(state) })
        loader.open(base); runCurrent()
        assertNull(state.detail); assertFalse(state.detailLoading); assertNotNull(state.detailError)
        fail = false; loader.retry(); runCurrent()
        assertEquals("general", state.detail!!.item.posterUrl); assertNull(state.detailError)
    }
}
