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
