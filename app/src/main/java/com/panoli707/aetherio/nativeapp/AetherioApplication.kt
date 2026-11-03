package com.panoli707.aetherio.nativeapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder

class AetherioApplication : Application(), ImageLoaderFactory {
    internal var torrentSessionsOverride: com.panoli707.aetherio.nativeapp.player.torrent.TorrentSessionManager? = null
    val torrentSessions get() = torrentSessionsOverride ?: defaultTorrentSessions
    private val defaultTorrentSessions by lazy {
        com.panoli707.aetherio.nativeapp.player.torrent.TorrentSessionManager(
            factory = { com.panoli707.aetherio.nativeapp.player.torrent.NuvioTorrentEngine.create(this) },
            enoughSpace = { cacheDir.usableSpace >= com.panoli707.aetherio.nativeapp.player.torrent.TorrentCachePolicy.RESERVE }
        )
    }
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .crossfade(true)
        .build()
}
