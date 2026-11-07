package com.panoli707.aetherio.nativeapp.data

import java.io.IOException
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

class AnimeSeasonRepositoryTest {
    @Test fun discoverPassesPageSortAndGenreToAniListWithoutAuthentication() = runBlocking {
        val client = client { request ->
            val variables = variables(request)
            assertEquals(3, variables.getInt("page"))
            assertEquals("FAVOURITES_DESC", variables.getString("sort"))
            assertEquals("Mystery", variables.getString("genre"))
            200 to page(false, 900000001)
        }
        try {
            val result = AnimeSeasonRepository(client).discover("FAVOURITES_DESC", 3, "Mystery")
            assertFalse(result.hasMore)
            assertEquals("900000001", result.items.single().id)
            assertTrue(result.items.single().isAnime)
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    private val season = AnimeSeason(2026, "FALL")
    private val now = 1793610000000L
    private val item = """{"id":123,"title":{"romaji":"Fixture anime"},"season":"FALL","seasonYear":2026,"status":"RELEASING","episodes":12,"nextAiringEpisode":{"episode":6,"airingAt":1793973600}}"""
    private fun page(more: Boolean, id: Int) = """{"data":{"Page":{"pageInfo":{"hasNextPage":$more},"media":[${item.replace("123", id.toString())}]}}}"""
    private fun client(answer: (Request) -> Pair<Int, String>) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        assertEquals("https://graphql.anilist.co/", request.url.toString())
        assertEquals("POST", request.method)
        listOf("Authorization", "Cookie", "Referer").forEach { assertNull(request.header(it)) }
        val result = answer(request)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(result.first).message("Fixture").body(result.second.toResponseBody()).build()
    }.build()
    private fun variables(request: Request): JSONObject {
        val buffer = Buffer(); request.body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8()).getJSONObject("variables")
    }

