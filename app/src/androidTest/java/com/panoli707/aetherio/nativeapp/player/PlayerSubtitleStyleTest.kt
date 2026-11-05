package com.panoli707.aetherio.nativeapp.player

import android.graphics.Bitmap
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlayerSubtitleStyleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun textAndEmbeddedWindowBackgroundsAreTransparentOnBrightAndDarkFrames() {
        assertEquals(Color.WHITE, PlayerSubtitleStyle.caption.foregroundColor)
        assertEquals(Color.TRANSPARENT, PlayerSubtitleStyle.caption.backgroundColor)
        assertEquals(Color.TRANSPARENT, PlayerSubtitleStyle.caption.windowColor)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_OUTLINE, PlayerSubtitleStyle.caption.edgeType)
        assertEquals(Color.BLACK, PlayerSubtitleStyle.caption.edgeColor)
        val background = mutableStateOf(Color.rgb(190, 220, 200))
        val styledText = SpannableString("Subtítulos sin fondo negro").apply {
            setSpan(BackgroundColorSpan(Color.BLACK), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val cue = Cue.Builder().setText(styledText).setWindowColor(Color.BLACK).setSize(.9f).build()
        compose.setContent {
            AndroidView(factory = { context -> SubtitleView(context).apply {
                PlayerSubtitleStyle.applyTo(this)
                setCues(listOf(cue))
            } }, update = { it.setBackgroundColor(background.value) }, modifier = Modifier.fillMaxSize())
        }
        listOf(Color.rgb(190, 220, 200), Color.rgb(35, 55, 80)).forEachIndexed { index, color ->
            compose.runOnIdle { background.value = color }
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            var black = 0
            var white = 0
            var longestBlackRun = 0
            for (y in 0 until bitmap.height) {
                var run = 0
                for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (pixel == Color.BLACK) { black++; run++ } else run = 0
                    longestBlackRun = maxOf(longestBlackRun, run)
                    if (pixel == Color.WHITE) white++
                }
            }
            assertTrue("Text must still be visible", white > 50)
            assertTrue("Only glyph outlines may be black, not cue windows", black < bitmap.width * bitmap.height / 40)
            assertTrue("No continuous black subtitle box", longestBlackRun < bitmap.width / 4)
            if (InstrumentationRegistry.getArguments().getString("capturePlayer") == "true") {
                val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
                File(directory, "subtitles-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
