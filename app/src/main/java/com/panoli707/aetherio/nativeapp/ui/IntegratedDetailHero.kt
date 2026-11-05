package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/** One photograph, one continuous fade and one title: the logo has no separate panel/background. */
@Composable
internal fun IntegratedDetailHero(item: MediaItem, imageHeight: Dp, showTitle: Boolean,
    imageLoader: ImageLoader, overlayTitle: Boolean = true, image: @Composable () -> Unit) {
    val density = LocalDensity.current
    val imageHeightPx = with(density) { imageHeight.roundToPx().coerceAtLeast(1) }
    val bottomGap = with(density) { 12.dp.roundToPx() }
    Layout(modifier = Modifier.fillMaxWidth().testTag("detailIntegratedHero"), content = {
        Box { image() }
        Box(Modifier.testTag("detailHeroFade").drawWithCache {
            val start = imageHeightPx * if (overlayTitle) .70f else .96f
            val brush = Brush.verticalGradient(0f to Color.Transparent, .6f to Ink.copy(alpha = .65f), 1f to Ink,
                startY = start, endY = imageHeightPx.toFloat())
            onDrawBehind {
                drawRect(brush, topLeft = Offset(0f, start), size = Size(size.width, (size.height - start).coerceAtLeast(0f)))
            }
        })
        if (showTitle) Box(Modifier.widthIn(max = 300.dp).fillMaxWidth().padding(horizontal = 18.dp)) {
            DetailTitle(item, Modifier.fillMaxWidth(), imageLoader)
        }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val title = measurables.getOrNull(2)?.measure(Constraints(maxWidth = width))
        val titleHeight = title?.height ?: 0
        val overlap = if (overlayTitle) detailTitleOverlap(imageHeightPx, titleHeight) else 0
        val titleTop = imageHeightPx - overlap
        val height = constraints.constrainHeight(if (title != null) maxOf(imageHeightPx, titleTop + titleHeight) + bottomGap else imageHeightPx)
        val photo = measurables[0].measure(Constraints.fixed(width, imageHeightPx))
        val fade = measurables[1].measure(Constraints.fixed(width, height))
        layout(width, height) {
            photo.placeRelative(0, 0)
            fade.placeRelative(0, 0)
            title?.placeRelative((width - title.width) / 2, titleTop)
        }
    }
}
