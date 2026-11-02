package com.panoli707.aetherio.nativeapp.data

import com.panoli707.aetherio.nativeapp.player.VideoFormat
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class AnimeAv1RepositoryTest {
    private val series = MediaItem("fixture", MediaType.SERIES, "Blue Sky")
    private val query = SourceQuery(series, 1, 3)

    // The application interceptor terminates every request before DNS, TLS, or any live HTTP.
    private class Fixture(
        listener: EventListener = EventListener.NONE,
        answer: (Request, Call) -> Response
    ) : Closeable {
        val requests = CopyOnWriteArrayList<Request>()
        val client = OkHttpClient.Builder().eventListener(listener).addInterceptor { chain ->
            requests += chain.request()
            answer(chain.request(), chain.call())
        }.build()
        val repository = AnimeAv1Repository(client)

        override fun close() {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    private fun response(
        request: Request,
        text: String = "",
        code: Int = 200,
        location: String? = null,
        body: ResponseBody = text.toResponseBody()
    ): Response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
        .body(body).apply { if (location != null) header("Location", location) }.build()

    private suspend fun rejects(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected a sanitized failure")
        } catch (error: IOException) {
            generateSequence(error as Throwable) { it.cause }.forEach { cause ->
                assertEquals(IOException::class.java, cause.javaClass)
                assertEquals("No se pudo consultar AnimeAV1", cause.message)
                assertTrue(cause.suppressed.isEmpty())
                assertFalse(cause.message.orEmpty().contains("signature"))
                assertFalse(cause.message.orEmpty().contains("https://"))
            }
        }
    }

    @Test fun exactMovieEndpointsDoNotRequireAnimeFlagOrFetchZillaPlayback() = runBlocking {
        Fixture { request, _ ->
            when (request.url.encodedPath) {
                "/catalogo/__data.json" -> response(request, AnimeAv1Fixtures.catalog("Cielo Azul" to "blue-sky"))
                "/media/blue-sky/1/__data.json" -> response(request, AnimeAv1Fixtures.episode(number = 1))
                else -> throw AssertionError("Unexpected fixture request")
            }
        }.use { fixture ->
            val desktop = DesktopSourceRepository(SourceRepository(fixture.client), fixture.repository)
            val result = desktop.sources("animeav1", SourceQuery(series.copy(type = MediaType.MOVIE, isAnime = false)))
            val source = result.sources.single()
            assertEquals(0, result.unsupportedCount)
            assertEquals("pc:animeav1", source.addonId)
            assertEquals("AnimeAV1", source.addonName)
            assertEquals("AnimeAV1", source.name)
            assertEquals("Zilla | SUB", source.description)
            assertEquals("https://player.zilla-networks.com/m3u8/fixture", source.url)
            assertEquals(VideoFormat.HLS, source.format)
            assertFalse(source.allowDesktopHttp)
            assertTrue(source.id.matches(Regex("[0-9a-f]{64}")))
            assertEquals(6, source.headers.size)
            assertEquals(2, fixture.requests.size)
            val search = fixture.requests[0]
            assertEquals("GET", search.method)
            assertEquals("https://animeav1.com/catalogo/__data.json?page=1&search=Blue%20Sky", search.url.toString())
            assertEquals("1", search.url.queryParameter("page"))
            assertEquals("Blue Sky", search.url.queryParameter("search"))
            fixture.requests.forEach {
                assertEquals(AnimeAv1Protocol.USER_AGENT, it.header("User-Agent"))
                assertNull(it.header("Cookie"))
                assertNull(it.header("Authorization"))
            }
        }
    }

    @Test fun resolversAreIsolatedAndUnknownUnsafeOrPackedEmbedsAreCountedNotPlayed() = runBlocking {
        val embeds = listOf(
            AnimeAv1Protocol.Embed("Direct", "SUB", "https://video.example/fixture.mp4?signature=fixture"),
            AnimeAv1Protocol.Embed("Zilla", "DUB", "https://player.zilla-networks.com/play/fixture"),
            AnimeAv1Protocol.Embed("Broken", "SUB", "https://www.mp4upload.com/embed-broken.html"),
            AnimeAv1Protocol.Embed("MP4Upload", "DUB", "https://www.mp4upload.com/embed-good.html"),
            AnimeAv1Protocol.Embed("YourUpload", "SUB", "https://yourupload.com/embed/fixture"),
            AnimeAv1Protocol.Embed("Unknown", "SUB", "https://unknown.example/embed/fixture"),
            AnimeAv1Protocol.Embed("Unsafe", "SUB", "http://www.mp4upload.com/embed-unsafe.html"),
            AnimeAv1Protocol.Embed("Packed", "SUB", "https://www.mp4upload.com/embed-packed.html")
        )
        Fixture { request, _ ->
            when (request.url.encodedPath) {
                "/catalogo/__data.json" -> response(request, AnimeAv1Fixtures.catalog())
                "/media/blue-sky/3/__data.json" -> response(request, AnimeAv1Fixtures.episode(embeds = embeds))
                "/embed-broken.html" -> response(request, code = 403)
                "/embed-good.html" -> response(request, "src:'https://a.mp4upload.com/d/fixture/video.mp4?signature=fixture'")
                "/embed/fixture" -> response(request, "file:'https://cdn.example/fixture.mp4?signature=fixture'")
                "/embed-packed.html" -> response(request, "eval(function(p,a,c,k,e,d){return 'packed';})")
                else -> throw AssertionError("Unexpected fixture request")
            }
        }.use { fixture ->
            val result = fixture.repository.sources(query)
            assertEquals(4, result.sources.size)
            assertEquals(4, result.unsupportedCount)
            assertEquals(6, fixture.requests.size)
            assertTrue(result.sources.any { it.description == "Zilla | DUB" })
            assertTrue(result.sources.any { it.description == "MP4Upload | DUB" })
            assertTrue(result.sources.all { !it.url.contains("embed") && !it.allowDesktopHttp })
            assertTrue(result.sources.all { it.id.matches(Regex("[0-9a-f]{64}")) })
            val yourUpload = result.sources.single { it.description.startsWith("YourUpload") }
            assertEquals("https://yourupload.com/embed/fixture", yourUpload.headers["Referer"])
            assertEquals("https://yourupload.com", yourUpload.headers["Origin"])
            assertEquals("https://www.mp4upload.com", result.sources.single { it.description.startsWith("MP4Upload") }.headers["Origin"])
            assertTrue(fixture.requests.none { it.url.host in setOf("unknown.example", "video.example", "cdn.example", "player.zilla-networks.com") })
        }
    }

    @Test fun invalidSeriesAndUnmatchedSeasonsNeverInventEpisodesOrUseFirstCatalogItem() = runBlocking {
        Fixture { request, _ -> response(request, AnimeAv1Fixtures.catalog("Other" to "other", "Blue Sky" to "blue-sky")) }
            .use { fixture ->
                listOf(SourceQuery(series), SourceQuery(series, 1), SourceQuery(series, null, 3),
                    SourceQuery(series, 0, 3), SourceQuery(series, 1, 0)).forEach {
                    assertEquals(SourceResult(emptyList()), fixture.repository.sources(it))
                }
                assertTrue(fixture.requests.isEmpty())
                assertEquals(SourceResult(emptyList()), fixture.repository.sources(query.copy(season = 2)))
                assertEquals(SourceResult(emptyList()), fixture.repository.sources(query.copy(item = series.copy(title = "Not Present"))))
                assertEquals(2, fixture.requests.size)
                assertTrue(fixture.requests.all { it.url.encodedPath == "/catalogo/__data.json" })
            }
        Fixture { request, _ ->
            if (request.url.encodedPath == "/catalogo/__data.json") response(request, AnimeAv1Fixtures.catalog())
            else response(request, AnimeAv1Fixtures.episode(number = 4))
        }.use { fixture ->
            rejects { fixture.repository.sources(query) }
            assertEquals(2, fixture.requests.size)
        }
    }

    @Test fun httpNetworkMalformedAndAllResolverFailuresStayGenericWithoutRetries() = runBlocking {
        listOf(401, 403, 429, 500).forEach { status ->
            Fixture { request, _ -> response(request, "https://fixture.example/?signature=fixture", status) }.use { fixture ->
                rejects { fixture.repository.sources(query) }
                assertEquals(1, fixture.requests.size)
            }
        }
        listOf("not json signature=fixture", "[]", "{\"nodes\":[]}").forEach { text ->
            Fixture { request, _ -> response(request, text) }.use { fixture ->
                rejects { fixture.repository.sources(query) }
                assertEquals(1, fixture.requests.size)
            }
        }
        Fixture { _, _ -> throw IOException("https://fixture.example/?signature=fixture") }.use { fixture ->
            rejects { fixture.repository.sources(query) }
            assertEquals(1, fixture.requests.size)
        }
        Fixture { request, _ ->
            when (request.url.encodedPath) {
                "/catalogo/__data.json" -> response(request, AnimeAv1Fixtures.catalog())
                "/media/blue-sky/3/__data.json" -> response(request, AnimeAv1Fixtures.episode(embeds = listOf(
                    AnimeAv1Protocol.Embed("MP4Upload", "SUB", "https://www.mp4upload.com/embed-fixture.html"))))
                else -> response(request, code = 429)
            }
        }.use { fixture ->
            rejects { fixture.repository.sources(query) }
            assertEquals(3, fixture.requests.size)
        }
    }

    @Test fun redirectsStayWithinAllowedHttpsOriginsAndBodiesHaveDeclaredAndStreamingBounds() = runBlocking {
        Fixture { request, _ ->
            when {
                request.url.host == "animeav1.com" && request.url.encodedPath == "/catalogo/__data.json" ->
                    response(request, code = 302, location = "https://www.animeav1.com/catalogo/__data.json?page=1&search=Blue%20Sky")
                request.url.host == "www.animeav1.com" -> response(request, AnimeAv1Fixtures.catalog())
                else -> response(request, AnimeAv1Fixtures.episode())
            }
        }.use { fixture ->
            assertEquals(1, fixture.repository.sources(query).sources.size)
            assertEquals(3, fixture.requests.size)
        }
        listOf("http://animeav1.com/catalogo/__data.json", "https://evil.example/data", "https://127.0.0.1/data",
            "https://animeav1.com:8443/data", "https://@animeav1.com/data", "/data#fragment").forEach { location ->
            Fixture { request, _ -> response(request, code = 302, location = location) }.use { fixture ->
                rejects { fixture.repository.sources(query) }
                assertEquals(1, fixture.requests.size)
            }
        }
        val oversized = " ".repeat(AnimeAv1Protocol.BODY_LIMIT + 1)
        listOf(false, true).forEach { unknownLength ->
            Fixture { request, _ ->
                val body = if (!unknownLength) oversized.toResponseBody() else object : ResponseBody() {
                    private val data = Buffer().writeUtf8(oversized)
                    override fun contentType() = null
                    override fun contentLength() = -1L
                    override fun source() = data
                }
                response(request, body = body)
            }.use { fixture ->
                rejects { fixture.repository.sources(query) }
                assertEquals(1, fixture.requests.size)
            }
        }
    }

    @Test fun cancellationAfterBodyBytesStillCancelsTheCallBeforeEof() = runBlocking {
        val waitingForEof = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val activeCall = AtomicReference<Call>()
        val listener = object : EventListener() {
            override fun canceled(call: Call) { cancelled.countDown() }
        }
        Fixture(listener) { request, call ->
            activeCall.set(call)
            val body = object : ResponseBody() {
                private val data = object : Source {
                    private var delivered = false
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        if (!delivered) {
                            delivered = true
                            val text = AnimeAv1Fixtures.catalog()
                            sink.writeUtf8(text)
                            return text.toByteArray(Charsets.UTF_8).size.toLong()
                        }
                        waitingForEof.countDown()
                        if (!release.await(5, TimeUnit.SECONDS) || call.isCanceled()) throw IOException("Fixture cancelled")
                        return -1
                    }
                    override fun timeout() = Timeout.NONE
                    override fun close() { closed.countDown() }
                }.buffer()
                override fun contentType() = null
                override fun contentLength() = -1L
                override fun source() = data
            }
            response(request, body = body)
        }.use { fixture ->
            val job = launch(Dispatchers.Default) { fixture.repository.sources(query) }
            try {
                assertTrue("Body did not reach the EOF wait", waitingForEof.await(5, TimeUnit.SECONDS))
                withTimeout(5000) { job.cancelAndJoin() }
                assertTrue(cancelled.await(5, TimeUnit.SECONDS))
                assertTrue(activeCall.get().isCanceled())
                assertTrue(job.isCancelled)
            } finally {
                release.countDown()
                job.cancelAndJoin()
            }
            assertTrue("Cancelled body was not closed", closed.await(5, TimeUnit.SECONDS))
            assertEquals(1, fixture.requests.size)
        }
    }
}
