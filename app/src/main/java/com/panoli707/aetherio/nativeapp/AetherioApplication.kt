package com.panoli707.aetherio.nativeapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.imageLoader
import coil.decode.SvgDecoder

class AetherioApplication : Application(), ImageLoaderFactory {
    internal val canonicalArtwork by lazy { com.panoli707.aetherio.nativeapp.ui.CanonicalArtworkProvider(this) { imageLoader } }
    internal var artworkProviderOverride: com.panoli707.aetherio.nativeapp.ui.ArtworkProvider? = null
    internal val artworkProvider get() = artworkProviderOverride ?: canonicalArtwork
    override fun onCreate() {
        super.onCreate()
        // Debug-only, on-device diagnostics. Release builds never install a logging observer.
        if (BuildConfig.DEBUG) com.panoli707.aetherio.nativeapp.data.LoadTiming.observer = { sample ->
            android.util.Log.d("AetherioLoad", "stage=${sample.stage} origin=${sample.origin} elapsedMs=${sample.elapsedMs} success=${sample.success}")
        }
    }
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
        .okHttpClient(com.panoli707.aetherio.nativeapp.data.metadataHttpClient())
        .eventListenerFactory { com.panoli707.aetherio.nativeapp.data.LoadImageTiming() }
        .crossfade(true)
        .build()
}
