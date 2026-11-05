package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.MediaItem
import coil.ImageLoader

/** Shared Home/detail geometry: the entire title stays inside the photo, drawn on top of its fade. */
@Composable
internal fun IntegratedDetailHero(item: MediaItem, imageHeight: Dp, showTitle: Boolean,
    imageLoader: ImageLoader, compact: Boolean = false, canonicalLogo: Boolean = false,
    titleLoading: Boolean = false, image: @Composable () -> Unit) {
    val density = LocalDensity.current
    val imageHeightPx = with(density) { imageHeight.roundToPx().coerceAtLeast(1) }
    val bottomGap = with(density) { 8.dp.roundToPx() }
    Layout(modifier = Modifier.fillMaxWidth().testTag("detailIntegratedHero"), content = {
        Box { image() }
        Box(Modifier.testTag("detailHeroFade").drawWithCache {
            val brush = Brush.verticalGradient(0f to Color.Transparent, .6f to Ink.copy(alpha = .65f), 1f to Ink,
                startY = 0f, endY = size.height)
            onDrawBehind {
                drawRect(brush)
            }
        })
        if (showTitle) Box(Modifier.widthIn(max = 300.dp).fillMaxWidth().padding(horizontal = 18.dp)) {
            DetailTitle(item, Modifier.fillMaxWidth(), imageLoader, compact = compact, insideArtwork = true,
                canonical = canonicalLogo, pending = titleLoading)
        }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.constrainHeight(imageHeightPx)
        val start = artworkFadeStart(height)
        val title = measurables.getOrNull(2)?.measure(Constraints(maxWidth = width,
            maxHeight = (height - bottomGap).coerceAtLeast(0)))
        val titleTop = artworkTitleTop(height, title?.height ?: 0, bottomGap)
        val photo = measurables[0].measure(Constraints.fixed(width, height))
        val fade = measurables[1].measure(Constraints.fixed(width, height - start))
        layout(width, height) {
            photo.placeRelative(0, 0)
            fade.placeRelative(0, start)
            title?.placeRelative((width - title.width) / 2, titleTop)
        }
    }
}
