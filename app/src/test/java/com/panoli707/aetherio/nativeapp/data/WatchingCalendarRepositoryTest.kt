package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class WatchingCalendarRepositoryTest {
    private fun client(answer: (Request) -> String) = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
            .body(answer(chain.request()).toResponseBody()).build()
    }.build()

    @Test fun animeCalendarPaginatesIncludesFutureEventsAndDateOnlyPremieresWithoutCredentials() = runBlocking {
        val range = WatchingRequest(WatchingCategory.ANIME, "2026-11-02", "2026-11-09")
        val start = WatchingDates.millis(range.start, AnimeSeason.japan) / 1000
        var calls = 0
        val client = client { request ->
            assertNull(request.header("Authorization"))
            val buffer = Buffer(); request.body!!.writeTo(buffer)
            val body = JSONObject(buffer.readUtf8())
            val variables = body.getJSONObject("variables")
            calls++
            if (body.getString("query").contains("WatchingPremieres")) {
                assertTrue(body.getString("query").contains("${'$'}start:FuzzyDateInt!"))
                assertTrue(body.getString("query").contains("${'$'}end:FuzzyDateInt!"))
                assertEquals(20261101, variables.getInt("start"))
                """{"data":{"Page":{"pageInfo":{"hasNextPage":false},"media":[{"id":2,"title":{"romaji":"Estreno"},"format":"MOVIE","startDate":{"year":2026,"month":11,"day":4}},{"id":3,"title":{"romaji":"Sin fecha"},"startDate":{"year":2026,"month":11}}]}}}"""
            } else {
                assertEquals(start - 1, variables.getLong("start"))
                val page = variables.getInt("page")
                """{"data":{"Page":{"pageInfo":{"hasNextPage":${page == 1}},"airingSchedules":[{"episode":$page,"airingAt":${start + 3600 * page},"media":{"id":1,"type":"ANIME","title":{"romaji":"Anime"}}}]}}}"""
            }
        }
        try {
            val events = AnimeSeasonRepository(client).calendar(range)
            assertEquals(3, calls); assertEquals(3, events.size)
            assertEquals(listOf(1, 2), events.filter { it.episode != null }.map { it.episode })
            assertNull(events.last().at); assertEquals(MediaType.MOVIE, events.last().item.type)
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun tmdbCalendarUsesEpisodeDatesNotSeriesFirstAirDatesAndPaginatesMovies() = runBlocking {
        val range = WatchingRequest(WatchingCategory.MOVIES_TV, "2026-11-02", "2026-11-09")
        val paths = Collections.synchronizedList(mutableListOf<String>())
        val client = client { request ->
            assertEquals("api.themoviedb.org", request.url.host)
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/3/discover/movie" -> {
                    assertEquals(range.start, request.url.queryParameter("primary_release_date.gte"))
                    val page = request.url.queryParameter("page")!!.toInt()
                    """{"total_pages":2,"results":[{"id":$page,"title":"Película $page","release_date":"2026-11-03"}]}"""
                }
                "/3/discover/tv" -> {
                    assertEquals(range.start, request.url.queryParameter("air_date.gte"))
                    """{"total_pages":1,"results":[{"id":50,"name":"Serie","first_air_date":"2010-01-01"}]}"""
                }
                "/3/tv/50" -> """{"last_episode_to_air":{"season_number":4},"next_episode_to_air":{"season_number":4},"seasons":[]}"""
                "/3/tv/50/season/4" -> """{"episodes":[{"episode_number":3,"air_date":"2026-11-03"},{"episode_number":4,"air_date":"2026-11-08"},{"episode_number":5,"air_date":"2026-11-09"}]}"""
                else -> error("Unexpected path ${request.url}")
            }
        }
        try {
            val repository = AetherioRepository(readAccessToken = "fixture", client = client)
            val first = repository.watchingCalendar(range)
            assertTrue(first.hasMore)
            val second = repository.watchingCalendar(range.copy(page = 2))
            assertFalse(second.hasMore)
            val events = WatchingDates.normalize(first.events + second.events, range)
            assertEquals(4, events.size)
            assertEquals(listOf(3, 4), events.filter { it.episode != null }.map { it.episode })
            assertTrue(events.all { it.at == null }); assertEquals(2, paths.count { it == "/3/tv/50/season/4" })
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }
}
