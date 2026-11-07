package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchHistoryRepositoryTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("qa_watch_history", mode)
    }
    private val target = WatchTarget(MediaItem("20", MediaType.SERIES, "Fixture anime", isAnime = true), 2, 3, "Capitulo")

    @Before fun resetFixtureOnly() { context.getSharedPreferences("", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun secondsAndWatchedStatePersistAcrossRepositoryInstancesAndDifferentEpisodesStayIndependent() = runBlocking {
        val first = WatchHistoryRepository(context)
        first.record(target, 754321, 1440000).join()
        first.record(target.copy(episode = 4), 30000, 1440000).join()
        val second = WatchHistoryRepository(context)
        val entries = second.load()
        assertEquals(2, entries.size)
        assertEquals(754321L, entries.first { it.target.key == target.key }.resumePositionMs)
        second.setWatched(target, true).join()
        assertTrue(WatchHistoryRepository(context).load().first { it.target.key == target.key }.watched)
        assertEquals(30000L, second.load().first { it.target.episode == 4 }.positionMs)
        second.setWatched(target.copy(episode = 1), true).join()
        assertEquals(4, WatchProgressRules.continuing(second.load()).single().target.episode)
    }

    @Test fun explicitUnwatchedResetsResumeWithoutAffectingAnotherEpisodeAndInvalidSamplesDoNotEraseIt() = runBlocking {
        val repository = WatchHistoryRepository(context)
        repository.record(target, 60000, 1440000).join()
        repository.record(target, 0, 0).join()
        assertEquals(60000L, repository.load().single().positionMs)
        repository.setWatched(target, true).join()
        assertTrue(repository.load().single().watched)
        repository.setWatched(target, false).join()
        assertFalse(repository.load().single().watched)
        assertEquals(0L, repository.load().single().resumePositionMs)
        assertTrue(WatchProgressRules.continuing(repository.load()).isEmpty())
    }

    @Test fun markingASeasonIsAtomicAndDoesNotResetTheOtherSeasonOrInventPlaybackTime() = runBlocking {
        val repository = WatchHistoryRepository(context)
        val other = target.copy(season = 3, episode = 1)
        repository.record(other, 45000, 120000).join()
        val targets = (1..3).map { target.copy(episode = it) }
        repository.setWatched(targets, true).join()
        val stored = WatchHistoryRepository(context).load()
        assertEquals(3, stored.count { it.watched })
        assertTrue(stored.filter { it.watched }.all { it.positionMs == 0L && it.updatedAt == 0L })
        assertEquals(45000L, stored.first { it.target.key == other.key }.positionMs)
        repository.setWatched(targets, false).join()
        assertTrue(repository.load().none { it.watched })
        assertEquals(45000L, repository.load().first { it.target.key == other.key }.positionMs)
    }

    @Test fun importingRemoteBadgesPreservesPositionsAndRejectsCapacityOverflowWithoutEviction() = runBlocking {
        val repository = WatchHistoryRepository(context)
        repository.record(target, 45000, 120000).join()
        assertTrue(repository.importWatched(listOf(target, target.copy(episode = 4))))
        assertEquals(45000L, repository.load().first { it.target.key == target.key }.positionMs)
        val previous = repository.load()
        assertFalse(repository.importWatched((1..WatchProgressCodec.MAX_ENTRIES + 1).map { target.copy(episode = it) }))
        assertEquals(previous, repository.load())
    }

    @Test fun legacyContinuousEpisodeMigratesWithABackupAndSurvivesTheNextSaveAndReload() = runBlocking {
        val old = WatchTarget(MediaItem("220542", MediaType.SERIES, "La boticaria", isAnime = true), 1, 25)
        val raw = WatchProgressCodec.encode(listOf(WatchProgress(old, 75000, 1440000, updatedAt = 1)))
        val preferences = context.getSharedPreferences("", Context.MODE_PRIVATE)
        preferences.edit().putString("history", raw).commit()
        val repository = WatchHistoryRepository(context)
        val migrated = repository.load().single()
        assertEquals(2, migrated.target.season)
        assertEquals(1, migrated.target.episode)
        assertEquals(75000L, migrated.positionMs)
        assertEquals(raw, preferences.getString("history_before_series_v1", null))
        repository.record(migrated.target, 90000, 1440000).join()
        val reloaded = WatchHistoryRepository(context).load().single()
        assertEquals(migrated.target.key, reloaded.target.key)
        assertEquals(90000L, reloaded.positionMs)
        assertEquals(raw, preferences.getString("history_before_series_v1", null))
    }
}
