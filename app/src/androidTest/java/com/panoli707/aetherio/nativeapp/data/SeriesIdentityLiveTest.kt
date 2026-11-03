package com.panoli707.aetherio.nativeapp.data

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeriesIdentityLiveTest {
    @Test fun boticariaHasOneIdentityWithRealSeasonArtAndCorrectProviderEpisodeOffsets() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualSeriesIdentity") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val repository = AetherioRepository()
        val separate = MediaItem("195516", MediaType.SERIES, "Kusuriya no Hitorigoto 3rd Season", CatalogSource.ANILIST, isAnime = true)
        val detail = repository.detail(separate)
        assertEquals("TMDB:tv:220542", detail.item.key)
        assertTrue(detail.seasons.map { it.number }.containsAll(listOf(1, 2, 3)))
        assertEquals(3, detail.selectedSeason)
        val first = repository.seasonContent(detail.item, 1)
        val second = repository.seasonContent(detail.item, 2)
        assertNotNull(first.item.posterUrl)
        assertNotEquals(first.item.posterUrl, second.item.posterUrl)
        assertEquals(1, first.episodes.first().season)
        assertEquals(2, second.episodes.first().season)
        assertEquals(1, second.episodes.first().number)
        val query = SourceQuery(second.item, 2, 1)
        val standard = repository.providerQuery(query, false)
        assertEquals(1, standard.season)
        assertEquals(25, standard.episode)
        val anime = repository.providerQuery(query, true)
        assertEquals("176301", anime.item.id)
        assertEquals(CatalogSource.ANILIST, anime.item.source)
        assertEquals(1, anime.season)
        assertEquals(1, anime.episode)
    }
}
