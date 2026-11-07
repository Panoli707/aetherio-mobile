package com.panoli707.aetherio.nativeapp.player.torrent

import android.content.Context
import com.nuvio.engine.NuvioEngine
import com.nuvio.engine.NuvioEngineConfig
import com.nuvio.engine.NuvioEventType
import com.nuvio.engine.NuvioTorrentProfile
import com.nuvio.engine.NuvioUploadMode
import java.io.File
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

internal class NuvioTorrentEngine(private val engine: NuvioEngine) : TorrentEngine {
    override val failures = engine.events.filter { it.requestId == 0L &&
        (it.type == NuvioEventType.TorrentError || it.type == NuvioEventType.StreamStopped) }.map { Unit }
    override suspend fun addMagnet(magnet: String) = engine.addMagnet(magnet)
    override suspend fun files(id: String) = engine.files(id).map { TorrentFile(it.index, it.path, it.size, it.pathTruncated) }
    override suspend fun prepare(id: String, file: TorrentFile): TorrentRoute {
        val stream = engine.prepareStream(id, file.index)
        check(stream.fileIndex == file.index && stream.fileSize == file.size)
        return TorrentRoute(stream.id, stream.url, stream.fileIndex, stream.fileSize)
    }
    override suspend fun metrics(route: TorrentRoute): TorrentMetrics {
        val stats = engine.stats.value
        val stream = engine.currentStreamStats(route.streamId)
        return TorrentMetrics(stats.downloadRateBytesPerSecond, stats.connectedPeers, stream.verifiedFileBytes, stream.deliveredBytes)
    }
    override suspend fun stop(route: TorrentRoute) { engine.stopStream(route.streamId) }
    override suspend fun shutdown() = engine.shutdown()

    companion object {
        /** Called synchronously inside the manager's IO worker so cancellation cannot lose the newly allocated handle. */
        fun create(context: Context, forDownload: Boolean = false): TorrentEngine {
            val directory = if (forDownload) "aetherio-torrent-downloads" else "aetherio-torrent"
            val state = File(context.noBackupFilesDir, "$directory/state")
            val cache = File(context.cacheDir, directory)
            check((state.mkdirs() || state.isDirectory) && (cache.mkdirs() || cache.isDirectory))
            val capacity = TorrentCachePolicy.capacity(cache.usableSpace)
            return NuvioTorrentEngine(NuvioEngine.create(NuvioEngineConfig(
                dataDirectory = state, cacheDirectory = cache, diskCacheCapacityBytes = capacity,
                memoryCacheCapacityBytes = 64L * 1024 * 1024, torrentProfile = NuvioTorrentProfile.Balanced,
                uploadMode = NuvioUploadMode.Disabled, streamInactivityTimeoutMilliseconds = 120_000,
                warmTorrentTimeoutMilliseconds = 0,
            )))
        }
    }
}
