package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class HomeAnimeRepositoryTest {
    private fun reply(request: Request, body: String, code: Int = 200, headers: Map<String,String> = emptyMap()) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
            .apply { headers.forEach { (key,value) -> header(key,value) } }.body(body.toResponseBody()).build()

    @Test fun desktopProxyAndMalBatchPreserveRecommendationOrderWithoutCredentials() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            synchronized(requests) { requests += request }
            if (request.url.host == "trkll.aetherio.workers.dev") reply(request,
                """{"data":[{"entry":[{"mal_id":20,"title":"Two"},{"mal_id":10,"title":"One"}]}]}""")
            else reply(request, """{"data":{"Page":{"media":[
                {"id":1010,"idMal":10,"title":{"romaji":"One","english":"One"},"format":"TV","startDate":{"year":2006}},
                {"id":1020,"idMal":20,"title":{"romaji":"Two","english":"Two"},"format":"TV","startDate":{"year":2007}}
            ]}}}""")
        }.build()
        val entries = HomeAnimeRepository(client, AnimeSeasonRepository(client)).fetch(AnimeFeed(jikan = HomeRequest("recommendations/anime", mapOf("limit" to "25"))))
        assertEquals(listOf(20,10), entries.map { it.malId })
        assertEquals(listOf("1020","1010"), entries.map { it.item.id })
        assertEquals(2, requests.size)
        assertEquals("/api/jikan/recommendations/anime", requests.first().url.encodedPath)
        assertEquals("25", requests.first().url.queryParameter("limit"))
        assertEquals("application/json", requests.first().header("Accept"))
        val body = Buffer().also { requests.last().body!!.writeTo(it) }.readUtf8()
        assertTrue(body.contains("idMal_in"))
        requests.forEach { assertNull(it.header("Authorization")); assertNull(it.header("Cookie")) }
    }
    @Test fun upstreamErrorHeaderOpensCircuitAfterFourFailuresInsteadOfReturningFakeData() = runBlocking {
        var count = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            count++
            reply(chain.request(), """{"data":[{"mal_id":10,"title":"Must not be accepted"}]}""", headers = mapOf("X-Aetherio-Upstream-Error" to "true"))
        }.build()
        val source = HomeAnimeRepository(client, AnimeSeasonRepository(client))
        repeat(5) { assertTrue(source.fetch(AnimeFeed(jikan = HomeRequest("top/anime"))).isEmpty()) }
        assertEquals(4, count)
    }
    @Test fun retryAfterStopsFurtherRequestsWithoutBlockingHomeForTheCooldown() = runBlocking {
        var count = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            count++
            reply(chain.request(), "{}", code = 429, headers = mapOf("Retry-After" to "60"))
        }.build()
        val source = HomeAnimeRepository(client, AnimeSeasonRepository(client))
        repeat(3) { assertTrue(source.fetch(AnimeFeed(jikan = HomeRequest("top/anime"))).isEmpty()) }
        assertEquals(1, count)
    }
}
