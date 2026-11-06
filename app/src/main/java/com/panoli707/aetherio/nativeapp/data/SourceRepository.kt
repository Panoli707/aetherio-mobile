package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class SourceRepository(client: OkHttpClient = DEFAULT, requestTimeoutMs: Long = 20_000, private val attempts: Int = 1) {
    init { require(requestTimeoutMs in 1..60_000 && attempts in 1..2) }
    private val client = client.newBuilder()
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(minOf(8_000, requestTimeoutMs), TimeUnit.MILLISECONDS)
        .readTimeout(requestTimeoutMs, TimeUnit.MILLISECONDS).callTimeout(requestTimeoutMs, TimeUnit.MILLISECONDS)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            try {
                SourceProtocol.httpsUrl(url.toString())
            } catch (_: Exception) {
                throw IOException("Destino de fuentes no permitido")
            }
            chain.proceed(chain.request())
        }.build()

    suspend fun configure(raw: String): AddonConfig = withContext(Dispatchers.IO) {
        val url = SourceProtocol.manifestUrl(raw)
        fetch(url, SourceProtocol.MANIFEST_LIMIT) { SourceProtocol.manifest(it, url) }
    }

    suspend fun sources(config: AddonConfig, query: SourceQuery): SourceResult = withContext(Dispatchers.IO) {
        val url = SourceProtocol.endpoint(config, query) ?: return@withContext SourceResult(emptyList())
        fetch(url, SourceProtocol.STREAM_LIMIT) { SourceProtocol.streams(it, config) }
    }

    suspend fun document(url: String, revalidate: Boolean = false): String = withContext(Dispatchers.IO) {
        fetch(SourceProtocol.httpsUrl(url), SourceProtocol.MANIFEST_LIMIT, revalidate) { it }
    }

    private suspend fun <T> fetch(url: HttpUrl, limit: Int, revalidate: Boolean = false, parse: (String) -> T): T {
        repeat(attempts) { attempt ->
            try { return fetchOnce(url, limit, revalidate, parse) }
            catch (e: CancellationException) { throw e }
            catch (e: SourceFetchException) {
                if (!e.retryable || attempt == attempts - 1) throw e
                delay(250)
            }
        }
        throw failure()
    }

    private suspend fun <T> fetchOnce(url: HttpUrl, limit: Int, revalidate: Boolean, parse: (String) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = try {
                client.newCall(Request.Builder().url(url).get().apply {
                    if (revalidate) header("Cache-Control", "no-cache")
                }.build())
            } catch (_: Exception) {
                continuation.resumeWithException(failure())
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation { call.cancel() }
            try {
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(failure(retryable = true))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        // Parse on OkHttp's worker, with cancellation attached through EOF and parsing.
                        val result: Result<T> = try {
                            response.use {
                                if (!it.isSuccessful) throw failure(it.code in setOf(408, 502, 503, 504))
                                val body = it.body ?: throw failure()
                                if (body.contentLength() > limit) throw failure()
                                val source = body.source()
                                source.request(limit.toLong() + 1)
                                if (source.buffer.size > limit) throw failure()
                                parse(source.readUtf8())
                            }.let { Result.success(it) }
                        } catch (e: SourceFetchException) {
                            Result.failure(e)
                        } catch (e: IOException) {
                            Result.failure(failure(retryable = true))
                        } catch (_: Exception) {
                            Result.failure(failure())
                        }
                        if (continuation.isActive) result.fold(
                            { continuation.resume(it) }, { continuation.resumeWithException(it) }
                        )
                    }
                })
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resumeWithException(failure())
            }
        }

    private fun failure(retryable: Boolean = false) = SourceFetchException(retryable)
    private class SourceFetchException(val retryable: Boolean) : IOException("No se pudo consultar la fuente")

    private companion object {
        val DEFAULT: OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
