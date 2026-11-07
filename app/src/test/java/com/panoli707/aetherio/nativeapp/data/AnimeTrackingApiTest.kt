package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnimeTrackingApiTest {
    @Test fun anilistListChunksAndFavoritesAreMergedWithoutInventingFavoriteListStatus() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val media1 = """{"id":1,"idMal":10,"title":{"userPreferred":"First"},"episodes":12,"format":"TV"}"""
            val media2 = """{"id":2,"idMal":20,"title":{"userPreferred":"Second"},"episodes":1,"format":"MOVIE"}"""
            server.enqueue(MockResponse().setBody("""{"data":{"MediaListCollection":{"hasNextChunk":true,"lists":[{"entries":[{"status":"CURRENT","progress":3,"score":80,"media":$media1}]}]}}}"""))
            server.enqueue(MockResponse().setBody("""{"data":{"MediaListCollection":{"hasNextChunk":false,"lists":[]}}}"""))
            server.enqueue(MockResponse().setBody("""{"data":{"User":{"favourites":{"anime":{"pageInfo":{"hasNextPage":false},"nodes":[$media1,$media2]}}}}}"""))
            val api = AnimeTrackingApi(OkHttpClient(), server.url("/graph").toString(), spacingMs = 0)
            val entries = api.list(AnimeService.ANILIST, AnimeAccount(42, "Test", null), "token")
            assertEquals(2, entries.size)
            assertEquals(AnimeStatus.WATCHING, entries.first().status)
            assertEquals(3, entries.first().progress)
            assertTrue(entries.all { it.favorite })
            assertNull(entries.last().status)
            assertEquals(1, JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("variables").getInt("chunk"))
            assertEquals(2, JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("variables").getInt("chunk"))
        } finally { server.shutdown() }
    }
    @Test fun malExchangeAndRefreshDoNotSendAClientSecretAndPreserveRotatedTokens() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = AnimeTrackingApi(OkHttpClient(), server.url("/graph").toString(), server.url("/v2").toString(), server.url("/token").toString(), 0)
            server.enqueue(MockResponse().setBody("""{"token_type":"Bearer","access_token":"one","refresh_token":"refresh-one","expires_in":3600}"""))
            val first = api.malToken("public-id", OAuthPending(AnimeService.MAL, "state", "verifier", 1), "code")
            val request = server.takeRequest()
            val body = request.body.readUtf8()
            assertTrue(body.contains("code_verifier=verifier"))
            assertTrue(body.contains("grant_type=authorization_code"))
            assertFalse(body.contains("client_secret"))
            assertNull(request.getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"token_type":"Bearer","access_token":"two","refresh_token":"refresh-two","expires_in":3600}"""))
            val second = api.malToken("public-id", null, first.refresh)
            assertEquals("refresh-two", second.refresh)
            assertEquals("two", second.access)
            assertTrue(server.takeRequest().body.readUtf8().contains("grant_type=refresh_token"))
        } finally { server.shutdown() }
    }
    @Test fun anilistMutationUsesScoreRawAndDoesNotExposeTokensInPayload() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = AnimeTrackingApi(OkHttpClient(), server.url("/graph").toString(), spacingMs = 0)
            server.enqueue(MockResponse().setBody("""{"data":{"SaveMediaListEntry":{"status":"CURRENT","progress":4,"score":85}}}"""))
            api.update(AnimeService.ANILIST, "user-token", AnimeEntry(42, 42, null, "Anime", episodes = 12), AnimeStatus.WATCHING, 4, 85.0)
            val request = server.takeRequest()
            assertEquals("Bearer user-token", request.getHeader("Authorization"))
            val json = JSONObject(request.body.readUtf8())
            assertTrue(json.getString("query").contains("scoreRaw:"))
            assertEquals(85, json.getJSONObject("variables").getInt("score"))
            assertFalse(json.toString().contains("user-token"))
        } finally { server.shutdown() }
    }
    @Test fun rateLimitsAndGraphqlErrorsRemainFailuresRatherThanEmptySuccessfulLists() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"errors":[{"message":"private failure","status":401}]}"""))
            val api = AnimeTrackingApi(OkHttpClient(), server.url("/graph").toString(), spacingMs = 0)
            try { api.account(AnimeService.ANILIST, "private-token"); fail("Expected error") }
            catch (e: AnimeApiException) { assertEquals(401, e.status); assertFalse(e.message.orEmpty().contains("private")) }
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "5"))
            try { api.account(AnimeService.ANILIST, "private-token"); fail("Expected rate limit") }
            catch (e: AnimeApiException) { assertEquals(5, e.retrySeconds); assertEquals(429, e.status) }
        } finally { server.shutdown() }
    }
}
