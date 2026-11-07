package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimeAccountSecurityTest {
    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun base(server: MockWebServer) = server.url("/").newBuilder().host("127.0.0.1").build().toString().removeSuffix("/")
    @Test fun oauthStateIsOneUseTokensAreEncryptedAndDisconnectOnlyClearsItsService() = runBlocking {
        val namespace = "aetherio-account-test-${UUID.randomUUID()}"
        val secrets = PrivateStore(context(), namespace)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = JSONObject(request.body.readUtf8())
                if (request.path == "/start") return MockResponse().setBody(JSONObject().put("authorizationUrl",
                    AnimeOAuth.authorize("56163", OAuthPending(AnimeService.ANILIST, body.getString("state"), "", 0))).toString())
                if (request.path == "/claim") {
                    assertEquals(96, body.getString("verifier").length)
                    assertEquals("f".repeat(64), body.getString("ticket"))
                    assertNull(request.getHeader("Authorization"))
                    return MockResponse().setBody("""{"access_token":"private-test-token","token_type":"Bearer","expires_in":3600}""")
                }
                val query = body.getString("query")
                val data = when {
                    "Viewer" in query -> """{"Viewer":{"id":42,"name":"QA account","avatar":{}}}"""
                    "MediaListCollection" in query -> """{"MediaListCollection":{"hasNextChunk":false,"lists":[]}}"""
                    else -> """{"User":{"favourites":{"anime":{"pageInfo":{"hasNextPage":false},"nodes":[]}}}}"""
                }
                return MockResponse().setBody("""{"data":$data}""")
            }
        }
        server.start()
        try {
            val store = AnimeTrackingStore(context(), AnimeTrackingApi(graphUrl = "${base(server)}/graph", spacingMs = 0, brokerUrl = base(server)), namespace)
            store.begin(AnimeService.ANILIST)
            val pending = OAuthPending.decode(requireNotNull(secrets.read("pending_ANILIST")))
            val raw = "aetherio://oauth/anilist#access_token=${"t".repeat(40)}&token_type=Bearer&expires_in=3600&state=${pending.state}"
            store.finish(raw.replace(pending.state, "wrong-state"))
            assertNull(store.state.value.connections.getValue(AnimeService.ANILIST).account)
            store.finish(raw)
            assertEquals("QA account", store.state.value.connections.getValue(AnimeService.ANILIST).account?.name)
            assertNull(secrets.read("pending_ANILIST"))
            assertFalse(context().getSharedPreferences(namespace, Context.MODE_PRIVATE).all.toString().contains("private-test-token"))
            val count = server.requestCount
            store.finish(raw)
            assertEquals("A replay must never reach the network", count, server.requestCount)
            secrets.write("token_MAL", OAuthSession("other-service-token", "refresh", System.currentTimeMillis() + 3600_000).encode())
            store.disconnect(AnimeService.ANILIST)
            assertNull(secrets.read("token_ANILIST"))
            assertNotNull(secrets.read("token_MAL"))
            assertNull(store.state.value.connections.getValue(AnimeService.ANILIST).account)
        } finally {
            listOf("pending_ANILIST", "token_ANILIST", "list_ANILIST", "token_MAL").forEach { secrets.write(it, null) }
            server.shutdown()
        }
    }
    @Test fun malRefreshRotationIsDurableAndRestorable() = runBlocking {
        val namespace = "aetherio-account-test-${UUID.randomUUID()}"
        val secrets = PrivateStore(context(), namespace)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path?.substringBefore('?')) {
                "/token" -> MockResponse().setBody("""{"token_type":"Bearer","access_token":"rotated","refresh_token":"rotated-refresh","expires_in":3600}""")
                "/v2/users/@me" -> MockResponse().setBody("""{"id":7,"name":"QA MAL"}""")
                else -> MockResponse().setBody("""{"data":[],"paging":{}}""")
            }
        }
        server.start()
        try {
            secrets.write("token_MAL", OAuthSession("expired", "old-refresh", 1).encode())
            secrets.write("list_MAL", AnimeAccountCodec.encode(AnimeConnection(AnimeAccount(7, "QA MAL", null))))
            val api = AnimeTrackingApi(malUrl = "${base(server)}/v2", malTokenUrl = "${base(server)}/token", spacingMs = 0)
            val store = AnimeTrackingStore(context(), api, namespace)
            store.restore(); store.refresh(AnimeService.MAL)
            val session = OAuthSession.decode(requireNotNull(secrets.read("token_MAL")))
            assertEquals("rotated", session.access)
            assertEquals("rotated-refresh", session.refresh)
            assertNull(store.state.value.connections.getValue(AnimeService.MAL).error)
            val restored = AnimeTrackingStore(context(), api, namespace)
            restored.restore()
            assertEquals("QA MAL", restored.state.value.connections.getValue(AnimeService.MAL).account?.name)
        } finally {
            listOf("token_MAL", "list_MAL").forEach { secrets.write(it, null) }
            server.shutdown()
        }
    }
    @Test fun externallyChangedEntryIsNotSilentlyOverwritten() = runBlocking {
        val namespace = "aetherio-account-test-${UUID.randomUUID()}"
        val secrets = PrivateStore(context(), namespace)
        val writes = AtomicInteger()
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method != "GET") writes.incrementAndGet()
                return MockResponse().setBody("""{"id":1,"my_list_status":{"status":"watching","num_episodes_watched":8,"score":9}}""")
            }
        }
        server.start()
        val entry = AnimeEntry(1, 1, 1, "Test", episodes = 12, progress = 3, score = 80.0, status = AnimeStatus.WATCHING)
        try {
            secrets.write("token_MAL", OAuthSession("test-token", "", System.currentTimeMillis() + 3600_000).encode())
            secrets.write("list_MAL", AnimeAccountCodec.encode(AnimeConnection(AnimeAccount(7, "QA", null), listOf(entry))))
            val store = AnimeTrackingStore(context(), AnimeTrackingApi(malUrl = "${base(server)}/v2", spacingMs = 0), namespace)
            store.restore()
            store.update(AnimeService.MAL, entry, AnimeStatus.WATCHING, 4, 80.0)
            assertEquals(0, writes.get())
            val connection = store.state.value.connections.getValue(AnimeService.MAL)
            assertTrue(connection.error.orEmpty().contains("No se ha sobrescrito"))
            assertEquals(8, connection.entries.single().progress)
        } finally {
            listOf("token_MAL", "list_MAL").forEach { secrets.write(it, null) }
            server.shutdown()
        }
    }
}
