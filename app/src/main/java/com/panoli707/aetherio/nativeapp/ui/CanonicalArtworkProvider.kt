package com.panoli707.aetherio.nativeapp.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal fun interface ArtworkProvider {
    suspend fun resolve(item: MediaItem): CanonicalArtwork
    suspend fun resolve(item: MediaItem, portrait: Boolean): CanonicalArtwork = resolve(item)
    suspend fun refine(item: MediaItem, current: CanonicalArtwork, portrait: Boolean): CanonicalArtwork = current
    suspend fun logo(item: MediaItem): String? = null
}
internal val LocalArtworkProvider = staticCompositionLocalOf<ArtworkProvider?> { null }

@androidx.compose.runtime.Composable
internal fun canonicalArtwork(item: MediaItem, portrait: Boolean = true): CanonicalArtwork? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val provider = LocalArtworkProvider.current ?:
        (context.applicationContext as com.panoli707.aetherio.nativeapp.AetherioApplication).artworkProvider
    val key = CanonicalArtworkIdentity.key(item)
    var choice by androidx.compose.runtime.remember(key, provider, portrait) { androidx.compose.runtime.mutableStateOf<CanonicalArtwork?>(null) }
    androidx.compose.runtime.LaunchedEffect(key, provider, portrait) {
        try { choice = provider.resolve(item, portrait) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { choice = CanonicalArtwork(item.title, null) }
    }
    return choice
}

@androidx.compose.runtime.Composable
internal fun CanonicalPoster(item: MediaItem, modifier: androidx.compose.ui.Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop, portrait: Boolean = true) {
    Artwork(canonicalArtwork(item, portrait)?.cover, modifier, description = item.title, contentScale = contentScale)
}

/** One app-scoped resolver: no per-screen choices, no season keys, no provisional illustration. */
internal class CanonicalArtworkProvider(context: Context, private val loader: () -> ImageLoader) : ArtworkProvider {
    private val context = context.applicationContext
    private val preferences = context.getSharedPreferences("canonical_artwork_v1", Context.MODE_PRIVATE)
    private val repository by lazy { AetherioRepository() }
    private val inspections = Semaphore(2)
    private val resolver = CanonicalArtworkResolver(
        metadata = { repository.artwork(it) },
        inspect = { url, logo, title -> inspect(url, logo, title) },
        read = { preferences.getString(it, null) },
        write = { key, value -> preferences.edit().putString(key, value).apply() })
    override suspend fun resolve(item: MediaItem): CanonicalArtwork = withContext(Dispatchers.IO) {
        LoadTiming.measure(LoadTiming.Stage.ARTWORK_SELECT) {
            CanonicalArtworkCatalog.entries[CanonicalArtworkIdentity.key(item)] ?: resolver.resolve(item)
        }
    }
    override suspend fun resolve(item: MediaItem, portrait: Boolean): CanonicalArtwork = withContext(Dispatchers.IO) {
        if (portrait) resolve(item) else CanonicalArtworkCatalog.landscapes[CanonicalArtworkIdentity.key(item)] ?: resolver.resolve(item, false)
    }
    override suspend fun refine(item: MediaItem, current: CanonicalArtwork, portrait: Boolean): CanonicalArtwork = withContext(Dispatchers.IO) {
        resolver.refine(item, current, portrait)
    }
    override suspend fun logo(item: MediaItem): String? = withContext(Dispatchers.IO) {
        val key = CanonicalArtworkIdentity.key(item)
        val saved = preferences.getString("logo:$key", null)
        saved ?: resolver.logo(item)?.also { preferences.edit().putString("logo:$key", it).apply() }
    }

    private suspend fun inspect(url: String, logo: Boolean, title: String): ArtworkInspection? = inspections.withPermit {
        val result = loader().execute(ImageRequest.Builder(context).data(url).size(960, 1440)
            .precision(Precision.INEXACT).scale(Scale.FIT).allowHardware(false).crossfade(false).build()) as? SuccessResult ?: return@withPermit null
        val bitmap = withContext(Dispatchers.Default) { result.drawable.toBitmap() }
        if (bitmap.width <= 0 || bitmap.height <= 0) return@withPermit null
        val mask = if (logo) withContext(Dispatchers.Default) { logoOcrMask(bitmap) } else null
        val reading = try { ArtworkTextRecognizer.read(context, if (logo) "logo-contrast-v2:$url" else url,
            (mask ?: bitmap).asImageBitmap()) } finally { mask?.recycle() }
        if (!reading.complete) return@withPermit null
        val transparent = if (logo && bitmap.hasAlpha()) withContext(Dispatchers.Default) {
            var empty = 0
            for (y in 0 until 20) for (x in 0 until 20) {
                if ((bitmap.getPixel(x * (bitmap.width - 1) / 19, y * (bitmap.height - 1) / 19) ushr 24) < 32) empty++
            }
            empty >= 40
        } else false
        ArtworkInspection(bitmap.width.toFloat() / bitmap.height,
            clean = CanonicalArtworkPolicy.clean(reading.text, reading.complete),
            genericLogo = transparent && bitmap.width >= 200 && bitmap.height >= 40 &&
                CanonicalArtworkPolicy.genericLogo(reading.text, title, reading.complete, transparent))
    }
}
