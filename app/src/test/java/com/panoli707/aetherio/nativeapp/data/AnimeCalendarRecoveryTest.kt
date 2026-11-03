package com.panoli707.aetherio.nativeapp.data

import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class AnimeCalendarRecoveryTest {
    private val season = AnimeSeason(2026, "FALL")
    private val now = 1793610000000L
    private val page = """{"data":{"Page":{"pageInfo":{"hasNextPage":false},"media":[]}}}"""
    private val policy = AnimeRequestPolicy(retryDelayMs = 0)
    private val clients = mutableListOf<OkHttpClient>()

    private fun client(answer: (Interceptor.Chain) -> Response): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        assertEquals("https://graphql.anilist.co/", chain.request().url.toString())
        assertEquals("POST", chain.request().method)
        listOf("Authorization", "Cookie", "Referer").forEach { assertNull(chain.request().header(it)) }
        answer(chain)
    }.build().also(clients::add)

    private fun response(request: Request, code: Int = 200, body: String = page, retryAfter: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
            .body(body.toResponseBody()).apply { retryAfter?.let { header("Retry-After", it) } }.build()

    private suspend fun failure(block: suspend () -> Unit): AnimeCalendarException {
        try { block(); fail("Expected classified failure") } catch (error: AnimeCalendarException) { return error }
        error("Expected failure")
    }

    @After fun closeClients() { clients.forEach { it.dispatcher.executorService.shutdownNow(); it.connectionPool.evictAll() } }

    @Test fun seasonalCardsDoNotDownloadSynopsesButDetailsStillRequestThem() = runBlocking {
        val queries = mutableListOf<String>()
        val client = client { chain ->
            val buffer = Buffer(); chain.request().body!!.writeTo(buffer)
            queries += JSONObject(buffer.readUtf8()).getString("query")
            response(chain.request())
        }
        val repository = AnimeSeasonRepository(client, policy)
        repository.season(season, now)
        failure { repository.detail(MediaItem("123", MediaType.SERIES, "Fixture", CatalogSource.ANILIST), now) }
        assertFalse(queries.first().contains("description"))
        assertTrue(queries.last().contains("description(asHtml:false)"))
        assertTrue(queries.first().contains("airingSchedule"))
    }

    @Test fun aTimeoutWhileReadingTheCompressedBodyCanRecoverWithoutCachingAPartialResponse() = runBlocking {
        var count = 0
        val original = InterruptedIOException("fixture read timeout")
        val client = client { chain ->
            count++
            if (count > 1) response(chain.request()) else response(chain.request()).newBuilder().body(object : ResponseBody() {
                override fun contentType() = null
                override fun contentLength() = -1L
                override fun source(): BufferedSource = object : ForwardingSource(Buffer()) {
                    override fun read(sink: Buffer, byteCount: Long): Long = throw original
                }.buffer()
            }).build()
        }
        val repository = AnimeSeasonRepository(client, policy)
        assertTrue(repository.season(season, now).isEmpty())
        repository.season(season, now + 1)
        assertEquals(2, count)
        assertSame(original, AnimeCalendarException.from(original).cause)
    }

    @Test fun transientServerAndConnectionFailuresHaveAtMostTwoAdditionalAttempts() = runBlocking {
        for (connection in listOf(false, true)) {
            var count = 0
            val client = client { chain ->
                count++
                if (connection) throw UnknownHostException("fixture") else response(chain.request(), 503)
            }
            val error = failure { AnimeSeasonRepository(client, policy).season(season, now) }
            assertEquals(if (connection) AnimeCalendarFailure.CONNECTION else AnimeCalendarFailure.HTTP, error.kind)
            assertEquals(3, count)
        }
    }

    @Test fun permanentHttpTlsGraphqlAndInvalidResponsesAreNotRetried() = runBlocking {
        for ((code, body, kind) in listOf(
            Triple(400, "{}", AnimeCalendarFailure.HTTP),
            Triple(200, """{"errors":[{"message":"sensitive fixture"}],"data":null}""", AnimeCalendarFailure.GRAPHQL),
            Triple(200, "not json", AnimeCalendarFailure.INVALID_RESPONSE),
            Triple(200, """{"data":null}""", AnimeCalendarFailure.INVALID_RESPONSE),
            Triple(200, """{"data":{"Page":{"media":[]}}}""", AnimeCalendarFailure.INVALID_RESPONSE)
        )) {
            var count = 0
            val client = client { chain -> count++; response(chain.request(), code, body) }
            val error = failure { AnimeSeasonRepository(client, policy).season(season, now) }
            assertEquals(kind, error.kind); assertEquals(1, count)
            assertFalse(error.userMessage().contains("sensitive"))
            assertFalse(error.diagnostic.contains("sensitive"))
        }
        var count = 0
        val tls = SSLHandshakeException("fixture TLS")
        val client = client { count++; throw tls }
        assertSame(tls, failure { AnimeSeasonRepository(client, policy).season(season, now) }.cause)
        assertEquals(1, count)
    }

    @Test fun emptyOrNullGraphqlErrorsDoNotRejectValidData() = runBlocking {
        for (errors in listOf("[]", "null")) {
            val body = JSONObject(page).put("errors", if (errors == "null") JSONObject.NULL else org.json.JSONArray()).toString()
            assertTrue(AnimeSeasonRepository(client { response(it.request(), body = body) }, policy).season(season, now).isEmpty())
        }
    }

    @Test fun longRateLimitCooldownIsSharedByManualRetriesAndDetailRequests() = runBlocking {
        var count = 0
        var clock = now
        val client = client { chain -> count++; if (count == 1) response(chain.request(), 429, retryAfter = "60") else response(chain.request()) }
        val repository = AnimeSeasonRepository(client, policy) { clock }
        val first = failure { repository.season(season, now) }
        assertEquals(AnimeCalendarFailure.RATE_LIMIT, first.kind)
        assertEquals(now + 60_000, first.retryAtMs)
        assertTrue(first.userMessage(clock).contains("60 segundos"))
        failure { repository.season(season, now) }
        failure { repository.detail(MediaItem("123", MediaType.SERIES, "Fixture", CatalogSource.ANILIST), now) }
        assertEquals(1, count)
        clock += 60_000
        assertTrue(repository.season(season, now).isEmpty())
        assertEquals(2, count)
    }

    @Test fun absentOrInvalidRetryAfterUsesACooldownAndHttpDatesAreSupported() = runBlocking {
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }
        for ((header, expected) in listOf(null to now + 60_000, "invalid" to now + 60_000, date.format(Date(now + 120_000)) to now + 120_000)) {
            var count = 0
            val client = client { chain -> count++; response(chain.request(), 429, retryAfter = header) }
            val repository = AnimeSeasonRepository(client, policy) { now }
            assertEquals(expected, failure { repository.season(season, now) }.retryAtMs)
            failure { repository.season(season, now) }
            assertEquals(1, count)
        }
    }

    @Test fun immediateRetryAfterStillHasAFiniteAttemptLimit() = runBlocking {
        var count = 0
        val client = client { chain -> count++; response(chain.request(), 429, retryAfter = "0") }
        failure { AnimeSeasonRepository(client, policy).season(season, now) }
        assertEquals(3, count)
    }

    @Test fun shortServerCooldownIsRespectedBeforeTheAutomaticRetry() = runBlocking {
        var count = 0
        var firstAt = 0L
        var retryAt = 0L
        val client = client { chain ->
            count++
            if (count == 1) {
                firstAt = System.nanoTime()
                response(chain.request(), 429, retryAfter = "1")
            } else {
                retryAt = System.nanoTime()
                response(chain.request())
            }
        }
        assertTrue(AnimeSeasonRepository(client, policy).season(season, now).isEmpty())
        assertEquals(2, count)
        assertTrue("Must not retry before the server cooldown", retryAt - firstAt >= 900_000_000)
    }

    @Test fun retryingALaterPageDoesNotRestartEarlierPagesAndDuplicatesAreRemoved() = runBlocking {
        val requests = mutableListOf<Int>()
        var failed = false
        val anime = """{"id":123,"title":{"romaji":"Fixture"},"season":"FALL","seasonYear":2026,"status":"RELEASING"}"""
        val client = client { chain ->
            val buffer = Buffer(); chain.request().body!!.writeTo(buffer)
            val pageNumber = JSONObject(buffer.readUtf8()).getJSONObject("variables").getInt("page")
            requests += pageNumber
            if (pageNumber == 2 && !failed) {
                failed = true
                response(chain.request(), 503)
            } else response(chain.request(), body = """{"data":{"Page":{"pageInfo":{"hasNextPage":${pageNumber == 1}},"media":[$anime]}}}""")
        }
        val repository = AnimeSeasonRepository(client, policy)
        assertEquals(1, repository.season(season, now).size)
        assertEquals(listOf(1, 2, 2), requests)
        repository.season(season, now + 1)
        assertEquals(3, requests.size)
    }

    @Test fun failedRefreshDoesNotReplaceTheLastCompleteCacheAndADifferentQuarterIsFetched() = runBlocking {
        var count = 0
        val client = client { chain -> count++; response(chain.request(), if (count == 2) 400 else 200) }
        val repository = AnimeSeasonRepository(client, policy)
        repository.season(season, now)
        failure { repository.season(season, now + 3_600_001) }
        repository.season(season, now + 1)
        assertEquals(2, count)
        repository.season(AnimeSeason(2027, "WINTER"), now + 2)
        assertEquals(3, count)
    }

    @Test fun responsesOverTheSizeLimitAreNotRetried() = runBlocking {
        var count = 0
        val client = client { chain -> count++; response(chain.request(), body = " ".repeat(2 * 1024 * 1024 + 1)) }
        assertEquals(AnimeCalendarFailure.INVALID_RESPONSE, failure { AnimeSeasonRepository(client, policy).season(season, now) }.kind)
        assertEquals(1, count)
    }

    @Test fun cancellationCancelsTheActiveCallWithoutAnotherAttempt() = runBlocking {
        val started = CompletableDeferred<Call>()
        val release = CountDownLatch(1)
        val count = AtomicInteger()
        val client = client { chain ->
            count.incrementAndGet(); started.complete(chain.call())
            release.await(5, TimeUnit.SECONDS)
            response(chain.request())
        }
        try {
            val repository = AnimeSeasonRepository(client, policy)
            val job = launch { repository.season(season, now) }
            val call = started.await()
            job.cancelAndJoin()
            assertTrue(call.isCanceled()); assertEquals(1, count.get())
        } finally { release.countDown() }
    }

    @Test fun totalOperationBudgetCancelsTheCallAndReportsTimeout() = runBlocking {
        val release = CountDownLatch(1)
        val started = CompletableDeferred<Call>()
        val client = client { chain -> started.complete(chain.call()); release.await(5, TimeUnit.SECONDS); response(chain.request()) }
        try {
            val repository = AnimeSeasonRepository(client, policy.copy(operationTimeoutMs = 200))
            assertEquals(AnimeCalendarFailure.TIMEOUT, failure { repository.season(season, now) }.kind)
            assertTrue(started.await().isCanceled())
        } finally { release.countDown() }
    }

    @Test fun operationBudgetAlsoIncludesRetryDelays() = runBlocking {
        var count = 0
        val client = client { chain -> count++; response(chain.request(), 503) }
        val repository = AnimeSeasonRepository(client, policy.copy(operationTimeoutMs = 200, retryDelayMs = 2_000))
        assertEquals(AnimeCalendarFailure.TIMEOUT, failure { repository.season(season, now) }.kind)
        assertEquals(1, count)
    }
}
