package com.panoli707.aetherio.nativeapp.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class AnimeCatalogEntry(val item: MediaItem, val malId: Int?, val titles: List<String>)
internal fun interface HomeAnimeSource { suspend fun fetch(feed: AnimeFeed): List<AnimeCatalogEntry> }

internal class HomeAnimeRepository(client: OkHttpClient, private val anime: AnimeSeasonRepository) : HomeAnimeSource {
    private val client = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    private val jikanLock = Mutex()
    private var nextRequest = 0L
    @Volatile private var unavailableUntil = 0L
    private var failures = 0
    private val byMal = mutableMapOf<Int, AnimeCatalogEntry>()

    override suspend fun fetch(feed: AnimeFeed): List<AnimeCatalogEntry> {
        if (feed.sort != null) {
            val entries = attempt { withTimeoutOrNull(8_000) { anime.homeCatalog(feed) }.orEmpty() }.orEmpty()
            if (entries.isNotEmpty()) {
                synchronized(byMal) { entries.forEach { entry -> entry.malId?.let { byMal[it] = entry } } }
                return entries
            }
        }
        val request = feed.jikan ?: return emptyList()
        val raw = attempt { jikanLock.withLock {
            if (System.currentTimeMillis() < unavailableUntil) return@withLock emptyList()
            delay((nextRequest - System.currentTimeMillis()).coerceAtLeast(0))
            nextRequest = System.currentTimeMillis() + 450
            try { parse(request(request)).also { failures = 0 } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (++failures >= 4) { unavailableUntil = System.currentTimeMillis() + 60_000; failures = 0 }
                emptyList()
            }
        } }.orEmpty()
        if (raw.isEmpty()) return emptyList()
        val missing = synchronized(byMal) { raw.mapNotNull { it.malId }.distinct().filterNot { it in byMal } }
        if (missing.isNotEmpty()) attempt {
            withTimeoutOrNull(8_000) { anime.homeByMal(missing.take(50)) }?.let { entries ->
                synchronized(byMal) { entries.forEach { entry -> entry.malId?.let { byMal[it] = entry } } }
            }
        }
        return raw.map { entry -> synchronized(byMal) { byMal[entry.malId] } ?: entry }
    }
    private suspend fun <T> attempt(block: suspend () -> T): T? = try { block() }
        catch (error: CancellationException) { throw error } catch (_: Exception) { null }
    private suspend fun request(spec: HomeRequest): JSONObject {
        // Same default metadata proxy as desktop; no service is deployed or reconfigured here.
        val url = "https://trkll.aetherio.workers.dev/api/jikan/${spec.path}".toHttpUrl().newBuilder().apply {
            spec.params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        return suspendCancellableCoroutine { continuation ->
            // No TMDB client, authorization, playback headers or cookies are used here.
            val call = client.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("No se pudo consultar el anime")) }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        if (it.code == 429) {
                            val seconds = (it.header("Retry-After")?.toLongOrNull() ?: 60).coerceIn(1, 3600)
                            unavailableUntil = System.currentTimeMillis() + seconds * 1000
                        }
                        check(it.isSuccessful)
                        check(it.header("X-Aetherio-Upstream-Error") != "true")
                        val source = requireNotNull(it.body).source()
                        source.request(2 * 1024 * 1024L + 1)
                        check(source.buffer.size <= 2 * 1024 * 1024L)
                        JSONObject(source.readUtf8())
                    } }
                    if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(IOException("No se pudo consultar el anime")) })
                }
            })
        }
    }
    companion object {
        internal fun parse(json: JSONObject): List<AnimeCatalogEntry> {
            val data = json.optJSONArray("data") ?: return emptyList()
            val records = (0 until data.length()).flatMap { i ->
                val entry = data.optJSONObject(i) ?: return@flatMap emptyList()
                when (val nested = entry.opt("entry")) {
                    is JSONArray -> (0 until nested.length()).mapNotNull { nested.optJSONObject(it) }
                    is JSONObject -> listOf(nested)
                    else -> listOf(entry)
                }
            }
            return records.mapNotNull { item ->
                val id = item.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                val titles = listOf("title_english", "title", "title_japanese").map { CatalogJson.text(item, it) }.filter { it.isNotBlank() }.distinct()
                if (titles.isEmpty()) return@mapNotNull null
                val images = item.optJSONObject("images")
                val image = CatalogJson.image(images?.optJSONObject("webp")?.optString("large_image_url"))
                    ?: CatalogJson.image(images?.optJSONObject("jpg")?.optString("large_image_url"))
                val type = if (item.optString("type") == "Movie") MediaType.MOVIE else MediaType.SERIES
                // ID 0 is internal-only: unresolved MAL records must be matched before entering the catalog.
                AnimeCatalogEntry(MediaItem("0", type, titles.first(), CatalogSource.ANILIST, posterUrl = image,
                    overview = CatalogJson.text(item, "synopsis"), year = item.optInt("year", 0).takeIf { it in 1900..2200 }?.toString()
                        ?: item.optJSONObject("aired")?.optString("from")?.take(4).orEmpty(),
                    score = item.optDouble("score", 0.0).takeIf { it.isFinite() } ?: 0.0, isAnime = true), id, titles)
            }.distinctBy { it.malId }.take(25)
        }
    }
}
