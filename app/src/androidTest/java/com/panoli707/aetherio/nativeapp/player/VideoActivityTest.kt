package com.panoli707.aetherio.nativeapp.player

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoActivityTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun intent(fromSources: Boolean) = Intent(
        InstrumentationRegistry.getInstrumentation().targetContext, VideoActivity::class.java
    ).putExtra(VideoActivity.EXTRA_URL, "invalid-offline-fixture")
        .putExtra(VideoActivity.EXTRA_HAS_SOURCES, fromSources)

    @Test fun sourceErrorStartsLandscapeRotatesWithoutRecreationAndReturnsSourceRequest() {
        ActivityScenario.launchActivityForResult<VideoActivity>(intent(true)).use { scenario ->
            var initial: VideoActivity? = null
            compose.waitUntil(10_000) {
                var landscape = false
                scenario.onActivity {
                    initial = it
                    landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
                landscape
            }
            compose.onNodeWithText("Reintentar").assertIsDisplayed()
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT }
            compose.waitUntil(10_000) {
                var portrait = false
                scenario.onActivity {
                    assertTrue("Rotation must retain the player activity", initial === it)
                    portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                }
                portrait
            }
            compose.onNodeWithText("Cambiar fuente").assertIsDisplayed().performClick()
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertTrue(scenario.result.resultData.getBooleanExtra(VideoActivity.EXTRA_FIND_SOURCES, false))
        }
    }

    @Test fun manualLinkErrorDoesNotOfferAnUnrelatedSourceQuery() {
        ActivityScenario.launch<VideoActivity>(intent(false)).use {
            compose.onNodeWithText("Reintentar").assertIsDisplayed()
            compose.onNodeWithText("Cambiar fuente").assertDoesNotExist()
            compose.onNodeWithContentDescription("Cambiar fuente").assertDoesNotExist()
            compose.onNodeWithContentDescription("Volver").performClick()
        }
    }
}
