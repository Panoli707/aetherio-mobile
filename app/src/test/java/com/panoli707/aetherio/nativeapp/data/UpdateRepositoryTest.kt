package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class UpdateRepositoryTest {
    private fun metadata(code: Int = 4, name: String = "0.4.0"): String =
        """{"schemaVersion":1,"applicationId":"com.panoli707.aetherio.nativeapp","versionCode":$code,"versionName":"$name","apkName":"Aetherio-debug.apk"}"""

    private fun marker(json: String = metadata()) = "<!-- aetherio-update: $json -->"

    private fun release(
        code: Int = 4,
        tag: String = "v0.4.0",
        preview: Boolean = false,
        body: String = marker(metadata(code))
    ): JSONObject = JSONObject().put("draft", false).put("prerelease", preview).put("tag_name", tag)
        .put("body", body).put("html_url", "https://private-secret.example/not-a-release")
        .put("assets", JSONArray().put(JSONObject().put("name", "Aetherio-debug.apk")
            .put("state", "uploaded").put("size", 12345)
            .put("browser_download_url", "http://private-secret.example/untrusted.apk")))

    private fun feed(vararg releases: JSONObject): String = JSONArray(releases.toList()).toString()
    private fun parse(release: JSONObject): AppUpdate? = UpdateRepository.parse(feed(release), 3)

    private fun assertSanitized(error: Throwable, type: Class<out Throwable>, message: String) {
        // Coroutine stack recovery may add an identical, sanitized exception as the cause.
        generateSequence(error) { it.cause }.forEach { cause ->
            assertEquals(type, cause.javaClass)
            assertEquals(message, cause.message)
            assertTrue(cause.suppressed.isEmpty())
            listOf("private-secret", "localhost", "127.0.0.1", "https://", "http://").forEach {
                assertFalse(cause.message.orEmpty().contains(it))
            }
        }
    }

    private fun rejectsParse(raw: String) {
        val error = try {
            UpdateRepository.parse(raw, 3)
            throw AssertionError("Expected a sanitized parse failure")
        } catch (error: IllegalArgumentException) { error }
        assertSanitized(error, IllegalArgumentException::class.java, "Invalid update data")
    }

    private suspend fun rejectsNetwork(block: suspend () -> Unit) {
        val error = try {
            block()
            throw AssertionError("Expected a sanitized network failure")
        } catch (error: IOException) { error }
        assertSanitized(error, IOException::class.java, "Unable to check for updates")
    }

    @Test fun exactGeneratedMarkerProvidesPublicUpdateFields() {
        val body = """<!-- aetherio-update: {"schemaVersion":1,"applicationId":"com.panoli707.aetherio.nativeapp","versionCode":3,"versionName":"0.3.0","apkName":"Aetherio-debug.apk"} -->"""
        assertEquals(AppUpdate(3, "0.3.0", "https://github.com/Panoli707/aetherio-mobile/releases/tag/v0.3.0", false),
            UpdateRepository.parse(feed(release(3, "v0.3.0", body = body)), 2))
    }

    @Test fun previewAndLegacyNativeTagAreEligibleAndFlagged() {
        listOf("native-v0.1.0-preview.1", "v0.3.0-preview.1").forEach { tag ->
            val update = parse(release(tag = tag, preview = true))!!
            assertTrue(update.prerelease)
            assertEquals("https://github.com/Panoli707/aetherio-mobile/releases/tag/$tag", update.releaseUrl)
        }
    }

    @Test fun highestEligibleCodeWinsRegardlessOfOrderOrPreviewStatus() {
        val releases = listOf(release(5), release(9, preview = true), release(4), release(7))
        listOf(releases, releases.reversed(), listOf(releases[1], releases[3], releases[2], releases[0])).forEach {
            val update = UpdateRepository.parse(feed(*it.toTypedArray()), 3)!!
            assertEquals(9, update.versionCode)
            assertTrue(update.prerelease)
        }
    }

    @Test fun equalOlderZeroAndNegativeVersionsNeverNotify() {
        assertNull(UpdateRepository.parse(feed(release(3), release(2), release(0), release(-1)), 3))
        assertNull(UpdateRepository.parse("[]", 3))
    }

    @Test fun legacyReleasesWithoutMetadataAreSilentlySkipped() {
        listOf(JSONObject.NULL, "", "Public 0.1 release", "{\"versionCode\":999}").forEach { body ->
            assertNull(parse(release(999).put("body", body)))
        }
        assertNull(parse(release(999).also { it.remove("body") }))
        assertEquals(4, UpdateRepository.parse(feed(release(999, body = "Legacy"), release(4)), 3)!!.versionCode)
    }

    @Test fun wrongPackageSchemaAndMetadataShapeAreSkipped() {
        listOf(
            metadata().replace("com.panoli707.aetherio.nativeapp", "private-secret.other"),
            metadata().replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            metadata().replace("\"schemaVersion\":1", "\"schemaVersion\":1.0"),
            metadata().replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
            metadata().replace("\"schemaVersion\":1,", ""),
            metadata().dropLast(1) + ",\"extra\":true}", "[]", "null"
        ).forEach { assertNull(parse(release(body = marker(it)))) }
    }

    @Test fun versionCodesRequirePositiveIntAndNeverCoerceOtherNumericTypes() {
        listOf("\"4\"", "4.0", "4e0", "true", "null", "2147483648", "9223372036854775808", "-4", "0").forEach {
            assertNull(parse(release(body = marker(metadata().replace("\"versionCode\":4", "\"versionCode\":$it")))))
        }
        assertEquals(Int.MAX_VALUE, parse(release(Int.MAX_VALUE))!!.versionCode)
        assertNull(UpdateRepository.parse(feed(release(Int.MAX_VALUE)), Int.MAX_VALUE))
    }

    @Test fun versionNameMustBeBoundedPrintableAndCannotContainControlsOrBidi() {
        listOf("", " ", " leading", "trailing ", "x".repeat(81), "v\n4", "v\u00004", "v\u007f4",
            "v\u00854", "v\u202e4", "v\u20664", "v\u200b4").forEach { name ->
            val json = JSONObject(metadata()).put("versionName", name).toString()
            assertNull(parse(release(body = marker(json))))
        }
        assertEquals("x".repeat(80), parse(release(body = marker(metadata(name = "x".repeat(80)))))!!.versionName)
        assertEquals("0.4.0 Preview (debug)", parse(release(body = marker(metadata(name = "0.4.0 Preview (debug)"))))!!.versionName)
    }

    @Test fun apkNameMustBeSafeAsciiAndEndInApk() {
        listOf("", ".apk", "../Aetherio-debug.apk", "dir/app.apk", "dir\\app.apk", "app.apk?token=private-secret",
            "app.APK", "app.apk.exe", "app name.apk", "app\u202e.apk", "x".repeat(121) + ".apk").forEach { apk ->
            assertNull(parse(release(body = marker(JSONObject(metadata()).put("apkName", apk).toString()))))
        }
    }

    @Test fun correspondingUploadedAssetWithPositiveIntegralSizeIsRequired() {
        listOf(
            JSONArray(), JSONObject(), JSONObject.NULL,
            JSONArray().put("private-secret"),
            JSONArray().put(JSONObject().put("name", "Other.apk").put("state", "uploaded").put("size", 1)),
            JSONArray().put(JSONObject().put("name", "Aetherio-debug.apk").put("state", "new").put("size", 1))
        ).forEach { assertNull(parse(release().put("assets", it))) }
        assertNull(parse(release().also { it.remove("assets") }))
        listOf(0, -1, "1", 1.5, true, JSONObject.NULL).forEach { size ->
            val value = release()
            value.getJSONArray("assets").getJSONObject(0).put("size", size)
            assertNull(parse(value))
        }
        val large = release()
        large.getJSONArray("assets").getJSONObject(0).put("size", 3_000_000_000L)
        assertNotNull(parse(large))
    }

    @Test fun draftAndMissingOrNonBooleanFlagsAreSkipped() {
        assertNull(parse(release().put("draft", true)))
        listOf("draft", "prerelease").forEach { key ->
            listOf("false", 0, JSONObject.NULL).forEach { assertNull(parse(release().put(key, it))) }
            assertNull(parse(release().also { it.remove(key) }))
        }
    }

    @Test fun malformedAndNonSingleLineMetadataAreSkippedWithoutHidingOtherCandidates() {
        listOf(
            "<!-- aetherio-update: private-secret -->",
            marker(metadata().dropLast(1)), marker(metadata()).dropLast(1),
            marker(metadata().replace("{", "{\n")),
            marker(metadata().replace("\"versionCode\":4", "\"versionCode\":4,")),
            marker(metadata() + " private-secret"),
            marker(metadata()).replace(": {", ":{"), "prefix " + marker(), marker() + " suffix"
        ).forEach { body ->
            assertNull(parse(release(999, body = body)))
            assertEquals(4, UpdateRepository.parse(feed(release(999, body = body), release(4)), 3)!!.versionCode)
        }
        assertNotNull(parse(release(body = "Release notes\r\n${marker()}\r\nMore notes")))
    }

    @Test fun duplicateMarkersIncludingMalformedSecondMarkerAreSkipped() {
        listOf(marker(), "<!-- aetherio-update: private-secret -->", "<!-- aetherio-update:").forEach {
            assertNull(parse(release(body = "${marker()}\n$it")))
        }
    }

    @Test fun metadataAndReleaseBodyBoundsAreEnforced() {
        assertNull(parse(release(body = marker(" ".repeat(1025) + metadata()))))
        assertNull(parse(release(body = "x".repeat(128 * 1024) + "\n" + marker())))
        val boundedMetadata = metadata().padStart(1024, ' ')
        assertNotNull(parse(release(body = marker(boundedMetadata))))
        assertNull(parse(release(body = marker("\u00e9".repeat(600) + metadata()))))
    }

    @Test fun untrustedUrlsAndDisplayFieldsAreIgnoredAndCanonicalUrlIsConstructed() {
        listOf("http://private-secret.example/evil", "javascript:private-secret", "https://github.com.evil.example/",
            "https://github.com/Other/repo/releases/tag/evil", "https://user:private-secret@github.com/").forEach { url ->
            val value = release().put("html_url", url).put("url", url).put("name", "private-secret\u202e")
            value.getJSONArray("assets").getJSONObject(0).put("browser_download_url", url).put("url", url)
            assertEquals(AppUpdate(4, "0.4.0", "https://github.com/Panoli707/aetherio-mobile/releases/tag/v0.4.0", false), parse(value))
        }
    }

    @Test fun unsafeTagsCannotBecomeReleaseUrls() {
        listOf("", "../v4", ".v4", "-v4", "v4/evil", "v4\\evil", "v4?private-secret", "v4#evil", "v4%2Fevil",
            "https://private-secret.example", "v 4", "v\n4", "v\u202e4", "x".repeat(121)).forEach {
            assertNull(parse(release(tag = it)))
        }
        assertNotNull(parse(release(tag = "x".repeat(120))))
    }

    @Test fun jsonRejectsMalformedFeedsWrongShapesAndTrailingJunk() {
        listOf("private-secret", "{}", "null", "[1]", "[null]", "[] private-secret", "[{}",
            "[{\"draft\":}]", "[{\"draft\" false}]", "[\"unterminated]", "[]\u0000private-secret")
            .forEach(::rejectsParse)
    }

    @Test fun feedByteCollectionAndNestingLimitsAreEnforced() {
        rejectsParse(" ".repeat(1024 * 1024 + 1))
        rejectsParse("[\"" + "\u00e9".repeat(600_000) + "\"]")
        rejectsParse(feed(*Array(11) { release() }))
        rejectsParse(feed(release().put("assets", JSONArray(List(101) { JSONObject() }))))
        val hundredAssets = release()
        repeat(99) { hundredAssets.getJSONArray("assets").put(JSONObject()) }
        assertNotNull(parse(hundredAssets))
        val tooManyFields = release()
        repeat(101) { tooManyFields.put("field$it", it) }
        rejectsParse(feed(tooManyFields))
        val nested = "[".repeat(14) + "0" + "]".repeat(14)
        assertNotNull(parse(release().put("unused", JSONArray(nested))))
        rejectsParse(feed(release().put("unused", JSONArray("[$nested]"))))
        val ten = Array(10) { release(it + 4) }
        assertEquals(13, UpdateRepository.parse(feed(*ten), 3)!!.versionCode)
        assertNull(UpdateRepository.parse("[]".padEnd(1024 * 1024, ' '), 3))
    }

    @Test fun productionEndpointIsTheFixedTenReleaseFeedNotLatest() {
        assertEquals("https://api.github.com/repos/Panoli707/aetherio-mobile/releases?per_page=10",
            UpdateRepository.DEFAULT_RELEASES_URL.toString())
    }

    // The private CA and custom trust configuration exist exclusively in these tests.
    private class TlsFixture(listener: EventListener = EventListener.NONE, chunked: Boolean = false) : Closeable {
        val server = MockWebServer()
        val client: OkHttpClient
        val repository: UpdateRepository

        init {
            val ca = HeldCertificate.Builder().commonName("Update test CA").certificateAuthority(0).build()
            val leaf = HeldCertificate.Builder().commonName("localhost")
                .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1")
                .addSubjectAlternativeName("::1").signedBy(ca).build()
            val serverCertificates = HandshakeCertificates.Builder().heldCertificate(leaf, ca.certificate).build()
            val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(ca.certificate).build()
            server.useHttps(serverCertificates.sslSocketFactory(), false)
            // Chunked transfer framing belongs to HTTP/1.1, not HTTP/2 DATA frames.
            if (chunked) server.protocols = listOf(Protocol.HTTP_1_1)
            server.start()
            client = OkHttpClient.Builder().sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .followRedirects(true).followSslRedirects(true).retryOnConnectionFailure(false).eventListener(listener)
                .connectTimeout(1, TimeUnit.MINUTES).readTimeout(1, TimeUnit.MINUTES).callTimeout(1, TimeUnit.MINUTES).build()
            repository = UpdateRepository(client, url())
        }

        fun url() = server.url("/private-secret/releases?per_page=10&token=private-secret")

        override fun close() {
            server.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    @Test fun tlsFixtureUsesVerifiedLocalCertificate() {
        TlsFixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setBody("TLS ready"))
            fixture.client.newCall(Request.Builder().url(fixture.url()).build()).execute().use { response ->
                assertEquals("TLS ready", response.body!!.string())
                assertNotNull(response.handshake)
            }
        }
    }

    @Test fun availableUsesHttpsGetRequiredHeadersNoCredentialsAndClosesResponse() = runBlocking {
        val ended = CountDownLatch(1)
        val listener = object : EventListener() {
            override fun responseBodyEnd(call: Call, byteCount: Long) { ended.countDown() }
        }
        TlsFixture(listener).use { fixture ->
            fixture.server.enqueue(MockResponse().setBody(feed(release(preview = true))))
            assertTrue(fixture.repository.available(3)!!.prerelease)
            val request = fixture.server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("GET", request.method)
            assertEquals("/private-secret/releases?per_page=10&token=private-secret", request.path)
            assertEquals("application/vnd.github+json", request.getHeader("Accept"))
            assertEquals("2022-11-28", request.getHeader("X-GitHub-Api-Version"))
            assertEquals("Aetherio-Android", request.getHeader("User-Agent"))
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
            assertEquals(0L, request.bodySize)
            assertTrue(ended.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun injectedLongTimeoutsAreOverridden() = runBlocking {
        val active = AtomicReference<Call>()
        val timeouts = AtomicReference<Pair<Int, Int>>()
        val listener = object : EventListener() {
            override fun callStart(call: Call) { active.set(call) }
        }
        TlsFixture(listener).use { fixture ->
            val client = fixture.client.newBuilder().addInterceptor { chain ->
                timeouts.set(chain.connectTimeoutMillis() to chain.readTimeoutMillis())
                chain.proceed(chain.request())
            }.build()
            fixture.server.enqueue(MockResponse().setBody("[]"))
            assertNull(UpdateRepository(client, fixture.url()).available(3))
            assertEquals(4000 to 6000, timeouts.get())
            assertEquals(10_000_000_000L, active.get().timeout().timeoutNanos())
        }
    }

    @Test fun nonHttpsAndCredentialedEndpointsAreRejectedBeforeAnyRequest() = runBlocking {
        TlsFixture().use { fixture ->
            listOf(fixture.url().newBuilder().scheme("http").build(),
                fixture.url().newBuilder().username("private-secret").password("private-secret").build(),
                fixture.url().newBuilder().fragment("private-secret").build()).forEach { endpoint ->
                val error = try {
                    UpdateRepository(fixture.client, endpoint).available(3)
                    throw AssertionError("Expected unsafe endpoint rejection")
                } catch (error: IllegalArgumentException) { error }
                assertSanitized(error, IllegalArgumentException::class.java, "Invalid update data")
            }
            assertEquals(0, fixture.server.requestCount)
        }
    }

    @Test fun httpAndRateLimitFailuresAreSanitized() = runBlocking {
        TlsFixture().use { fixture ->
            listOf(401, 403, 404, 429, 500, 503).forEach { status ->
                fixture.server.enqueue(MockResponse().setResponseCode(status).setHeader("X-RateLimit-Remaining", "0")
                    .setBody("private-secret ${fixture.url()}"))
                rejectsNetwork { fixture.repository.available(3) }
            }
        }
    }

    @Test fun networkDisconnectAndMalformedFeedFailuresAreSanitized() = runBlocking {
        TlsFixture().use { fixture ->
            fixture.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            rejectsNetwork { fixture.repository.available(3) }
            listOf("private-secret ${fixture.url()}", "{}", "[null]").forEach { body ->
                fixture.server.enqueue(MockResponse().setBody(body))
                rejectsNetwork { fixture.repository.available(3) }
            }
        }
    }

    @Test fun declaredAndChunkedOversizedBodiesAreRejected() = runBlocking {
        TlsFixture(chunked = true).use { fixture ->
            val oversized = " ".repeat(1024 * 1024 + 1)
            fixture.server.enqueue(MockResponse().setBody(oversized))
            rejectsNetwork { fixture.repository.available(3) }
            fixture.server.enqueue(MockResponse().setChunkedBody(oversized, 4096))
            rejectsNetwork { fixture.repository.available(3) }
            fixture.server.enqueue(MockResponse().setChunkedBody("[]".padEnd(1024 * 1024, ' '), 4096))
            assertNull(fixture.repository.available(3))
        }
    }

    @Test fun httpsAndCleartextRedirectsAreNotFollowedDespiteInjectedSettings() = runBlocking {
        TlsFixture().use { fixture ->
            listOf(301, 302, 303, 307, 308).forEach { code ->
                val before = fixture.server.requestCount
                val target = fixture.url().newBuilder().encodedPath("/private-secret/redirect").build()
                fixture.server.enqueue(MockResponse().setResponseCode(code).setHeader("Location", target).setBody("private-secret"))
                fixture.server.enqueue(MockResponse().setBody("[]"))
                rejectsNetwork { fixture.repository.available(3) }
                assertEquals(before + 1, fixture.server.requestCount)
                assertNull(fixture.repository.available(3))
            }
            val before = fixture.server.requestCount
            fixture.server.enqueue(MockResponse().setResponseCode(302)
                .setHeader("Location", fixture.url().newBuilder().scheme("http").build()))
            rejectsNetwork { fixture.repository.available(3) }
            assertEquals(before + 1, fixture.server.requestCount)
        }
    }

    @Test fun cancellingAfterHeadersCancelsOkHttpBeforeBodyArrives() = runBlocking {
        cancellationTest(afterBodyStarts = false)
    }

    @Test fun cancellingDuringBodyReadCancelsOkHttpAndPropagatesCancellation() = runBlocking {
        cancellationTest(afterBodyStarts = true)
    }

    private suspend fun cancellationTest(afterBodyStarts: Boolean) = kotlinx.coroutines.coroutineScope {
        val ready = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val active = AtomicReference<Call>()
        val observed = AtomicReference<CancellationException>()
        val listener = object : EventListener() {
            override fun callStart(call: Call) { active.set(call) }
            override fun responseHeadersEnd(call: Call, response: okhttp3.Response) {
                if (!afterBodyStarts) ready.countDown()
            }
            override fun responseBodyStart(call: Call) {
                if (afterBodyStarts) ready.countDown()
            }
            override fun canceled(call: Call) { cancelled.countDown() }
        }
        TlsFixture(listener).use { fixture ->
            val response = MockResponse().setBody(feed(release()))
            if (afterBodyStarts) response.throttleBody(1, 100, TimeUnit.MILLISECONDS)
            else response.setBodyDelay(2, TimeUnit.SECONDS)
            fixture.server.enqueue(response)
            val job = launch(Dispatchers.Default) {
                try {
                    fixture.repository.available(3)
                    fail("Expected cancellation, not an update")
                } catch (error: CancellationException) {
                    observed.set(error)
                    throw error
                }
            }
            try {
                assertTrue("Response did not reach the cancellation point", ready.await(5, TimeUnit.SECONDS))
                withTimeout(5000) { job.cancelAndJoin() }
                assertTrue("OkHttp did not receive cancellation", cancelled.await(5, TimeUnit.SECONDS))
                assertTrue(active.get().isCanceled())
                assertTrue(job.isCancelled)
                assertNotNull(observed.get())
            } finally {
                job.cancelAndJoin()
            }
        }
    }
}
