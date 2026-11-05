package com.panoli707.aetherio.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Optional display fields must never turn an otherwise valid stream into an unsupported one. */
internal object SourceMetadata {
    fun text(value: Any?, max: Int = 256): String? = (value as? String)?.filterNot {
        it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()
    }?.trim()?.take(max)?.takeIf { it.isNotEmpty() }

    fun safeLogo(value: Any?): String? = text(value, 8192)?.let {
        runCatching { SourceProtocol.httpsUrl(it).toString() }.getOrNull()
    }

    private fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() && it >= 0 }

    private fun list(value: Any?): List<String> = when (value) {
        is JSONArray -> (0 until minOf(value.length(), 40)).mapNotNull { text(value.opt(it), 80) }
        is String -> value.split(',', ';', '|').take(40).mapNotNull { text(it, 80) }
        else -> emptyList()
    }

    private val languageNames = listOf(
        Triple("Latino", "ES", "latino|latam|spa-la|es-419|es-mx|🇲🇽"),
        Triple("Castellano", "ES", "castellano|spa(?!-la)|es-es|🇪🇸"),
        Triple("Inglés", "EN", "eng(?:lish)?|en|🇬🇧|🇺🇸"),
        Triple("Francés", "FR", "fre|fra|french|fr|🇫🇷"),
        Triple("Alemán", "DE", "ger|deu|german|de|🇩🇪"),
        Triple("Italiano", "IT", "ita|italian|it|🇮🇹"),
        Triple("Portugués", "PT", "por(?:-br)?|portuguese|pt(?:-br|-pt)?|🇧🇷|🇵🇹"),
        Triple("Japonés", "JA", "jpn|japanese|ja|🇯🇵"),
        Triple("Ruso", "RU", "rus|russian|ru|🇷🇺"),
        Triple("Árabe", "AR", "ara|arabic|ar|🇸🇦"),
        Triple("Hindi", "HI", "hin|hindi|hi|🇮🇳"),
        Triple("Coreano", "KO", "kor|korean|ko|🇰🇷"),
        Triple("Chino", "ZH", "chi|zho|chinese|zh|🇨🇳"),
        Triple("Español", "ES", "es|español|spanish")
    )
    private val languagePatterns = languageNames.map { (_, _, pattern) ->
        Regex("(?<![\\p{L}\\d])(?:$pattern)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)
    }

    private fun language(value: String): String {
        val index = languagePatterns.indexOfFirst { it.matches(value.trim()) }
        return if (index >= 0) languageNames[index].first else value
    }

    fun languageBadge(value: String): String? {
        val index = languageNames.indexOfFirst { it.first == value }
        return if (index >= 0) languageNames[index].second else null
    }

    fun parse(stream: JSONObject, hints: JSONObject, addon: AddonConfig): SourceDisplay {
        val rawText = listOf("name", "title", "description").mapNotNull { stream.opt(it) as? String }
            .plus(listOfNotNull(text(hints.opt("filename")))).joinToString("\n").take(5000)
        fun value(vararg keys: String): Any? = keys.firstNotNullOfOrNull { key ->
            stream.opt(key)?.takeUnless { it == JSONObject.NULL } ?: hints.opt(key)?.takeUnless { it == JSONObject.NULL }
        }
        val explicitLanguages = list(value("languages", "language")).map(::language)
        val languageText = rawText.lineSequence().filterNot {
            Regex("(?i)^\\s*(?:sub(?:s|titles|títulos)?|subtitulado)\\s*:").containsMatchIn(it)
        }.joinToString("\n")
        val languages = (if (explicitLanguages.isNotEmpty()) explicitLanguages else languagePatterns.mapIndexedNotNull { i, regex ->
            languageNames[i].first.takeIf { regex.containsMatchIn(languageText) }
        }).distinct().take(20)
        val bytes = number(value("size", "videoSize"))?.takeIf { it > 0 && it < 1e16 }
        val size = bytes?.let {
            val unit = if (it >= 1024 * 1024 * 1024) "GB" else "MB"
            val divisor = if (unit == "GB") 1024.0 * 1024 * 1024 else 1024.0 * 1024
            String.format(Locale.ROOT, "%.2f %s", it / divisor, unit)
        } ?: Regex("(?i)\\b\\d+(?:[.,]\\d+)?\\s*(?:[KMGT]i?B)\\b").find(rawText)?.value
        val seeders = number(value("seeders", "seeds"))?.takeIf { it <= Int.MAX_VALUE }?.toInt()
            ?: Regex("(?:👤|👥)\\s*(\\d{1,9})|(?i)\\b(\\d{1,9})\\s*(?:seeders|seeds)\\b")
                .find(rawText)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.toIntOrNull()
        val duration = number(value("duration"))?.takeIf { it in 60.0..86400.0 }?.let { (it / 60).toInt() }
            ?: Regex("(?i)(?:duración|duration)\\s*:\\s*(\\d{1,4})\\s*min").find(rawText)?.groupValues?.get(1)?.toIntOrNull()
        val indexer = text(value("indexer"), 80)
            ?: Regex("(?:🌐|⚙️?)\\s*([^\n|]+)").find(rawText)?.groupValues?.get(1)?.let { text(it, 80) }
        val technical = stream.optJSONObject("technicalMetadata") ?: hints.optJSONObject("technicalMetadata") ?: JSONObject()
        val techText = listOf("quality", "resolution", "resolutionHeight", "height", "videoCodec", "video_codec", "codec",
            "audioCodec", "audio_codec", "audioProfile", "dynamicRange", "bitDepth", "audioChannels")
            .mapNotNull { key -> text(value(key)) ?: text(technical.opt(key)) ?: (value(key) as? Number)?.toString()
                ?: (technical.opt(key) as? Number)?.toString() }.joinToString(" ") + " " + rawText
        val resolution = Regex("(?<!\\d)(2160|1440|1080|720|480|360)(?:p|px)?(?!\\d)", RegexOption.IGNORE_CASE)
            .find(techText)?.groupValues?.get(1)?.toIntOrNull()
            ?: 2160.takeIf { Regex("\\b(?:4k|uhd)\\b", RegexOption.IGNORE_CASE).containsMatchIn(techText) }
        val badges = buildList {
            when {
                resolution == 2160 -> add("4K")
                resolution == 1440 -> add("1440p")
                resolution == 1080 -> add("FHD")
                resolution == 720 -> add("HD")
                resolution == 480 -> add("SD")
                resolution == 360 -> add("360p")
            }
            listOf("HEVC" to "hevc|[hx][ ._-]?265", "AVC" to "avc|[hx][ ._-]?264", "AV1" to "av1",
                "AAC" to "aac", "FLAC" to "flac", "OPUS" to "opus", "DTS" to "dts", "DD+" to "e-?ac-?3|ddp",
                "Atmos" to "atmos", "HDR10+" to "hdr10\\+", "HDR10" to "hdr10(?!\\+)", "HDR" to "hdr(?!10)",
                "Dolby Vision" to "dolby[ ._-]?vision|dovi", "10-bit" to "10[ ._-]?bit").forEach { (label, pattern) ->
                if (Regex("(?i)(?<![a-z0-9])(?:$pattern)(?![a-z0-9])").containsMatchIn(techText)) add(label)
            }
            Regex("\\b[257]\\.1\\b").find(techText)?.value?.let(::add)
        }.distinct()
        // Hidden aggregators encode their real provider in Source/name; don't create a filter per quality/server.
        val payloadProvider = if (addon.payloadAttribution) text(stream.opt("sourceName"), 80)
            ?: text(hints.opt("providerName"), 80)
            ?: Regex("(?i)\\bSource\\s*:\\s*([\\p{L}\\d][\\p{L}\\d ._-]{0,79})")
                .find(rawText)?.groupValues?.get(1)?.trim()?.substringBefore(" - ")
            else null
        val provider = if (ProviderHub.isHub(addon) || addon.payloadAttribution) payloadProvider ?: text(hints.opt("providerId"), 80)
            ?: (stream.opt("name") as? String)?.lineSequence()?.firstOrNull()?.let { text(it, 120) }?.substringBefore(" - ")?.trim()
                ?.replace(Regex("(?i)\\s+(?:2160|1080|720|480)p.*$"), "")
            else null
        val filename = (text(hints.opt("filename"), 512) ?: text(stream.opt("filename"), 512))?.substringBefore("|^|")
        val title = filename ?: (stream.opt("title") as? String)?.lineSequence()?.mapNotNull { text(it, 512) }
            ?.firstOrNull { line ->
                !Regex("(?i)^(?:sub(?:s|títulos)?|idiomas|languages|duración|duration|tamaño|size)\\s*:").containsMatchIn(line) &&
                    !Regex("(?:💾|👤|👥)|(?i)\\b\\d+(?:[.,]\\d+)?\\s*[KMGT]i?B\\b").containsMatchIn(line)
            }
        return SourceDisplay(provider?.let { "${addon.id}:$it" }, provider, addon.logoUrl,
            title, size, indexer, duration, seeders, languages, badges, resolution)
    }

    /** Deduplicate real playback targets, without conflating distinct headers or torrent file selections. */
    fun merge(existing: List<StreamSource>, incoming: List<StreamSource>): List<StreamSource> {
        val result = linkedMapOf<Pair<String, Map<String, String>>, StreamSource>()
        for (source in existing + incoming) {
            val hash = source.infoHash ?: if (source.isTorrent) runCatching {
                com.panoli707.aetherio.nativeapp.player.torrent.TorrentRequestParser.magnetHash(source.url)
            }.getOrNull() else null
            val target = if (hash != null) "torrent:$hash:${source.fileIdx ?: -1}" else source.url
            val key = target to source.headers
            val old = result[key]
            if (old == null || (old.addonId == ProviderHub.config.id && source.addonId != old.addonId)) result[key] = source
        }
        return result.values.toList()
    }
}
