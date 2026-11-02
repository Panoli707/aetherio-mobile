package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class WatchHistoryRepository internal constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("aetherio_watch_history", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val mutex = Mutex()
    private var loaded = false
    private val mutableEntries = MutableStateFlow<List<WatchProgress>>(emptyList())
    val entries = mutableEntries.asStateFlow()

    init { scope.launch { load() } }

    private fun loadLocked() {
        if (loaded) return
        mutableEntries.value = WatchProgressCodec.decode(preferences.getString("history", "{}").orEmpty())
        loaded = true
    }

    suspend fun load(): List<WatchProgress> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        mutex.withLock { loadLocked(); mutableEntries.value }
    }

    fun record(target: WatchTarget, positionMs: Long, durationMs: Long, ended: Boolean = false) = scope.launch {
        mutex.withLock {
            loadLocked()
            val previous = mutableEntries.value.firstOrNull { it.target.key == target.key }
            val sample = WatchProgressRules.sample(previous, target, positionMs, durationMs, ended, System.currentTimeMillis())
                ?: return@withLock
            save(sample)
        }
    }

    fun setWatched(target: WatchTarget, watched: Boolean) = scope.launch {
        if (!WatchProgressRules.valid(target)) return@launch
        mutex.withLock {
            loadLocked()
            val previous = mutableEntries.value.firstOrNull { it.target.key == target.key }
            // A manual badge is not a new playback session and must not displace an unfinished later episode.
            save(WatchProgress(target, if (watched) previous?.positionMs ?: 0 else 0,
                previous?.durationMs ?: 0, watched, previous?.updatedAt ?: 0))
        }
    }

    private fun save(entry: WatchProgress) {
        val updated = (listOf(entry) + mutableEntries.value.filterNot { it.target.key == entry.target.key })
            .take(WatchProgressCodec.MAX_ENTRIES)
        // Run on IO, including the disk write. A completed record is durable before publishing to UI.
        if (preferences.edit().putString("history", WatchProgressCodec.encode(updated)).commit()) mutableEntries.value = updated
    }

    companion object {
        @Volatile private var instance: WatchHistoryRepository? = null
        fun get(context: Context): WatchHistoryRepository = instance ?: synchronized(this) {
            instance ?: WatchHistoryRepository(context).also { instance = it }
        }
    }
}
