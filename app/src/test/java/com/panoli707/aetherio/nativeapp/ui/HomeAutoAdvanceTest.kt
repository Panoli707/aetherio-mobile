package com.panoli707.aetherio.nativeapp.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeAutoAdvanceTest {
    @Test fun noWorkWhileHiddenOrDraggingAndAFullQuietIntervalIsRequired() = runTest {
        val eligible = MutableStateFlow(false)
        var advances = 0
        backgroundScope.launch { runHomeAutoAdvance(eligible, { false }) { advances++ } }
        runCurrent(); advanceTimeBy(30_000); runCurrent()
        assertEquals(0, advances)
        eligible.value = true; runCurrent(); advanceTimeBy(7_999); runCurrent()
        assertEquals(0, advances)
        eligible.value = false; runCurrent(); advanceTimeBy(20_000)
        eligible.value = true; runCurrent(); advanceTimeBy(8_000); runCurrent()
        assertEquals(1, advances)
    }

    @Test fun manualPagerAnimationIsNotInterruptedByAutomaticAdvance() = runTest {
        val eligible = MutableStateFlow(true)
        var busy = true
        var advances = 0
        backgroundScope.launch { runHomeAutoAdvance(eligible, { busy }) { advances++ } }
        runCurrent(); advanceTimeBy(8_000); runCurrent()
        assertEquals(0, advances)
        busy = false; advanceTimeBy(8_000); runCurrent()
        assertEquals(1, advances)
    }

    @Test fun scrollingOrHidingCancelsAnAlreadyRunningAutomaticAnimation() = runTest {
        val eligible = MutableStateFlow(true)
        var running = false
        backgroundScope.launch {
            runHomeAutoAdvance(eligible, { false }) {
                running = true
                try { awaitCancellation() } finally { running = false }
            }
        }
        runCurrent(); advanceTimeBy(8_000); runCurrent()
        assertTrue(running)
        eligible.value = false; runCurrent()
        assertFalse(running)
    }
}
