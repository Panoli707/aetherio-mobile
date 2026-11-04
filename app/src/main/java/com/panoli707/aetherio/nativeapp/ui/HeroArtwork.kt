package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.panoli707.aetherio.nativeapp.data.ArtworkImages
import com.panoli707.aetherio.nativeapp.data.MediaItem

/** Shared by Home and details: full-width artwork, bounded cropping and display-sized decoding. */
@Composable
internal fun HeroArtwork(item: MediaItem, portrait: Boolean, viewportHeight: Dp,
    modifier: Modifier = Modifier, maxArtworkHeight: Dp = Dp.Infinity) {
    val candidates = remember(item.posterUrl, item.backdropUrl, item.portraitArtworkUrl, portrait) {
        ArtworkImages.headers(item, portrait).flatMap { art ->
            listOf(art.copy(url = ArtworkImages.highResolution(art.url)!!), art)
        }.distinctBy { it.url }
    }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val artwork = candidates.getOrNull(index)
    var loadedRatio by remember(artwork) { mutableStateOf<Float?>(null) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val inspection = LocalInspectionMode.current
    BoxWithConstraints(modifier.fillMaxWidth().background(Ink).testTag("heroArtwork")) {
        val imageHeight = heroArtworkHeight(maxWidth, viewportHeight,
            loadedRatio ?: artwork?.aspectRatio ?: (16f / 9f), maxArtworkHeight)
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = with(density) { imageHeight.roundToPx().coerceAtLeast(1) }
        val request = remember(artwork?.url, widthPx, heightPx, context) {
            ImageRequest.Builder(context).data(artwork?.url).size(widthPx, heightPx)
                // FILL requests enough pixels for the actual drawing scale, not a smaller fit thumbnail.
                .precision(Precision.INEXACT).scale(Scale.FILL).build()
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (artwork == null) Spacer(Modifier.statusBarsPadding().height(56.dp))
            else Box(Modifier.fillMaxWidth().height(imageHeight)) {
                if (!inspection) AsyncImage(model = request, contentDescription = if (artwork.containsTitle) item.title else null,
                    // Inside uses decoded bitmap pixels as a layout limit, leaving black margins after sampling.
                    // Crop fills the screen width; heroArtworkHeight bounds how much may be trimmed vertically.
                    contentScale = ContentScale.Crop, alignment = Alignment.Center,
                    filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize().testTag("heroImage"),
                    onSuccess = { state ->
                        val drawable = state.result.drawable
                        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                            if (!state.result.isSampled && drawable.intrinsicWidth < minOf(widthPx, 540) && index < candidates.lastIndex) {
                                // Try another source before scaling up a genuinely low-resolution original.
                                index++
                            } else loadedRatio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
                        }
                    }, onError = { index++ })
                // Keep artwork readable: only fade its edges, never blur it or darken the whole image.
                Box(Modifier.fillMaxWidth().height(56.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent))))
                if (!artwork.containsTitle) Box(Modifier.fillMaxWidth().height(48.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
            }
            // A conventional poster already carries its title. Do not stamp another logo over it.
            if (artwork?.containsTitle != true) Box(Modifier.widthIn(max = 300.dp).fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                MediaTitle(item, Modifier.fillMaxWidth(), centered = true)
            }
        }
    }
}
