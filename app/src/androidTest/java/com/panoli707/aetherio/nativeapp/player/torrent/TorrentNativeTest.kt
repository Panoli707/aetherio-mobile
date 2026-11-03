package com.panoli707.aetherio.nativeapp.player.torrent

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.engine.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No public swarm or copyrighted media: a generated CC0 video, a private torrent and a local controlled webseed. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TorrentNativeTest {
    private fun bencode(value: Any): ByteArray {
        val out = ByteArrayOutputStream()
        fun write(item: Any) {
            when (item) {
                is ByteArray -> { out.write("${item.size}:".toByteArray()); out.write(item) }
                is String -> write(item.toByteArray())
                is Number -> out.write("i${item}e".toByteArray())
                is Map<*, *> -> { out.write('d'.code); item.keys.map { it as String }.sorted().forEach { write(it); write(item[it]!!) }; out.write('e'.code) }
                is List<*> -> { out.write('l'.code); item.forEach { write(it!!) }; out.write('e'.code) }
                else -> error("Unsupported test fixture")
            }
        }
        write(value)
        return out.toByteArray()
    }

    @Test fun realEngineVerifiesTorrentPiecesAndMedia3RendersVideoOverLocalRanges() = runBlocking<Unit>(Dispatchers.IO) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val payload = instrumentation.context.assets.open("torrent-fixture.mp4").use { it.readBytes() }
        assertEquals("911f08a8cee2153b0790d7b0f0550045a8d70f806f4b3c725a0d852e8d33f26d",
            MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) })
        val root = File(context.cacheDir, "torrent-native-test-${System.nanoTime()}").apply { mkdirs() }
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/fixture.mp4") return MockResponse().setResponseCode(404)
                val range = request.getHeader("Range")?.removePrefix("bytes=")?.substringBefore(',')
                val start = range?.substringBefore('-')?.toIntOrNull() ?: 0
                val end = range?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(payload.lastIndex) ?: payload.lastIndex
                if (start !in payload.indices || end < start) return MockResponse().setResponseCode(416)
                val response = MockResponse().setHeader("Accept-Ranges", "bytes").setHeader("Content-Type", "video/mp4")
                    .setBody(Buffer().write(payload, start, end - start + 1))
                if (range != null) response.setResponseCode(206).setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                return response
            }
        }
        server.start()
        var engine: NuvioEngine? = null
        var player: ExoPlayer? = null
        var texture: SurfaceTexture? = null
        var surface: Surface? = null
        var route: TorrentRoute? = null
        try {
            val activeEngine = NuvioEngine.create(NuvioEngineConfig(File(root, "state"), File(root, "cache"),
                uploadMode = NuvioUploadMode.Disabled, warmTorrentTimeoutMilliseconds = 0)).also { engine = it }
            assertEquals("0.1.6", NuvioEngine.version)
            assertTrue(NuvioEngine.protocolBackendVersion.startsWith("2.0.12"))
            val pieceLength = 16 * 1024
            val hashes = ByteArrayOutputStream()
            payload.asList().chunked(pieceLength).forEach { piece -> hashes.write(MessageDigest.getInstance("SHA-1").digest(piece.toByteArray())) }
            val torrent = bencode(mapOf("info" to mapOf("name" to "fixture.mp4", "length" to payload.size,
                "piece length" to pieceLength, "pieces" to hashes.toByteArray(), "private" to 1),
                "url-list" to listOf(server.url("/fixture.mp4").toString())))
            val id = withTimeout(15_000) { activeEngine.addTorrent(torrent) }
            val stream = withTimeout(15_000) { activeEngine.prepareStream(id, 0) }
            route = TorrentRoute(stream.id, stream.url, stream.fileIndex, stream.fileSize)
            val frame = CountDownLatch(1)
            val failure = AtomicReference<PlaybackException?>()
            val localRoute = requireNotNull(route)
            instrumentation.runOnMainSync {
                texture = SurfaceTexture(0)
                surface = Surface(texture)
                player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(
                    TorrentDataSourceFactory(localRoute, OkHttpDataSource.Factory(OkHttpClient())))).build().apply {
                    setVideoSurface(surface)
                    volume = 0f
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { frame.countDown() }
                        override fun onPlayerError(error: PlaybackException) { failure.set(error); frame.countDown() }
                    })
                    setMediaItem(MediaItem.fromUri(localRoute.url))
                    prepare()
                    play()
                }
            }
            assertTrue("No first frame from real native torrent engine", frame.await(60, TimeUnit.SECONDS))
            assertNull("Media3 failed: ${failure.get()?.errorCodeName}", failure.get())
            assertTrue(activeEngine.currentStreamStats(stream.id).verifiedFileBytes > 0)
            instrumentation.runOnMainSync { player!!.seekTo(2000) }
            activeEngine.stopStream(stream.id)
        } finally {
            route?.revoke()
            instrumentation.runOnMainSync { player?.release(); surface?.release(); texture?.release() }
            engine?.shutdown()
            server.shutdown()
            root.deleteRecursively() // A uniquely owned test-only directory, after native shutdown.
        }
    }

    @Test fun localDatasourceRejectsRedirectsAndDoesNotSendProviderCredentials() {
        val server = MockWebServer()
        server.start()
        try {
            val token = "a".repeat(64)
            val route = TorrentRoute(token, "http://127.0.0.1:${server.port}/stream/$token", 0, 10)
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.invalid/stolen"))
            val source = TorrentDataSourceFactory(route, OkHttpDataSource.Factory(OkHttpClient())).createDataSource()
            try {
                assertThrows(java.io.IOException::class.java) { source.open(androidx.media3.datasource.DataSpec(android.net.Uri.parse(route.url))) }
            } finally { source.close() }
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
            assertNull(request.getHeader("Referer"))
            assertEquals(1, server.requestCount)
            route.revoke()
        } finally { server.shutdown() }
    }
}
