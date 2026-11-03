package com.panoli707.aetherio.nativeapp.player

import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class OpeningRepositoryTest {
    private val target = WatchTarget(MediaItem("20", MediaType.SERIES, "Anime", isAnime = true, imdbId = "tt0409591"), 1, 2)
    private val response = """{"imdb_id":"tt0409591","season":1,"episode":2,"intro":{"start_sec":30,"end_sec":120}}"""

    @Test fun parsesPublicTimingsOnlyForTheExactEpisode() {
        assertEquals(OpeningInterval(30000, 120000), OpeningRepository.parse(response, target))
        assertNull(OpeningRepository.parse(response, target.copy(episode = 3)))
        assertNull(OpeningRepository.parse(response, target.copy(season = 2)))
        assertNull(OpeningRepository.parse(response.replace("tt0409591", "tt0903747"), target))
        assertNull(OpeningRepository.parse(response.replace("30", "-30"), target))
        assertNull(OpeningRepository.parse(response.replace("120", "999999"), target))
        assertNull(OpeningRepository.parse(response.replace("120", "30"), target))
    }

    @Test fun exactSkipIsOfferedOnlyDuringTheIntervalNotBeforeAfterOrBeyondTheVideo() {
        val state = VideoUiState(animeEpisode = true, seekable = true, positionMs = 60000, durationMs = 1440000,
            opening = OpeningInterval(30000, 120000))
        assertEquals(OpeningSkip(120000, true), openingSkip(state))
        assertNull(openingSkip(state.copy(positionMs = 10000)))
        assertNull(openingSkip(state.copy(positionMs = 120000)))
        assertNull(openingSkip(state.copy(durationMs = 100000)))
        assertNull(openingSkip(state.copy(live = true)))
        assertNull(openingSkip(state.copy(seekable = false)))
        assertNull(openingSkip(state.copy(animeEpisode = false)))
        assertNull(openingSkip(state.copy(error = "Error")))
    }

    @Test fun fallbackIsAnExplicit90SecondAdvanceNeverAnAssumedOpeningBoundary() {
        val state = VideoUiState(animeEpisode = true, seekable = true, positionMs = 42000, durationMs = 1440000)
        assertEquals(OpeningSkip(132000, false), openingSkip(state))
        assertNull(openingSkip(state.copy(positionMs = 600000)))
        assertNull(openingSkip(state.copy(durationMs = 100000)))
        assertNull(openingSkip(state.copy(live = true)))
    }

    @Test fun publicRequestContainsOnlyEpisodeIdentifiersAndDoesNotFollowRedirectsOrFailPlayback() = runBlocking {
        var count = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            count++
            val request = chain.request()
            assertEquals("https://api.introdb.app/segments?imdb_id=tt0409591&season=1&episode=2", request.url.toString())
            assertNull(request.header("Authorization")); assertNull(request.header("Cookie")); assertNull(request.header("Referer"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (count == 1) 200 else 429)
                .message("Fixture").body(response.toResponseBody()).build()
        }.build()
        try {
            val repository = OpeningRepository(client)
            assertNull(repository.opening(target.copy(item = target.item.copy(isAnime = false))))
            assertEquals(0, count)
            assertEquals(OpeningInterval(30000, 120000), repository.opening(target))
            assertNull(repository.opening(target))
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun endingUsesExactOutroAndPreservesScenesAfterItOrOffersAnExplicitManualAdvance() {
        val state = VideoUiState(animeEpisode = true, seekable = true, positionMs = 1260000, durationMs = 1440000)
        assertEquals(OpeningSkip(1350000, false), endingSkip(state))
        val outro = """{"imdb_id":"tt0409591","season":1,"episode":2,"outro":{"start_sec":1250,"end_sec":1340}}"""
        val interval = OpeningRepository.parse(outro, target, "outro")
        assertEquals(OpeningInterval(1250000, 1340000), interval)
        assertEquals(OpeningSkip(1340000, true), endingSkip(state.copy(ending = interval)))
        assertNull(endingSkip(state.copy(ending = interval, positionMs = 1340000)))
        assertNull(endingSkip(state.copy(live = true)))
        assertNull(endingSkip(state.copy(seekable = false)))
        assertNull(endingSkip(state.copy(animeEpisode = false)))
        assertNull(endingSkip(state.copy(positionMs = 30000)))
        assertNull(endingSkip(state.copy(positionMs = 1390000)))
        assertNull(OpeningRepository.parse(outro, target.copy(episode = 3), "outro"))
        assertEquals(OpeningSkip(1440000, true), endingSkip(state.copy(ending = OpeningInterval(1250000, 1440000))))
    }
}
