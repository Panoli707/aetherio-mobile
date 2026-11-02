package com.panoli707.aetherio.nativeapp.data

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in metadata only: never opens trailers or starts media playback. */
@RunWith(AndroidJUnit4::class)
class DetailMetadataLiveTest {
    @Test fun movieSeriesAndAnimeDetailsExposeOnlyRealSupportedMetadataLinks() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualDetailMetadata") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val repository = AetherioRepository()
        val movie = repository.detail(MediaItem("550", MediaType.MOVIE, "Fight Club"))
        val series = repository.detail(MediaItem("105248", MediaType.SERIES, "Cyberpunk: Edgerunners", isAnime = true))
        for (detail in listOf(movie, series)) {
            assertTrue(detail.cast.isNotEmpty())
            assertTrue(detail.trailers.isNotEmpty())
            assertTrue(detail.facts.isNotEmpty())
            assertTrue(detail.trailers.all { DetailLinks.validate(it.url) == it.url })
            assertTrue(detail.availability.all { region -> region.link == null || DetailLinks.validate(region.link) == region.link })
        }
        val episodes = repository.episodes(series.item, 1)
        assertTrue(episodes.isNotEmpty())
        assertTrue(episodes.any { it.airedOn.isNotBlank() })
        val anime = AnimeSeasonRepository().detail(MediaItem("195516", MediaType.SERIES, "Anime", source = CatalogSource.ANILIST, isAnime = true))
        assertTrue(anime.trailers.isNotEmpty())
        assertTrue(anime.cast.isNotEmpty())
        assertTrue(anime.facts.isNotEmpty())
    }
}
