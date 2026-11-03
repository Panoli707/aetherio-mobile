package com.panoli707.aetherio.nativeapp.player.torrent

import org.json.JSONArray
import org.json.JSONObject

/** Validated v1 torrent request. No endpoint, credentials or provider response crosses the activity boundary. */
data class TorrentRequest(val infoHash: String, val fileIndex: Int? = null,
    val filenameHint: String? = null, val trackers: List<String> = emptyList()) {
    fun validated(): TorrentRequest {
        require(fileIndex == null || fileIndex >= 0)
        require(filenameHint == null || (filenameHint.length in 1..512 && filenameHint.none(Char::isISOControl)))
        val hash = TorrentRequestParser.canonicalHash(infoHash)
        require(trackers.size <= 32)
        val unique = trackers.map(TorrentRequestParser::validateTracker).distinct()
        return copy(infoHash = hash, trackers = unique).also { require(it.magnet().toByteArray(Charsets.UTF_8).size <= 16 * 1024) }
    }

    fun magnet(): String = "magnet:?xt=urn:btih:$infoHash" + trackers.joinToString("") {
        "&tr=" + java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }

    fun encode(): String = validated().let { request -> JSONObject().apply {
        put("hash", request.infoHash)
        request.fileIndex?.let { put("index", it) }
        request.filenameHint?.let { put("filename", it) }
        put("trackers", JSONArray(request.trackers))
    }.toString() }

    companion object {
        fun decode(raw: String): TorrentRequest {
            require(raw.length <= 32 * 1024)
            val json = JSONObject(raw)
            val index = if (!json.has("index")) null else {
                val number = json.get("index") as? Number ?: throw IllegalArgumentException()
                number.toInt().also { require(it >= 0 && number.toDouble() == it.toDouble()) }
            }
            val array = json.getJSONArray("trackers")
            require(array.length() <= 32)
            return TorrentRequest(json.get("hash") as String, index,
                if (json.has("filename")) json.get("filename") as String else null,
                (0 until array.length()).map { array.get(it) as String }).validated()
        }
    }
}
