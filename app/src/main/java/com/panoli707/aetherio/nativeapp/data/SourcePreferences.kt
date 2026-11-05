package com.panoli707.aetherio.nativeapp.data

import java.security.MessageDigest
import java.util.Locale

enum class SourceLanguage(val label: String) {
    ANY("Sin preferencia"), SPANISH("Español (todos)"), CASTILIAN("Castellano"), LATIN("Latino"),
    ENGLISH("Inglés"), JAPANESE("Japonés"), FRENCH("Francés"), GERMAN("Alemán"),
    ITALIAN("Italiano"), PORTUGUESE("Portugués"), KOREAN("Coreano"), CHINESE("Chino"),
    RUSSIAN("Ruso"), ARABIC("Árabe"), HINDI("Hindi")
}

enum class SourceQuality(val label: String, val height: Int?) {
    ANY("Sin preferencia", null), UHD("4K · 2160p", 2160), QHD("1440p", 1440),
    FHD("1080p", 1080), HD("720p", 720), SD("480p", 480), LOW("360p", 360)
}

data class SourcePreferences(
    val language: SourceLanguage = SourceLanguage.ANY,
    val quality: SourceQuality = SourceQuality.ANY,
    val favoritesFirst: Boolean = true,
    val favoriteSources: Set<String> = emptySet(),
    val favoriteProviders: Set<String> = emptySet()
) {
    val active: Boolean get() = language != SourceLanguage.ANY || quality != SourceQuality.ANY ||
        (favoritesFirst && (favoriteSources.isNotEmpty() || favoriteProviders.isNotEmpty()))
}

enum class SeederLevel(val description: String) {
    UNKNOWN("Seeders sin reportar"), LOW("Sin seeders reportados"), MEDIUM("Pocos seeders"), HIGH("Muchos seeders");

    companion object {
        // Same thresholds as PC: Episodie/index.tsx and Player/SourcePanel.tsx.
        fun of(count: Int?): SeederLevel = when {
            count == null || count < 0 -> UNKNOWN
            count == 0 -> LOW
            count < 10 -> MEDIUM
            else -> HIGH
        }
    }
}

object SourcePreferenceRules {
    private fun digest(value: String): String {
        val digits = "0123456789abcdef"
        return buildString(64) {
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).forEach {
                val byte = it.toInt() and 255
                append(digits[byte ushr 4]); append(digits[byte and 15])
            }
        }
    }

    // Store only fingerprints, never a URL, token, request header or magnet.
    fun providerKey(source: StreamSource): String = digest("provider:v1:${source.providerKey}")

    fun sourceKey(source: StreamSource): String {
        val hash = source.infoHash ?: if (source.isTorrent) runCatching {
            com.panoli707.aetherio.nativeapp.player.torrent.TorrentRequestParser.magnetHash(source.url)
        }.getOrNull() else null
        val target = if (hash != null) "torrent:${hash.lowercase(Locale.ROOT)}:${source.fileIdx ?: -1}"
            else "http:${source.url}:${source.format}:" + source.headers.toSortedMap().entries.joinToString("\n") { "${it.key}:${it.value}" }
        return digest("source:v1:${providerKey(source)}:$target")
    }

    fun favorite(source: StreamSource, preferences: SourcePreferences): Boolean =
        (preferences.favoriteSources.isNotEmpty() && sourceKey(source) in preferences.favoriteSources) ||
            (preferences.favoriteProviders.isNotEmpty() && providerKey(source) in preferences.favoriteProviders)

    fun toggleSource(source: StreamSource, preferences: SourcePreferences): SourcePreferences {
        val key = sourceKey(source)
        return preferences.copy(favoriteSources = if (key in preferences.favoriteSources)
            preferences.favoriteSources - key else preferences.favoriteSources + key)
    }

    fun toggleProvider(source: StreamSource, preferences: SourcePreferences): SourcePreferences {
        val key = providerKey(source)
        return preferences.copy(favoriteProviders = if (key in preferences.favoriteProviders)
            preferences.favoriteProviders - key else preferences.favoriteProviders + key)
    }

    fun quality(source: StreamSource): Int? = source.display.resolutionHeight ?: when {
        "4K" in source.display.badges -> 2160
        "FHD" in source.display.badges -> 1080
        "HD" in source.display.badges -> 720
        "SD" in source.display.badges -> 480
        else -> null
    }

    private fun languageMatches(source: StreamSource, preferred: SourceLanguage): Boolean {
        if (preferred == SourceLanguage.ANY) return true
        // Subtitles alone must not be mistaken for a reported audio language.
        return source.display.languages.any { value ->
            val language = value.lowercase(Locale.ROOT).trim()
            when (preferred) {
                SourceLanguage.SPANISH -> language in setOf("es", "spa", "español", "spanish", "castellano", "latino") || language.startsWith("es-")
                SourceLanguage.CASTILIAN -> language in setOf("castellano", "es-es")
                SourceLanguage.LATIN -> language in setOf("latino", "latam", "es-419", "es-mx", "spa-la")
                else -> language == preferred.label.lowercase(Locale.ROOT)
            }
        }
    }

    fun order(sources: List<StreamSource>, preferences: SourcePreferences): List<StreamSource> {
        if (!preferences.active) return sources
        // Precompute once per result, keep ties in their original order, and never filter/mutate playback data.
        data class Ranked(val source: StreamSource, val index: Int, val favorite: Int, val language: Int, val quality: Int)
        return sources.mapIndexed { index, source -> Ranked(source, index,
            if (preferences.favoritesFirst && favorite(source, preferences)) 0 else 1,
            if (languageMatches(source, preferences.language)) 0 else 1,
            if (preferences.quality.height == null || quality(source) == preferences.quality.height) 0 else 1)
        }.sortedWith(compareBy<Ranked> { it.favorite }.thenBy { it.language }.thenBy { it.quality }.thenBy { it.index })
            .map { it.source }
    }
}
