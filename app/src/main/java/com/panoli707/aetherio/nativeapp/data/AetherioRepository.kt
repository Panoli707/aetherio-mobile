package com.panoli707.aetherio.nativeapp.data

import com.panoli707.aetherio.nativeapp.BuildConfig
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AetherioRepository(
    private val readAccessToken: String = BuildConfig.TMDB_READ_ACCESS_TOKEN,
    client: OkHttpClient = OkHttpClient(),
    private val tmdbBase: HttpUrl = (if (readAccessToken.isNotBlank()) "https://api.themoviedb.org/3"
        else "https://trkll.aetherio.workers.dev/api/tmdb").toHttpUrl(),
    private val animeRepository: AnimeSeasonRepository = AnimeSeasonRepository()
) {
    init {
        val direct = readAccessToken.isNotBlank()
        val productionHost = if (direct) "api.themoviedb.org" else "trkll.aetherio.workers.dev"
        // Endpoint injection is internal and limited to verified HTTPS loopback servers for tests.
        val loopback = tmdbBase.host in setOf("localhost", "127.0.0.1", "::1")
        if (!tmdbBase.isHttps || tmdbBase.username.isNotEmpty() || tmdbBase.password.isNotEmpty() ||
            tmdbBase.query != null || tmdbBase.fragment != null ||
            tmdbBase.encodedPath != (if (direct) "/3" else "/api/tmdb") ||
            !(loopback || (tmdbBase.host == productionHost && tmdbBase.port == 443))) {
            throw IOException("Destino de catalogo no permitido")
        }
    }

    private val tmdb = tmdbBase.toString()
    private val cinemeta = "https://v3-cinemeta.strem.io"
    private val client = client.newBuilder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).followRedirects(true).followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (!url.isHttps || url.host.isEmpty() || url.username.isNotEmpty() || url.password.isNotEmpty()) throw IOException("Destino no permitido")
            if (request.header("Authorization") != null && !authenticatedTmdb(url)) throw IOException("Destino no permitido")
            chain.proceed(request)
        }.build()
    // Do not let even same-origin redirects forward credentials outside the API path.
    private val authenticatedClient = this.client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    private fun authenticatedTmdb(url: HttpUrl): Boolean = readAccessToken.isNotBlank() &&
        url.isHttps && url.host == tmdbBase.host && url.port == tmdbBase.port &&
        url.username.isEmpty() && url.password.isEmpty() && url.encodedPath.startsWith("${tmdbBase.encodedPath}/")

    private suspend fun json(url: String, parameters: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        try {
            val builder = url.toHttpUrl().newBuilder()
            parameters.forEach { (key, value) -> builder.addQueryParameter(key, value) }
            val target = builder.build()
            val authenticated = authenticatedTmdb(target)
            val request = Request.Builder().url(target).apply {
                if (authenticated) header("Authorization", "Bearer $readAccessToken")
            }.build()
            val call = (if (authenticated) authenticatedClient else client).newCall(request)
            // Keep cancellation connected until body parsing finishes, not just until headers arrive.
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException("No se pudo consultar el catalogo")) }
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
                        if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(IOException("No se pudo consultar el catalogo")) })
                    }
                })
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw IOException("No se pudo consultar el catalogo") }
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
            fun section(id: String, title: String, style: SectionStyle = SectionStyle.POSTERS, fetch: suspend () -> List<MediaItem>) =
                CatalogSection(id, title, emptyList(), style) to fetch
            val specs = listOf(
                section("movies", "Películas en tendencias") { trending(MediaType.MOVIE) },
                section("series", "Series en tendencias") { trending(MediaType.SERIES) },
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
        if (item.source == CatalogSource.ANILIST) return animeRepository.detail(item)
        val data = if (item.source == CatalogSource.TMDB) tmdb("${item.type.path}/${item.id}", mapOf("append_to_response" to "images,credits,external_ids", "include_image_language" to "es,en,null"))
            else requireNotNull(json("$cinemeta/meta/${cineType(item.type)}/${item.id}.json").optJSONObject("meta"))
        check(CatalogJson.text(data, "id") == item.id)
        return CatalogJson.detail(item, data)
    }
    suspend fun episodes(item: MediaItem, season: Int): List<EpisodeItem> = if (item.source == CatalogSource.ANILIST)
        if (season == 1) animeRepository.detail(item).episodes else emptyList()
        else if (item.source == CatalogSource.TMDB)
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
