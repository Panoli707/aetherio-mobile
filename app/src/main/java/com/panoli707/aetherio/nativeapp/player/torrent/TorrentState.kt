package com.panoli707.aetherio.nativeapp.player.torrent

data class TorrentMetrics(val downloadBytesPerSecond: Long = 0, val peers: Int = 0,
    val verifiedBytes: Long = 0, val deliveredBytes: Long = 0)

sealed interface TorrentState {
    data class Preparing(val label: String) : TorrentState
    data class SelectFile(val files: List<TorrentFile>) : TorrentState
    data class Ready(val route: TorrentRoute, val metrics: TorrentMetrics = TorrentMetrics()) : TorrentState
    data class Failed(val message: String) : TorrentState
    data object Closed : TorrentState
}
