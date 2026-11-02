package com.panoli707.aetherio.nativeapp.data

import com.panoli707.aetherio.nativeapp.player.PlaybackHeaders
import com.panoli707.aetherio.nativeapp.player.MediaUrlPolicy
import com.panoli707.aetherio.nativeapp.player.VideoFormat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/** Pure protocol and plaintext codec. Persistence encryption belongs to AddonStore. */
internal object SourceProtocol {
    const val MANIFEST_LIMIT = 512 * 1024
    const val STREAM_LIMIT = 2 * 1024 * 1024
    const val STORE_LIMIT = 512 * 1024
    private const val URL_LIMIT = 8192
    private const val LIST_LIMIT = 200
    private const val CONFIG_LIMIT = 30
    private val imdb = Regex("tt[0-9]{5,12}")
    private val imdbPrefix = Regex("tt[0-9]{0,12}")
    private val encodedControl = Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE)

    private fun invalid(): Nothing = throw IllegalArgumentException("Datos de fuentes no validos")

    // Never retain parser/URI exceptions: they can contain credentials or private query values.
    private inline fun <T> checked(block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        invalid()
    }

    fun httpsUrl(raw: String): HttpUrl = checked {
        if (raw.length > URL_LIMIT || raw.any(Char::isISOControl) || encodedControl.containsMatchIn(raw)) invalid()
        val uri = URI(raw.trim())
        if (!uri.isAbsolute || !uri.scheme.equals("https", true) || uri.rawAuthority.isNullOrEmpty() ||
            uri.rawUserInfo != null || uri.rawFragment != null) invalid()
        val url = uri.toASCIIString().toHttpUrlOrNull() ?: invalid()
        if (!url.isHttps || url.host.isEmpty() || url.port !in 1..65535 || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.toString().length > URL_LIMIT) invalid()
        url
    }

    fun manifestUrl(raw: String): HttpUrl = checked {
        httpsUrl(raw).also { if (it.pathSegments.lastOrNull() != "manifest.json") invalid() }
    }

    private fun hash(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            if (parts.size > 1) digest.update("${bytes.size}:".toByteArray(Charsets.US_ASCII))
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun addonId(url: HttpUrl): String = hash(url.toString())

    private fun root(raw: String, limit: Int): JSONObject {
        if (raw.length > limit || raw.toByteArray(Charsets.UTF_8).size > limit) invalid()
        // Bound nesting and every collection before org.json allocates its object tree.
        val separators = IntArray(32)
        var depth = 0
        var quoted = false
        var escaped = false
        raw.forEach { char ->
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '\'', ';' -> invalid()
                '{', '[' -> {
                    if (depth == separators.size) invalid()
                    separators[depth++] = 0
                }
                '}', ']' -> { if (depth == 0) invalid(); depth-- }
                ',' -> if (depth > 0 && ++separators[depth - 1] >= LIST_LIMIT) invalid()
            }
        }
        if (quoted || depth != 0) invalid()
        val parser = JSONTokener(raw)
        val value = parser.nextValue()
        if (value !is JSONObject || parser.nextClean() != '\u0000') invalid()
        return value
    }

    private fun array(parent: JSONObject, key: String, required: Boolean = false): JSONArray {
        if (!parent.has(key) && !required) return JSONArray()
        val value = parent.opt(key) as? JSONArray ?: invalid()
        if (value.length() > LIST_LIMIT) invalid()
        return value
    }

    private fun strings(values: JSONArray, max: Int): List<String> = (0 until values.length()).map {
        val value = values.opt(it) as? String ?: invalid()
        if (value.isEmpty() || value.length > max || value.any(Char::isISOControl)) invalid()
        value
    }

    private fun text(value: Any?, max: Int, required: Boolean = false): String {
        if (value == null || value == JSONObject.NULL) {
            if (required) invalid()
            return ""
        }
        val raw = value as? String ?: invalid()
        val cleaned = raw.filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }
            .trim().take(max)
        if (required && cleaned.isEmpty()) invalid()
        return cleaned
    }

    private fun types(values: JSONArray): Set<MediaType> = strings(values, 40).mapNotNull {
        when (it) {
            "movie" -> MediaType.MOVIE
            "series" -> MediaType.SERIES
            else -> null
        }
    }.toSet()

    // Empty means unrestricted; null means no IMDb identifier is supported.
    private fun prefixes(values: JSONArray): List<String>? {
        val all = strings(values, 120)
        if (all.isEmpty()) return emptyList()
        return all.filter { imdbPrefix.matches(it) }.distinct().takeIf { it.isNotEmpty() }
    }

    private fun intersect(a: List<String>, b: List<String>): List<String>? {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        return a.flatMap { left -> b.mapNotNull { right ->
            when {
                left.startsWith(right) -> left
                right.startsWith(left) -> right
                else -> null
            }
        } }.distinct().let { all ->
            all.filter { value -> all.none { it != value && value.startsWith(it) } }
        }.also { if (it.size > LIST_LIMIT) invalid() }.takeIf { it.isNotEmpty() }
    }

    private fun union(a: List<String>, b: List<String>): List<String> {
        if (a.isEmpty() || b.isEmpty()) return emptyList()
        val all = (a + b).distinct()
        return all.filter { value -> all.none { it != value && value.startsWith(it) } }
            .also { if (it.size > LIST_LIMIT) invalid() }
    }

    fun manifest(raw: String, url: HttpUrl): AddonConfig = checked {
        val canonical = manifestUrl(url.toString())
        val json = root(raw, MANIFEST_LIMIT)
        val manifestId = text(json.opt("id"), 256, required = true)
        if ((json.opt("id") as String).length > 256) invalid()
        val name = text(json.opt("name"), 80, required = true)
        val declaredTypes = types(array(json, "types", required = true))
        val declaredPrefixes = prefixes(array(json, "idPrefixes")) ?: invalid()
        val available = mutableMapOf<MediaType, List<String>>()
        val resources = array(json, "resources", required = true)
        for (index in 0 until resources.length()) {
            val resource = resources.opt(index)
            val resourceTypes: Set<MediaType>
            val resourcePrefixes: List<String>
            when (resource) {
                is String -> {
                    if (resource != "stream") continue
                    resourceTypes = declaredTypes
                    resourcePrefixes = declaredPrefixes
                }
                is JSONObject -> {
                    val resourceName = resource.opt("name") as? String ?: invalid()
                    if (resourceName != "stream") continue
                    resourceTypes = if (resource.has("types")) types(array(resource, "types")).intersect(declaredTypes)
                        else declaredTypes
                    val ownPrefixes = if (resource.has("idPrefixes")) prefixes(array(resource, "idPrefixes"))
                        else declaredPrefixes
                    resourcePrefixes = intersect(declaredPrefixes, ownPrefixes ?: continue) ?: continue
                }
                else -> invalid()
            }
            resourceTypes.forEach { type ->
                available[type] = available[type]?.let { union(it, resourcePrefixes) } ?: resourcePrefixes
            }
        }
        if (available.isEmpty()) invalid()
        // The public config is a cross-product of types and prefixes. Never widen resource restrictions.
        var common = available.values.first()
        available.values.drop(1).forEach { common = intersect(common, it) ?: invalid() }
        AddonConfig(addonId(canonical), manifestId, name, canonical.toString(), available.keys.toSet(), common)
    }

    fun endpoint(config: AddonConfig, query: SourceQuery): HttpUrl? = checked {
        if (query.item.type !in config.types) return@checked null
        val id = query.item.imdbId?.takeIf { imdb.matches(it) }
            ?: query.item.id.takeIf { query.item.source == CatalogSource.CINEMETA && imdb.matches(it) }
            ?: return@checked null
        if (config.idPrefixes.isNotEmpty() && config.idPrefixes.none { id.startsWith(it) }) return@checked null
        val addon = validated(config)
        val type: String
        val videoId: String
        when (query.item.type) {
            MediaType.MOVIE -> { type = "movie"; videoId = id }
            MediaType.SERIES -> {
                val season = query.season ?: return@checked null
                val episode = query.episode ?: return@checked null
                if (season < 0 || episode <= 0) return@checked null
                type = "series"
                videoId = "$id:$season:$episode"
            }
        }
        val base = manifestUrl(addon.manifestUrl)
        base.newBuilder().removePathSegment(base.pathSize - 1)
            .addPathSegment("stream").addPathSegment(type).addPathSegment("$videoId.json").build()
            .also { if (it.toString().length > URL_LIMIT) invalid() }
    }

    private fun optionalObject(parent: JSONObject, key: String): JSONObject? {
        if (!parent.has(key) || parent.isNull(key)) return null
        return parent.opt(key) as? JSONObject ?: invalid()
    }

    private fun playbackHeaders(stream: JSONObject, hints: JSONObject): Map<String, String> {
        val merged = linkedMapOf<String, String>()
        for (parent in listOf(stream, hints)) {
            val proxy = optionalObject(parent, "proxyHeaders")
            val flatProxy = JSONObject()
            proxy?.keys()?.forEach { key ->
                if (key !in listOf("request", "response") && proxy.opt(key) is String) flatProxy.put(key, proxy.opt(key))
            }
            for (headers in listOfNotNull(optionalObject(parent, "headers"),
                optionalObject(parent, "requestHeaders"), flatProxy, proxy?.let { optionalObject(it, "request") })) {
                if (headers.length() > 3) invalid()
                val values = linkedMapOf<String, String>()
                headers.keys().forEach { key -> values[key] = headers.opt(key) as? String ?: invalid() }
                PlaybackHeaders.validate(values).forEach { (key, value) ->
                    if (key in merged && merged[key] != value) invalid()
                    merged[key] = value
                }
            }
        }
        for ((field, header) in listOf("referrer" to "Referer", "userAgent" to "User-Agent")) {
            if (header !in merged && hints.has(field) && !hints.isNull(field)) {
                val value = hints.opt(field) as? String ?: invalid()
                if (value.isNotEmpty()) merged.putAll(PlaybackHeaders.validate(mapOf(header to value)))
            }
        }
        return PlaybackHeaders.validate(merged)
    }

    private fun requiresDrm(parent: JSONObject): Boolean = listOf(
        "drm", "drmInfo", "drmScheme", "drmLicense", "drmLicenseUrl", "licenseUrl", "license",
        "licenseKey", "licenseType", "keySystem", "widevine", "playready", "fairplay", "clearKey"
    ).any { parent.has(it) && !parent.isNull(it) && parent.opt(it) != false }

    private fun format(stream: JSONObject, hints: JSONObject, url: HttpUrl): VideoFormat {
        val mime = text(stream.opt("mimeType") ?: hints.opt("mimeType"), 120).lowercase(Locale.ROOT)
        when (mime) {
            "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl" -> return VideoFormat.HLS
            "application/dash+xml" -> return VideoFormat.DASH
        }
        val filename = text(stream.opt("filename") ?: hints.opt("filename"), 512)
        val path = (filename.ifEmpty { url.pathSegments.lastOrNull().orEmpty() }).lowercase(Locale.ROOT)
        return when {
            path.endsWith(".m3u8") -> VideoFormat.HLS
            path.endsWith(".mpd") -> VideoFormat.DASH
            else -> VideoFormat.AUTO
        }
    }

    fun streams(raw: String, config: AddonConfig): SourceResult = checked {
        val addon = validated(config)
        val desktopTransport = DesktopSources.providers.any { it.manifestUrl == addon.manifestUrl }
        val values = array(root(raw, STREAM_LIMIT), "streams")
        val sources = linkedMapOf<String, StreamSource>()
        var unsupported = 0
        for (index in 0 until values.length()) {
            val source = try {
                val stream = values.opt(index) as? JSONObject ?: invalid()
                if (listOf("externalUrl", "ytId", "infoHash").any { stream.has(it) && !stream.isNull(it) }) invalid()
                val hints = optionalObject(stream, "behaviorHints") ?: JSONObject()
                if (requiresDrm(stream) || requiresDrm(hints)) invalid()
                val headers = playbackHeaders(stream, hints)
                val rawUrl = stream.opt("url") as? String ?: invalid()
                val url = if (URI(rawUrl.trim()).scheme.equals("http", true)) {
                    if (encodedControl.containsMatchIn(rawUrl) || URI(rawUrl.trim()).rawFragment != null) invalid()
                    MediaUrlPolicy.validate(rawUrl, desktopTransport).toHttpUrlOrNull() ?: invalid()
                } else httpsUrl(rawUrl)
                val name = text(stream.opt("name"), 120).ifEmpty { addon.name }
                val description = text(stream.opt("description"), 600).ifEmpty { text(stream.opt("title"), 600) }
                val identity = listOf(addon.id, url.toString(), name, description) +
                    headers.toSortedMap().flatMap { (key, value) -> listOf(key, value) }
                StreamSource(hash(*identity.toTypedArray()), addon.id, addon.name,
                    name, description, url.toString(), format(stream, hints, url), headers, desktopTransport)
            } catch (_: Exception) {
                unsupported++
                continue
            }
            sources.putIfAbsent(source.id, source)
        }
        SourceResult(sources.values.toList(), unsupported)
    }

    private fun validated(config: AddonConfig): AddonConfig {
        val url = manifestUrl(config.manifestUrl)
        if (config.id != addonId(url) || config.manifestId != text(config.manifestId, 256, required = true) ||
            config.name != text(config.name, 80, required = true) || config.types.isEmpty() || config.idPrefixes.size > LIST_LIMIT ||
            config.idPrefixes.any { !imdbPrefix.matches(it) }) invalid()
        return config.copy(manifestUrl = url.toString(), idPrefixes = config.idPrefixes.distinct())
    }

    fun encode(configs: List<AddonConfig>): String = checked {
        if (configs.size > CONFIG_LIMIT) invalid()
        val values = JSONArray()
        configs.map(::validated).distinctBy { it.id }.forEach { config ->
            values.put(JSONObject().put("id", config.id).put("manifestId", config.manifestId)
                .put("name", config.name).put("manifestUrl", config.manifestUrl)
                .put("types", JSONArray(config.types.map { it.name }.sorted()))
                .put("idPrefixes", JSONArray(config.idPrefixes)))
        }
        JSONObject().put("version", 1).put("addons", values).toString().also {
            if (it.toByteArray(Charsets.UTF_8).size > STORE_LIMIT) invalid()
        }
    }

    fun decode(raw: String): List<AddonConfig> = checked {
        val json = root(raw, STORE_LIMIT)
        if (json.opt("version") != 1) invalid()
        val values = array(json, "addons", required = true)
        if (values.length() > CONFIG_LIMIT) invalid()
        (0 until values.length()).map { index ->
            val value = values.optJSONObject(index) ?: invalid()
            val storedTypes = strings(array(value, "types", required = true), 16).map {
                when (it) {
                    "MOVIE" -> MediaType.MOVIE
                    "SERIES" -> MediaType.SERIES
                    else -> invalid()
                }
            }.toSet()
            validated(AddonConfig(
                value.opt("id") as? String ?: invalid(),
                value.opt("manifestId") as? String ?: invalid(),
                value.opt("name") as? String ?: invalid(),
                value.opt("manifestUrl") as? String ?: invalid(),
                storedTypes, strings(array(value, "idPrefixes"), 14)
            ))
        }.distinctBy { it.id }
    }
}
