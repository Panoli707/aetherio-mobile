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
        val raw = preferences.getString("history", "{}").orEmpty()
        val decoded = WatchProgressCodec.decode(raw)
        val normalized = SeriesIdentity.progress(decoded)
        if (normalized != decoded && !preferences.contains("history_before_series_v1"))
            preferences.edit().putString("history_before_series_v1", raw).commit()
        mutableEntries.value = normalized
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

    fun setWatched(targets: List<WatchTarget>, watched: Boolean) = scope.launch {
        if (targets.isEmpty() || targets.any { !WatchProgressRules.valid(it) }) return@launch
        mutex.withLock {
            loadLocked()
            val previous = mutableEntries.value.associateBy { it.target.key }
            val changed = targets.distinctBy { it.key }.map { target ->
                val saved = previous[target.key]
                WatchProgress(target, if (watched) saved?.positionMs ?: 0 else 0,
                    saved?.durationMs ?: 0, watched, saved?.updatedAt ?: 0)
            }
            val keys = changed.map { it.target.key }.toSet()
            saveEntries((changed + mutableEntries.value.filterNot { it.target.key in keys }).take(WatchProgressCodec.MAX_ENTRIES))
        }
    }

    private fun save(entry: WatchProgress) {
        val updated = (listOf(entry) + mutableEntries.value.filterNot { it.target.key == entry.target.key })
            .take(WatchProgressCodec.MAX_ENTRIES)
        // Run on IO, including the disk write. A completed record is durable before publishing to UI.
        saveEntries(updated)
    }

    private fun saveEntries(updated: List<WatchProgress>) {
        if (preferences.edit().putString("history", WatchProgressCodec.encode(updated)).commit()) mutableEntries.value = updated
    }

    companion object {
        @Volatile private var instance: WatchHistoryRepository? = null
        fun get(context: Context): WatchHistoryRepository = instance ?: synchronized(this) {
            instance ?: WatchHistoryRepository(context).also { instance = it }
        }
    }
}
