package com.panoli707.aetherio.nativeapp.player.torrent

import com.panoli707.aetherio.nativeapp.data.StreamSource
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import org.json.JSONObject

object TorrentRequestParser {
    private val defaults = listOf("udp://tracker.opentrackr.org:1337/announce", "udp://tracker.torrent.eu.org:451/announce")
    private val encodedControl = Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE)

    fun canonicalHash(value: String): String {
        if (value.matches(Regex("[a-fA-F0-9]{40}"))) return value.lowercase(Locale.ROOT)
        require(value.matches(Regex("[A-Za-z2-7]{32}")))
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var accumulator = 0
        var bits = 0
        val bytes = ArrayList<Int>(20)
        value.uppercase(Locale.ROOT).forEach { char ->
            accumulator = (accumulator shl 5) or alphabet.indexOf(char)
            bits += 5
            if (bits >= 8) { bits -= 8; bytes.add((accumulator ushr bits) and 255) }
        }
        require(bytes.size == 20 && bits == 0)
        return bytes.joinToString("") { it.toString(16).padStart(2, '0') }
    }

    private fun parameters(raw: String): List<Pair<String, String>> {
        require(raw.length <= 16 * 1024 && raw.none(Char::isISOControl) && !encodedControl.containsMatchIn(raw))
        val uri = URI(raw)
        require(uri.scheme.equals("magnet", true) && uri.rawFragment == null && uri.rawSchemeSpecificPart.startsWith("?"))
        return uri.rawSchemeSpecificPart.drop(1).split('&').map {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
    }

    fun magnetHash(raw: String): String {
        val topic = parameters(raw).filter { it.first == "xt" }.single().second
        require(topic.startsWith("urn:btih:", true))
        return canonicalHash(topic.substring(9))
    }

    fun validateTracker(raw: String): String {
        require(raw.length in 1..2048 && raw.none(Char::isISOControl) && !encodedControl.containsMatchIn(raw))
        val uri = URI(raw)
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("udp", "http", "https"))
        val host = requireNotNull(uri.host).lowercase(Locale.ROOT).removePrefix("[").removeSuffix("]")
        require(uri.rawUserInfo == null && uri.rawFragment == null && !uri.rawAuthority.orEmpty().endsWith(":"))
        require(uri.port == -1 || uri.port in 1..65535)
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local"))
        require('.' in host || ':' in host)
        if (':' in host || host.all { it.isDigit() || it == '.' }) {
            if (':' !in host) require(host.split('.').let { it.size == 4 && it.all { part ->
                part.isNotEmpty() && (part == "0" || !part.startsWith('0')) && (part.toIntOrNull() ?: -1) in 0..255
            } })
            val address = InetAddress.getByName(host) // Numeric literals only; never resolve DNS here.
            require(!address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isSiteLocalAddress &&
                !address.isLinkLocalAddress && !address.isMulticastAddress)
            val bytes = address.address
            if (bytes.size == 16) require((bytes[0].toInt() and 0xfe) != 0xfc)
            if (bytes.size == 4) {
                val first = bytes[0].toInt() and 255
                val second = bytes[1].toInt() and 255
                require(first in 1..223 && !(first == 100 && second in 64..127) && !(first == 198 && second in 18..19))
            }
        }
        return uri.toASCIIString()
    }

    fun fromSource(source: StreamSource): TorrentRequest {
        require(source.isTorrent)
        val magnet = source.url.takeIf { it.startsWith("magnet:", true) }
        val magnetHash = magnet?.let(::magnetHash)
        val hash = source.infoHash?.let(::canonicalHash) ?: requireNotNull(magnetHash)
        require(magnetHash == null || hash == magnetHash)
        val trackers = (magnet?.let(::parameters).orEmpty().filter { it.first == "tr" }.map { it.second } +
            source.sources.filter { it.startsWith("tracker:") }.map { it.removePrefix("tracker:") }).distinct()
        val hints = JSONObject(source.behaviorHints)
        val raw = JSONObject(source.rawStream)
        val filename = (hints.opt("filename") as? String ?: raw.opt("filename") as? String)?.takeIf { it.isNotBlank() }
        return TorrentRequest(hash, source.fileIdx, filename, trackers.ifEmpty { defaults }).validated()
    }
}
