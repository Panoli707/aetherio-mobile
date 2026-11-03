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
    private val readAccessToken: String = EmbeddedTmdbToken.value,
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
            if (item.source == CatalogSource.ANILIST) item else optional {
                // Trending results omit named genres and age ratings, even when they already have a logo.
                val data = if (item.source == CatalogSource.TMDB) tmdb("${item.type.path}/${item.id}", mapOf(
                    "append_to_response" to "images,${if (item.type == MediaType.SERIES) "content_ratings" else "release_dates"}",
                    "include_image_language" to "es,en,null"))
                else requireNotNull(json("$cinemeta/meta/${cineType(item.type)}/${item.id}.json").optJSONObject("meta"))
                check(CatalogJson.text(data, "id") == item.id)
                CatalogJson.detail(item, data).item
            } ?: item
        } }.awaitAll()
    }
    suspend fun detail(item: MediaItem): MediaDetail {
        if (item.source == CatalogSource.ANILIST) {
            val canonical = SeriesIdentity.item(item)
            if (canonical.key != item.key) return detail(canonical)
            return animeRepository.detail(item)
        }
        val data = if (item.source == CatalogSource.TMDB) tmdb("${item.type.path}/${item.id}", mapOf(
            "append_to_response" to "images,credits,external_ids,videos,watch/providers,reviews,recommendations,${if (item.type == MediaType.SERIES) "content_ratings" else "release_dates"}",
            "include_image_language" to "es,en,null", "include_video_language" to "es,en,null"))
            else requireNotNull(json("$cinemeta/meta/${cineType(item.type)}/${item.id}.json").optJSONObject("meta"))
        check(CatalogJson.text(data, "id") == item.id)
        val detail = CatalogJson.detail(item, data)
        val parts = SeriesIdentity.index.parts(item)
        if (parts.isEmpty()) return detail
        val grouped = parts.groupBy { it.season }.mapNotNull { (number, group) ->
            val first = group.minBy { it.offset }
            val source = detail.seasons.firstOrNull { it.number == first.tmdbSeason } ?: return@mapNotNull null
            val until = parts.filter { it.tmdbSeason == first.tmdbSeason && it.season != number && it.offset > first.offset }
                .minOfOrNull { it.offset } ?: source.episodeCount
            val count = (minOf(until, source.episodeCount) - first.offset).coerceAtLeast(0)
            if (count == 0) null else SeasonInfo(number, "Temporada $number", count)
        }
        val untouched = detail.seasons.filter { source -> parts.none { it.tmdbSeason == source.number } }
            .map { source -> source.copy(number = if (parts.any { it.season == source.number }) 1000 + source.number else source.number) }
        val seasons = (grouped + untouched).sortedBy { it.number }
        return detail.copy(item = detail.item.copy(seriesNumbering = true, isAnime = true), seasons = seasons,
            selectedSeason = item.seasonHint?.takeIf { hint -> seasons.any { it.number == hint } } ?: seasons.firstOrNull()?.number)
    }
    suspend fun episodes(item: MediaItem, season: Int): List<EpisodeItem> = if (item.source == CatalogSource.ANILIST)
        if (season == 1) animeRepository.detail(item).episodes else emptyList()
        else if (item.source == CatalogSource.TMDB)
        CatalogJson.episodes(tmdb("tv/${item.id}/season/$season"), season)
        else CatalogJson.episodes(requireNotNull(json("$cinemeta/meta/${cineType(item.type)}/${item.id}.json").optJSONObject("meta")), season, true)

    suspend fun seasonContent(item: MediaItem, season: Int): SeasonContent {
        val parts = SeriesIdentity.index.parts(item)
        val group = parts.filter { it.season == season }
        if (item.seriesNumbering && group.isNotEmpty()) {
            val first = group.minBy { it.offset }
            val end = parts.filter { it.tmdbSeason == first.tmdbSeason && it.season != season && it.offset > first.offset }
                .minOfOrNull { it.offset } ?: Int.MAX_VALUE
            val json = tmdb("tv/${item.id}/season/${first.tmdbSeason}")
            val episodes = CatalogJson.episodes(json, first.tmdbSeason).filter { it.number > first.offset && it.number <= end }
                .map { it.copy(season = season, number = it.number - first.offset) }
            val art = optional { animeRepository.detail(MediaItem(first.animeId, MediaType.SERIES, item.title,
                source = CatalogSource.ANILIST, isAnime = true)) }?.item
            return SeasonContent(item.copy(posterUrl = art?.posterUrl ?: item.posterUrl,
                backdropUrl = art?.backdropUrl ?: item.backdropUrl, seasonHint = season, animeEntryId = first.animeId), episodes)
        }
        val sourceSeason = if (item.seriesNumbering && parts.isNotEmpty() && season >= 1000) season - 1000 else season
        if (item.source == CatalogSource.TMDB) {
            val json = tmdb("tv/${item.id}/season/$sourceSeason")
            val poster = CatalogJson.text(json, "poster_path").takeIf { it.startsWith("/") && !it.startsWith("//") && '\\' !in it }
                ?.let { "https://image.tmdb.org/t/p/original$it" }
            return SeasonContent(item.copy(posterUrl = poster ?: item.posterUrl, seasonHint = season),
                CatalogJson.episodes(json, sourceSeason).map { it.copy(season = season) })
        }
        return SeasonContent(item, episodes(item, season))
    }

    suspend fun providerQuery(query: SourceQuery, animeAv1: Boolean): SourceQuery {
        val season = query.season ?: return query
        val episode = query.episode ?: return query
        val parts = SeriesIdentity.index.parts(query.item)
        if (!query.item.seriesNumbering || parts.isEmpty()) return query
        val group = parts.filter { it.season == season }
        if (group.isEmpty()) return if (season >= 1000) query.copy(season = season - 1000) else query
        val base = group.minOf { it.offset }
        if (!animeAv1) return query.copy(season = group.first().tmdbSeason, episode = episode + base)
        val part = group.filter { episode + base > it.offset }.maxByOrNull { it.offset } ?: return query
        val entry = animeRepository.detail(MediaItem(part.animeId, MediaType.SERIES, query.item.title,
            source = CatalogSource.ANILIST, isAnime = true)).item
        return query.copy(item = entry, season = 1, episode = episode + base - part.offset)
    }
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

internal data class SeasonContent(val item: MediaItem, val episodes: List<EpisodeItem>)
