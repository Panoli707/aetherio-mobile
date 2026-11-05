package com.panoli707.aetherio.nativeapp.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeTrailerTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun directMediaRoutesToTheNativePlayerAndYoutubeStaysOfficialAndInternal() {
        val direct = DetailLinks.directTrailer("https://cdn.example/trailer.mp4", "Tráiler directo")!!
        val native = TrailerPlayback.intent(context, direct)
        assertEquals(VideoActivity::class.java.name, native.component!!.className)
        assertTrue(native.getBooleanExtra(VideoActivity.EXTRA_TRAILER, false))
        assertFalse(native.hasExtra(VideoActivity.EXTRA_WATCH_TARGET))
        assertFalse(native.hasExtra(VideoActivity.EXTRA_HAS_SOURCES))
        assertFalse(native.hasExtra(VideoActivity.EXTRA_TORRENT_REQUEST))
        val youtube = TrailerPlayback.intent(context, DetailLinks.trailer("M7lc1UVf-VE", "YouTube")!!)
        assertEquals(TrailerActivity::class.java.name, youtube.component!!.className)
        assertThrows(IllegalArgumentException::class.java) {
            TrailerPlayback.intent(context, direct.copy(url = "https://www.youtube.com/watch?v=M7lc1UVf-VE"))
        }
    }

    @Test fun failedNativeTrailerDoesNotOfferSourcesOrWriteEpisodeHistoryEvenWithStrayExtras() = runBlocking {
        val history = WatchHistoryRepository.get(context)
        val before = history.load()
        val intent = TrailerPlayback.intent(context, DetailLinks.directTrailer("https://cdn.example/trailer.mp4", "Tráiler")!!)
            .putExtra(VideoActivity.EXTRA_URL, "invalid-offline-fixture")
            .putExtra(VideoActivity.EXTRA_HAS_SOURCES, true)
            .putExtra(VideoActivity.EXTRA_WATCH_TARGET, WatchProgressCodec.encodeTarget(
                WatchTarget(MediaItem("1254207", MediaType.SERIES, "Not an episode"), 1, 1)))
        ActivityScenario.launch<VideoActivity>(intent).use {
            compose.onNodeWithText("Reintentar").assertIsDisplayed()
            compose.onNodeWithText("Cambiar fuente").assertDoesNotExist()
            compose.onNodeWithContentDescription("Volver").performClick()
        }
        assertEquals(before, history.load())
    }

    private fun player(view: View): Player? {
        if (view is PlayerView) return view.player
        if (view is ViewGroup) for (i in 0 until view.childCount) player(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun optInDirectSampleReallyPlaysInAetherioWithoutCreatingWatchProgress() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveNativeTrailer") == "true")
        val history = WatchHistoryRepository.get(context)
        val before = history.load()
        // Public Media3 test media, not an extracted YouTube URL or a commercial trailer.
        val trailer = DetailLinks.directTrailer("https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4", "Tráiler QA")!!
        ActivityScenario.launch<VideoActivity>(TrailerPlayback.intent(context, trailer)).use { scenario ->
            compose.waitUntil(45_000) {
                var ready = false
                scenario.onActivity { val p = player(it.window.decorView); ready = p?.playbackState == Player.STATE_READY && p.currentPosition > 0 }
                ready
            }
            scenario.onActivity { player(it.window.decorView)!!.apply { pause(); seekTo(2_000) } }
        }
        assertEquals(before, history.load())
    }
}
