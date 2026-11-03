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

/** Fit the complete illustration; banners stay short and portrait covers cannot consume the screen. */
internal fun heroArtworkHeight(width: Dp, viewportHeight: Dp, aspectRatio: Float): Dp {
    val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    return (width / ratio).coerceAtMost(viewportHeight * .55f).coerceAtMost(620.dp).coerceAtLeast(1.dp)
}
