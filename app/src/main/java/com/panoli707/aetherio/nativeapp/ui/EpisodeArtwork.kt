package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.panoli707.aetherio.nativeapp.data.MediaItem

/** Load only visible thumbnails. Resolve canonical fallback art only when it is actually needed. */
@Composable
internal fun EpisodeArtwork(url: String?, media: MediaItem, modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalContext.current.imageLoader) {
    var failed by remember(url, media.key) { mutableStateOf(false) }
    val primary = url?.takeIf { it.isNotBlank() }
    val inspection = LocalInspectionMode.current
    Box(modifier.background(Panel), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.ImageNotSupported, null, tint = Muted.copy(alpha = .5f), modifier = Modifier.size(24.dp))
        if (inspection) return@Box
        if (primary != null && !failed) {
            EpisodeImage(primary, "Miniatura del episodio", imageLoader, onError = { failed = true })
        } else {
            EpisodeFallback(media, imageLoader, primary)
        }
    }
}

@Composable
private fun BoxScope.EpisodeFallback(media: MediaItem, imageLoader: ImageLoader, failedPrimary: String?) {
    // Preserve the app's canonical series identity; do not guess a different episode or season.
    val landscape = canonicalArtwork(media, portrait = false)
    var landscapeFailed by remember(media.key, landscape?.cover) { mutableStateOf(false) }
    val needsPortrait = landscape != null && (landscape.cover.isNullOrBlank() || landscapeFailed || landscape.cover == failedPrimary)
    val portrait = if (needsPortrait) canonicalArtwork(media) else null
    val cover = (if (needsPortrait) portrait?.cover?.takeUnless { it == landscape?.cover || it == failedPrimary } else landscape?.cover)
        ?.takeIf { it.isNotBlank() }
    var failed by remember(cover) { mutableStateOf(false) }
    if (cover != null && !failed) {
        EpisodeImage(cover, "Imagen de la serie", imageLoader, onError = {
            if (!needsPortrait) landscapeFailed = true else failed = true
        })
    } else if (failed || (needsPortrait && portrait != null)) {
        Box(Modifier.fillMaxSize().testTag("episodeArtworkUnavailable")
            .semantics { contentDescription = "Sin miniatura disponible" })
    }
}

@Composable
private fun BoxScope.EpisodeImage(url: String, description: String, imageLoader: ImageLoader, onError: () -> Unit) {
    val context = LocalContext.current
    val request = remember(context, url) {
        // AsyncImage supplies the measured card size; no original-sized decode or prefetch/OCR.
        ImageRequest.Builder(context).data(url).crossfade(false).build()
    }
    AsyncImage(request, description, imageLoader = imageLoader, contentScale = ContentScale.Crop,
        onError = { onError() }, modifier = Modifier.fillMaxSize().testTag("episodeArtworkImage"))
}
