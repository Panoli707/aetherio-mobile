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
    val revision: Int get() = 0
    suspend fun resolve(item: MediaItem): CanonicalArtwork
    suspend fun resolve(item: MediaItem, portrait: Boolean): CanonicalArtwork = resolve(item)
    suspend fun refine(item: MediaItem, current: CanonicalArtwork, portrait: Boolean): CanonicalArtwork = current
    suspend fun logo(item: MediaItem): String? = null
    suspend fun options(item: MediaItem, kind: ArtworkSelectionKind): List<ArtworkOption> = emptyList()
    suspend fun selection(item: MediaItem, kind: ArtworkSelectionKind): ArtworkSelection? = null
    suspend fun select(item: MediaItem, kind: ArtworkSelectionKind, url: String?) {
        error("La selección manual no está disponible")
    }
}
internal val LocalArtworkProvider = staticCompositionLocalOf<ArtworkProvider?> { null }

@androidx.compose.runtime.Composable
internal fun canonicalArtwork(item: MediaItem, portrait: Boolean = true): CanonicalArtwork? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val provider = LocalArtworkProvider.current ?:
        (context.applicationContext as com.panoli707.aetherio.nativeapp.AetherioApplication).artworkProvider
    val key = CanonicalArtworkIdentity.key(item)
    val revision = provider.revision
    var choice by androidx.compose.runtime.remember(key, provider, portrait, revision) { androidx.compose.runtime.mutableStateOf<CanonicalArtwork?>(null) }
    androidx.compose.runtime.LaunchedEffect(key, provider, portrait, revision) {
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
    private val selections = context.getSharedPreferences("artwork_selections_v1", Context.MODE_PRIVATE)
    override var revision by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    private val repository by lazy { AetherioRepository() }
    private val inspections = Semaphore(2)
    private val resolver = CanonicalArtworkResolver(
        metadata = { repository.artwork(it) },
        inspect = { url, logo, title -> inspect(url, logo, title) },
        read = { preferences.getString(it, null) },
        write = { key, value -> preferences.edit().putString(key, value).apply() })
    override suspend fun resolve(item: MediaItem): CanonicalArtwork = withContext(Dispatchers.IO) {
        LoadTiming.measure(LoadTiming.Stage.ARTWORK_SELECT) {
            resolve(item, true)
        }
    }
    override suspend fun resolve(item: MediaItem, portrait: Boolean): CanonicalArtwork = withContext(Dispatchers.IO) {
        val kind = if (portrait) ArtworkSelectionKind.PORTRAIT else ArtworkSelectionKind.BACKDROP
        val manual = selection(item, kind)
        val base = if (manual != null) CanonicalArtwork(item.title, manual.url, manual.ratio,
            titlePresence = ArtworkTitlePresence.ABSENT) else resolver.resolve(item, portrait)
        val logo = selection(item, ArtworkSelectionKind.LOGO)
        if (logo != null) base.copy(logo = logo.url.takeIf(String::isNotEmpty)) else base
    }
    override suspend fun refine(item: MediaItem, current: CanonicalArtwork, portrait: Boolean): CanonicalArtwork = withContext(Dispatchers.IO) {
        val kind = if (portrait) ArtworkSelectionKind.PORTRAIT else ArtworkSelectionKind.BACKDROP
        if (selection(item, kind) != null) current else resolver.refine(item, current, portrait)
    }
    override suspend fun logo(item: MediaItem): String? = withContext(Dispatchers.IO) {
        selection(item, ArtworkSelectionKind.LOGO)?.let { return@withContext it.url.takeIf(String::isNotEmpty) }
        val key = CanonicalArtworkIdentity.key(item)
        val saved = preferences.getString("provider-logo:$key", null)
        saved ?: resolver.logo(item)?.also { preferences.edit().putString("provider-logo:$key", it).apply() }
    }

    override suspend fun selection(item: MediaItem, kind: ArtworkSelectionKind): ArtworkSelection? =
        ArtworkSelection.decode(selections.getString(ArtworkSelectionIdentity.key(item, kind), null))

    override suspend fun options(item: MediaItem, kind: ArtworkSelectionKind): List<ArtworkOption> = withContext(Dispatchers.IO) {
        val source = repository.artwork(CanonicalArtworkIdentity.root(item))
        val urls = when (kind) {
            ArtworkSelectionKind.PORTRAIT -> listOfNotNull(source.portraitArtworkUrl, source.posterUrl) + source.portraitAlternatives
            ArtworkSelectionKind.BACKDROP -> listOfNotNull(source.backdropUrl) + source.backdropAlternatives
            ArtworkSelectionKind.LOGO -> listOfNotNull(source.logoUrl) + source.logoAlternatives + ArtworkImages.logos(source)
        }
        urls.filter(String::isNotBlank).map { if (kind == ArtworkSelectionKind.LOGO) ArtworkImages.highResolution(it) ?: it
            else ArtworkImages.canonicalResolution(it) }.distinct().take(36).mapIndexed { index, url ->
            ArtworkOption(url, "${source.source.name} ${index + 1}")
        }
    }

    override suspend fun select(item: MediaItem, kind: ArtworkSelectionKind, url: String?) {
        val preferenceKey = ArtworkSelectionIdentity.key(item, kind)
        val choice = if (url == null) null else if (kind == ArtworkSelectionKind.LOGO && url.isEmpty()) ArtworkSelection("") else {
            // Keep selections within the provider's root-level alternatives, but let users choose any artwork.
            val candidates = options(item, kind)
            require(candidates.any { it.url == url }) { "Esta imagen ya no está disponible para el título" }
            ArtworkSelection(url, when (kind) {
                ArtworkSelectionKind.PORTRAIT -> 2f / 3f
                ArtworkSelectionKind.BACKDROP -> 16f / 9f
                ArtworkSelectionKind.LOGO -> 3f
            })
        }
        withContext(Dispatchers.IO) {
            val editor = selections.edit()
            if (choice == null) editor.remove(preferenceKey) else editor.putString(preferenceKey, choice.encode())
            check(editor.commit()) { "No se pudo guardar la selección" }
        }
        withContext(Dispatchers.Main) { revision++ }
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
