package com.panoli707.aetherio.nativeapp.data

import com.panoli707.aetherio.nativeapp.player.VideoFormat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

internal object AnimeAv1Protocol {
    const val BODY_LIMIT = 2 * 1024 * 1024
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val NODE_LIMIT = 32768
    private val slugPattern = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private val safeId = Regex("[A-Za-z0-9_-]{1,160}")
    private val encodedControl = Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE)
    private val seasonMarker = Regex("\\b(?:season\\s*([1-9][0-9]*)|temporada\\s*([1-9][0-9]*)|([1-9][0-9]*)(?:st|nd|rd|th)\\s*season)\\b|\\b(ii|iii|iv|v)\\b")

    data class Title(val title: String, val slug: String)
    data class Embed(val server: String, val variant: String, val url: String)
    data class Media(val url: HttpUrl, val format: VideoFormat, val headers: Map<String, String>)

    private fun invalid(): Nothing = throw IllegalArgumentException("Datos de AnimeAV1 no validos")

    private inline fun <T> checked(block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        invalid()
    }

    private fun text(value: Any?, limit: Int): String {
        val text = value as? String ?: invalid()
        if (text.isBlank() || text.length > limit || text.any(Char::isISOControl)) invalid()
        return text.trim()
    }

    private fun slug(value: String): String = value.also {
        if (it.length > 240 || !slugPattern.matches(it)) invalid()
    }

    // References are deliberately shallow and local. Shared references are legal; cycles are never expanded.
    private class Pool(val values: JSONArray) {
        val root = values.optJSONObject(0) ?: invalid()
        fun ref(value: Any?): Any {
            val index = value as? Int ?: invalid()
            if (index !in 0 until values.length()) invalid()
            return values.get(index)
        }
        fun field(parent: JSONObject, key: String): Any = ref(parent.opt(key))
        fun obj(value: Any?): JSONObject = ref(value) as? JSONObject ?: invalid()
        fun array(value: Any?): JSONArray = ref(value) as? JSONArray ?: invalid()
        fun string(parent: JSONObject, key: String, limit: Int): String = text(field(parent, key), limit)
    }

    private fun pools(raw: String): List<Pool> {
        if (raw.length > BODY_LIMIT || raw.toByteArray(Charsets.UTF_8).size > BODY_LIMIT) invalid()
        // Bound allocations before org.json parses, including irrelevant document fields.
        val stack = CharArray(32)
        var depth = 0
        var nodes = 0
        var quoted = false
        var escaped = false
        raw.forEach { char ->
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> { quoted = true; nodes++ }
                '{', '[' -> {
                    if (depth == stack.size) invalid()
                    stack[depth++] = char
                    nodes++
                }
                '}', ']' -> {
                    if (depth == 0 || stack[--depth] != if (char == '}') '{' else '[') invalid()
                }
                ',', ':' -> nodes++
                '\'', ';' -> invalid()
            }
            if (nodes > NODE_LIMIT) invalid()
        }
        if (quoted || depth != 0) invalid()
        val parser = JSONTokener(raw)
        val document = parser.nextValue() as? JSONObject ?: invalid()
        if (parser.nextClean() != '\u0000') invalid()
        val nodesArray = document.optJSONArray("nodes") ?: invalid()
        if (nodesArray.length() > 32) invalid()
        return (0 until nodesArray.length()).mapNotNull { index ->
            if (nodesArray.isNull(index)) return@mapNotNull null
            val node = nodesArray.optJSONObject(index) ?: invalid()
            if (!node.has("data")) return@mapNotNull null
            val data = node.optJSONArray("data") ?: invalid()
            if (data.length() !in 1..8192) invalid()
            Pool(data)
        }
    }

    fun parseCatalog(raw: String): List<Title> = checked {
        val titles = mutableListOf<Title>()
        var found = false
        for (pool in pools(raw)) {
            if (!pool.root.has("results")) continue
            found = true
            val results = pool.array(pool.root.opt("results"))
            if (results.length() > 32 - titles.size) invalid()
            for (index in 0 until results.length()) {
                val item = pool.obj(results.opt(index))
                titles += Title(pool.string(item, "title", 300), slug(pool.string(item, "slug", 240)))
            }
        }
        if (!found) invalid()
        titles.distinct()
    }

    fun parseEpisode(raw: String, selectedSlug: String, requestedEpisode: Int): List<Embed> = checked {
        slug(selectedSlug)
        if (requestedEpisode <= 0) invalid()
        val embeds = mutableListOf<Embed>()
        var found = false
        for (pool in pools(raw)) {
            if (!pool.root.has("embeds")) continue
            found = true
            val media = pool.obj(pool.root.opt("media"))
            if (media.has("title")) pool.string(media, "title", 300)
            if (media.has("slug") && pool.string(media, "slug", 240) != selectedSlug) invalid()
            if (pool.root.has("episode")) {
                val episode = pool.field(pool.root, "episode")
                val number = if (episode is JSONObject) {
                    if (episode.has("number")) pool.field(episode, "number") else null
                } else episode
                if (number != null && number != JSONObject.NULL) {
                    val value = when (number) {
                        is Int -> number
                        is String -> number.takeIf { it.matches(Regex("[1-9][0-9]*")) }?.toIntOrNull()
                        else -> null
                    }
                    if (value != requestedEpisode) invalid()
                }
            }
            val variants = pool.obj(pool.root.opt("embeds"))
            for (variant in listOf("SUB", "DUB")) {
                if (!variants.has(variant)) continue
                val items = pool.array(variants.opt(variant))
                if (items.length() > 24 - embeds.size) invalid()
                for (index in 0 until items.length()) {
                    val embed = pool.obj(items.opt(index))
                    embeds += Embed(pool.string(embed, "server", 80), variant, pool.string(embed, "url", 8192))
                }
            }
        }
        if (!found) invalid()
        embeds.distinct()
    }

    fun episodeFor(query: SourceQuery): Int? = when (query.item.type) {
        MediaType.MOVIE -> 1
        MediaType.SERIES -> query.episode?.takeIf { it > 0 && (query.season ?: 0) >= 1 }
    }

    fun catalogUrl(title: String): HttpUrl = "https://animeav1.com/catalogo/__data.json".toHttpUrl()
        .newBuilder().addQueryParameter("page", "1").addQueryParameter("search", title).build()

    fun episodeUrl(selectedSlug: String, episode: Int): HttpUrl {
        if (episode <= 0) invalid()
        return "https://animeav1.com/media/${slug(selectedSlug)}/$episode/__data.json".toHttpUrl()
    }

    private data class Words(val tokens: List<String>, val seasons: Set<Int>)

    private fun words(raw: String, seasons: Boolean): Words {
        var normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
        val numbers = mutableSetOf<Int>()
        if (seasons) normalized = seasonMarker.replace(normalized) { match ->
            val number = match.groupValues.drop(1).first { it.isNotEmpty() }
            numbers += when (number) {
                "ii" -> 2; "iii" -> 3; "iv" -> 4; "v" -> 5
                else -> number.toIntOrNull() ?: invalid()
            }
            " "
        }
        return Words(normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }, numbers)
    }

    fun selectTitle(titles: List<Title>, query: SourceQuery): Title? = checked {
        if (episodeFor(query) == null || titles.size > 32) return@checked null
        if (query.item.source == CatalogSource.ANILIST) {
            // AniList IDs identify an individual anime entry/cour. Its episodes are entry-local,
            // not IMDb TV-season numbers. Keep season words intact and demand an exact title.
            if (query.season != 1) return@checked null
            val target = words(query.item.title, false).tokens
            return@checked titles.filter { words(it.title, false).tokens == target || words(it.slug, false).tokens == target }
                .distinctBy { it.slug }.singleOrNull()
        }
        val series = query.item.type == MediaType.SERIES
        val requested = if (series) query.season!! else 1
        val target = words(query.item.title, series)
        if (target.tokens.isEmpty() || target.seasons.any { it != requested }) return@checked null
        val ranked = titles.mapNotNull { title ->
            val name = words(title.title, series)
            val path = words(title.slug, series)
            val seasons = name.seasons + path.seasons
            if (series && (seasons.size > 1 || seasons.any { it != requested } ||
                    (requested > 1 && requested !in seasons))) return@mapNotNull null
            fun confidence(tokens: List<String>): Int = when {
                tokens == target.tokens -> 100
                tokens.size >= 2 && tokens.size == target.tokens.size && tokens.toSet() == target.tokens.toSet() -> 90
                else -> 0
            }
            val score = maxOf(confidence(name.tokens), confidence(path.tokens))
            if (score == 0) null else title to (score + if (series && requested == 1 && seasons.isEmpty()) 2 else 0)
        }
        val best = ranked.maxOfOrNull { it.second } ?: return@checked null
        ranked.filter { it.second == best }.map { it.first }.distinctBy { it.slug }.singleOrNull()
    }

    fun httpsUrl(raw: String): HttpUrl = checked {
        if (raw.length > 8192 || raw.any(Char::isISOControl) || encodedControl.containsMatchIn(raw)) invalid()
        val uri = URI(raw)
        if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null || uri.rawFragment != null ||
            uri.host.isNullOrEmpty() || uri.rawAuthority.orEmpty().endsWith(":")) invalid()
        val url = raw.toHttpUrl()
        val host = url.host
        if (!url.isHttps || url.port !in 1..65535 || host.length > 253 ||
            !host.matches(Regex("[a-z0-9-]+(?:\\.[a-z0-9-]+)+")) ||
            host.substringAfterLast('.').none { it in 'a'..'z' } ||
            listOf("localhost", "local", "internal", "lan", "home", "arpa").any { host == it || host.endsWith(".$it") }) invalid()
        url
    }

    fun isMp4Upload(host: String): Boolean = host == "mp4upload.com" || host.endsWith(".mp4upload.com")
    fun isYourUpload(host: String): Boolean = host == "yourupload.com" || host == "www.yourupload.com"

    fun fetchUrl(raw: String): HttpUrl = checked {
        val url = httpsUrl(raw)
        if (url.port != 443 || !(url.host == "animeav1.com" || url.host.endsWith(".animeav1.com") ||
                url.host == "player.zilla-networks.com" || isMp4Upload(url.host) || isYourUpload(url.host))) invalid()
        url
    }

    fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address
        if (bytes.size == 16) return (bytes[0].toInt() and 0xfe) != 0xfc
        val first = bytes[0].toInt() and 255
        val second = bytes[1].toInt() and 255
        return first != 0 && first < 224 && !(first == 100 && second in 64..127) &&
            !(first == 198 && second in 18..19)
    }

    private fun headers(root: String, origin: Boolean = false): Map<String, String> = buildMap {
        put("Referer", root)
        if (origin) put("Origin", root.removeSuffix("/"))
        put("User-Agent", USER_AGENT)
    }

    fun direct(raw: String): Media? = checked {
        val url = httpsUrl(raw)
        val extension = url.pathSegments.last().substringAfterLast('.', "").lowercase(Locale.ROOT)
        val format = when (extension) {
            "mp4" -> VideoFormat.AUTO
            "m3u8" -> VideoFormat.HLS
            "mpd" -> VideoFormat.DASH
            else -> return@checked null
        }
        Media(url, format, headers("https://animeav1.com/"))
    }

    fun zilla(raw: String): Media? = checked {
        val url = httpsUrl(raw)
        if (url.host != "player.zilla-networks.com" || url.port != 443 || url.query != null ||
            url.pathSegments.size != 2 || url.pathSegments[0] != "play" ||
            !safeId.matches(url.pathSegments[1]) || url.encodedPath != "/play/${url.pathSegments[1]}") return@checked null
        Media("https://player.zilla-networks.com/m3u8/${url.pathSegments[1]}".toHttpUrl(), VideoFormat.HLS,
            headers("https://player.zilla-networks.com/", origin = true) + mapOf(
                "Sec-Fetch-Dest" to "empty", "Sec-Fetch-Mode" to "cors", "Sec-Fetch-Site" to "same-origin"))
    }

    fun parseMp4Upload(html: String): Media? = checked {
        if (html.toByteArray(Charsets.UTF_8).size > BODY_LIMIT) invalid()
        val matches = Regex("\\bsrc\\s*:\\s*(['\"])(https://[^'\"\\s<>]+)\\1", RegexOption.IGNORE_CASE)
        matches.findAll(html).take(24).mapNotNull { match ->
            val url = try { httpsUrl(match.groupValues[2]) } catch (_: IllegalArgumentException) { return@mapNotNull null }
            if (!isMp4Upload(url.host) || !url.encodedPath.startsWith("/d/") ||
                !url.encodedPath.endsWith("/video.mp4") || url.pathSegments.size < 3) return@mapNotNull null
            Media(url, VideoFormat.AUTO, headers("https://www.mp4upload.com/", origin = true))
        }.firstOrNull()
    }

    fun parseYourUpload(html: String, embedUrl: HttpUrl): Media? = checked {
        if (!isYourUpload(fetchUrl(embedUrl.toString()).host) || html.toByteArray(Charsets.UTF_8).size > BODY_LIMIT) invalid()
        val matches = Regex("\\bfile\\s*:\\s*(['\"])(https://[^'\"\\s<>]+)\\1", RegexOption.IGNORE_CASE)
        matches.findAll(html).take(24).mapNotNull { match ->
            val media = try { direct(match.groupValues[2]) } catch (_: IllegalArgumentException) { return@mapNotNull null }
            if (media == null || !media.url.pathSegments.last().endsWith(".mp4", true)) return@mapNotNull null
            media.copy(headers = headers("https://${embedUrl.host}/", origin = true) + ("Referer" to embedUrl.toString()))
        }.firstOrNull()
    }

    fun source(embed: Embed, media: Media): StreamSource {
        val identity = "${embed.variant}\u0000${embed.server}\u0000${media.url}"
        val id = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return StreamSource(id, "pc:animeav1", "AnimeAV1", "AnimeAV1", "${embed.server} | ${embed.variant}",
            media.url.toString(), media.format, media.headers, allowDesktopHttp = false)
    }
}
