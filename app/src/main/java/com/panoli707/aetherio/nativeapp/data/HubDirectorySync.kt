package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Foreground-only refresh, with automatic recovery after an offline/partial first installation. */
internal class HubDirectorySync(
    private val scope: CoroutineScope,
    private val refresh: suspend () -> HubDirectoryResult,
    private val intervalMs: Long = 5 * 60_000L,
    private val retryDelaysMs: List<Long> = listOf(2_000, 10_000, 30_000, 60_000)
) {
    private var job: Job? = null
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var failures = 0
            while (isActive) {
                val stale = try { refresh().stale }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { true }
                val wait = if (stale) retryDelaysMs[minOf(failures++, retryDelaysMs.lastIndex)]
                    else intervalMs.also { failures = 0 }
                delay(wait)
            }
        }
    }
    fun stop() { job?.cancel(); job = null }
}
