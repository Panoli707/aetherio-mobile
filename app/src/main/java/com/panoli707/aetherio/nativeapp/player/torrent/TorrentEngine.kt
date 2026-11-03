package com.panoli707.aetherio.nativeapp.player.torrent

import kotlinx.coroutines.flow.Flow

interface TorrentEngine {
    val failures: Flow<Unit>
    suspend fun addMagnet(magnet: String): String
    suspend fun files(id: String): List<TorrentFile>
    suspend fun prepare(id: String, file: TorrentFile): TorrentRoute
    suspend fun metrics(route: TorrentRoute): TorrentMetrics
    suspend fun stop(route: TorrentRoute)
    suspend fun shutdown()
}
