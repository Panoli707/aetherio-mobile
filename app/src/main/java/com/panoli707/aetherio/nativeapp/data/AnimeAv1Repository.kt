package com.panoli707.aetherio.nativeapp.data

import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AnimeAv1Repository(client: OkHttpClient = OkHttpClient()) {
    private val upstreamDns = client.dns
    private val client = client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
        .cache(null)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                try {
                    AnimeAv1Protocol.fetchUrl("https://$hostname/")
                } catch (_: IllegalArgumentException) {
                    throw failure()
                }
                val addresses = upstreamDns.lookup(hostname)
                if (addresses.isEmpty() || addresses.any { !AnimeAv1Protocol.isPublicAddress(it) }) throw failure()
                return addresses
            }
        })
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            try {
                AnimeAv1Protocol.fetchUrl(request.url.toString())
                if (listOf("Authorization", "Proxy-Authorization", "Cookie").any { request.header(it) != null }) throw failure()
            } catch (_: Exception) {
                throw failure()
            }
            chain.proceed(request)
        }.build()

    suspend fun sources(query: SourceQuery): SourceResult = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(45_000) {
                val episode = AnimeAv1Protocol.episodeFor(query) ?: return@withTimeoutOrNull SourceResult(emptyList())
                val title = query.item.title.trim()
                if (title.isEmpty() || title.length > 300 || title.any(Char::isISOControl)) {
                    return@withTimeoutOrNull SourceResult(emptyList())
                }
                val catalog = fetch(AnimeAv1Protocol.catalogUrl(title))
                val selected = AnimeAv1Protocol.selectTitle(AnimeAv1Protocol.parseCatalog(catalog.body), query)
                    ?: return@withTimeoutOrNull SourceResult(emptyList())
                val page = fetch(AnimeAv1Protocol.episodeUrl(selected.slug, episode))
                val embeds = AnimeAv1Protocol.parseEpisode(page.body, selected.slug, episode)
                val sources = linkedMapOf<String, StreamSource>()
                var unsupported = 0
                var failures = 0
                for (embed in embeds) {
                    try {
                        val media = resolve(embed.url)
                        if (media == null) unsupported++
                        else AnimeAv1Protocol.source(embed, media).also { sources.putIfAbsent(it.id, it) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: IOException) {
                        failures++
                    } catch (_: IllegalArgumentException) {
                        unsupported++
                    }
                }
                if (sources.isEmpty() && failures > 0) throw failure()
                SourceResult(sources.values.toList(), unsupported + failures)
            } ?: throw failure()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw failure()
        }
    }

    private suspend fun resolve(raw: String): AnimeAv1Protocol.Media? {
        AnimeAv1Protocol.direct(raw)?.let { return it }
        AnimeAv1Protocol.zilla(raw)?.let { return it }
        val url = AnimeAv1Protocol.httpsUrl(raw)
        return when {
            AnimeAv1Protocol.isMp4Upload(url.host) -> AnimeAv1Protocol.parseMp4Upload(fetch(url).body)
            AnimeAv1Protocol.isYourUpload(url.host) -> fetch(url).let { AnimeAv1Protocol.parseYourUpload(it.body, it.url) }
            else -> null
        }
    }

    private data class Page(val url: HttpUrl, val body: String = "", val redirect: HttpUrl? = null)

    private suspend fun fetch(initial: HttpUrl): Page {
        var url = AnimeAv1Protocol.fetchUrl(initial.toString())
        repeat(5) {
            val page = fetchOnce(url)
            val redirect = page.redirect ?: return page
            url = redirect
        }
        throw failure()
    }

    private suspend fun fetchOnce(url: HttpUrl): Page = suspendCancellableCoroutine { continuation ->
        val root = url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
        val call = client.newCall(Request.Builder().url(url).get()
            .header("User-Agent", AnimeAv1Protocol.USER_AGENT)
            .header("Referer", root).header("Origin", root.removeSuffix("/")).build())
        continuation.invokeOnCancellation { call.cancel() }
        try {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(failure())
                }

                override fun onResponse(call: Call, response: Response) {
                    // Keep cancellation attached until the bounded body has reached EOF and is closed.
                    val result = try {
                        response.use {
                            if (it.code in listOf(301, 302, 303, 307, 308)) {
                                val location = it.header("Location") ?: throw failure()
                                if (location.length > 8192 || location.any(Char::isISOControl) ||
                                    URI(location).rawUserInfo != null || URI(location).rawFragment != null) throw failure()
                                val redirect = url.resolve(location) ?: throw failure()
                                Page(url, redirect = AnimeAv1Protocol.fetchUrl(redirect.toString()))
                            } else {
                                if (!it.isSuccessful) throw failure()
                                val body = it.body ?: throw failure()
                                if (body.contentLength() > AnimeAv1Protocol.BODY_LIMIT) throw failure()
                                val source = body.source()
                                source.request(AnimeAv1Protocol.BODY_LIMIT.toLong() + 1)
                                if (source.buffer.size > AnimeAv1Protocol.BODY_LIMIT) throw failure()
                                Page(url, source.readUtf8())
                            }
                        }.let { Result.success(it) }
                    } catch (_: Exception) {
                        Result.failure(failure())
                    }
                    if (continuation.isActive) result.fold(
                        { continuation.resume(it) }, { continuation.resumeWithException(it) })
                }
            })
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resumeWithException(failure())
        }
    }

    private fun failure() = IOException("No se pudo consultar AnimeAV1")
}
