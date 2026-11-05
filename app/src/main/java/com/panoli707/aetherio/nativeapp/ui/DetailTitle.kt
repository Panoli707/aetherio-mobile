package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import com.panoli707.aetherio.nativeapp.data.ArtworkImages
import com.panoli707.aetherio.nativeapp.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A separate, stable title area. A failed/missing logo never leaves a detail page unnamed. */
@Composable
internal fun DetailTitle(item: MediaItem, modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalContext.current.imageLoader, compact: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val inspection = LocalInspectionMode.current
    val candidates = remember(item.key, item.animeEntryId, item.seasonHint, item.logoUrl, item.logoAlternatives) { ArtworkImages.logos(item) }
    var logo by remember(item.key, candidates) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(item.key, candidates) { mutableStateOf(candidates.isNotEmpty()) }
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center)
    BoxWithConstraints(modifier.testTag("detailTitle").semantics {
        stateDescription = if (loading && !inspection) "Cargando logo" else "Título preparado"
    }, contentAlignment = Alignment.Center) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val textSize = measurer.measure(AnnotatedString(item.title), style, constraints = Constraints(maxWidth = widthPx)).size
        // A valid logo must not inherit hundreds of dp of blank space from a novel-length title.
        // Only the final written fallback expands; the loading/decoded-logo frame stays fixed.
        val logoHeight = if (compact) 72.dp else 112.dp
        val frameHeight = if (!loading && logo == null) maxOf(if (compact) 0.dp else logoHeight, with(density) { textSize.height.toDp() }) else logoHeight
        // A text fallback changing height must not restart the failed logo request.
        val heightPx = with(density) { logoHeight.roundToPx().coerceAtLeast(1) }
        LaunchedEffect(item.key, candidates, widthPx, heightPx, imageLoader) {
            if (inspection) return@LaunchedEffect
            loading = candidates.isNotEmpty()
            var decoded: ImageBitmap? = null
            for (candidate in candidates) {
                val request = ImageRequest.Builder(context).data(candidate).size(widthPx, heightPx)
                    .precision(Precision.INEXACT).scale(Scale.FIT).crossfade(false).build()
                val result = imageLoader.execute(request)
                ensureActive()
                if (result !is SuccessResult || result.drawable.intrinsicWidth <= 0 || result.drawable.intrinsicHeight <= 0) continue
                decoded = withContext(Dispatchers.Default) { result.drawable.toBitmap().asImageBitmap() }
                break
            }
            ensureActive()
            logo = decoded
            loading = false
        }
        Box(Modifier.fillMaxWidth().height(frameHeight), contentAlignment = Alignment.Center) {
            val ready = logo
            if (ready != null) {
                val painter = remember(ready) { BitmapPainter(ready, filterQuality = FilterQuality.High) }
                Image(painter, item.title, Modifier.fillMaxSize().testTag("detailLogo").drawWithContent {
                    // A fine alpha-shaped outline keeps dark logos readable over the fade without
                    // introducing a rectangular panel or changing the logo's colours/proportions.
                    val scale = minOf(size.width / ready.width, size.height / ready.height)
                    val fitted = Size(ready.width * scale, ready.height * scale)
                    val x = (size.width - fitted.width) / 2f
                    val y = (size.height - fitted.height) / 2f
                    val radius = 1.dp.toPx()
                    for ((dx, dy) in listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f)) {
                        translate(x + dx * radius, y + dy * radius) {
                            with(painter) { draw(fitted, colorFilter = ColorFilter.tint(Color.White.copy(alpha = .85f))) }
                        }
                    }
                    drawContent()
                }, contentScale = ContentScale.Fit)
            }
            else Text(item.title, Modifier.fillMaxWidth().testTag("detailTitleText"), style = style,
                maxLines = if (loading) 3 else Int.MAX_VALUE, overflow = if (loading) TextOverflow.Ellipsis else TextOverflow.Clip)
        }
    }
}
