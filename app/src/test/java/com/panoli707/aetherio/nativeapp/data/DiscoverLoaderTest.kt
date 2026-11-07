package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverLoaderTest {
    private val movie = MediaItem("900000001", MediaType.MOVIE, "Prueba")

    @Test fun staleResponsesCannotReplaceANewerFilterSelection() = runTest {
        var state = DiscoverState()
        val pending = CompletableDeferred<DiscoverPage>()
        val loader = DiscoverLoader(this, { _, _, genre ->
            if (genre.isEmpty()) withContext(NonCancellable) { pending.await() }
            else DiscoverPage(listOf(movie), false)
        }, { state }, { state = it(state) })
        loader.load(); runCurrent()
        loader.load(state.selection.copy(genre = "Terror")); runCurrent()
        assertEquals("Terror", state.selection.genre)
        pending.complete(DiscoverPage(listOf(movie.copy(id = "old")), true)); runCurrent()
        assertEquals(listOf(movie), state.items)
        assertFalse(state.loading)
    }

    @Test fun nextPageFailureKeepsItemsAndRetriesTheSamePageWithoutDuplicates() = runTest {
        var state = DiscoverState()
        var fail = true
        val pages = mutableListOf<Int>()
        val loader = DiscoverLoader(this, { _, page, _ ->
            pages += page
            if (page == 2 && fail) error("offline")
            DiscoverPage(if (page == 1) listOf(movie) else listOf(movie, movie.copy(id = "900000002")), true)
        }, { state }, { state = it(state) })
        loader.load(); runCurrent()
        loader.load(more = true); runCurrent()
        assertNotNull(state.error); assertEquals(1, state.page); assertTrue(state.hasMore)
        assertEquals(listOf(movie), state.items)
        fail = false; loader.load(more = true); runCurrent()
        assertEquals(listOf(1, 2, 2), pages); assertEquals(2, state.items.size); assertNull(state.error)
        loader.load(more = true); runCurrent()
        assertFalse(state.hasMore)
    }

    @Test fun initialFailureCanRetryAndSameSelectionDoesNotReloadOnReturnFromDetails() = runTest {
        var state = DiscoverState()
        var calls = 0
        val loader = DiscoverLoader(this, { _, _, _ -> if (++calls == 1) error("offline") else DiscoverPage(emptyList(), false) }, { state }, { state = it(state) })
        loader.load(); runCurrent(); assertNotNull(state.error)
        loader.load(force = true); runCurrent(); assertNull(state.error); assertEquals(1, state.page)
        loader.load(); runCurrent(); assertEquals(2, calls)
    }
}
