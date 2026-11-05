package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import com.panoli707.aetherio.nativeapp.data.ArtworkImages
import com.panoli707.aetherio.nativeapp.data.MediaItem
import com.panoli707.aetherio.nativeapp.data.ArtworkTitlePresence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private data class ResolvedHero(val artwork: ArtworkImages.Header, val bitmap: ImageBitmap,
    val ratio: Float, val item: MediaItem, val index: Int)

/** Shared by Home and details: full-width artwork, bounded cropping and display-sized decoding. */
@Composable
internal fun HeroArtwork(item: MediaItem, portrait: Boolean, viewportHeight: Dp,
    modifier: Modifier = Modifier, maxArtworkHeight: Dp = Dp.Infinity,
    imageLoader: ImageLoader = LocalContext.current.imageLoader, fullImage: Boolean = false,
    analyzeText: (suspend (String, ImageBitmap) -> ArtworkTitlePresence)? = null) {
    val candidates = remember(item.posterUrl, item.backdropUrl, item.portraitArtworkUrl, portrait,
        item.posterTitlePresence, item.portraitTitlePresence, item.backdropTitlePresence, item.portraitAlternatives) {
        ArtworkImages.headers(item, portrait).flatMap { art ->
            listOf(art.copy(url = ArtworkImages.highResolution(art.url)!!), art)
        }.distinctBy { it.url }
    }
    val imageKey = candidates.map { it.url }
    var index by rememberSaveable(imageKey) { mutableIntStateOf(0) }
    var loadedRatio by rememberSaveable(imageKey) { mutableStateOf<Float?>(null) }
    var settled by rememberSaveable(imageKey) { mutableStateOf(false) }
    // A replacement is prepared off-screen; never draw a rejected candidate or two covers at once.
    var resolved by remember(item.key, portrait) { mutableStateOf<ResolvedHero?>(null) }
    var loading by remember(item.key, portrait, imageKey) { mutableStateOf(candidates.isNotEmpty()) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val inspection = LocalInspectionMode.current
    BoxWithConstraints(modifier.fillMaxWidth().background(Ink).testTag("heroArtwork")) {
        val width = maxWidth
        val widthPx = with(density) { width.roundToPx().coerceAtLeast(1) }
        LaunchedEffect(item.key, portrait, imageKey, widthPx, viewportHeight, maxArtworkHeight, imageLoader, fullImage) {
            if (inspection) return@LaunchedEffect
            loading = candidates.isNotEmpty()
            var chosen: ResolvedHero? = null
            // Save the successful candidate across LazyColumn disposal, but not bitmap data in a Bundle.
            val start = index.coerceIn(0, candidates.lastIndex.coerceAtLeast(0))
            // Once displayed, a cover is pinned even if a later cache miss/network failure occurs
            // after LazyColumn disposal. Do not silently replace it with another illustration.
            val end = if (settled) start else candidates.lastIndex
            for (candidateIndex in start..end) {
                val candidate = candidates[candidateIndex]
                // AniList extraLarge covers can be 425–460 px at their native maximum. A usable
                // portrait must not turn into a short landscape strip just to meet a 540 px heuristic.
                // Still try every sharper portrait variant, and retain the banner fallback for tiny/missing covers.
                if (portrait && candidate.aspectRatio >= 1f && chosen != null &&
                    chosen.ratio < 1f && (fullImage || chosen.bitmap.width >= minOf(widthPx, 320))) break
                val requestHeight = if (fullImage) detailArtworkHeight(width, candidate.aspectRatio)
                    else heroArtworkHeight(width, viewportHeight, candidate.aspectRatio, maxArtworkHeight)
                val heightPx = with(density) { requestHeight.roundToPx().coerceAtLeast(1) }
                val request = ImageRequest.Builder(context).data(candidate.url).size(widthPx, heightPx)
                    .precision(Precision.INEXACT).scale(Scale.FILL).allowHardware(false).crossfade(false).build()
                val result = imageLoader.execute(request)
                ensureActive()
                if (result !is SuccessResult) continue
                val drawable = result.drawable
                if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) continue
                val next = ResolvedHero(candidate, withContext(Dispatchers.Default) { drawable.toBitmap().asImageBitmap() },
                    drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight, item, candidateIndex)
                val preferPortrait = fullImage && portrait && next.ratio < 1f && chosen?.ratio?.let { it >= 1f } == true
                if (chosen == null || preferPortrait ||
                    (!(fullImage && portrait && chosen.ratio < 1f && next.ratio >= 1f) && next.bitmap.width > chosen.bitmap.width)) chosen = next
                // The URL's field name is not proof of orientation: a landscape file cannot win
                // the portrait search early just because it was supplied as posterUrl.
                if ((!fullImage || !portrait || next.ratio < 1f || candidateIndex == candidates.lastIndex) &&
                    (result.isSampled || drawable.intrinsicWidth >= minOf(widthPx, 540))) {
                    if (!(fullImage && portrait && chosen?.ratio?.let { it < 1f } == true && next.ratio >= 1f)) chosen = next
                    break
                }
            }
            // Inspect once after decoding, never during drawing or scrolling. Printed text at the
            // top is compatible with the bottom logo; bottom/central text triggers same-scope alternatives.
            suspend fun inspected(value: ResolvedHero): ResolvedHero {
                val presence = value.artwork.titlePresence.takeUnless { it == ArtworkTitlePresence.UNKNOWN || it == ArtworkTitlePresence.EMBEDDED }
                    ?: (analyzeText?.invoke(value.artwork.url, value.bitmap)
                        ?: ArtworkTextRecognizer.analyze(context, value.artwork.url, value.bitmap))
                return value.copy(artwork = value.artwork.copy(titlePresence = presence))
            }
            chosen = chosen?.let { inspected(it) }
            if (chosen != null && !ArtworkImages.overlayTitle(chosen.artwork) && !settled) {
                for ((candidateIndex, candidate) in candidates.withIndex().filter { (_, art) ->
                    art.url != chosen.artwork.url && art.kind != ArtworkImages.Kind.BACKDROP &&
                        ArtworkImages.highResolution(art.url) == art.url
                }.take(4)) {
                    val heightPx = with(density) { detailArtworkHeight(width, candidate.aspectRatio).roundToPx().coerceAtLeast(1) }
                    val result = imageLoader.execute(ImageRequest.Builder(context).data(candidate.url).size(widthPx, heightPx)
                        .allowHardware(false).precision(Precision.INEXACT).scale(Scale.FILL).crossfade(false).build())
                    ensureActive()
                    if (result !is SuccessResult || result.drawable.intrinsicWidth <= 0 || result.drawable.intrinsicHeight <= 0) continue
                    val bitmap = withContext(Dispatchers.Default) { result.drawable.toBitmap().asImageBitmap() }
                    val alternative = inspected(ResolvedHero(candidate, bitmap, bitmap.width.toFloat() / bitmap.height, item, candidateIndex))
                    if (alternative.ratio < 1f && ArtworkImages.overlayTitle(alternative.artwork)) { chosen = alternative; break }
                }
            }
            ensureActive()
            resolved = chosen ?: resolved?.takeIf { it.artwork.url == candidates.getOrNull(start)?.url }
            chosen?.let { index = it.index; loadedRatio = it.ratio; settled = true }
            loading = false
        }
        val artwork = resolved?.artwork
            ?: if (loading || inspection || settled) candidates.getOrNull(index) else null
        val ratio = resolved?.ratio ?: loadedRatio ?: artwork?.aspectRatio ?: (16f / 9f)
        val imageHeight = if (fullImage) detailArtworkHeight(maxWidth, ratio)
            else heroArtworkHeight(maxWidth, viewportHeight, ratio, maxArtworkHeight)
        val separateTitle = ArtworkImages.separateTitle(artwork, detail = fullImage)
        val titleItem = if (resolved?.artwork?.url in imageKey) item else resolved?.item ?: item
        if (fullImage) {
            Box {
                IntegratedDetailHero(titleItem, imageHeight, separateTitle, imageLoader) {
                    resolved?.let { ready -> Image(painter = BitmapPainter(ready.bitmap, filterQuality = FilterQuality.High),
                        contentDescription = if (!separateTitle) item.title else null,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().testTag("heroImage")) }
                }
                if (loading && !inspection) androidx.compose.material3.CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(28.dp).testTag("heroArtworkLoading"), color = Accent)
            }
            return@BoxWithConstraints
        }
        Box {
            IntegratedDetailHero(titleItem, if (artwork == null) 180.dp else imageHeight, separateTitle, imageLoader, compact = true) {
                resolved?.let { ready -> Image(painter = BitmapPainter(ready.bitmap, filterQuality = FilterQuality.High),
                    contentDescription = if (!separateTitle) item.title else null,
                    // Inside uses decoded bitmap pixels as a layout limit, leaving black margins after sampling.
                    // Crop fills the screen width; heroArtworkHeight bounds how much may be trimmed vertically.
                    contentScale = if (fullImage) ContentScale.Fit else ContentScale.Crop, alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().testTag("heroImage")) }
                // Keep artwork readable: only fade its edges, never blur it or darken the whole image.
                Box(Modifier.fillMaxWidth().height(56.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent))))
            }
            if (loading && !inspection) androidx.compose.material3.CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(28.dp).testTag("heroArtworkLoading"), color = Accent)
        }
    }
}
