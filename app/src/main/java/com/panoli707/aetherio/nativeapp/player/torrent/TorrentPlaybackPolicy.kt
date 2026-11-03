package com.panoli707.aetherio.nativeapp.player.torrent

import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/** Capability granted only to the current prepared native route, never to arbitrary Intent URLs. */
class TorrentRoute internal constructor(val streamId: String, val url: String, val fileIndex: Int, val fileSize: Long) {
    private val active = AtomicBoolean(true)
    init {
        require(streamId.matches(Regex("[0-9a-fA-F]{64}")))
        val uri = URI(url)
        require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535)
        require(url == "http://127.0.0.1:${uri.port}/stream/$streamId")
        require(fileIndex >= 0 && fileSize > 0)
    }
    fun validate(value: String): String {
        require(active.get() && value == url) { "Ruta torrent no permitida o cerrada" }
        return value
    }
    internal fun revoke() { active.set(false) }
    override fun toString(): String = "TorrentRoute(redacted)"
}