    @Test fun dailyEventsAreFullyPaginatedAndCacheReevaluatesReleaseTimeAndMidnight() = runBlocking {
        val start = AnimeToday.start(now)
        val dayNow = (start + 36000) * 1000
        var calls = 0
        val client = client { request ->
            calls++
            val variables = variables(request)
            val day = if (calls <= 2) start else start + 86400
            assertEquals(day - 29 * 86400 - 1, variables.getLong("start")); assertEquals(day + 86400, variables.getLong("end"))
            val page = variables.getInt("page")
            val event = JSONObject().put("episode", page).put("airingAt", day + 36000 + if (page == 1) -1 else 1)
                .put("media", JSONObject(item).put("id", page))
            200 to JSONObject().put("data", JSONObject().put("Page", JSONObject()
                .put("pageInfo", JSONObject().put("hasNextPage", page == 1))
                .put("airingSchedules", org.json.JSONArray().put(event)))).toString()
        }
        try {
            val repository = AnimeSeasonRepository(client)
            assertEquals(listOf(1), repository.today(dayNow).map { it.episode })
            assertEquals(listOf(2, 1), repository.today(dayNow + 2000).map { it.episode })
            assertEquals(2, calls)
            repository.today(dayNow + 86400000)
            assertEquals(4, calls)
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun recentFeedLoadsAllPagesBeyondThirtyDistinctAnimeAndCachesOnlyTheCompleteResult() = runBlocking {
        var calls = 0
        val client = client { request ->
            calls++
            val page = variables(request).getInt("page")
            assertTrue("Should finish after the third page", page <= 3)
            val events = org.json.JSONArray()
            // A whole page for one anime must not count as 50 distinct anime.
            if (page == 1) repeat(50) { index ->
                events.put(JSONObject().put("episode", 50 - index).put("airingAt", now / 1000 - index)
                    .put("media", JSONObject(item)))
            } else repeat(40) { index ->
                events.put(JSONObject().put("episode", 1).put("airingAt", now / 1000 - 100 - index)
                    .put("media", JSONObject(item).put("id", page * 200 + index)))
            }
            200 to JSONObject().put("data", JSONObject().put("Page", JSONObject()
                .put("pageInfo", JSONObject().put("hasNextPage", page < 3)).put("airingSchedules", events))).toString()
        }
        try {
            val repository = AnimeSeasonRepository(client)
            val entries = repository.today(now)
            assertEquals(81, entries.size)
            assertEquals(81, entries.map { it.item.key }.toSet().size)
            assertEquals(50, entries.first().episode)
            assertEquals(3, calls)
            assertEquals(entries, repository.today(now + 1000))
            assertEquals(3, calls)
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun exhaustedRecentFeedCanReturnFewerThanThirtyWithoutInventingData() = runBlocking {
        val client = client { _ ->
            val events = org.json.JSONArray()
                .put(JSONObject().put("episode", 3).put("airingAt", now / 1000 - 86400).put("media", JSONObject(item)))
                .put(JSONObject().put("episode", 4).put("airingAt", now / 1000 + 1).put("media", JSONObject(item).put("id", 456)))
            200 to JSONObject().put("data", JSONObject().put("Page", JSONObject()
                .put("pageInfo", JSONObject().put("hasNextPage", false)).put("airingSchedules", events))).toString()
        }
        try {
            val result = AnimeSeasonRepository(client).today(now)
            assertEquals(1, result.size)
            assertEquals(3, result.single().episode)
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun aFailedDailyPageDoesNotCacheAnIncompleteListOrReplaceItWithSeasonalData() = runBlocking {
        var calls = 0
        val client = client { request ->
            calls++
            if (variables(request).getInt("page") == 1) 200 to """{"data":{"Page":{"pageInfo":{"hasNextPage":true},"airingSchedules":[]}}}"""
            else 400 to "{}"
        }
        try {
            val repository = AnimeSeasonRepository(client)
            repeat(2) {
                try { repository.today(now); fail("Expected incomplete daily feed failure") }
                catch (_: AnimeCalendarException) { }
            }
            assertEquals(4, calls)
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun allPagesAreLoadedAndOnlyACompleteSeasonIsCachedWithinItsActualQuarter() = runBlocking {
        var count = 0
        val client = client { request ->
            count++
            val variables = variables(request)
            assertEquals(2026, variables.getInt("year")); assertEquals("FALL", variables.getString("season"))
            200 to page(variables.getInt("page") == 1, variables.getInt("page"))
        }
        try {
            val repository = AnimeSeasonRepository(client)
            assertEquals(2, repository.season(season, now).size)
            assertEquals(2, repository.season(season, now + 1000).size)
            assertEquals(2, count)
            repository.season(season, now + 3600001)
            assertEquals(4, count)
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun aFailedSecondPageIsNotExposedOrCachedAsACompleteCurrentSeason() = runBlocking {
        var count = 0
        val client = client { request -> count++; if (variables(request).getInt("page") == 1) 200 to page(true, 1) else 400 to "{}" }
        try {
            val repository = AnimeSeasonRepository(client)
            repeat(2) {
                try { repository.season(season, now); fail("Expected unavailable season") }
                catch (error: IOException) { assertEquals("No se pudo consultar el calendario de anime", error.message) }
            }
            assertEquals(4, count)
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun episodeSchedulesAreFullyPaginatedAndCachedWithoutInventingUnreleasedEpisodes() = runBlocking {
        var count = 0
        val client = client { request ->
            count++
            val page = variables(request).getInt("page")
            val nodes = org.json.JSONArray().put(JSONObject().put("episode", page).put("airingAt", 1790949600))
            val schedule = JSONObject().put("nodes", nodes).put("pageInfo", JSONObject().put("hasNextPage", page == 1))
            val anime = JSONObject(item).put("airingSchedule", schedule)
            200 to JSONObject().put("data", JSONObject().put("Media", anime)).toString()
        }
        try {
            val repository = AnimeSeasonRepository(client)
            val target = AnimeSeasonProtocol.item(JSONObject(item))!!
            assertEquals(listOf(1, 2), repository.detail(target, now).episodes.map { it.number })
            assertEquals(2, repository.detail(target, now + 1000).episodes.size)
            assertEquals(2, count)
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }
}
