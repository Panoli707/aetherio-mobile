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
            AetherioRepository(readAccessToken = token, client = client, tmdbBase = base,
                homeAnimeSource = HomeAnimeSource { emptyList() })

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
            assertEquals(44, home.sections.size)
            assertFalse(home.partial)
            assertTrue(home.heroes.isNotEmpty())
            assertTrue(home.heroes.all { it.id == "550" })
            val requests = List(44) { fixture.request() }
            assertEquals(setOf("/3/trending/movie/day", "/3/trending/tv/day"),
                requests.map { it.requestUrl!!.encodedPath }.filter { it.contains("/trending/") }.toSet())
            requests.forEach { request -> authenticated(request, request.requestUrl!!.encodedPath) }
            assertEquals(40, requests.count { it.requestUrl!!.encodedPath.startsWith("/3/discover/") })
            val editorial = requests.filter { it.requestUrl!!.encodedPath.startsWith("/3/discover/") && it.requestUrl!!.queryParameter("with_watch_providers") == null }
            assertTrue(editorial.isNotEmpty())
            editorial.forEach { assertNull("Editorial and anime queries must not inherit the starter region", it.requestUrl!!.queryParameter("region")) }
            assertEquals(setOf("/3/tv/popular", "/3/movie/popular"), requests.map { it.requestUrl!!.encodedPath }.filter { it.endsWith("/popular") }.toSet())
            assertEquals(0, fixture.externalRequests.size)
        }
    }

    @Test fun homePublishesProgressAndReusesAllCachedRowsUntilExplicitRefresh() = runBlocking {
        TlsFixture().use { fixture ->
            fixture.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setBody(CATALOG)
            }
            val saved = mutableMapOf<String, List<MediaItem>>()
            val cache = object : HomeItemsCache {
                override fun read(key: String) = saved[key]
                override fun write(key: String, items: List<MediaItem>) { saved[key] = items }
            }
            val repository = AetherioRepository(TOKEN, fixture.client, fixture.server.url("/3"), homeCache = cache,
                homeAnimeSource = HomeAnimeSource { emptyList() })
            val updates = mutableListOf<HomeCatalog>()
            repository.home(onUpdate = { updates += it })
            assertEquals(44, updates.first().sections.size)
            assertTrue(updates.first().sections.all { it.loading })
            assertTrue(updates.any { update -> update.sections.any { it.items.isNotEmpty() } && update.sections.any { it.loading } })
            assertTrue(updates.last().sections.none { it.loading || it.error != null })
            assertEquals(44, fixture.server.requestCount)
            val cached = repository.home()
            assertEquals(44, cached.sections.size)
            assertEquals(44, fixture.server.requestCount)
            assertTrue(cached.sections.all { it.items.isNotEmpty() })
            repository.home(force = true)
            assertEquals(88, fixture.server.requestCount)
        }
    }

    @Test fun failedRefreshKeepsCachedRowsAndTheirExactIdentities() = runBlocking {
        TlsFixture().use { fixture ->
            val saved = DesktopHomeCatalog.rails().associate { spec ->
                "${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}:${spec.id}" to
                    listOf(movie.copy(posterUrl = "https://image.tmdb.org/t/p/w500/p.jpg"))
            }
            val cache = object : HomeItemsCache {
                override fun read(key: String) = saved[key]
                override fun write(key: String, items: List<MediaItem>) = Unit
            }
            fixture.server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(503) }
            val repository = AetherioRepository(TOKEN, fixture.client, fixture.server.url("/3"), homeCache = cache,
                homeAnimeSource = HomeAnimeSource { emptyList() })
            val result = repository.home(force = true)
            assertTrue(result.partial)
            val row = result.sections.first { it.id == "tmdb.home.movie.horror" }
            assertEquals(listOf(movie.key), row.items.map { it.key })
            assertNotNull(row.error)
            assertFalse(row.loading)
        }
    }

    @Test fun genreAndPlatformBrowsersRetainQueryFiltersAndAuthorizationScope() = runBlocking {
        TlsFixture().use { fixture ->
            fixture.server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = MockResponse().setBody(CATALOG) }
            val repository = fixture.repository()
            val rows = repository.browse("platform:netflix")
            assertEquals(listOf("Top 10 Anime", "Películas populares", "Series populares", "Top 10 Películas", "Series mejor valoradas"), rows.map { it.title })
            val platformRequests = List(5) { fixture.request() }
            platformRequests.forEach { request ->
                authenticated(request, request.requestUrl!!.encodedPath)
                assertEquals("8", request.requestUrl!!.queryParameter("with_watch_providers"))
                assertEquals("PE", request.requestUrl!!.queryParameter("watch_region"))
            }
            val genre = repository.browse("genre:action-adventure").single()
            assertEquals(listOf(MediaType.MOVIE, MediaType.SERIES), genre.items.map { it.type })
            val genreRequests = List(10) { fixture.request() }
            assertEquals(setOf("1","2","3","4","5"), genreRequests.map { it.requestUrl!!.queryParameter("page") }.toSet())
            genreRequests.forEach { request ->
                authenticated(request, request.requestUrl!!.encodedPath, language = false)
                assertEquals("es-ES", request.requestUrl!!.queryParameter("language"))
                assertEquals("false", request.requestUrl!!.queryParameter("include_adult"))
                assertEquals(if (request.requestUrl!!.encodedPath.endsWith("movie")) "28|12" else "10759", request.requestUrl!!.queryParameter("with_genres"))
            }
            assertEquals(0, fixture.externalRequests.size)
        }
    }

    @Test fun actualAnimeClientsNeverReceiveTmdbAuthorization() = runBlocking {
        TlsFixture().use { fixture ->
            fixture.server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = MockResponse().setBody(CATALOG) }
            val repository = AetherioRepository(TOKEN, fixture.client, fixture.server.url("/3"))
            val result = repository.home()
            assertEquals(44, result.sections.size)
            assertFalse(result.partial)
            assertTrue(fixture.externalRequests.any { it.url.host == "graphql.anilist.co" })
            assertTrue(fixture.externalRequests.any { it.url.host == "trkll.aetherio.workers.dev" && it.url.encodedPath.startsWith("/api/jikan/") })
            fixture.externalRequests.forEach { request ->
                assertNull(request.header("Authorization"))
                assertNull(request.header("Cookie"))
                assertFalse(request.url.toString().contains(TOKEN))
            }
        }
    }

    @Test fun cancellingHomeCancelsAllFourActiveRequestsInsteadOfStartingMoreRows() = runBlocking {
        val started = CountDownLatch(4)
        val calls: MutableList<Call> = Collections.synchronizedList(mutableListOf())
        val listener = object : EventListener() {
            override fun callStart(call: Call) { calls.add(call) }
            override fun responseBodyStart(call: Call) { started.countDown() }
        }
        TlsFixture(listener).use { fixture ->
            fixture.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setBody(CATALOG).throttleBody(1, 100, TimeUnit.MILLISECONDS)
            }
            val job = launch(Dispatchers.Default) { fixture.repository().home() }
            try {
                assertTrue("The four bounded requests did not start", started.await(5, TimeUnit.SECONDS))
                withTimeout(5_000) { job.cancelAndJoin() }
                assertTrue(job.isCancelled)
                assertEquals(calls.map { it.request().url.encodedPath }.toString(), 4, calls.size)
                assertTrue(calls.all { it.isCanceled() })
                assertTrue(fixture.server.requestCount <= 4)
            } finally { job.cancelAndJoin() }
        }
    }

    @Test fun searchDetailSeasonAndHeroMetadataUseAuthorizationWithoutApiKey() = runBlocking {
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
            assertEquals("images,credits,external_ids,videos,watch/providers,reviews,recommendations,release_dates", detail.requestUrl!!.queryParameter("append_to_response"))

            fixture.server.enqueue(MockResponse().setBody(EPISODES))
            assertEquals(listOf(EpisodeItem(1, 1, "Pilot", airedOn = "2008-01-20")), repository.episodes(series, 1))
            authenticated(fixture.request(), "/3/tv/1396/season/1")

            fixture.server.enqueue(MockResponse().setBody(HERO_DETAIL))
            val hero = repository.heroes(listOf(movie)).single()
            assertEquals("https://image.tmdb.org/t/p/w500/logo.png", hero.logoUrl)
            assertEquals("2026", hero.year)
            assertEquals(6.4, hero.score, 0.0)
            assertEquals(58, hero.voteCount)
            assertEquals(listOf("Terror"), hero.genres)
            assertEquals("16 (ES)", hero.certification)
            val images = fixture.request()
            authenticated(images, "/3/movie/550")
            assertEquals("images,release_dates", images.requestUrl!!.queryParameter("append_to_response"))
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
            repeat(10) { fixture.server.enqueue(MockResponse().setBody(HERO_DETAIL)) }
            assertEquals(10, repository.heroes(List(10) { movie }).size)
            repeat(10) {
                val request = fixture.request()
                assertEquals("/api/tmdb/movie/550", request.requestUrl!!.encodedPath)
                assertNull(request.getHeader("Authorization"))
            }
            val cine = movie.copy(id = "tt0137523", source = CatalogSource.CINEMETA)
            val withLogo = movie.copy(logoUrl = "https://image.tmdb.org/t/p/w500/logo.png")
            fixture.server.enqueue(MockResponse().setBody(HERO_DETAIL))
            val heroes = repository.heroes(listOf(cine, withLogo))
            assertEquals(listOf(cine.key, withLogo.key), heroes.map { it.key })
            assertEquals(listOf("Terror"), heroes.last().genres)
            assertEquals(13, fixture.server.requestCount)
            assertEquals("v3-cinemeta.strem.io", fixture.externalRequests.single().url.host)
            assertNull(fixture.externalRequests.single().header("Authorization"))
        }
    }

    @Test fun missingSpanishHeroSynopsisUsesEnglishWithoutReplacingItsLogo() = runBlocking {
        TlsFixture().use { fixture ->
            val repository = fixture.repository()
            fixture.server.enqueue(MockResponse().setBody(HERO_DETAIL.replace("\"overview\":\"Sinopsis completa\"", "\"overview\":\"\"")))
            fixture.server.enqueue(MockResponse().setBody("""{"id":550,"overview":"English synopsis"}"""))
            val enriched = repository.heroes(listOf(movie)).single()
            assertEquals("English synopsis", enriched.overview)
            assertEquals("https://image.tmdb.org/t/p/w500/logo.png", enriched.logoUrl)
            authenticated(fixture.request(), "/3/movie/550")
            val fallback = fixture.request()
            authenticated(fallback, "/3/movie/550", language = false)
            assertEquals("en-US", fallback.requestUrl!!.queryParameter("language"))
        }
    }

    @Test fun missingOrMismatchedHeroDetailsKeepExistingMetadataAndSeriesUseContentRatings() = runBlocking {
        TlsFixture().use { fixture ->
            val repository = fixture.repository()
            val existing = movie.copy(score = 7.2, year = "2025", genres = listOf("Drama"), voteCount = 12)
            fixture.server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(existing, repository.heroes(listOf(existing)).single())
            fixture.request()
            fixture.server.enqueue(MockResponse().setBody("""{"id":999,"title":"Wrong movie"}"""))
            assertEquals(existing, repository.heroes(listOf(existing)).single())
            fixture.request()
            fixture.server.enqueue(MockResponse().setBody("""{"id":1396,"name":"Breaking Bad","overview":"Una serie",
                "genres":[{"name":"Drama"}],"content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]}}"""))
            assertEquals("TV-MA (US)", repository.heroes(listOf(series)).single().certification)
            val request = fixture.request()
            authenticated(request, "/3/tv/1396")
            assertEquals("images,content_ratings", request.requestUrl!!.queryParameter("append_to_response"))
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

    @Test fun seasonContentLoadsRealSeasonArtAndDoesNotLetTheGeneralPortraitWin() = runBlocking {
        TlsFixture().use { fixture ->
            val repository = fixture.repository()
            val base = series.copy(posterUrl = "https://example.org/general.jpg", portraitArtworkUrl = "https://example.org/general-clean.jpg")
            fixture.server.enqueue(MockResponse().setBody("""{"poster_path":"/season-two.jpg",
                "episodes":[{"episode_number":1,"name":"Season two episode"}]}"""))
            val content = repository.seasonContent(base, 2)
            assertEquals("https://image.tmdb.org/t/p/original/season-two.jpg", content.item.posterUrl)
            assertNull(content.item.portraitArtworkUrl)
            assertEquals(content.item.posterUrl, ArtworkImages.headers(content.item, true).first().url)
            assertEquals(2, content.episodes.single().season)
            val request = fixture.request()
            authenticated(request, "/3/tv/1396/season/2")
            assertEquals("images", request.requestUrl!!.queryParameter("append_to_response"))
            fixture.server.enqueue(MockResponse().setBody("""{"poster_path":null,"episodes":[]}"""))
            assertEquals(base.copy(seasonHint = 3), repository.seasonContent(base, 3).item)
            fixture.request()
            Unit
        }
    }

    @Test fun boticariaUsesEachMappedEntryWithoutARepeatedTmdbPortraitAndKeepsEpisodeOffsets() = runBlocking {
        TlsFixture().use { fixture ->
            val requested = mutableListOf<String>()
            val repository = AetherioRepository(readAccessToken = TOKEN, client = fixture.client, tmdbBase = fixture.server.url("/3"),
                seasonAnimeArtwork = { entry -> requested.add(entry.id); entry.copy(posterUrl = "https://example.org/${entry.id}.jpg") })
            val base = SeriesIdentity.item(MediaItem("220542", MediaType.SERIES, "Los diarios de la boticaria",
                portraitArtworkUrl = "https://example.org/general.jpg"))
            for (number in 1..3) {
                val episodes = (1..72).joinToString(",") { """{"episode_number":$it,"name":"Episode $it"}""" }
                fixture.server.enqueue(MockResponse().setBody("""{"poster_path":"/shared.jpg","images":{"posters":[
                    {"file_path":"/shared-clean.jpg","iso_639_1":null,"width":1200,"aspect_ratio":0.666}]},"episodes":[$episodes]}"""))
                val content = repository.seasonContent(base, number)
                assertEquals("https://example.org/${requested.last()}.jpg", ArtworkImages.headers(content.item, true).first().url)
                assertNull(content.item.portraitArtworkUrl)
                assertEquals(1, content.episodes.first().number); assertEquals(number, content.episodes.first().season)
                assertEquals(24, content.episodes.size)
                authenticated(fixture.request(), "/3/tv/220542/season/1")
            }
            assertEquals(listOf("161645", "176301", "195516"), requested)
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
            assertEquals(listOf(EpisodeItem(1, 1, "Pilot", airedOn = "2008-01-20")), repository.episodes(results.first { it.type == MediaType.SERIES }, 1))
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
        const val CATALOG = """{"results":[{"id":550,"media_type":"movie","title":"Fight Club","name":"Fight Club","poster_path":"/poster.jpg","backdrop_path":"/background.jpg"}]}"""
        const val DETAIL = """{"id":550,"title":"Fight Club","external_ids":{"imdb_id":"tt0137523"}}"""
        const val EPISODES = """{"episodes":[{"episode_number":1,"name":"Pilot","air_date":"2008-01-20"}]}"""
        const val HERO_DETAIL = """{"id":550,"title":"Fight Club","release_date":"2026-01-02","overview":"Sinopsis completa",
            "vote_average":6.4,"vote_count":58,"genres":[{"id":27,"name":"Terror"}],
            "images":{"logos":[{"iso_639_1":"es","file_path":"/logo.png"}]},
            "release_dates":{"results":[{"iso_3166_1":"ES","release_dates":[{"certification":"16"}]}]}}"""
        const val CINEMETA_CATALOG = """{"metas":[{"id":"tt0137523","name":"Fight Club"}]}"""
        const val CINEMETA_DETAIL = """{"meta":{"id":"tt0137523","name":"Fight Club","videos":[{"season":1,"episode":1,"title":"Pilot","released":"2008-01-20T00:00:00Z"}]}}"""
    }
}
