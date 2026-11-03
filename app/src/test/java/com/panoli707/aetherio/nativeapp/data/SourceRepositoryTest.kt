package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SourceRepositoryTest {
    private val manifest = """{"id":"org.example.addon","name":"TLS addon","types":["movie","series"],"resources":["stream"]}"""
    private val movie = MediaItem("550", MediaType.MOVIE, "Movie", imdbId = "tt0137523")
    private val series = MediaItem("tt0903747", MediaType.SERIES, "Series", source = CatalogSource.CINEMETA)

    // The private CA and custom trust configuration exist exclusively in these tests.
    private class TlsFixture(listener: EventListener = EventListener.NONE, requestTimeoutMs: Long = 20_000, attempts: Int = 1) : Closeable {
        val server = MockWebServer()
        val client: OkHttpClient
        val repository: SourceRepository

        init {
            val ca = HeldCertificate.Builder().commonName("Source test CA").certificateAuthority(0).build()
            val leaf = HeldCertificate.Builder().commonName("localhost")
                .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1")
                .addSubjectAlternativeName("::1").signedBy(ca).build()
            val serverCertificates = HandshakeCertificates.Builder().heldCertificate(leaf, ca.certificate).build()
            val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(ca.certificate).build()
            server.useHttps(serverCertificates.sslSocketFactory(), false)
            server.start()
            client = OkHttpClient.Builder().sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .followRedirects(true).followSslRedirects(true).retryOnConnectionFailure(false).eventListener(listener).build()
            repository = SourceRepository(client, requestTimeoutMs, attempts)
        }

        fun url(): String = server.url("/private/config/manifest.json?token=private-secret&encoded=a%2Fb").toString()

        override fun close() {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    private fun config(fixture: TlsFixture, raw: String = manifest): AddonConfig =
        SourceProtocol.manifest(raw, SourceProtocol.manifestUrl(fixture.url()))

    private suspend fun rejects(block: suspend () -> Unit) {
        val error = try {
            block()
            throw AssertionError("Expected a sanitized failure")
        } catch (error: Exception) { error }
        // Coroutine stack recovery can wrap the sanitized exception in an identical copy.
        generateSequence(error as Throwable) { it.cause }.forEach { cause ->
            assertEquals(error.javaClass, cause.javaClass)
            assertEquals(error.message, cause.message)
            assertTrue(cause.suppressed.isEmpty())
            assertFalse(cause.message.orEmpty().contains("private-secret"))
            assertFalse(cause.message.orEmpty().contains("localhost"))
            assertFalse(cause.message.orEmpty().contains("https://"))
        }
    }

    @Test fun tlsFixtureUsesVerifiedLocalCertificate() {
        TlsFixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setBody("TLS ready"))
            fixture.client.newCall(Request.Builder().url(fixture.url()).build()).execute().use { response ->
                assertEquals("TLS ready", response.body!!.string())
            }
        }
    }

    @Test fun configureUsesRealHttpsForStringAndObjectStreamResources() = runBlocking {
        TlsFixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setBody(manifest))
            val first = fixture.repository.configure("  ${fixture.url()}  ")
            assertEquals(config(fixture), first)
            assertEquals(setOf(MediaType.MOVIE, MediaType.SERIES), first.types)
            val request = fixture.server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("GET", request.method)
            assertEquals("/private/config/manifest.json?token=private-secret&encoded=a%2Fb", request.path)
            val objects = manifest.replace("[\"stream\"]", """[{"name":"stream","types":["series"],"idPrefixes":["tt09"]}]""")
            fixture.server.enqueue(MockResponse().setBody(objects))
            val second = fixture.repository.configure(fixture.url())
            assertEquals(setOf(MediaType.SERIES), second.types)
            assertEquals(listOf("tt09"), second.idPrefixes)
            assertEquals(first.id, second.id)
        }
    }

    @Test fun configureRejectsInvalidResourcesTypesPrefixesAndShapesWithoutLeakingData() = runBlocking {
        TlsFixture().use { fixture ->
            listOf(
                manifest.replace("[\"stream\"]", "[\"catalog\"]"),
                manifest.replace("[\"movie\",\"series\"]", "[\"anime\"]"),
                manifest.replace("\"resources\":", "\"idPrefixes\":[\"kitsu:\"],\"resources\":"),
                "private-secret", "[]", """{"id":1,"name":"private-secret","types":[],"resources":[]}"""
            ).forEach { body ->
                fixture.server.enqueue(MockResponse().setBody(body))
                rejects { fixture.repository.configure(fixture.url()) }
            }
            assertEquals(6, fixture.server.requestCount)
        }
    }

    @Test fun unsafeConfigureUrlsAreRejectedBeforeAnyHttp() = runBlocking {
        TlsFixture().use { fixture ->
            listOf(fixture.url().replace("https://", "http://"),
                fixture.url().replace("https://", "https://user:private-secret@"),
                fixture.url().replace("https://", "https://@"), fixture.url() + "\n",
                fixture.url() + "#private-secret", fixture.url() + "x".repeat(8192)).forEach { url ->
                rejects { fixture.repository.configure(url) }
            }
            assertEquals(0, fixture.server.requestCount)
        }
    }

    @Test fun movieAndExplicitSeriesRequestsPreservePrivateQueryAndConfigurationPath() = runBlocking {
        TlsFixture().use { fixture ->
            val addon = config(fixture)
            fixture.server.enqueue(MockResponse().setBody("""{"streams":[{"url":"https://video.example/a.m3u8"}]}"""))
            assertEquals(1, fixture.repository.sources(addon, SourceQuery(movie)).sources.size)
            val movieRequest = fixture.server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/private/config/stream/movie/tt0137523.json?token=private-secret&encoded=a%2Fb", movieRequest.path)
            fixture.server.enqueue(MockResponse().setBody("""{"streams":[]}"""))
            assertTrue(fixture.repository.sources(addon, SourceQuery(series, 0, 3)).sources.isEmpty())
            val seriesRequest = fixture.server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals(listOf("private", "config", "stream", "series", "tt0903747:0:3.json"), seriesRequest.pathSegments)
            assertEquals("private-secret", seriesRequest.queryParameter("token"))
            assertEquals("a/b", seriesRequest.queryParameter("encoded"))
        }
    }

    @Test fun incompatibleRequestsReturnEmptyWithoutAnyNetworkOrAssumedEpisode() = runBlocking {
        TlsFixture().use { fixture ->
            val addon = config(fixture)
            val cases = listOf(
                addon.copy(types = setOf(MediaType.SERIES)) to SourceQuery(movie),
                addon.copy(types = emptySet()) to SourceQuery(movie),
                addon.copy(idPrefixes = listOf("tt99")) to SourceQuery(movie),
                addon.copy(idPrefixes = listOf("kitsu:")) to SourceQuery(movie),
                addon to SourceQuery(movie.copy(imdbId = null)),
                addon to SourceQuery(movie.copy(imdbId = "tt1234")),
                addon to SourceQuery(series), addon to SourceQuery(series, 1, null),
                addon to SourceQuery(series, -1, 1), addon to SourceQuery(series, 1, 0)
            )
            cases.forEach { (config, query) ->
                assertEquals(SourceResult(emptyList()), fixture.repository.sources(config, query))
            }
            assertEquals(0, fixture.server.requestCount)
        }
    }

    @Test fun streamsNormalizeDirectHttpsAndCountUnsupportedWithoutRejectingNotWebReady() = runBlocking {
        TlsFixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setBody("""{"streams":[
                {"url":"HTTPS://VIDEO.EXAMPLE:443/a/../ok.m3u8","behaviorHints":{"notWebReady":true}},
                {"url":"https://video.example/ok.m3u8"},
                {"url":"https://video.example/torrent","infoHash":"hash"},
                {"url":"https://video.example/header","behaviorHints":{"proxyHeaders":{"request":{"Cookie":"private-secret"}}}},
                {"url":"http://video.example/insecure"},{"externalUrl":"https://video.example/external"},
                {"url":"https://video.example/drm","drm":true}]}"""))
            val result = fixture.repository.sources(config(fixture), SourceQuery(movie))
            assertEquals(1, result.sources.size)
            assertEquals("https://video.example/ok.m3u8", result.sources.single().url)
            assertEquals("TLS addon", result.sources.single().addonName)
            assertEquals(5, result.unsupportedCount)
        }
    }

    @Test fun injectedRedirectSettingsAreOverriddenForManifestAndStreamRequests() = runBlocking {
        TlsFixture().use { fixture ->
            val destination = fixture.server.url("/redirect/manifest.json?token=private-secret")
            fixture.server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", destination).setBody("private-secret"))
            fixture.server.enqueue(MockResponse().setBody(manifest))
            rejects { fixture.repository.configure(fixture.url()) }
            assertEquals(1, fixture.server.requestCount)
            // Consume the queued success normally, then independently exercise a stream redirect.
            fixture.repository.configure(fixture.url())
            fixture.server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", destination))
            fixture.server.enqueue(MockResponse().setBody("""{"streams":[]}"""))
            rejects { fixture.repository.sources(config(fixture), SourceQuery(movie)) }
            assertEquals(3, fixture.server.requestCount)
        }
    }

    @Test fun httpFailuresMalformedBodiesAndNetworkFailuresHaveNoRawUrlOrCause() = runBlocking {
        TlsFixture().use { fixture ->
            listOf(401, 403, 500).forEach { code ->
                fixture.server.enqueue(MockResponse().setResponseCode(code).setBody(fixture.url()))
                rejects { fixture.repository.configure(fixture.url()) }
            }
            listOf("private-secret", "[]", """{"streams":{}}""").forEach { body ->
                fixture.server.enqueue(MockResponse().setBody(body))
                rejects { fixture.repository.sources(config(fixture), SourceQuery(movie)) }
            }
            fixture.server.enqueue(MockResponse().setResponseCode(503).setBody(fixture.url()))
            rejects { fixture.repository.sources(config(fixture), SourceQuery(movie)) }
            fixture.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            rejects { fixture.repository.configure(fixture.url()) }
        }
    }

    @Test fun declaredAndChunkedManifestBodiesAndChunkedStreamsAreBounded() = runBlocking {
        TlsFixture().use { fixture ->
            val oversizedManifest = " ".repeat(SourceProtocol.MANIFEST_LIMIT + 1)
            fixture.server.enqueue(MockResponse().setBody(oversizedManifest))
            rejects { fixture.repository.configure(fixture.url()) }
            fixture.server.enqueue(MockResponse().setChunkedBody(oversizedManifest, 4096))
            rejects { fixture.repository.configure(fixture.url()) }
            fixture.server.enqueue(MockResponse().setChunkedBody(" ".repeat(SourceProtocol.STREAM_LIMIT + 1), 4096))
            rejects { fixture.repository.sources(config(fixture), SourceQuery(movie)) }
        }
    }

    @Test fun cancellingAfterResponseHeadersCancelsOkHttpWhileBodyIsStillBeingRead() = runBlocking {
        val bodyStarted = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val activeCall = AtomicReference<Call>()
        val listener = object : EventListener() {
            override fun callStart(call: Call) { activeCall.set(call) }
            override fun responseBodyStart(call: Call) { bodyStarted.countDown() }
            override fun canceled(call: Call) { cancelled.countDown() }
        }
        TlsFixture(listener, attempts = 2).use { fixture ->
            fixture.server.enqueue(MockResponse().setBody(manifest).throttleBody(1, 100, TimeUnit.MILLISECONDS))
            val job = launch(Dispatchers.Default) { fixture.repository.configure(fixture.url()) }
            try {
                assertTrue("Response body did not start", bodyStarted.await(5, TimeUnit.SECONDS))
                withTimeout(5000) { job.cancelAndJoin() }
                assertTrue("OkHttp did not receive cancellation", cancelled.await(5, TimeUnit.SECONDS))
                assertTrue(activeCall.get().isCanceled())
                assertTrue(job.isCancelled)
                assertEquals(1, fixture.server.requestCount)
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    @Test fun timeoutHasAtMostTwoAttemptsAndMalformedJsonIsNotRetried() = runBlocking {
        TlsFixture(requestTimeoutMs = 500, attempts = 2).use { fixture ->
            repeat(2) { fixture.server.enqueue(MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setBody("""{"streams":[]}""")) }
            val start = System.nanoTime()
            rejects { fixture.repository.sources(config(fixture), SourceQuery(movie)) }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 6000)
            assertTrue(fixture.server.requestCount in 1..2)
        }
        TlsFixture(attempts = 2).use { fixture ->
            fixture.server.enqueue(MockResponse().setBody("invalid json"))
            rejects { fixture.repository.sources(config(fixture), SourceQuery(movie)) }
            assertEquals(1, fixture.server.requestCount)
        }
    }
}
