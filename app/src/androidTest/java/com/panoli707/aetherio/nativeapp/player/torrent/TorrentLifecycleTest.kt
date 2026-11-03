package com.panoli707.aetherio.nativeapp.player.torrent

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.AetherioApplication
import com.panoli707.aetherio.nativeapp.player.VideoActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TorrentLifecycleTest {
    private class PendingEngine : TorrentEngine {
        val started = CountDownLatch(1)
        val closed = CountDownLatch(1)
        override val failures = emptyFlow<Unit>()
        override suspend fun addMagnet(magnet: String): String { started.countDown(); awaitCancellation() }
        override suspend fun files(id: String): List<TorrentFile> = error("Not prepared")
        override suspend fun prepare(id: String, file: TorrentFile): TorrentRoute = error("Not prepared")
        override suspend fun metrics(route: TorrentRoute) = TorrentMetrics()
        override suspend fun stop(route: TorrentRoute) = Unit
        override suspend fun shutdown() { closed.countDown() }
    }

    @Test fun stopAndRecreateCloseMetadataWaitAndStartFreshWithoutStaleEndpoint() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AetherioApplication
        val engines = LinkedBlockingQueue<PendingEngine>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        app.torrentSessionsOverride = TorrentSessionManager({ PendingEngine().also { engines.add(it) } }, scope)
        val request = TorrentRequest("a".repeat(40)).encode()
        try {
            val intent = Intent(app, VideoActivity::class.java).putExtra(VideoActivity.EXTRA_TORRENT_REQUEST, request)
            ActivityScenario.launch<VideoActivity>(intent).use { scenario ->
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val first = requireNotNull(engines.poll(10, TimeUnit.SECONDS))
                assertTrue(first.started.await(10, TimeUnit.SECONDS))
                scenario.moveToState(Lifecycle.State.CREATED)
                assertTrue(first.closed.await(10, TimeUnit.SECONDS))
                scenario.moveToState(Lifecycle.State.RESUMED)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val second = requireNotNull(engines.poll(10, TimeUnit.SECONDS))
                assertTrue(second.started.await(10, TimeUnit.SECONDS))
                scenario.recreate()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertTrue(second.closed.await(10, TimeUnit.SECONDS))
                val third = requireNotNull(engines.poll(10, TimeUnit.SECONDS))
                assertTrue(third.started.await(10, TimeUnit.SECONDS))
                scenario.moveToState(Lifecycle.State.CREATED)
                assertTrue(third.closed.await(10, TimeUnit.SECONDS))
            }
        } finally {
            app.torrentSessionsOverride = null
            scope.cancel()
        }
    }
}
