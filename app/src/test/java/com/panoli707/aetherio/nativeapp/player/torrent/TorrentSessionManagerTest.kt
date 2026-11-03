package com.panoli707.aetherio.nativeapp.player.torrent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TorrentSessionManagerTest {
    private val request = TorrentRequest("a".repeat(40))
    private class FakeEngine : TorrentEngine {
        override val failures = MutableSharedFlow<Unit>()
        var closed = false
        var stopped = false
        var metadata: CompletableDeferred<Unit>? = null
        var destroyGate: CompletableDeferred<Unit>? = null
        var prepareGate: CompletableDeferred<Unit>? = null
        var files = listOf(TorrentFile(0, "movie.mp4", 100))
        override suspend fun addMagnet(magnet: String): String { metadata?.await(); return "a".repeat(40) }
        override suspend fun files(id: String) = files
        override suspend fun prepare(id: String, file: TorrentFile): TorrentRoute {
            prepareGate?.await()
            return TorrentRoute("b".repeat(64), "http://127.0.0.1:8765/stream/${"b".repeat(64)}", file.index, file.size)
        }
        override suspend fun metrics(route: TorrentRoute) = TorrentMetrics(100, 2)
        override suspend fun stop(route: TorrentRoute) { stopped = true }
        override suspend fun shutdown() { destroyGate?.await(); closed = true }
    }

    @Test fun readyCloseIsIdempotentAndRevokesRoute() = runTest {
        val engine = FakeEngine()
        val session = TorrentSessionManager({ engine }, backgroundScope).open(request)
        runCurrent()
        val route = (session.state.value as TorrentState.Ready).route
        session.close()
        session.close()
        runCurrent()
        assertTrue(engine.stopped && engine.closed)
        assertEquals(TorrentState.Closed, session.state.value)
        assertThrows(IllegalArgumentException::class.java) { route.validate(route.url) }
    }

    @Test fun cancellingNativeMetadataWaitAlwaysDestroysTheEngine() = runTest {
        val engine = FakeEngine().apply { metadata = CompletableDeferred() }
        val session = TorrentSessionManager({ engine }, backgroundScope).open(request)
        runCurrent()
        session.close()
        runCurrent()
        assertTrue(engine.closed)
        assertFalse(engine.stopped)
        engine.metadata!!.complete(Unit)
        runCurrent()
        assertEquals(TorrentState.Closed, session.state.value)
    }

    @Test fun nextSessionWaitsForOldDestructorAndOldCloseCannotAffectNewRoute() = runTest {
        val gate = CompletableDeferred<Unit>()
        val firstEngine = FakeEngine().apply { destroyGate = gate }
        val secondEngine = FakeEngine()
        var creations = 0
        val manager = TorrentSessionManager({ if (++creations == 1) firstEngine else secondEngine }, backgroundScope)
        val first = manager.open(request)
        runCurrent()
        val second = manager.open(request)
        runCurrent()
        assertEquals(1, creations)
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, creations)
        first.close()
        assertTrue(second.state.value is TorrentState.Ready)
        assertFalse(secondEngine.closed)
        second.close()
        runCurrent()
    }

    @Test fun selectionIsExplicitAndCancelDuringPrepareCleansUp() = runTest {
        val engine = FakeEngine().apply {
            files = listOf(TorrentFile(0, "E01.mp4", 100), TorrentFile(1, "E02.mp4", 100))
            prepareGate = CompletableDeferred()
        }
        val session = TorrentSessionManager({ engine }, backgroundScope).open(request)
        runCurrent()
        assertTrue(session.state.value is TorrentState.SelectFile)
        session.selectFile(99)
        assertTrue(session.state.value is TorrentState.SelectFile)
        session.selectFile(1)
        runCurrent()
        session.close()
        runCurrent()
        assertTrue(engine.closed)
    }

    @Test fun metadataTimeoutAndNativeFailureAreSanitizedAndClosed() = runTest {
        val engine = FakeEngine().apply { metadata = CompletableDeferred() }
        val manager = TorrentSessionManager({ engine }, backgroundScope)
        val session = manager.open(request)
        runCurrent()
        advanceTimeBy(90_001)
        runCurrent()
        assertTrue(session.state.value is TorrentState.Failed)
        assertTrue(engine.closed)
    }

    @Test fun unsolicitedNativeFailureStopsTheSession() = runTest {
        val engine = FakeEngine()
        val session = TorrentSessionManager({ engine }, backgroundScope).open(request)
        runCurrent()
        engine.failures.emit(Unit)
        runCurrent()
        assertTrue(session.state.value is TorrentState.Failed)
        assertTrue(engine.closed)
    }
}
