package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.R
import com.panoli707.aetherio.nativeapp.data.AppState
import com.panoli707.aetherio.nativeapp.data.UpdateState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun nativeStartingThemeHasTransparentIconNoBrandingAndMatchingBackground() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val theme = resources.newTheme().apply { applyStyle(R.style.Theme_Aetherio_Starting, true) }
        val attributes = theme.obtainStyledAttributes(intArrayOf(
            android.R.attr.windowSplashScreenAnimatedIcon,
            android.R.attr.windowSplashScreenBrandingImage,
            android.R.attr.windowSplashScreenBackground
        ))
        try {
            val icon = attributes.getDrawable(0)
            assertNotNull(icon)
            assertTrue(icon is GradientDrawable)
            assertNull(attributes.getDrawable(1))
            assertEquals(Color.rgb(0x0B, 0x0B, 0x0F), attributes.getColor(2, Color.TRANSPARENT))

            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            try {
                icon!!.setBounds(0, 0, 32, 32)
                icon.draw(Canvas(bitmap))
                val pixels = IntArray(32 * 32)
                bitmap.getPixels(pixels, 0, 32, 0, 0, 32, 32)
                assertTrue("The native splash icon must be fully transparent", pixels.all { Color.alpha(it) == 0 })
            } finally {
                bitmap.recycle()
            }
        } finally {
            attributes.recycle()
        }
    }

    @Test fun initialLoadingShowsSplashAndLaterLoadingDoesNotRestoreIt() {
        val state = mutableStateOf(AppState(
            homeLoading = true,
            sections = emptyList(),
            homeError = null,
            addonsLoading = false
        ))
        render(state)

        compose.onNodeWithContentDescription("Logo de Aetherio").assertIsDisplayed()
        compose.onNodeWithText("Aetherio").assertIsDisplayed()
        compose.onNodeWithText("Cargando\u2026").assertIsDisplayed()
        compose.onNodeWithText("Ajustes").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(homeLoading = false) }

        compose.onNodeWithContentDescription("Logo de Aetherio").assertDoesNotExist()
        compose.onNodeWithText("Cargando\u2026").assertDoesNotExist()
        compose.onNodeWithText("Ajustes").assertIsDisplayed()

        compose.runOnIdle { state.value = state.value.copy(homeLoading = true) }

        compose.onNodeWithContentDescription("Logo de Aetherio").assertDoesNotExist()
        compose.onNodeWithText("Cargando\u2026").assertDoesNotExist()
        compose.onNodeWithText("Ajustes").assertIsDisplayed()
    }

    @Test fun initialLoadingErrorDismissesSplashAndShowsRetry() {
        val state = mutableStateOf(AppState(
            homeLoading = true,
            sections = emptyList(),
            homeError = null,
            addonsLoading = false
        ))
        render(state)

        compose.onNodeWithContentDescription("Logo de Aetherio").assertIsDisplayed()
        compose.onNodeWithText("Ajustes").assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(
                homeLoading = false,
                homeError = "No se pudo cargar el contenido."
            )
        }

        compose.onNodeWithContentDescription("Logo de Aetherio").assertDoesNotExist()
        compose.onNodeWithText("Cargando\u2026").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
    }

    @Test fun initiallyReadyStateShowsNavigationAfterIdleWithoutForcedDelay() {
        val state = mutableStateOf(AppState(homeLoading = false, addonsLoading = false))
        render(state)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Logo de Aetherio").assertDoesNotExist()
        compose.onNodeWithText("Cargando\u2026").assertDoesNotExist()
        compose.onNodeWithText("Ajustes").assertIsDisplayed()
    }

    private fun render(state: State<AppState>) {
        compose.setContent {
            AetherioTheme {
                AetherioApp(
                    state = state.value,
                    updates = UpdateState(),
                    onUpdateAction = {},
                    onAutomaticUpdatesChange = {},
                    onSelectMedia = {},
                    onToggleSaved = {},
                    onSearch = {},
                    onRetryHome = {},
                    onRetryDetail = {},
                    onSelectSeason = {},
                    onRenameProfile = {},
                    onOpenVideo = { _, _ -> },
                    onAddAddon = {},
                    onRemoveAddon = {},
                    onDesktopSourceToggle = { _, _ -> },
                    onRequestSources = { _, _, _ -> },
                    onDismissSources = {},
                    onPlaySource = { _, _ -> }
                )
            }
        }
    }
}
