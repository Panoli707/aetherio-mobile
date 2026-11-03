package com.panoli707.aetherio.nativeapp.player

import com.panoli707.aetherio.nativeapp.data.WatchTarget
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import kotlin.coroutines.resume

data class OpeningInterval(val startMs: Long, val endMs: Long) {
    fun active(positionMs: Long, durationMs: Long): Boolean =
        startMs >= 0 && endMs > startMs && endMs < durationMs && positionMs >= startMs && positionMs < endMs
}

data class OpeningSkip(val targetMs: Long, val precise: Boolean)

fun openingSkip(state: VideoUiState): OpeningSkip? {
    if (!state.animeEpisode || !state.seekable || state.live || state.error != null || state.durationMs <= 0) return null
    val interval = state.opening
    if (interval != null) return if (interval.active(state.positionMs, state.durationMs)) OpeningSkip(interval.endMs, true) else null
    return if (state.positionMs in 0 until 600000L && state.durationMs > state.positionMs + 90000)
        OpeningSkip(state.positionMs + 90000, false) else null
}

internal class OpeningRepository(client: OkHttpClient = OkHttpClient()) {
    private val client = client.newBuilder().cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()

    suspend fun opening(target: WatchTarget): OpeningInterval? = segments(target)?.opening

    suspend fun segments(target: WatchTarget): EpisodeSegments? {
        val imdb = target.item.imdbId ?: return null
        if (!target.item.isAnime || !imdb.matches(Regex("tt[0-9]{5,12}")) ||
            target.season == null || target.season < 1 || target.episode == null || target.episode < 1) return null
        val url = "https://api.introdb.app/segments".toHttpUrl().newBuilder()
            .addQueryParameter("imdb_id", imdb).addQueryParameter("season", target.season.toString())
            .addQueryParameter("episode", target.episode.toString()).build()
        return suspendCancellableCoroutine { continuation ->
            // Public timing metadata only; do not forward video headers or catalog credentials.
            val call = client.newCall(Request.Builder().url(url).get().build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }
                override fun onResponse(call: Call, response: Response) {
                    val interval = runCatching { response.use {
                        if (!it.isSuccessful) return@use null
                        val body = it.body ?: return@use null
                        if (body.contentLength() > 65536) return@use null
                        val source = body.source()
                        source.request(65537)
                        if (source.buffer.size > 65536) return@use null
                        val raw = source.readUtf8()
                        EpisodeSegments(parse(raw, target), parse(raw, target, "outro"))
                    } }.getOrNull()
                    if (continuation.isActive) continuation.resume(interval)
                }
            })
        }
    }

    companion object {
        internal fun parse(raw: String, target: WatchTarget, kind: String = "intro"): OpeningInterval? = runCatching {
            if (raw.length > 65536) return null
            val json = JSONObject(raw)
            if (json.optString("imdb_id") != target.item.imdbId || json.optInt("season", -1) != target.season ||
                json.optInt("episode", -1) != target.episode || json.optBoolean("is_movie") ||
                kind !in listOf("intro", "outro") || json.optBoolean("no_$kind")) return null
            val intro = json.optJSONObject(kind) ?: return null
            fun millis(name: String): Long? {
                val seconds = (intro.opt("${name}_sec") as? Number)?.toDouble()
                val value = seconds?.times(1000) ?: (intro.opt("${name}_ms") as? Number)?.toDouble() ?: return null
                return value.takeIf { it.isFinite() && it in 0.0..3600000.0 }?.toLong()
            }
            val start = millis("start") ?: return null
            val end = millis("end") ?: return null
            OpeningInterval(start, end).takeIf { end - start in 5000..300000 }
        }.getOrNull()
    }
}

data class EpisodeSegments(val opening: OpeningInterval?, val ending: OpeningInterval?)

fun endingSkip(state: VideoUiState): OpeningSkip? {
    if (!state.animeEpisode || !state.seekable || state.live || state.error != null || state.durationMs <= 0) return null
    val interval = state.ending
    if (interval != null) return if (interval.startMs >= 0 && interval.endMs > interval.startMs &&
        interval.endMs <= state.durationMs && state.positionMs >= interval.startMs && state.positionMs < interval.endMs)
        OpeningSkip(interval.endMs, true) else null
    // Explicit manual advance near the end, never an invented detected ending or an automatic finish.
    val remaining = state.durationMs - state.positionMs
    return if (state.positionMs >= state.durationMs * .75 && remaining > 90000 && remaining <= 300000)
        OpeningSkip(state.positionMs + 90000, false) else null
}
