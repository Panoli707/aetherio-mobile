package com.panoli707.aetherio.nativeapp.player.torrent

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.panoli707.aetherio.nativeapp.player.MediaUrlPolicy
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Local movie and remote HTTPS subtitles never share credentials, redirects or loopback authorization. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class TorrentDataSourceFactory(private val route: TorrentRoute, private val https: DataSource.Factory) : DataSource.Factory {
    private val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            try { route.validate(chain.request().url.toString()) }
            catch (_: IllegalArgumentException) { throw IOException("Ruta torrent cerrada o no permitida") }
            val safe = chain.request().newBuilder()
            listOf("Authorization", "Cookie", "Referer", "Origin", "Proxy-Authorization").forEach(safe::removeHeader)
            chain.proceed(safe.build())
        }.build()
    private val local = OkHttpDataSource.Factory(client)
    override fun createDataSource(): DataSource = object : DataSource {
        private var delegate: DataSource? = null
        private val listeners = mutableListOf<TransferListener>()
        override fun addTransferListener(transferListener: TransferListener) {
            listeners.add(transferListener)
            delegate?.addTransferListener(transferListener)
        }
        override fun open(dataSpec: DataSpec): Long {
            val uri = dataSpec.uri.toString()
            val factory = try {
                if (uri == route.url) { route.validate(uri); local }
                else { MediaUrlPolicy.validate(uri); https }
            } catch (_: IllegalArgumentException) { throw IOException("Destino de vídeo no permitido") }
            return factory.createDataSource().also { source ->
                delegate = source
                listeners.forEach(source::addTransferListener)
            }.open(dataSpec)
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = requireNotNull(delegate).read(buffer, offset, length)
        override fun getUri(): Uri? = delegate?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()
        override fun close() { try { delegate?.close() } finally { delegate = null } }
    }
}
