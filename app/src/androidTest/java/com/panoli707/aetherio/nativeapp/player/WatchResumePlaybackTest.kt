package com.panoli707.aetherio.nativeapp.player

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, emulator-only playback using the licensed Big Buck Bunny public ExoPlayer sample. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class WatchResumePlaybackTest {
    private fun findPlayer(view: View): Player? {
        if (view is PlayerView) return view.player
        if (view is ViewGroup) for (index in 0 until view.childCount) findPlayer(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun until(timeoutMs: Long = 40000, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            if (predicate()) return
            SystemClock.sleep(150)
        }
        fail("Playback condition did not become ready")
    }

    @Test fun realVideoResumesSameEpisodeAfterClosingAndARejectedSourceCannotEraseProgress() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualProgressPlayback") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = WatchTarget(MediaItem("1254207", MediaType.SERIES, "Big Buck Bunny QA"), 2, 3, "Resume fixture")
        val history = WatchHistoryRepository.get(context)
        history.setWatched(target, false).join()
        fun intent(valid: Boolean, provider: String) = Intent(context, VideoActivity::class.java)
            .putExtra(VideoActivity.EXTRA_URL, if (valid) "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4" else "invalid-fixture")
            .putExtra(VideoActivity.EXTRA_WATCH_TARGET, WatchProgressCodec.encodeTarget(target))
            .putExtra(VideoActivity.EXTRA_PROVIDER, provider)

        ActivityScenario.launch<VideoActivity>(intent(true, "Fuente A")).use { scenario ->
            until {
                var ready = false
                scenario.onActivity { ready = findPlayer(it.window.decorView)?.playbackState == Player.STATE_READY }
                ready
            }
            scenario.onActivity { findPlayer(it.window.decorView)!!.apply { pause(); seekTo(67321) } }
            until {
                var ready = false
                scenario.onActivity { val player = findPlayer(it.window.decorView); ready = player?.playbackState == Player.STATE_READY && player.currentPosition >= 67321 }
                ready
            }
        }
        until {
            val saved = history.entries.value.firstOrNull { it.target.key == target.key }
            saved != null && saved.positionMs >= 67321 && !saved.watched
        }
        val saved = history.entries.value.first { it.target.key == target.key }.positionMs
        ActivityScenario.launch<VideoActivity>(intent(false, "Fuente rechazada")).use { scenario ->
            scenario.onActivity { assertNull(findPlayer(it.window.decorView)) }
        }
        assertEquals(saved, history.load().first { it.target.key == target.key }.resumePositionMs)
        ActivityScenario.launch<VideoActivity>(intent(true, "Fuente B")).use { scenario ->
            until {
                var resumed = false
                scenario.onActivity {
                    val player = findPlayer(it.window.decorView)
                    if (player?.playbackState == Player.STATE_READY) {
                        player.pause()
                        resumed = player.currentPosition in saved..(saved + 5000)
                    }
                }
                resumed
            }
        }
    }
}
