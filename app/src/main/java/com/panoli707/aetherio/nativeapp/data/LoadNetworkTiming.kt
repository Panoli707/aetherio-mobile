package com.panoli707.aetherio.nativeapp.data

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/** Used only by production-created clients, so injected test/client listeners remain untouched. */
internal class LoadNetworkTiming : EventListener() {
    private var total: LoadTiming.Span? = null
    private var dns: LoadTiming.Span? = null
    private var connect: LoadTiming.Span? = null
    private var headers: LoadTiming.Span? = null
    private var body: LoadTiming.Span? = null
    private var origin = LoadTiming.Origin.OTHER
    private fun start(stage: LoadTiming.Stage) = LoadTiming.start(stage, origin)
    override fun callStart(call: Call) {
        val url = call.request().url
        origin = when (url.host) {
            "api.themoviedb.org" -> LoadTiming.Origin.TMDB
            "graphql.anilist.co" -> LoadTiming.Origin.ANILIST
            "v3-cinemeta.strem.io" -> LoadTiming.Origin.CINEMETA
            "image.tmdb.org", "s4.anilist.co", "i.ytimg.com" -> LoadTiming.Origin.ARTWORK
            "trkll.aetherio.workers.dev" -> when {
                url.encodedPath.startsWith("/api/tmdb/") -> LoadTiming.Origin.TMDB
                url.encodedPath.startsWith("/api/jikan/") -> LoadTiming.Origin.JIKAN
                else -> LoadTiming.Origin.OTHER
            }
            else -> LoadTiming.Origin.OTHER
        }
        total = start(LoadTiming.Stage.NETWORK_TOTAL)
    }
    override fun dnsStart(call: Call, domainName: String) { dns = start(LoadTiming.Stage.NETWORK_DNS) }
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) { dns?.finish() }
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connect = start(LoadTiming.Stage.NETWORK_CONNECT)
    }
    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) { connect?.finish() }
    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) { connect?.finish(false) }
    override fun requestHeadersStart(call: Call) { headers = start(LoadTiming.Stage.NETWORK_HEADERS) }
    override fun responseHeadersEnd(call: Call, response: Response) { headers?.finish(response.isSuccessful) }
    override fun responseBodyStart(call: Call) { body = start(LoadTiming.Stage.NETWORK_BODY) }
    override fun responseBodyEnd(call: Call, byteCount: Long) { body?.finish() }
    override fun callEnd(call: Call) { total?.finish() }
    override fun callFailed(call: Call, ioe: IOException) { total?.finish(false) }
}

internal fun metadataHttpClient(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
    .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
    .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
    .eventListenerFactory { LoadNetworkTiming() }.build()
