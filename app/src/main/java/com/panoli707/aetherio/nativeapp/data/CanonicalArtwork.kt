package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** A title's visual identity, not the selected episode/season. Versioned separately from user data. */
internal data class CanonicalArtwork(val title: String, val cover: String?, val ratio: Float = 2f / 3f,
    val logo: String? = null) {
    fun item(base: MediaItem) = base.copy(title = title, posterUrl = cover, portraitArtworkUrl = cover,
        backdropUrl = null, logoUrl = logo, posterTitlePresence = ArtworkTitlePresence.ABSENT,
        portraitTitlePresence = ArtworkTitlePresence.ABSENT, logoAlternatives = emptyList(), portraitAlternatives = emptyList())
    fun encode(): String = JSONObject().put("title", title).put("cover", cover).put("ratio", ratio)
        .put("logo", logo).toString()
    companion object {
        fun decode(raw: String?): CanonicalArtwork? = runCatching {
            val json = JSONObject(requireNotNull(raw))
            val title = json.getString("title").takeIf { it.isNotBlank() && it.length <= 500 } ?: return null
            val cover = CatalogJson.image(CatalogJson.text(json, "cover")) ?: return null
            val ratio = json.getDouble("ratio").toFloat().takeIf { it in .35f..3f } ?: return null
            val logo = CatalogJson.text(json, "logo")
            val local = logo.takeIf { Regex("file:///android_asset/anime-logos/(anilist_[0-9]+|dandivine|chojo|dark_machine|firefly_wedding)\\.png").matches(it) }
            CanonicalArtwork(title, cover, ratio, CatalogJson.image(logo) ?: local)
        }.getOrNull()
    }
}

internal object CanonicalArtworkIdentity {
    fun root(item: MediaItem): MediaItem {
        val series = SeriesIdentity.item(item)
        val mapped = if (series.source == CatalogSource.ANILIST) AnimeLogoCatalog.entries
            .firstOrNull { it.animeId == series.id && it.type == series.type } else null
        return (if (mapped != null) series.copy(id = mapped.tmdbId, source = CatalogSource.TMDB) else series)
            .copy(seasonHint = null, animeEntryId = null)
    }
    fun key(item: MediaItem) = root(item).key
}

/** OCR is a guard, never a claim that language-neutral metadata proves an image is textless. */
internal object CanonicalArtworkPolicy {
    fun clean(text: String, complete: Boolean) = complete && text.none { it.isLetterOrDigit() }
    fun genericLogo(text: String, title: String, complete: Boolean, transparent: Boolean): Boolean {
        if (!complete || !transparent || text.none(Char::isLetterOrDigit)) return false
        fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        val value = normalized(text)
        val name = normalized(title)
        if (Regex("\\b(season|temporada|saison|staffel|stagione|cour|part|parte)\\b|第.{0,6}[期季部]").containsMatchIn(value)) return false
        // A digit in the real title (86, 3%, etc.) is legitimate; a season number is not.
        if (Regex("[0-9]+").findAll(value).any { !name.contains(it.value) }) return false
        if (Regex("\\b(ii|iii|iv|vi|vii|viii|ix|x)\\b").findAll(value).any {
                !Regex("\\b${it.value}\\b").containsMatchIn(name) }) return false
        // Reject arc labels, taglines and unrelated words, not just the literal word 'season'.
        val titleWords = Regex("[\\p{L}\\p{N}]+").findAll(name).map { it.value }.toList()
        val words = Regex("[\\p{L}\\p{N}]+").findAll(value).map { it.value }.filter { it.length >= 3 }.toList()
        if (words.isEmpty() && value.trim() != name.trim()) return false
        if (words.any { word -> titleWords.none { it == word || (minOf(it.length, word.length) >= 4 && (it.contains(word) || word.contains(it))) } }) return false
        return true
    }
}

internal data class ArtworkInspection(val ratio: Float, val clean: Boolean = false, val genericLogo: Boolean = false)

/** Shared by Home and detail. Only inspected covers persist; their written-name fallback is stable too. */
internal class CanonicalArtworkResolver(
    private val metadata: suspend (MediaItem) -> MediaItem,
    private val inspect: suspend (String, Boolean, String) -> ArtworkInspection?,
    private val read: (String) -> String? = { null },
    private val write: (String, String) -> Unit = { _, _ -> }
) {
    private val locks = List(32) { Mutex() }
    private val slots = Semaphore(2)
    private val memory = object : LinkedHashMap<String, CanonicalArtwork>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CanonicalArtwork>?) = size > 256
    }
    suspend fun resolve(item: MediaItem): CanonicalArtwork = resolve(item, CanonicalArtworkIdentity.key(item))
    internal suspend fun resolve(item: MediaItem, key: String): CanonicalArtwork = locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
        synchronized(memory) { memory[key] }?.let { return@withLock it }
        CanonicalArtwork.decode(read(key))?.let { synchronized(memory) { memory[key] = it }; return@withLock it }
        slots.withPermit {
            val root = CanonicalArtworkIdentity.root(item)
            val source = try { metadata(root) } catch (e: CancellationException) { throw e }
                catch (_: Exception) { return@withPermit CanonicalArtwork(root.title, null) }
            var transientFailure = false
            suspend fun check(url: String, logo: Boolean): ArtworkInspection? = try {
                inspect(url, logo, source.title).also { if (it == null) transientFailure = true }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { transientFailure = true; null }
            var cover: String? = null
            var ratio = 2f / 3f
            // Ranked metadata candidates are all inspected off-screen. Nothing rejected is painted.
            withTimeoutOrNull(12_000) {
                for (url in (listOfNotNull(source.portraitArtworkUrl) + source.portraitAlternatives +
                        listOfNotNull(source.posterUrl, source.backdropUrl)).map(ArtworkImages::canonicalResolution).distinct().take(10)) {
                    val result = check(url, false) ?: continue
                    if (result.clean) { cover = url; ratio = result.ratio; break }
                }
            } ?: run { transientFailure = true }
            var logo: String? = null
            val logos = (listOfNotNull(source.logoUrl) + source.logoAlternatives + ArtworkImages.logos(source))
                .mapNotNull(ArtworkImages::highResolution).distinct().take(8)
            withTimeoutOrNull(6_000) {
                for (url in logos) {
                    if (check(url, true)?.genericLogo == true) { logo = url; break }
                }
            } ?: run { transientFailure = true }
            val choice = CanonicalArtwork(source.title, cover, ratio, logo)
            currentCoroutineContext().ensureActive()
            if (cover != null || !transientFailure) {
                if (cover != null) write(key, choice.encode())
                synchronized(memory) { memory[key] = choice }
            }
            choice
        }
    }
}
