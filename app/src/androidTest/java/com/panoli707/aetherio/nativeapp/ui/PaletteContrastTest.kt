package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaletteContrastTest {
    @Test fun approvedPaletteAndAndroidResourcesMatch() {
        assertEquals(Color(0xFF0B0B0F), Ink)
        assertEquals(Color(0xFF17171E), Panel)
        assertEquals(Color(0xFFF5F5F7), Cream)
        assertEquals(Color(0xFFAAAAB8), Muted)
        assertEquals(Color(0xFFA78BFA), Accent)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(Ink, Color(ContextCompat.getColor(context, R.color.startup_background)))
        assertEquals(Accent, Color(ContextCompat.getColor(context, R.color.app_accent)))
        assertEquals(Accent, AetherioColors.primary)
        assertEquals(Color.Transparent, AetherioColors.surfaceTint)
    }

    @Test fun textAndSemanticStatesMeetNormalTextContrast() {
        listOf(Ink, Panel, RaisedPanel, HighestPanel).forEach { background ->
            listOf(Cream, Muted, Accent, Success, Warning, Error).forEach { foreground ->
                contrastAtLeast(foreground, background, 4.5f)
            }
        }
        with(AetherioColors) {
            listOf(onPrimary to primary, onPrimaryContainer to primaryContainer,
                onSecondary to secondary, onSecondaryContainer to secondaryContainer,
                onTertiary to tertiary, onTertiaryContainer to tertiaryContainer,
                onError to error, onErrorContainer to errorContainer,
                inverseOnSurface to inverseSurface, inversePrimary to inverseSurface).forEach { (text, background) ->
                contrastAtLeast(text, background, 4.5f)
            }
        }
    }

    @Test fun translucentPanelsStayReadableEvenOverWhiteArtwork() {
        val worstPanel = Panel.copy(alpha = GlassPanelAlpha).compositeOver(Color.White)
        listOf(Cream, Muted, Accent, Error).forEach { contrastAtLeast(it, worstPanel, 4.5f) }
        contrastAtLeast(Outline, worstPanel, 3f)
        listOf(Ink, Panel, RaisedPanel, HighestPanel).forEach {
            contrastAtLeast(Outline, it, 3f)
            contrastAtLeast(Accent, it, 3f)
        }
    }

    private fun contrastAtLeast(foreground: Color, background: Color, minimum: Float) {
        val light = foreground.compositeOver(background).luminance()
        val dark = background.luminance()
        val ratio = (maxOf(light, dark) + .05f) / (minOf(light, dark) + .05f)
        assertTrue("Contrast $ratio < $minimum for $foreground over $background", ratio >= minimum)
    }
}
