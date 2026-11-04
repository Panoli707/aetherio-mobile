package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A shared grid for every poster surface: two columns on narrow windows, three on phones. */
internal fun posterColumns(width: Dp): Int = when {
    width < 360.dp -> 2
    width < 600.dp -> 3
    else -> ((width.value - 24f) / 152f).toInt().coerceAtLeast(4)
}

/** Rows and grids use the same outer margins, spacing and portrait-cover width. */
internal fun posterWidth(width: Dp): Dp = ((width - 36.dp - 12.dp * (posterColumns(width) - 1)) / posterColumns(width))
    .coerceAtLeast(1.dp)

/** Leave room for the next row and floating navigation instead of occupying most of the screen. */
internal fun homeHeroHeight(width: Dp, height: Dp): Dp = if (width < 600.dp)
    (width * 1.36f).coerceIn(340.dp, 620.dp).coerceAtMost(height * .62f)
else 400.dp.coerceAtMost(height * .70f)

/** Fill the available width, with at most 18% vertical cropping instead of letterboxing or deep zoom. */
internal fun heroArtworkHeight(width: Dp, viewportHeight: Dp, aspectRatio: Float,
    maxHeight: Dp = Dp.Infinity): Dp {
    val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    val naturalHeight = width / ratio
    val preferred = naturalHeight.coerceAtMost(viewportHeight * .55f).coerceAtMost(620.dp).coerceAtMost(maxHeight)
    // On a short window, allow scrolling rather than cropping most of a face or shrinking the poster.
    return preferred.coerceAtLeast(naturalHeight * .82f).coerceAtLeast(1.dp)
}

/** Detail pages scroll: the complete image, including an embedded title at either edge, has priority. */
internal fun detailArtworkHeight(width: Dp, aspectRatio: Float): Dp {
    val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: (2f / 3f)
    return (width / ratio).coerceAtLeast(1.dp)
}

/** Overlap only the title, never crop/resize the underlying photograph to make room for it. */
internal fun detailTitleOverlap(imageHeightPx: Int, titleHeightPx: Int): Int =
    minOf((imageHeightPx * .24f).toInt(), (titleHeightPx * .55f).toInt()).coerceAtLeast(0)
