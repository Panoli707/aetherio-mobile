package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AetherioRepository {
    private val tmdb = "https://trkll.aetherio.workers.dev/api/tmdb"
    private val cinemeta = "https://v3-cinemeta.strem.io"
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).followRedirects(true).followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            if (!url.isHttps || url.host.isEmpty() || url.username.isNotEmpty() || url.password.isNotEmpty()) throw IOException("Destino no permitido")
            chain.proceed(chain.request())
        }.build()

    private suspend fun json(url: String, parameters: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        val builder = url.toHttpUrl().newBuilder()
        parameters.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val call = client.newCall(Request.Builder().url(builder.build()).build())
        // Keep cancellation connected until body parsing finishes, not just until headers arrive.
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            check(it.isSuccessful)
                            val body = requireNotNull(it.body)
                            val limit = 4L * 1024 * 1024
                            check(body.contentLength() <= limit)
                            val source = body.source()
                            source.request(limit + 1)
                            check(source.buffer.size <= limit)
                            JSONObject(source.readUtf8())
                        }
                    }
                    if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
                }
            })
        }
    }
    private suspend fun <T> optional(block: suspend () -> T): T? = try { block() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    private suspend fun tmdb(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        json("$tmdb/$path", mapOf("language" to "es-ES", "page" to "1") + params)
    private fun cineType(type: MediaType) = if (type == MediaType.MOVIE) "movie" else "series"
    private suspend fun trending(type: MediaType): List<MediaItem> = optional {
        CatalogJson.catalog(tmdb("trending/${type.path}/day"), type, CatalogSource.TMDB).also { check(it.isNotEmpty()) }
    } ?: CatalogJson.catalog(json("$cinemeta/catalog/${cineType(type)}/top.json"), type, CatalogSource.CINEMETA)
    private suspend fun anime(type: MediaType, extra: Map<String, String>): List<MediaItem> = CatalogJson.catalog(
        tmdb("discover/${type.path}", mapOf("with_genres" to "16", "with_original_language" to "ja", "sort_by" to "vote_average.desc", "vote_count.gte" to "200") + extra),
        type, CatalogSource.TMDB, true)
    suspend fun home(): HomeCatalog {
        val results = supervisorScope {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fun day(offset: Int) = date.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }.time)
            fun section(id: String, title: String, style: SectionStyle = SectionStyle.POSTERS, fetch: suspend () -> List<MediaItem>) =
                CatalogSection(id, title, emptyList(), style) to fetch
            val specs = listOf(
                section("movies", "Películas en tendencias") { trending(MediaType.MOVIE) },
                section("series", "Series en tendencias") { trending(MediaType.SERIES) },
                section("airing", "Anime emitiéndose ahora") {
                    anime(MediaType.SERIES, mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "0", "air_date.gte" to day(-90), "air_date.lte" to day(90))) },
                section("anime90", "Lo mejor de los 90: anime", SectionStyle.FEATURED) {
                    anime(MediaType.SERIES, mapOf("first_air_date.gte" to "1990-01-01", "first_air_date.lte" to "1999-12-31")) },
                section("animeMovies", "Top 15 películas anime", SectionStyle.RANKED) { anime(MediaType.MOVIE, emptyMap()).take(15) },
                section("animeSeries", "Top 15 series anime", SectionStyle.RANKED) { anime(MediaType.SERIES, emptyMap()).take(15) },
                section("anime2010", "Lo mejor de los 2010: anime", SectionStyle.FEATURED) {
                    anime(MediaType.SERIES, mapOf("first_air_date.gte" to "2010-01-01", "first_air_date.lte" to "2019-12-31")) })
            specs.map { (section, fetch) -> async { optional { section.copy(items = fetch()) } } }.awaitAll()
        }
        val sections = results.filterNotNull().filter { it.items.isNotEmpty() }
        check(sections.isNotEmpty())
        return HomeCatalog(sections, sections.firstOrNull { it.id == "movies" }?.items.orEmpty().take(4), partial = results.any { it == null })
    }
    suspend fun heroes(items: List<MediaItem>): List<MediaItem> = supervisorScope {
        items.take(4).map { item -> async {
            if (item.source != CatalogSource.TMDB || item.logoUrl != null) item else optional {
                item.copy(logoUrl = CatalogJson.logo(json("$tmdb/${item.type.path}/${item.id}/images", mapOf("include_image_language" to "es,en,null"))))
            } ?: item
        } }.awaitAll()
    }
    suspend fun detail(item: MediaItem): MediaDetail {
        val data = if (item.source == CatalogSource.TMDB) tmdb("${item.type.path}/${item.id}", mapOf("append_to_response" to "images,credits", "include_image_language" to "es,en,null"))
            else requireNotNull(json("$cinemeta/meta/${cineType(item.type)}/${item.id}.json").optJSONObject("meta"))
        check(CatalogJson.text(data, "id") == item.id)
        return CatalogJson.detail(item, data)
    }
    suspend fun episodes(item: MediaItem, season: Int): List<EpisodeItem> = if (item.source == CatalogSource.TMDB)
        CatalogJson.episodes(tmdb("tv/${item.id}/season/$season"), season)
        else CatalogJson.episodes(requireNotNull(json("$cinemeta/meta/${cineType(item.type)}/${item.id}.json").optJSONObject("meta")), season, true)
    suspend fun search(query: String): List<MediaItem> = optional {
        val data = tmdb("search/multi", mapOf("query" to query, "include_adult" to "false"))
        check(data.optJSONArray("results") != null)
        CatalogJson.search(data)
    } ?: supervisorScope {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val results = MediaType.entries.map { type -> async { optional {
            CatalogJson.catalog(json("$cinemeta/catalog/${cineType(type)}/top/search=$encoded.json"), type, CatalogSource.CINEMETA)
        } } }.awaitAll()
        ensureActive()
        check(results.any { it != null })
        results.filterNotNull().flatten().distinctBy { it.key }
    }
}
