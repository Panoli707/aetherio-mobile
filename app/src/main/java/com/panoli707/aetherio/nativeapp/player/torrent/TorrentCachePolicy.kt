package com.panoli707.aetherio.nativeapp.player.torrent

object TorrentCachePolicy {
    const val RESERVE = 256L * 1024 * 1024
    fun capacity(usable: Long): Long {
        check(usable >= RESERVE + 64L * 1024 * 1024) { "DISK_SPACE" }
        return minOf(2L * 1024 * 1024 * 1024, usable - RESERVE)
    }
}
