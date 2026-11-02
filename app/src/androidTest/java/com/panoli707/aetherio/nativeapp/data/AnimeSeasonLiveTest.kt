package com.panoli707.aetherio.nativeapp.data

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in metadata probe; never requests or plays copyrighted media. */
@RunWith(AndroidJUnit4::class)
class AnimeSeasonLiveTest {
    @Test fun actualCurrentSeasonUsesRealIdsAndReleasedEpisodeSchedules() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualAnimeCalendar") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val repository = AnimeSeasonRepository()
        val season = AnimeSeason.current()
        val entries = repository.season(season)
        assertTrue("The current season should have releasing anime", entries.isNotEmpty())
        assertTrue(entries.all { it.item.source == CatalogSource.ANILIST && it.item.animeSeasonLabel == season.label && it.item.isAnime })
        val sample = entries.first { it.nextEpisode != null && it.nextEpisode > 1 }
        val detail = repository.detail(sample.item)
        assertEquals(sample.item.id, detail.item.id)
        assertTrue(detail.episodes.isNotEmpty())
        assertTrue(detail.episodes.all { it.number < sample.nextEpisode!! && it.released })
        assertTrue(detail.episodes.distinctBy { it.number }.size == detail.episodes.size)
    }
}
