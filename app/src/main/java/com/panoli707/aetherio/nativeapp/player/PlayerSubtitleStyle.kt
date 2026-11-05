package com.panoli707.aetherio.nativeapp.player

import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/** One style for external and embedded text tracks, regardless of the stream's provider or transport. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object PlayerSubtitleStyle {
    val caption = CaptionStyleCompat(
        Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, Typeface.DEFAULT
    )

    fun applyTo(view: SubtitleView) {
        // Embedded SRT/WebVTT/ASS spans and cue window colors can otherwise restore a black box.
        // Media3 retains timing and cue positioning; bitmap/hardcoded subtitles are not restyled.
        view.setApplyEmbeddedStyles(false)
        view.setApplyEmbeddedFontSizes(false)
        view.setStyle(caption)
        view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
    }
}
