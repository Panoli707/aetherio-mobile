package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AetherioRepositoryTest {
    private val movie = MediaItem("550", MediaType.MOVIE, "Fight Club")
    private val series = MediaItem("1396", MediaType.SERIES, "Breaking Bad")

    // External origins are intercepted, never contacted. Local requests use real verified TLS.
    private class TlsFixture(listener: EventListener = EventListener.NONE) : Closeable {
        val server = MockWebServer()
        val externalRequests: MutableList<Request> = Collections.synchronizedList(mutableListOf())
        val client: OkHttpClient

        init {
            val ca = HeldCertificate.Builder().commonName("Catalog test CA").certificateAuthority(0).build()
            val leaf = HeldCertificate.Builder().commonName("localhost")
                .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1")
                .addSubjectAlternativeName("::1").signedBy(ca).build()
            val serverCertificates = HandshakeCertificates.Builder().heldCertificate(leaf, ca.certificate).build()
            val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(ca.certificate).build()
            server.protocols = listOf(Protocol.HTTP_1_1)
            server.useHttps(serverCertificates.sslSocketFactory(), false)
            server.start()
            client = OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .protocols(listOf(Protocol.HTTP_1_1))
                .followRedirects(true).followSslRedirects(true).retryOnConnectionFailure(false)
                .eventListener(listener)
                .addInterceptor { chain ->
                    val request = chain.request()
                    if (request.url.host in setOf("localhost", "127.0.0.1", "::1")) chain.proceed(request)
                    else {
                        externalRequests.add(request)
                        val body = when (request.url.host) {
                            "api.themoviedb.org", "trkll.aetherio.workers.dev" -> CATALOG
                            "v3-cinemeta.strem.io" -> if (request.url.encodedPath.startsWith("/meta/")) CINEMETA_DETAIL else CINEMETA_CATALOG
                            else -> "{}"
                        }
                        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .body(body.toResponseBody()).build()
                    }
                }.build()
        }

        fun repository(token: String = TOKEN, base: HttpUrl = server.url(if (token.isNotBlank()) "/3" else "/api/tmdb")) =
            AetherioRepository(readAccessToken = token, client = client, tmdbBase = base)

        fun request(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)
            ?: throw AssertionError("Expected catalog request")

        override fun close() {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    private fun authenticated(request: RecordedRequest, path: String, language: Boolean = true) {
        assertEquals("GET", request.method)
        assertEquals("Bearer $TOKEN", request.getHeader("Authorization"))
        val url = request.requestUrl!!
        assertEquals(path, url.encodedPath)
        assertNull(url.queryParameter("api_key"))
        assertFalse(url.encodedQuery.orEmpty().contains(TOKEN))
        if (language) {
            assertEquals("es-ES", url.queryParameter("language"))
            assertEquals("1", url.queryParameter("page"))
        }
    }

    private suspend fun rejects(block: suspend () -> Unit) {
        val error = try {
            block()
            throw AssertionError("Expected sanitized catalog failure")
        } catch (error: IOException) { error }
        // Stack recovery may copy an IOException, but must never retain the original network/JSON cause.
        generateSequence(error as Throwable) { it.cause }.forEach { cause ->
            assertEquals(IOException::class.java, cause.javaClass)
            assertEquals(error.message, cause.message)
            assertTrue(cause.suppressed.isEmpty())
            assertFalse(cause.message.orEmpty().contains(TOKEN))
            assertFalse(cause.message.orEmpty().contains("localhost"))
            assertFalse(cause.message.orEmpty().contains("https://"))
        }
    }

    @Test fun tokenSelectsOfficialApiAndEmptyOrBlankTokenPreservesShippedWorker() = runBlocking {
        TlsFixture().use { fixture ->
            listOf(TOKEN, "", "  ").forEach { token ->
                val repository = AetherioRepository(readAccessToken = token, client = fixture.client)
                assertEquals("550", repository.search("Fight Club").single().id)
                val request = fixture.externalRequests.last()
                assertEquals(if (token.isNotBlank()) "api.themoviedb.org" else "trkll.aetherio.workers.dev", request.url.host)
                assertEquals(if (token.isNotBlank()) "/3/search/multi" else "/api/tmdb/search/multi", request.url.encodedPath)
                assertTrue(request.url.isHttps)
                assertEquals(if (token.isNotBlank()) "Bearer $TOKEN" else null, request.header("Authorization"))
                assertEquals("es-ES", request.url.queryParameter("language"))
                assertNull(request.url.queryParameter("api_key"))
                assertFalse(request.url.encodedQuery.orEmpty().contains(TOKEN))
            }
            assertEquals(3, fixture.externalRequests.size)
            assertEquals(0, fixture.server.requestCount)
        }
    }

    @Test fun trendingAndDiscoverRequestsUseScopedAuthorizationAndSpanishLanguage() = runBlocking {
        TlsFixture().use { fixture ->
            fixture.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setBody(CATALOG)
            }
            val home = fixture.repository().home()
            assertEquals(7, home.sections.size)
            assertFalse(home.partial)
            assertEquals("550", home.heroes.single().id)
            val requests = List(7) { fixture.request() }
            assertEquals(setOf("/3/trending/movie/day", "/3/trending/tv/day"),
                requests.map { it.requestUrl!!.encodedPath }.filter { it.contains("/trending/") }.toSet())
            requests.forEach { request -> authenticated(request, request.requestUrl!!.encodedPath) }
            assertEquals(5, requests.count { it.requestUrl!!.encodedPath.startsWith("/3/discover/") })
            assertEquals(0, fixture.externalRequests.size)
        }
    }

    @Test fun searchDetailSeasonAndHeroImagesUseAuthorizationWithoutApiKey() = runBlocking {
        TlsFixture().use { fixture ->
            val repository = fixture.repository()
            fixture.server.enqueue(MockResponse().setBody(CATALOG))
            assertEquals("550", repository.search("Fight Club & test").single().id)
            val search = fixture.request()
            authenticated(search, "/3/search/multi")
            assertEquals("Fight Club & test", search.requestUrl!!.queryParameter("query"))
            assertEquals("false", search.requestUrl!!.queryParameter("include_adult"))

            fixture.server.enqueue(MockResponse().setBody(DETAIL))
            assertEquals("550", repository.detail(movie).item.id)
            val detail = fixture.request()
            authenticated(detail, "/3/movie/550")
            assertEquals("images,credits,external_ids", detail.requestUrl!!.queryParameter("append_to_response"))

            fixture.server.enqueue(MockResponse().setBody(EPISODES))
            assertEquals(listOf(EpisodeItem(1, 1, "Pilot")), repository.episodes(series, 1))
            authenticated(fixture.request(), "/3/tv/1396/season/1")

            fixture.server.enqueue(MockResponse().setBody(LOGOS))
            assertEquals("https://image.tmdb.org/t/p/w500/logo.png", repository.heroes(listOf(movie)).single().logoUrl)
            val images = fixture.request()
            authenticated(images, "/3/movie/550/images", language = false)
            assertEquals("es,en,null", images.requestUrl!!.queryParameter("include_image_language"))
            assertEquals(0, fixture.externalRequests.size)
        }
    }

    @Test fun unauthenticatedWorkerStillFollowsHttpsRedirectsAndLimitsHeroRequests() = runBlocking {
        TlsFixture().use { fixture ->
            val repository = fixture.repository(token = "")
            fixture.server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", fixture.server.url("/legacy/movie/550")))
            fixture.server.enqueue(MockResponse().setBody(DETAIL))
            assertEquals("550", repository.detail(movie).item.id)
            listOf("/api/tmdb/movie/550", "/legacy/movie/550").forEach { path ->
                val request = fixture.request()
                assertEquals(path, request.requestUrl!!.encodedPath)
                assertNull(request.getHeader("Authorization"))
                assertNull(request.requestUrl!!.queryParameter("api_key"))
            }
            repeat(4) { fixture.server.enqueue(MockResponse().setBody(LOGOS)) }
            assertEquals(4, repository.heroes(List(5) { movie }).size)
            repeat(4) {
                val request = fixture.request()
                assertEquals("/api/tmdb/movie/550/images", request.requestUrl!!.encodedPath)
                assertNull(request.getHeader("Authorization"))
            }
            val cine = movie.copy(id = "tt0137523", source = CatalogSource.CINEMETA)
            val withLogo = movie.copy(logoUrl = "https://image.tmdb.org/t/p/w500/logo.png")
            assertEquals(listOf(cine, withLogo), repository.heroes(listOf(cine, withLogo)))
            assertEquals(6, fixture.server.requestCount)
        }
    }

    @Test fun authenticatedRedirectsNeverReachAnotherOriginOrPath() = runBlocking {
        TlsFixture().use { fixture ->
            TlsFixture().use { destination ->
                destination.server.enqueue(MockResponse().setBody(DETAIL))
                val repository = fixture.repository()
                val targets = listOf(
                    destination.server.url("/3/movie/550"), fixture.server.url("/outside/movie/550"),
                    fixture.server.url("/3/movie/551"), "https://v3-cinemeta.strem.io/meta/movie/tt0137523.json".toHttpUrl(),
                    "https://trkll.aetherio.workers.dev/api/tmdb/movie/550".toHttpUrl(),
                    "https://image.tmdb.org/t/p/w500/logo.png".toHttpUrl(), "https://github.com/".toHttpUrl()
                )
                targets.forEachIndexed { index, target ->
                    fixture.server.enqueue(MockResponse().setResponseCode(if (index % 2 == 0) 302 else 307).addHeader("Location", target))
                    rejects { repository.detail(movie) }
                    authenticated(fixture.request(), "/3/movie/550")
                    assertEquals(index + 1, fixture.server.requestCount)
                    assertEquals(0, destination.server.requestCount)
                    assertEquals(0, fixture.externalRequests.size)
                }
            }
        }
    }

    @Test fun failedTmdbSearchFallsBackToCinemetaWithoutForwardingAuthorization() = runBlocking {
        TlsFixture().use { fixture ->
            val repository = fixture.repository()
            fixture.server.enqueue(MockResponse().setResponseCode(503).setBody(TOKEN))
            val results = repository.search("Fight Club")
            assertEquals(2, results.size)
            assertTrue(results.all { it.source == CatalogSource.CINEMETA })
            authenticated(fixture.request(), "/3/search/multi")
            assertEquals("tt0137523", repository.detail(results.first { it.type == MediaType.MOVIE }).item.id)
            assertEquals(listOf(EpisodeItem(1, 1, "Pilot")), repository.episodes(results.first { it.type == MediaType.SERIES }, 1))
            assertEquals(4, fixture.externalRequests.size)
            fixture.externalRequests.forEach { request ->
                assertEquals("v3-cinemeta.strem.io", request.url.host)
                assertNull(request.header("Authorization"))
                assertNull(request.url.queryParameter("api_key"))
            }
        }
    }

    @Test fun failuresAndUnsafeBasesAreSanitizedAndResponseBodiesRemainBounded() = runBlocking {
        TlsFixture().use { fixture ->
            val repository = fixture.repository()
            listOf(401, 403, 500).forEach { code ->
                fixture.server.enqueue(MockResponse().setResponseCode(code).setBody("${fixture.server.url("/3")}?marker=$TOKEN"))
                rejects { repository.detail(movie) }
            }
            listOf(TOKEN, "[]", "{\"id\":").forEach { body ->
                fixture.server.enqueue(MockResponse().setBody(body))
                rejects { repository.detail(movie) }
            }
            val oversized = " ".repeat(4 * 1024 * 1024 + 1)
            fixture.server.enqueue(MockResponse().setBody(oversized))
            rejects { repository.detail(movie) }
            fixture.server.enqueue(MockResponse().setChunkedBody(oversized, 4096))
            rejects { repository.detail(movie) }
            fixture.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            rejects { repository.detail(movie) }
            assertEquals(9, fixture.server.requestCount)

            listOf("http://localhost/3", "https://localhost/outside", "https://localhost/3?marker=$TOKEN",
                "https://localhost/3#$TOKEN", "https://user:$TOKEN@localhost/3",
                "https://trkll.aetherio.workers.dev/3", "https://v3-cinemeta.strem.io/3",
                "https://image.tmdb.org/3", "https://github.com/3").forEach { base ->
                rejects { fixture.repository(base = base.toHttpUrl()) }
            }
            assertEquals(9, fixture.server.requestCount)
            assertEquals(0, fixture.externalRequests.size)
        }
    }

    @Test fun cancellationAfterHeadersCancelsAuthenticatedCallDuringBodyRead() = runBlocking {
        val bodyStarted = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val activeCall = AtomicReference<Call>()
        val listener = object : EventListener() {
            override fun callStart(call: Call) { activeCall.set(call) }
            override fun responseBodyStart(call: Call) { bodyStarted.countDown() }
            override fun canceled(call: Call) { cancelled.countDown() }
        }
        TlsFixture(listener).use { fixture ->
            fixture.server.enqueue(MockResponse().setBody(DETAIL).throttleBody(1, 100, TimeUnit.MILLISECONDS))
            val repository = fixture.repository()
            val job = launch(Dispatchers.Default) { repository.detail(movie) }
            try {
                assertTrue("Response body did not start", bodyStarted.await(5, TimeUnit.SECONDS))
                withTimeout(5000) { job.cancelAndJoin() }
                assertTrue("OkHttp did not receive cancellation", cancelled.await(5, TimeUnit.SECONDS))
                assertTrue(activeCall.get().isCanceled())
                assertTrue(job.isCancelled)
                authenticated(fixture.request(), "/3/movie/550")
                assertEquals(1, fixture.server.requestCount)
                assertEquals(0, fixture.externalRequests.size)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    private companion object {
        const val TOKEN = "synthetic-read-token"
        const val CATALOG = """{"results":[{"id":550,"media_type":"movie","title":"Fight Club","name":"Fight Club"}]}"""
        const val DETAIL = """{"id":550,"title":"Fight Club","external_ids":{"imdb_id":"tt0137523"}}"""
        const val EPISODES = """{"episodes":[{"episode_number":1,"name":"Pilot"}]}"""
        const val LOGOS = """{"logos":[{"iso_639_1":"es","file_path":"/logo.png"}]}"""
        const val CINEMETA_CATALOG = """{"metas":[{"id":"tt0137523","name":"Fight Club"}]}"""
        const val CINEMETA_DETAIL = """{"meta":{"id":"tt0137523","name":"Fight Club","videos":[{"season":1,"episode":1,"title":"Pilot"}]}}"""
    }
}
