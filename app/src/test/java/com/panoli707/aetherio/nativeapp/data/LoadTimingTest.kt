package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LoadTimingTest {
    @After fun reset() { LoadTiming.observer = null }
    @Test fun diagnosticsHaveOnlyClosedLabelsAndPreserveResultsFailuresAndCancellation() = runBlocking {
        val samples = mutableListOf<LoadTiming.Sample>()
        LoadTiming.observer = { samples += it }
        assertEquals(7, LoadTiming.measure(LoadTiming.Stage.JSON_PARSE) { 7 })
        assertThrows(IllegalStateException::class.java) { runBlocking {
            LoadTiming.measure(LoadTiming.Stage.SEASON) { error("synthetic secret not recorded") }
        } }
        assertThrows(CancellationException::class.java) { runBlocking {
            LoadTiming.measure(LoadTiming.Stage.SEASON) { throw CancellationException("cancel") }
        } }
        assertEquals(listOf(true, false, false), samples.map { it.success })
        assertTrue(samples.all { it.elapsedMs >= 0 })
        assertFalse(samples.toString().contains("synthetic"))
        LoadTiming.observer = { error("broken diagnostic sink") }
        assertEquals(8, LoadTiming.measure(LoadTiming.Stage.JSON_PARSE) { 8 })
    }
}
