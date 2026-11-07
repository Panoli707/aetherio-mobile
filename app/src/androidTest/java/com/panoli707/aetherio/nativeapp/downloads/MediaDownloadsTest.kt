package com.panoli707.aetherio.nativeapp.downloads

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.AetherioApplication
import com.panoli707.aetherio.nativeapp.data.*
import com.panoli707.aetherio.nativeapp.player.torrent.*
import com.nuvio.engine.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MediaDownloadsTest {
    @Test fun preparePausedTaskForMinifiedReleaseUpgradeSmoke() = runBlocking<Unit>(Dispatchers.IO) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue("Opt-in fixture for verifying the installed release, not a normal unit of this suite",
            InstrumentationRegistry.getArguments().getString("prepareReleaseDownload") == "true")
        val app = instrumentation.targetContext.applicationContext as AetherioApplication
        val target = WatchTarget(MediaItem("99021999", MediaType.MOVIE, "QA release download"))
        // Sintel trailer (Blender Foundation, CC BY 3.0): stable ranges/ETag and long enough to verify offline playback.
        val source = StreamSource("release-smoke", "qa", "QA", "QA", "Sintel · Blender Foundation · CC BY 3.0",
            "https://media.w3.org/2010/05/sintel/trailer.mp4")
        app.mediaDownloads.queueLock.lock()
        try {
            app.mediaDownloads.entries.value.firstOrNull { it.target.key == target.key }?.let { app.mediaDownloads.delete(it.id) }
            assertTrue(app.mediaDownloads.enqueue(target, source))
            val id = app.mediaDownloads.entries.value.single { it.target.key == target.key }.id
            app.mediaDownloads.pause(id)
            assertEquals(DownloadStage.PAUSED, app.mediaDownloads.entries.value.single { it.id == id }.stage)
            check(app.getSharedPreferences("qa_release_021", 0).edit().putString("marker", id).commit())
        } finally { app.mediaDownloads.queueLock.unlock() }
    }
    @Test fun persistentHttpDownloadRendersWithServerOfflineAndCanBeRemovedWithoutChangingHistory() = checkDownload("http")
    @Test fun hlsVodDownloadRendersWithoutAnyServerOrUpstreamDatasource() = checkDownload("hls")
    @Test fun dashVodDownloadRendersWithoutAnyServerOrUpstreamDatasource() = checkDownload("dash")
    @Test fun nativeTorrentDownloadOutlivesItsRevokedRouteAndNativeEngine() = checkDownload("torrent")
    @Test fun pauseAndResumePreserveTheTaskAndValidatedPartialBlocks() = checkDownload("resume")

    @Test fun forbiddenHtmlResponseFailsWithoutRetry() = runBlocking<Unit>(Dispatchers.IO) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AetherioApplication
        val certificate = HeldCertificate.Builder().commonName("downloads.example.test")
            .addSubjectAlternativeName("downloads.example.test").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply {
            useHttps(serverTls.sslSocketFactory(), false)
            enqueue(MockResponse().setResponseCode(403).setHeader("Content-Type", "text/html").setBody("Forbidden"))
            start()
        }
        val store = app.mediaDownloads
        var id: String? = null
        try {
            app.downloadHttpClientOverride = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                .dns(object : okhttp3.Dns { override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1")) }).build()
            val target = WatchTarget(MediaItem("99021006", MediaType.MOVIE, "QA rejected download"))
            val source = StreamSource("qa-forbidden", "qa", "QA", "QA", "Rejected response",
                server.url("/forbidden.mp4").newBuilder().host("downloads.example.test").build().toString())
            assertTrue(store.enqueue(target, source))
            id = store.entries.value.single { it.target.key == target.key }.id
            val task = requireNotNull(id)
            val failed = withTimeout(30_000) { store.entries.first { rows ->
                rows.any { it.id == task && it.stage == DownloadStage.FAILED }
            }.single { it.id == task } }
            assertEquals("El enlace no está disponible o ha caducado. Elimina esta tarea y elige otra fuente.", failed.error)
            assertEquals(1, server.requestCount)
        } finally {
            app.downloadHttpClientOverride = null
            id?.let { task -> withTimeout(10_000) {
                while (true) { try { store.delete(task); break } catch (_: IllegalStateException) { delay(100) } }
            } }
            server.shutdown()
        }
    }

    private fun checkDownload(kind: String) = runBlocking<Unit>(Dispatchers.IO) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as AetherioApplication
        val connectivity = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        assertFalse("This suite requires an unmetered QA network; do not weaken the app's network constraints", connectivity.isActiveNetworkMetered)
        val store = app.mediaDownloads
        val payload = instrumentation.context.assets.open("torrent-fixture.mp4").use { it.readBytes() }
        val certificate = HeldCertificate.Builder().commonName("downloads.example.test").addSubjectAlternativeName("downloads.example.test").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false) }
        val webseed = if (kind == "torrent") MockWebServer() else null
        val nativeClosed = CountDownLatch(1)
        val nativeRoot = File(app.cacheDir, "offline-native-test-${System.nanoTime()}")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path?.substringBefore('?')?.removePrefix("/").orEmpty()
                val bytes = if (path == "fixture.mp4") payload else try { instrumentation.context.assets.open(path).use { it.readBytes() } }
                    catch (_: Exception) { return MockResponse().setResponseCode(404) }
                val range = request.getHeader("Range")?.removePrefix("bytes=")
                val start = range?.substringBefore('-')?.toIntOrNull() ?: 0
                val end = range?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(bytes.lastIndex) ?: bytes.lastIndex
                if (start !in bytes.indices) return MockResponse().setResponseCode(416)
                val mime = when { path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"; path.endsWith(".mpd") -> "application/dash+xml"
                    path.endsWith(".ts") -> "video/mp2t"; path.endsWith(".m4s") -> "application/octet-stream"; else -> "video/mp4" }
                return MockResponse().setHeader("Content-Type", mime).setHeader("ETag", "\"fixture-v1\"")
                    .setHeader("Accept-Ranges", "bytes").setBody(Buffer().write(bytes, start, end - start + 1)).apply {
                        if (range != null) setResponseCode(206).setHeader("Content-Range", "bytes $start-$end/${bytes.size}")
                        if (kind == "resume" && end - start > 1) throttleBody(1024, 100, TimeUnit.MILLISECONDS)
                    }
            }
        }
        server.start()
        webseed?.dispatcher = server.dispatcher
        webseed?.start()
        val history = WatchHistoryRepository.get(app).load()
        var id: String? = null
        var offline: OfflineDownload? = null
        var player: ExoPlayer? = null
        var texture: SurfaceTexture? = null
        var surface: Surface? = null
        try {
            app.downloadHttpClientOverride = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                .dns(object : okhttp3.Dns { override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1")) }).build()
            var hash: String? = null
            if (webseed != null) {
                val pieceLength = 16 * 1024
                val pieces = ByteArrayOutputStream().apply { payload.asList().chunked(pieceLength).forEach { write(MessageDigest.getInstance("SHA-1").digest(it.toByteArray())) } }.toByteArray()
                val info = mapOf("name" to "fixture.mp4", "length" to payload.size, "piece length" to pieceLength, "pieces" to pieces, "private" to 1)
                hash = MessageDigest.getInstance("SHA-1").digest(bencode(info)).joinToString("") { "%02x".format(it) }
                val torrent = bencode(mapOf("info" to info, "url-list" to listOf(webseed.url("/fixture.mp4").newBuilder().host("127.0.0.1").build().toString())))
                app.downloadTorrentSessionsOverride = TorrentSessionManager(factory = {
                    val native = NuvioEngine.create(NuvioEngineConfig(File(nativeRoot, "state"), File(nativeRoot, "cache"),
                        uploadMode = NuvioUploadMode.Disabled, warmTorrentTimeoutMilliseconds = 0))
                    val delegate = NuvioTorrentEngine(native)
                    object : TorrentEngine by delegate {
                        override suspend fun addMagnet(magnet: String): String = native.addTorrent(torrent)
                        override suspend fun shutdown() { try { delegate.shutdown() } finally { nativeClosed.countDown() } }
                    }
                })
            }
            val item = MediaItem((99021001 + listOf("http", "hls", "dash", "torrent", "resume").indexOf(kind)).toString(), MediaType.MOVIE, "QA generated CC0 video · $kind")
            val target = WatchTarget(item)
            val path = when (kind) { "hls" -> "/offline-hls/index.m3u8"; "dash" -> "/offline-dash/index.mpd"; else -> "/fixture.mp4" }
            val source = if (hash != null) StreamSource("qa-torrent", "qa", "QA", "QA torrent", "Generated fixture", infoHash = hash, fileIdx = 0)
                else StreamSource("qa-$kind", "qa", "QA", "QA", "Generated fixture", server.url(path).newBuilder().host("downloads.example.test").build().toString())
            assertTrue(store.enqueue(target, source))
            id = store.entries.value.single { it.target.key == target.key }.id
            assertFalse(store.enqueue(target, source))
            if (kind == "resume") {
                withTimeout(30_000) { store.entries.first { rows -> rows.any { it.id == id && it.stage == DownloadStage.DOWNLOADING && it.bytes > 0 } } }
                store.pause(requireNotNull(id))
                assertEquals(DownloadStage.PAUSED, MediaDownloads(app).entries.value.single { it.id == id }.stage)
                store.resume(requireNotNull(id))
            }
            val done = withTimeout(90_000) { store.entries.first { rows -> rows.any { it.id == id && it.stage in listOf(DownloadStage.COMPLETE, DownloadStage.FAILED) } }.single { it.id == id } }
            assertEquals(done.error, DownloadStage.COMPLETE, done.stage)
            assertTrue(done.bytes > 0)
            val restored = MediaDownloads(app)
            assertEquals(DownloadStage.COMPLETE, restored.entries.value.single { it.id == id }.stage)
            server.shutdown()
            if (webseed != null) { assertTrue("Native engine must close before offline playback", nativeClosed.await(10, TimeUnit.SECONDS)); webseed.shutdown() }
            offline = store.open(requireNotNull(id))
            assertThrows(IllegalStateException::class.java) { store.delete(requireNotNull(id)) }
            val frame = CountDownLatch(1)
            val error = AtomicReference<PlaybackException?>()
            instrumentation.runOnMainSync {
                texture = SurfaceTexture(0); surface = Surface(texture)
                player = ExoPlayer.Builder(app).setMediaSourceFactory(DefaultMediaSourceFactory(offline!!.factory)).build().apply {
                    setVideoSurface(surface); volume = 0f
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { frame.countDown() }
                        override fun onPlayerError(e: PlaybackException) { error.set(e); frame.countDown() }
                    })
                    setMediaItem(offline!!.media); prepare(); play()
                }
            }
            assertTrue("No offline frame", frame.await(30, TimeUnit.SECONDS))
            assertNull(error.get()?.let { failure -> failure.errorCodeName + ": " + generateSequence(failure as Throwable) { it.cause }.joinToString(" → ") { it.message.orEmpty() } }, error.get())
            assertEquals(history, WatchHistoryRepository.get(app).load())
        } finally {
            instrumentation.runOnMainSync { player?.release(); surface?.release(); texture?.release() }
            offline?.close()
            app.downloadHttpClientOverride = null
            app.downloadTorrentSessionsOverride = null
            id?.let { task ->
                withTimeout(10_000) { while (true) { try { store.delete(task); break } catch (_: IllegalStateException) { delay(100) } } }
            }
            server.shutdown()
            webseed?.shutdown()
            if (webseed != null && nativeClosed.count == 0L) nativeRoot.deleteRecursively() // Only this test's newly created directory, after native shutdown.
        }
    }
    private fun bencode(value: Any): ByteArray {
        val out = ByteArrayOutputStream()
        fun write(item: Any) { when (item) {
            is ByteArray -> { out.write("${item.size}:".toByteArray()); out.write(item) }
            is String -> write(item.toByteArray())
            is Number -> out.write("i${item}e".toByteArray())
            is Map<*, *> -> { out.write('d'.code); item.keys.map { it as String }.sorted().forEach { write(it); write(item[it]!!) }; out.write('e'.code) }
            is List<*> -> { out.write('l'.code); item.forEach { write(it!!) }; out.write('e'.code) }
            else -> error("Fixture type")
        } }
        write(value)
        return out.toByteArray()
    }
}
