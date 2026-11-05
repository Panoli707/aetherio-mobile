package com.panoli707.aetherio.nativeapp.data

/** Normalized OCR line bounds. Any substantial text near the logo is treated conservatively. */
internal data class ArtworkTextBox(val left: Float, val top: Float, val right: Float, val bottom: Float, val characters: Int)

internal object ArtworkTextPlacement {
    fun classify(boxes: List<ArtworkTextBox>): ArtworkTitlePresence {
        val text = boxes.filter { it.characters >= 2 && it.right - it.left >= .08f && it.bottom - it.top >= .012f }
        return when {
            text.isEmpty() -> ArtworkTitlePresence.ABSENT
            text.any { it.bottom > .65f } -> ArtworkTitlePresence.BOTTOM
            text.all { it.bottom <= .43f } -> ArtworkTitlePresence.TOP
            else -> ArtworkTitlePresence.CENTER
        }
    }
}
