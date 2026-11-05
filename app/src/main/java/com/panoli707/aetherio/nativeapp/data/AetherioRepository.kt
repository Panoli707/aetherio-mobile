package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AetherioRepository(
    private val readAccessToken: String = EmbeddedTmdbToken.value,
    client: OkHttpClient = metadataHttpClient(),
    private val tmdbBase: HttpUrl = (if (readAccessToken.isNotBlank()) "https://api.themoviedb.org/3"
        else "https://trkll.aetherio.workers.dev/api/tmdb").toHttpUrl(),
    private val animeRepository: AnimeSeasonRepository = AnimeSeasonRepository(client),
    private val homeCache: HomeItemsCache? = null,
    private val homeAnimeSource: HomeAnimeSource? = null
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
    private val tmdbSlots = Semaphore(4)
    private val homeSlots = Semaphore(4)
    private val homeAnime by lazy { homeAnimeSource ?: HomeAnimeRepository(client, animeRepository) }
    private val animeMatches = mutableMapOf<String, MediaItem?>()
    private val animeMatchLock = Mutex()

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
                                val parse = LoadTiming.start(LoadTiming.Stage.JSON_PARSE)
                                JSONObject(source.readUtf8()).also { parse.finish() }
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
    private suspend fun tmdb(path: String, params: Map<String, String> = emptyMap()): JSONObject {
        val queue = LoadTiming.start(LoadTiming.Stage.CATALOG_QUEUE)
        return tmdbSlots.withPermit {
            queue.finish()
            json("$tmdb/$path", mapOf("language" to "es-ES", "page" to "1") + params)
        }
    }
    private fun cineType(type: MediaType) = if (type == MediaType.MOVIE) "movie" else "series"
    private suspend fun trending(type: MediaType): List<MediaItem> = optional {
        CatalogJson.catalog(tmdb("trending/${type.path}/day"), type, CatalogSource.TMDB).also { check(it.isNotEmpty()) }
    } ?: CatalogJson.catalog(json("$cinemeta/catalog/${cineType(type)}/top.json"), type, CatalogSource.CINEMETA)
    private fun day() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private suspend fun cached(key: String): List<MediaItem>? = withContext(Dispatchers.IO) { optional { homeCache?.read(key) } }
    private suspend fun remember(key: String, items: List<MediaItem>) = withContext(Dispatchers.IO) { optional { homeCache?.write(key, items) }; Unit }

    suspend fun home(force: Boolean = false, onUpdate: suspend (HomeCatalog) -> Unit = {}): HomeCatalog = supervisorScope {
        val first = LoadTiming.start(LoadTiming.Stage.HOME_FIRST_CONTENT)
        val complete = LoadTiming.start(LoadTiming.Stage.HOME_COMPLETE)
        var firstPublished = false
        val homeContext = coroutineContext
        val today = day()
        val specs = DesktopHomeCatalog.rails()
        val sections = specs.map { spec ->
            val saved = cached("$today:${spec.id}").orEmpty()
            spec.section(saved, loading = force || saved.isEmpty())
        }.toMutableList()
        val lock = Mutex()
        fun snapshot(): HomeCatalog {
            val ready = DesktopHomeCatalog.dedupeAnime(sections.toList())
            return HomeCatalog(ready, DesktopHomeCatalog.heroes(ready, today), partial = ready.any { it.error != null })
        }
        suspend fun publish() {
            val value = snapshot()
            onUpdate(value)
            if (!firstPublished && value.sections.any { it.items.isNotEmpty() }) { first.finish(); firstPublished = true }
        }
        publish()
        // Visible starter rows first, then the anime rows that seed the carousel, then editorial rows.
        // This reorders work only: catalogue order, results and the four-request cap are unchanged.
        specs.indices.sortedBy { index -> when {
            index < 6 -> 0
            specs[index].id in setOf("mal.airing_anime", "mal.top_anime") -> 1
            else -> 2
        } }.map { index -> async {
            val spec = specs[index]
            if (!sections[index].loading) return@async
            val fetched = homeSlots.withPermit {
                // A cancelled sibling can release a permit before cancellation reaches every queued child.
                homeContext.ensureActive()
                optional { fetchRail(spec) }
            }
            ensureActive()
            lock.withLock {
                val old = sections[index]
                sections[index] = if (fetched != null && fetched.items.isNotEmpty()) fetched
                    else old.copy(loading = false, error = if (old.items.isEmpty()) "No se pudo cargar esta fila. Reintenta la actualización." else "No se pudo actualizar; se conserva el catálogo guardado.")
                if (fetched != null && fetched.items.isNotEmpty()) remember("$today:${spec.id}", fetched.items)
                publish()
            }
        } }.awaitAll()
        snapshot().also { complete.finish(!it.partial) }
    }

    private fun catalogItems(json: JSONObject, spec: HomeRail): List<MediaItem> {
        val array = json.optJSONArray("results") ?: return emptyList()
        val starter = spec.id.startsWith("tmdb.top_") || spec.id.startsWith("tmdb.trending_") || spec.id.startsWith("tmdb.discover.")
        return (0 until array.length()).mapNotNull { index ->
            val raw = array.optJSONObject(index) ?: return@mapNotNull null
            if (raw.optBoolean("adult")) return@mapNotNull null
            val japanese = CatalogJson.text(raw, "original_language").equals("ja", true) &&
                raw.optJSONArray("genre_ids")?.let { ids -> (0 until ids.length()).any { ids.optInt(it) == 16 } } == true
            if ((starter && CatalogJson.text(raw, "original_language").startsWith("zh", true)) || (spec.excludeJapaneseAnime && japanese)) return@mapNotNull null
            CatalogJson.item(raw, spec.type, CatalogSource.TMDB, spec.anime || japanese)
                ?.takeIf { it.posterUrl != null || it.backdropUrl != null }
        }.distinctBy { it.key }
    }
    private suspend fun fetchRail(spec: HomeRail): CatalogSection {
        if (spec.feed != null) {
            val entries = optional { homeAnime.fetch(spec.feed) }.orEmpty()
            val items = entries.mapNotNull { entry -> optional { resolveAnime(entry) } }
                .filter { item -> if (spec.id == "jikan.top_movies") item.type == MediaType.MOVIE else true }
                .filter { item ->
                    val start = spec.request.params["first_air_date.gte"]?.take(4)?.toIntOrNull()
                    val end = spec.request.params["first_air_date.lte"]?.take(4)?.toIntOrNull()
                    start == null || end == null || item.year.toIntOrNull()?.let { it in start..end } == true
                }.distinctBy { it.key }.take(DesktopHomeCatalog.ITEM_LIMIT)
            if (items.isNotEmpty()) return spec.section(items)
        }
        val starter = spec.id.startsWith("tmdb.top_") || spec.id.startsWith("tmdb.trending_") || spec.id.startsWith("tmdb.discover.")
        val defaults = mapOf("include_adult" to "false") + if (starter) mapOf("region" to "PE") else emptyMap()
        for (variant in listOf(spec.request) + spec.fallbacks) {
            val items = optional { catalogItems(tmdb(variant.path, defaults + variant.params), spec) }.orEmpty()
                .take(DesktopHomeCatalog.ITEM_LIMIT)
            if (items.isNotEmpty()) return spec.section(items)
        }
        if (spec.id in setOf("tmdb.trending_movie", "tmdb.trending_series")) {
            val items = CatalogJson.catalog(json("$cinemeta/catalog/${cineType(spec.type)}/top.json"), spec.type, CatalogSource.CINEMETA).take(20)
            if (items.isNotEmpty()) return spec.section(items)
        }
        return spec.section()
    }
    private suspend fun resolveAnime(entry: AnimeCatalogEntry): MediaItem? {
        val canonical = SeriesIdentity.item(entry.item)
        if (canonical.source == CatalogSource.TMDB) return canonical
        if (canonical.id != "0" && canonical.type == MediaType.SERIES) return canonical
        val key = "${entry.item.type}:${entry.malId}:${entry.titles.joinToString("|")}:${entry.item.year}"
        return animeMatchLock.withLock {
            if (animeMatches.containsKey(key)) return@withLock animeMatches[key]
            fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).lowercase(Locale.ROOT)
                .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]"), "")
            var match: MediaItem? = null
            outer@ for (type in listOf(entry.item.type, MediaType.MOVIE).distinct()) {
                for (title in entry.titles.take(2)) {
                    val params = mapOf("query" to title, "include_adult" to "false") +
                        entry.item.year.takeIf { it.matches(Regex("[0-9]{4}")) }?.let { mapOf((if (type == MediaType.MOVIE) "year" else "first_air_date_year") to it) }.orEmpty()
                    val data = optional { tmdb("search/${type.path}", params) } ?: continue
                    val raw = data.optJSONArray("results") ?: continue
                    val candidates = (0 until raw.length()).mapNotNull { raw.optJSONObject(it) }.filter {
                        CatalogJson.text(it, "original_language") == "ja" && !it.optBoolean("adult") &&
                            it.optJSONArray("genre_ids")?.let { ids -> (0 until ids.length()).any { i -> ids.optInt(i) == 16 } } == true
                    }
                    val exact = candidates.firstOrNull { candidate ->
                        listOf("title", "name", "original_title", "original_name").any { normalized(CatalogJson.text(candidate, it)) == normalized(title) }
                    }
                    val chosen = exact ?: candidates.singleOrNull()?.takeIf {
                        entry.item.year.isNotBlank() && CatalogJson.text(it, if (type == MediaType.MOVIE) "release_date" else "first_air_date").startsWith(entry.item.year)
                    }
                    if (chosen != null) {
                        match = CatalogJson.item(chosen, type, CatalogSource.TMDB, true)?.copy(title = entry.item.title)
                        if (match != null) break@outer
                    }
                }
            }
            if (animeMatches.size >= 400) animeMatches.clear()
            animeMatches[key] = match
            match
        }
    }

    suspend fun browse(id: String, force: Boolean = false): List<CatalogSection> {
        require(DesktopHomeCatalog.browseTitle(id) != null)
        val today = day()
        if (id.startsWith("platform:")) {
            val platform = DesktopHomeCatalog.platforms.first { it.id == id.removePrefix("platform:") }
            val specs = listOf(
                DesktopHomeCatalog.platformRail(platform, MediaType.SERIES, top = true, anime = true),
                DesktopHomeCatalog.platformRail(platform, MediaType.MOVIE), DesktopHomeCatalog.platformRail(platform, MediaType.SERIES),
                DesktopHomeCatalog.platformRail(platform, MediaType.MOVIE, top = true), DesktopHomeCatalog.platformRail(platform, MediaType.SERIES, top = true))
            val rows = supervisorScope { specs.map { spec -> async {
                val cacheKey = "$today:${spec.id}"
                val saved = cached(cacheKey).orEmpty()
                val fresh = if (!force && saved.isNotEmpty()) saved else optional { platformItems(platform, spec) }.orEmpty()
                val items = fresh.ifEmpty { saved }
                remember(cacheKey, items)
                spec.section(if (spec.anime) items.take(10) else items, error = if (items.isEmpty()) "No hay títulos disponibles para esta región. Puedes reintentar." else null)
            } }.awaitAll() }
            val topMovies = rows[3].copy(id = "streaming.${platform.id}.movie.top-10", title = "Top 10 Películas", items = rows[3].items.take(10), style = SectionStyle.RANKED)
            return listOf(rows[0], rows[1], rows[2], topMovies, rows[4])
        }
        val cacheKey = "$today:$id"
        if (!force) cached(cacheKey)?.let { return listOf(CatalogSection(id, DesktopHomeCatalog.browseTitle(id)!!, it)) }
        val items = if (id.startsWith("genre:")) {
            val genre = DesktopHomeCatalog.genres.first { it.id == id.removePrefix("genre:") }
            supervisorScope { (1..5).map { page -> async {
                val groups = DesktopHomeCatalog.genreRequests(genre, page).map { (type, request) -> async {
                    optional { catalogItems(tmdb(request.path, request.params), HomeRail(id, genre.title, type, request, excludeJapaneseAnime = true)) }.orEmpty()
                } }.awaitAll()
                interleave(groups[0], groups[1])
            } }.awaitAll().flatten().distinctBy { it.key } }
        } else {
            val genre = DesktopHomeCatalog.animeGenres.first { it.id == id.removePrefix("anime-genre:") }
            val feed = AnimeFeed(sort = "POPULARITY_DESC", genre = genre.id,
                jikan = HomeRequest("anime", mapOf("genres" to genre.malId.toString(), "order_by" to "members", "sort" to "desc", "sfw" to "true", "limit" to "25")))
            optional { homeAnime.fetch(feed) }.orEmpty().mapNotNull { optional { resolveAnime(it) } }.distinctBy { it.key }
        }
        remember(cacheKey, items)
        return listOf(CatalogSection(id, DesktopHomeCatalog.browseTitle(id)!!, items,
            error = if (items.isEmpty()) "No se pudo cargar este catálogo. Puedes reintentar." else null))
    }
    private suspend fun platformItems(platform: StreamingPlatform, spec: HomeRail): List<MediaItem> {
        val combined = mutableListOf<MediaItem>()
        for (request in listOf(spec.request) + spec.fallbacks) {
            val first = optional { tmdb(request.path, mapOf("include_adult" to "false") + request.params) } ?: continue
            val initial = catalogItems(first, spec)
            combined += initial
            if (platform.id != "crunchyroll" || spec.anime) {
                if (combined.isNotEmpty()) break
            } else if (initial.isNotEmpty()) {
                for (page in 2..first.optInt("total_pages", 1).coerceIn(1, 10)) {
                    val data = optional { tmdb(request.path, mapOf("include_adult" to "false") + request.params + ("page" to page.toString())) } ?: break
                    combined += catalogItems(data, spec)
                }
            }
        }
        val items = combined.distinctBy { it.key }
        val sorted = if (spec.request.params["sort_by"] == "vote_average.desc") items.sortedByDescending { it.score } else items
        return sorted.take(if (platform.id == "crunchyroll" && !spec.anime) 200 else 20)
    }
    private fun interleave(movies: List<MediaItem>, series: List<MediaItem>): List<MediaItem> = buildList {
        for (index in 0 until maxOf(movies.size, series.size)) { movies.getOrNull(index)?.let(::add); series.getOrNull(index)?.let(::add) }
    }
    suspend fun heroes(items: List<MediaItem>, onReady: (MediaItem) -> Unit = {}): List<MediaItem> = supervisorScope {
        items.take(10).map { item -> async {
            val resolved = if (item.source == CatalogSource.ANILIST) optional { animeRepository.detail(item).item } ?: item else optional {
                // Trending results omit named genres and age ratings, even when they already have a logo.
                val data = if (item.source == CatalogSource.TMDB) tmdb("${item.type.path}/${item.id}", mapOf(
                    "append_to_response" to "images,${if (item.type == MediaType.SERIES) "content_ratings" else "release_dates"}",
                    "include_image_language" to "es,en,null"))
                else requireNotNull(json("$cinemeta/meta/${cineType(item.type)}/${item.id}.json").optJSONObject("meta"))
                check(CatalogJson.text(data, "id") == item.id)
                val enriched = CatalogJson.detail(item, data).item
                if (enriched.overview.isNotBlank() || item.source != CatalogSource.TMDB) enriched
                else {
                    // Some es-ES entries have no synopsis; request English only for the missing text.
                    val fallback = optional { tmdb("${item.type.path}/${item.id}", mapOf("language" to "en-US")) }
                    enriched.copy(overview = fallback?.takeIf { CatalogJson.text(it, "id") == item.id }
                        ?.let { CatalogJson.text(it, "overview") }.orEmpty())
                }
            } ?: item
            resolved.also(onReady)
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
    /** Series-level images only. No season artwork or paginated episode/calendar queries. */
    suspend fun artwork(item: MediaItem): MediaItem {
        val root = CanonicalArtworkIdentity.root(item)
        if (root.source == CatalogSource.ANILIST) return animeRepository.artwork(root)
        val json = if (root.source == CatalogSource.TMDB) tmdb("${root.type.path}/${root.id}", mapOf(
            "append_to_response" to "images", "include_image_language" to "es,en,null"))
        else requireNotNull(json("$cinemeta/meta/${cineType(root.type)}/${root.id}.json").optJSONObject("meta"))
        check(CatalogJson.text(json, "id") == root.id)
        // Never let an old saved season image become the series fallback.
        val empty = root.copy(posterUrl = null, backdropUrl = null, portraitArtworkUrl = null,
            logoUrl = null, logoAlternatives = emptyList(), portraitAlternatives = emptyList())
        return CatalogJson.detail(empty, json).item
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
            return SeasonContent(item.copy(seasonHint = season, animeEntryId = first.animeId), episodes)
        }
        val sourceSeason = if (item.seriesNumbering && parts.isNotEmpty() && season >= 1000) season - 1000 else season
        if (item.source == CatalogSource.TMDB) {
            val json = tmdb("tv/${item.id}/season/$sourceSeason")
            return SeasonContent(item.copy(seasonHint = season),
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
