package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.AppState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeColorSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun profileOpensColorWindowAndAppliesPalette() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        loadThemePalette(context)
        val original = ThemePalette.entries.firstOrNull { it.accent == Accent } ?: ThemePalette.VIOLET
        val originalTransparency = TransparentControlsEnabled
        try {
            compose.setContent {
                AetherioTheme {
                    ProfileSpace(AppState(addonsLoading = false), {}, PersonalActions(), {}, {})
                }
            }

            compose.onNodeWithText("Diseño de Aetherio").performScrollTo().performClick()
            compose.onNodeWithTag("themeColorSettings").assertExists()
            compose.onNodeWithText("Colores de Aetherio").assertExists()
            compose.onNodeWithText("Botón de ejemplo").assertDoesNotExist()
            compose.onNodeWithText("El cambio se aplica al instante y se guarda en este dispositivo.").assertDoesNotExist()
            compose.onNodeWithTag("themePalette:BLUE").performClick()
            compose.runOnIdle { check(Accent == ThemePalette.BLUE.accent) }
            compose.onNodeWithTag("themeTransparency").performClick()
            compose.runOnIdle { check(TransparentControlsEnabled && AppButtonAlpha < 1f) }
            compose.onNodeWithContentDescription("Volver").performClick()
            compose.onNodeWithText("Mi espacio").assertExists()
        } finally {
            saveThemePalette(context, original)
            saveTransparentControls(context, originalTransparency)
        }
    }
}
