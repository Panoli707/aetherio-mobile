package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HubDirectorySyncTest {
    @Test fun emptyInstallationRetriesAutomaticallyThenDiscoversNewProvidersWithoutAnAppUpdate() = runTest {
        var online = false
        var published = "first"
        var calls = 0
        var snapshot = HubDirectoryResult(emptyList(), true)
        val sync = HubDirectorySync(backgroundScope, {
            calls++
            snapshot = if (online) HubDirectoryResult(listOf(HubProvider(published, published, null, null, true, false)), false)
                else snapshot.copy(stale = true)
            snapshot
        })
        sync.start(); sync.start(); runCurrent()
        assertEquals(1, calls)
        assertTrue(snapshot.providers.isEmpty())
        online = true
        advanceTimeBy(2_000); runCurrent()
        assertEquals("first", snapshot.providers.single().id)
        assertEquals(2, calls)
        published = "just-published"
        advanceTimeBy(5 * 60_000L); runCurrent()
        assertEquals("just-published", snapshot.providers.single().id)
        assertEquals(3, calls)
        sync.stop()
        advanceTimeBy(10 * 60_000L); runCurrent()
        assertEquals(3, calls)
        sync.start(); runCurrent()
        assertEquals(4, calls)
        sync.stop()
    }

    @Test fun partialOrThrownFailuresBackOffAndRecoverInsteadOfStoppingSync() = runTest {
        var calls = 0
        val sync = HubDirectorySync(backgroundScope, {
            calls++
            if (calls == 1) error("offline")
            HubDirectoryResult(emptyList(), calls < 4)
        })
        sync.start(); runCurrent()
        advanceTimeBy(2_000); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        advanceTimeBy(30_000); runCurrent()
        assertEquals(4, calls)
        advanceTimeBy(5 * 60_000L); runCurrent()
        assertEquals(5, calls)
        sync.stop()
    }
}
