package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnimeTrackingApiTest {
    @Test fun malDeletionAcceptsEmptySuccessfulResponsesButStillRejectsHttpFailures() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = AnimeTrackingApi(OkHttpClient(), server.url("/graph").toString(),
                server.url("/v2").toString(), spacingMs = 0)
            val entry = AnimeEntry(10, 1, 10, "Anime")
            for (status in listOf(200, 204)) {
                server.enqueue(MockResponse().setResponseCode(status))
                api.remove(AnimeService.MAL, "token", entry)
                val request = server.takeRequest()
                assertEquals("DELETE", request.method)
                assertEquals("/v2/anime/10/my_list_status", request.path)
            }
            server.enqueue(MockResponse().setBody("[]"))
            api.remove(AnimeService.MAL, "token", entry)
            assertEquals("DELETE", server.takeRequest().method)
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody("""{"id":10,"my_list_status":{"status":"watching"}}"""))
            try {
                api.remove(AnimeService.MAL, "token", entry)
                fail("An unsuccessful deletion must not be accepted")
            } catch (e: AnimeApiException) { assertEquals(503, e.status) }
        } finally { server.shutdown() }
    }
    @Test fun malDeletionReconcilesAbsentEntriesButDoesNotIgnoreAuthorizationErrors() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = AnimeTrackingApi(OkHttpClient(), server.url("/graph").toString(),
                server.url("/v2").toString(), spacingMs = 0)
            val entry = AnimeEntry(10, 1, 10, "Anime")
            for (status in listOf(404, 503)) {
                server.enqueue(MockResponse().setResponseCode(status))
                server.enqueue(MockResponse().setBody("""{"id":10}"""))
                api.remove(AnimeService.MAL, "token", entry)
                assertEquals("DELETE", server.takeRequest().method)
                val verification = server.takeRequest()
                assertEquals("GET", verification.method)
                assertEquals("Bearer token", verification.getHeader("Authorization"))
            }
            server.enqueue(MockResponse().setResponseCode(401))
            try {
                api.remove(AnimeService.MAL, "token", entry)
                fail("Authorization failures must remain failures")
            } catch (e: AnimeApiException) { assertEquals(401, e.status) }
        } finally { server.shutdown() }
    }
    @Test fun brokerStartSendsOnlyChallengeAndValidatesTheImplicitAuthorizationUrl() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val pending = AnimeOAuth.pending(AnimeService.ANILIST, System.currentTimeMillis())
            val expected = AnimeOAuth.authorize("56163", pending)
            val api = AnimeTrackingApi(spacingMs = 0, brokerUrl = server.url("/").toString().removeSuffix("/"))
            server.enqueue(MockResponse().setBody(JSONObject().put("authorizationUrl", expected).toString()))
            assertEquals(expected, api.aniListAuthorization("56163", pending))
            val start = server.takeRequest()
            val body = JSONObject(start.body.readUtf8())
            assertEquals(AnimeOAuth.challenge(pending), body.getString("challenge"))
            assertFalse(body.toString().contains(pending.verifier))
            assertFalse(body.has("client_secret")); assertNull(start.getHeader("Authorization"))
            for (wrong in listOf("https://evil.example/", expected.replace("response_type=token", "response_type=code"), expected.replace("56163", "12345"),
                expected + "&redirect_uri=https%3A%2F%2Fauth.example%2Fcallback", expected + "&response_type=token")) {
                server.enqueue(MockResponse().setBody(JSONObject().put("authorizationUrl", wrong).toString()))
                try { api.aniListAuthorization("56163", pending); fail("Foreign authorization URL accepted") }
                catch (_: IllegalArgumentException) { }
            }
        } finally { server.shutdown() }
    }
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
    @Test fun anilistRemovalResolvesAndDeletesTheListEntryIdNotTheAnimeId() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = AnimeTrackingApi(OkHttpClient(), server.url("/graph").toString(), spacingMs = 0)
            server.enqueue(MockResponse().setBody("""{"data":{"Media":{"isFavourite":false,"mediaListEntry":{"id":99,"status":"CURRENT","progress":2,"score":80}}}}"""))
            server.enqueue(MockResponse().setBody("""{"data":{"DeleteMediaListEntry":{"deleted":true}}}"""))
            api.remove(AnimeService.ANILIST, "token", AnimeEntry(42, 42, null, "Anime"))
            server.takeRequest()
            val deletion = JSONObject(server.takeRequest().body.readUtf8())
            assertTrue(deletion.getString("query").contains("DeleteMediaListEntry"))
            assertEquals(99, deletion.getJSONObject("variables").getInt("id"))
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
