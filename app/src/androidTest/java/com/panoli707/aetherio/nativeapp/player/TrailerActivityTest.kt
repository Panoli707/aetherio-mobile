package com.panoli707.aetherio.nativeapp.player

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.widget.FrameLayout
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.DetailLinks
import com.panoli707.aetherio.nativeapp.data.WatchHistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TrailerActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val trailer = DetailLinks.trailer("M7lc1UVf-VE", "Tráiler integrado de prueba")!!

    private fun fixture(scenario: ActivityScenario<TrailerActivity>, error: Int? = null) {
        scenario.onActivity { activity ->
            val web = activity.trailerWebView!!
            web.stopLoading(); web.settings.blockNetworkLoads = true
            val script = """<script>
                window.pauseCount=0;
                window.YT={Player:function(id,options){this.pauseVideo=function(){window.pauseCount++};
                  document.getElementById(id).textContent='Reproductor de prueba';options.events.onReady();
                  ${if (error != null) "options.events.onError({data:$error});" else ""}
                }};onYouTubeIframeAPIReady();</script>"""
            val html = TrailerEmbed.document(trailer.id).replace("<script src=\"https://www.youtube.com/iframe_api\"></script>", script)
            web.loadDataWithBaseURL(TrailerEmbed.BASE_URL, html, "text/html", "UTF-8", TrailerEmbed.BASE_URL)
        }
        compose.waitUntil(10_000) { var ready = false; scenario.onActivity { ready = it.ready }; ready }
    }

    private fun javascript(scenario: ActivityScenario<TrailerActivity>, script: String): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        scenario.onActivity { it.trailerWebView!!.evaluateJavascript(script) { value -> result.set(value); latch.countDown() } }
        assertTrue("WebView JavaScript did not answer", latch.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    @Test fun playerUsesTheAppIdentityAndSecureWebViewWithoutAnExternalActivity() {
        ActivityScenario.launch<TrailerActivity>(TrailerActivity.intent(context, trailer)).use { scenario ->
            fixture(scenario)
            scenario.onActivity { activity ->
                val web = activity.trailerWebView!!
                assertFalse(web.settings.allowFileAccess)
                assertFalse(web.settings.allowContentAccess)
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.settings.mixedContentMode)
                assertTrue(web.settings.javaScriptEnabled)
                assertFalse(activity.isFinishing)
            }
            assertEquals("\"${TrailerEmbed.ORIGIN}\"", javascript(scenario, "location.origin"))
            // getUrl() may still report the previous navigation during loadDataWithBaseURL.
            assertEquals("\"${TrailerEmbed.BASE_URL}\"", javascript(scenario, "document.baseURI"))
            onView(withContentDescription("Cerrar tráiler")).check(matches(isDisplayed()))
        }
    }

    @Test fun blockedEmbeddingShowsAnInAppExplanationAndRetryInsteadOfOpeningYoutube() {
        ActivityScenario.launch<TrailerActivity>(TrailerActivity.intent(context, trailer)).use { scenario ->
            fixture(scenario, 101)
            onView(withText(TrailerEmbed.error(101))).check(matches(isDisplayed()))
            onView(withText("Reintentar tráiler")).check(matches(isDisplayed()))
            scenario.onActivity { assertFalse(it.isFinishing) }
            assertEquals("\"${TrailerEmbed.BASE_URL}\"", javascript(scenario, "document.baseURI"))
        }
    }

    @Test fun fullscreenBackReturnsToTheTrailerAndClosingDestroysItsWebView() {
        ActivityScenario.launch<TrailerActivity>(TrailerActivity.intent(context, trailer)).use { scenario ->
            fixture(scenario)
            var exitedFullscreen = false
            var activity: TrailerActivity? = null
            scenario.onActivity {
                activity = it
                val full = FrameLayout(it)
                it.showFullscreen(full, WebChromeClient.CustomViewCallback { exitedFullscreen = true })
                assertEquals(View.GONE, it.trailerWebView!!.parent.let { parent -> (parent as View).visibility })
                it.onBackPressedDispatcher.onBackPressed()
                assertTrue(exitedFullscreen)
                assertFalse(it.isFinishing)
                it.onBackPressedDispatcher.onBackPressed()
            }
            compose.waitUntil(10_000) { scenario.state == Lifecycle.State.DESTROYED }
            assertNull(activity!!.trailerWebView)
        }
    }

    @Test fun goingToBackgroundPausesTheTrailerAndNeverWritesEpisodeProgress() {
        val history = WatchHistoryRepository.get(context)
        val before = runBlocking { history.load() }
        ActivityScenario.launch<TrailerActivity>(TrailerActivity.intent(context, trailer)).use { scenario ->
            fixture(scenario)
            scenario.moveToState(Lifecycle.State.STARTED)
            assertTrue(javascript(scenario, "window.pauseCount").toInt() > 0)
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
        assertEquals(before, runBlocking { history.load() })
    }

    @Test fun liveOfficialYoutubePlayerLoadsWhenExplicitlyRequested() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveTrailer") == "true")
        ActivityScenario.launch<TrailerActivity>(TrailerActivity.intent(context, trailer)).use { scenario ->
            compose.waitUntil(45_000) { var ready = false; scenario.onActivity { ready = it.ready }; ready }
            assertEquals("\"${TrailerEmbed.ORIGIN}\"", javascript(scenario, "location.origin"))
            assertEquals("\"function\"", javascript(scenario, "typeof window.aetherioPlayer.getPlayerState"))
            compose.waitUntil(45_000) {
                runCatching { javascript(scenario, "window.aetherioPlayer.getPlayerState()") == "1" }.getOrDefault(false)
            }
        }
    }
}
