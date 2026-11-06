package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import com.panoli707.aetherio.nativeapp.AetherioApplication
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Own the entire subcomposition by title, not only individual remembered image fields. */
@Composable
internal fun HeroArtwork(item: MediaItem, portrait: Boolean, viewportHeight: Dp,
    modifier: Modifier = Modifier, maxArtworkHeight: Dp = Dp.Infinity,
    imageLoader: ImageLoader = LocalContext.current.imageLoader, fullImage: Boolean = false) {
    key(CanonicalArtworkIdentity.key(item)) {
        HeroArtworkContent(item, portrait, viewportHeight, modifier, maxArtworkHeight, imageLoader, fullImage)
    }
}

@Composable
private fun HeroArtworkContent(item: MediaItem, portrait: Boolean, viewportHeight: Dp,
    modifier: Modifier, maxArtworkHeight: Dp, imageLoader: ImageLoader, fullImage: Boolean) {
    val context = LocalContext.current
    val provider = LocalArtworkProvider.current ?: (context.applicationContext as AetherioApplication).artworkProvider
    val key = CanonicalArtworkIdentity.key(item)
    var choice by remember(key, provider) { mutableStateOf<CanonicalArtwork?>(null) }
    var bitmap by remember(key, provider) { mutableStateOf<ImageBitmap?>(null) }
    var loadedRatio by rememberSaveable(key) { mutableStateOf<Float?>(null) }
    var loading by remember(key, provider) { mutableStateOf(true) }
    var presence by remember(key, provider) { mutableStateOf(ArtworkTitlePresence.UNKNOWN) }
    var genericLogo by remember(key, provider) { mutableStateOf<String?>(null) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val inspection = LocalInspectionMode.current
    BoxWithConstraints(modifier.fillMaxWidth().background(Ink).testTag("heroArtwork")) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        LaunchedEffect(key, provider, widthPx, imageLoader, attempt) {
            if (inspection) return@LaunchedEffect
            loading = bitmap == null
            try {
                // Metadata updates and season changes deliberately do not restart this effect.
                val selected = choice ?: provider.resolve(item).also { choice = it; loadedRatio = it.ratio }
                val url = selected.cover ?: return@LaunchedEffect
                val heightPx = (widthPx / selected.ratio).toInt().coerceAtLeast(1)
                val result = LoadTiming.measure(LoadTiming.Stage.IMAGE_REQUEST) {
                    imageLoader.execute(ImageRequest.Builder(context).data(url).size(widthPx, heightPx)
                        .precision(Precision.INEXACT).scale(Scale.FIT).allowHardware(false).crossfade(false).build())
                }
                ensureActive()
                if (result is SuccessResult && result.drawable.intrinsicWidth > 0 && result.drawable.intrinsicHeight > 0) {
                    val decoded = withContext(Dispatchers.Default) { result.drawable.toBitmap().asImageBitmap() }
                    ensureActive()
                    bitmap = decoded
                    loadedRatio = decoded.width.toFloat() / decoded.height
                    presence = selected.titlePresence
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the canonical illustration or the neutral frame; never rotate artwork. */ }
            finally { loading = false }
        }
        LaunchedEffect(key, bitmap, choice) {
            val selected = choice ?: return@LaunchedEffect
            val ready = bitmap ?: return@LaunchedEffect
            if (selected.titlePresence == ArtworkTitlePresence.UNKNOWN) {
                presence = withTimeoutOrNull(4_000) { ArtworkTextRecognizer.analyze(context, selected.cover.orEmpty(), ready) }
                    ?: ArtworkTitlePresence.UNKNOWN
            }
            if (presence == ArtworkTitlePresence.ABSENT && selected.logo == null) genericLogo = provider.logo(item)
        }
        val ratio = loadedRatio ?: choice?.ratio ?: 2f / 3f
        val imageHeight = if (fullImage) detailArtworkHeight(maxWidth, ratio)
            else heroArtworkHeight(maxWidth, viewportHeight, ratio, maxArtworkHeight)
        // Clear all unvalidated logo inputs while resolving. Only the selected pair can be drawn.
        val titleItem = choice?.copy(logo = if (bitmap == null) null else choice?.logo ?: genericLogo)?.item(item)
            ?: item.copy(logoUrl = null, logoAlternatives = emptyList())
        Box {
            IntegratedDetailHero(titleItem, imageHeight, bitmap == null || presence == ArtworkTitlePresence.ABSENT,
                imageLoader, compact = !fullImage,
                canonicalLogo = true) {
                bitmap?.let { ready -> Image(painter = BitmapPainter(ready, filterQuality = FilterQuality.High),
                    contentDescription = item.title, contentScale = if (fullImage) ContentScale.Fit else ContentScale.Crop,
                    alignment = Alignment.Center, modifier = Modifier.fillMaxSize().testTag("heroImage")) }
                if (!fullImage) Box(Modifier.fillMaxWidth().height(56.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent))))
            }
            if (loading && !inspection) androidx.compose.material3.CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(28.dp).testTag("heroArtworkLoading"), color = Accent)
            if (!loading && bitmap == null && !inspection) androidx.compose.material3.TextButton(
                onClick = { choice = null; attempt++ }, modifier = Modifier.align(Alignment.Center).testTag("heroArtworkRetry")) {
                androidx.compose.material3.Text("Reintentar portada")
            }
        }
    }
}
