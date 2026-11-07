package com.panoli707.aetherio.nativeapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.imageLoader
import coil.decode.SvgDecoder

class AetherioApplication : Application(), ImageLoaderFactory {
    internal val animeTracking by lazy { com.panoli707.aetherio.nativeapp.data.AnimeTrackingStore(this) }
    internal val mediaDownloads by lazy { com.panoli707.aetherio.nativeapp.downloads.MediaDownloads(this) }
    internal var downloadHttpClientOverride: okhttp3.OkHttpClient? = null
    internal var downloadTorrentSessionsOverride: com.panoli707.aetherio.nativeapp.player.torrent.TorrentSessionManager? = null
    internal val downloadTorrentSessions get() = downloadTorrentSessionsOverride ?: defaultDownloadTorrentSessions
    private val defaultDownloadTorrentSessions by lazy {
        com.panoli707.aetherio.nativeapp.player.torrent.TorrentSessionManager(
            factory = { com.panoli707.aetherio.nativeapp.player.torrent.NuvioTorrentEngine.create(this, forDownload = true) },
            enoughSpace = { filesDir.usableSpace >= com.panoli707.aetherio.nativeapp.downloads.MediaDownloads.RESERVE })
    }
    internal val canonicalArtwork by lazy { com.panoli707.aetherio.nativeapp.ui.CanonicalArtworkProvider(this) { imageLoader } }
    internal var artworkProviderOverride: com.panoli707.aetherio.nativeapp.ui.ArtworkProvider? = null
    internal val artworkProvider get() = artworkProviderOverride ?: canonicalArtwork
    internal var youtubeTrailerSourceOverride: com.panoli707.aetherio.nativeapp.player.YoutubeTrailerSource? = null
    internal val youtubeTrailers get() = youtubeTrailerSourceOverride ?: com.panoli707.aetherio.nativeapp.player.YoutubeTrailerResolver
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
