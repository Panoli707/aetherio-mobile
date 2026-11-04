package com.panoli707.aetherio.nativeapp.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class HomeRequest(val path: String, val params: Map<String, String> = emptyMap())
internal data class AnimeFeed(val sort: String? = null, val airing: Boolean = false, val jikan: HomeRequest? = null, val genre: String? = null)
internal data class HomeRail(
    val id: String, val title: String, val type: MediaType, val request: HomeRequest,
    val style: SectionStyle = SectionStyle.POSTERS, val anime: Boolean = false,
    val feed: AnimeFeed? = null, val excludeJapaneseAnime: Boolean = false,
    val fallbacks: List<HomeRequest> = emptyList()
) {
    fun section(items: List<MediaItem> = emptyList(), loading: Boolean = false, error: String? = null) =
        CatalogSection(id, title, items, style, loading = loading, error = error)
}
internal data class StreamingPlatform(val id: String, val name: String, val providers: String, val networks: String, val companies: String)
internal data class CatalogGenre(val id: String, val title: String, val movies: String, val series: String)
internal data class AnimeGenre(val id: String, val title: String, val malId: Int)

/** Native query contract, referenced against trklll/aetherio 53a9276 (not a frozen list of titles). */
internal object DesktopHomeCatalog {
    const val REFERENCE = "53a927670f1f8c34282351f8a6ca62c679f6320e"
    const val ITEM_LIMIT = 20
    val platforms = listOf(
        StreamingPlatform("netflix", "Netflix", "8", "213", "213"),
        StreamingPlatform("hbo-max", "HBO Max", "1899|384", "49|3186", "174|3268"),
        StreamingPlatform("crunchyroll", "Crunchyroll", "283", "", ""),
        StreamingPlatform("prime-video", "Prime Video", "9", "1024", "1024"),
        StreamingPlatform("apple-tv-plus", "Apple TV+", "350", "2552", "2552"),
        StreamingPlatform("mubi", "MUBI", "11", "", ""),
        StreamingPlatform("disney-plus", "Disney+", "337", "2739", "2|6125")
    )
    val genres = listOf(
        CatalogGenre("action-adventure", "Acción y aventura", "28|12", "10759"),
        CatalogGenre("comedy", "Comedia", "35", "35"), CatalogGenre("crime", "Crimen", "80", "80"),
        CatalogGenre("documentary", "Documental", "99", "99"), CatalogGenre("drama", "Drama", "18", "18"),
        CatalogGenre("family", "Familia", "10751", "10751"), CatalogGenre("mystery", "Misterio y suspenso", "9648|53", "9648"),
        CatalogGenre("science-fiction", "Ciencia ficción y fantasía", "878|14", "10765"), CatalogGenre("western", "Western", "37", "37")
    )
    val animeGenres = listOf(
        AnimeGenre("Action", "Acción", 1), AnimeGenre("Adventure", "Aventura", 2), AnimeGenre("Comedy", "Comedia", 4),
        AnimeGenre("Drama", "Drama", 8), AnimeGenre("Fantasy", "Fantasía", 10), AnimeGenre("Horror", "Horror", 14),
        AnimeGenre("Mecha", "Mecha", 18), AnimeGenre("Music", "Música", 19), AnimeGenre("Psychological", "Psicológico", 40),
        AnimeGenre("Romance", "Romance", 22), AnimeGenre("Sci-Fi", "Ciencia ficción", 24),
        AnimeGenre("Slice of Life", "Slice of Life", 36), AnimeGenre("Sports", "Deportes", 30),
        AnimeGenre("Supernatural", "Sobrenatural", 37), AnimeGenre("Thriller", "Suspenso", 41)
    )
    private fun discover(type: MediaType, params: Map<String, String>) = HomeRequest("discover/${type.path}", params)
    private fun providers(platform: StreamingPlatform, region: String) = mapOf(
        "sort_by" to "popularity.desc", "region" to region, "watch_region" to region,
        "with_watch_monetization_types" to "flatrate", "with_watch_providers" to platform.providers)

    private val base = listOf(
        HomeRail("tmdb.top_series", "Series populares", MediaType.SERIES, HomeRequest("tv/popular")),
        HomeRail("tmdb.trending_movie", "Top Películas", MediaType.MOVIE, HomeRequest("trending/movie/day"), SectionStyle.RANKED),
        HomeRail("tmdb.top_movie", "Películas populares", MediaType.MOVIE, HomeRequest("movie/popular")),
        HomeRail("tmdb.trending_series", "Top Series", MediaType.SERIES, HomeRequest("trending/tv/day"), SectionStyle.RANKED)
    ) + listOf("netflix", "hbo-max", "disney-plus", "prime-video", "apple-tv-plus").flatMap { id ->
        val platform = platforms.first { it.id == id }
        val pcId = when (id) { "hbo-max" -> "hbo_max"; "disney-plus" -> "disney"; "prime-video" -> "prime_video"; "apple-tv-plus" -> "apple_tv"; else -> id }
        MediaType.entries.map { type ->
            val movie = type == MediaType.MOVIE
            HomeRail("tmdb.discover.${if (movie) "movie" else "series"}.streaming_$pcId", "${platform.name}: ${if (movie) "Películas" else "Series"}",
                type, discover(type, providers(platform, "PE")), fallbacks = listOf(
                    discover(type, providers(platform, "US")), discover(type, mapOf("sort_by" to "popularity.desc",
                        (if (movie) "with_companies" else "with_networks") to if (movie) platform.companies else platform.networks))))
        }
    }
    private fun theme(id: String, title: String, type: MediaType, genre: String, votes: String) =
        HomeRail("tmdb.home.$id", title, type, discover(type, mapOf("sort_by" to "popularity.desc", "with_genres" to genre, "vote_count.gte" to votes)), excludeJapaneseAnime = true)
    private val themes = listOf(
        theme("movie.horror", "Películas de terror", MediaType.MOVIE, "27", "50"),
        theme("movie.family", "Películas familiares", MediaType.MOVIE, "10751", "50"),
        theme("series.comedy", "Series de comedia", MediaType.SERIES, "35", "100"),
        theme("series.drama", "Series de drama", MediaType.SERIES, "18", "100"),
        theme("movie.science_fiction_fantasy", "Películas de ciencia ficción y fantasía", MediaType.MOVIE, "878|14", "100"),
        theme("movie.romance", "Películas románticas", MediaType.MOVIE, "10749", "50"),
        theme("series.documentary", "Series documentales", MediaType.SERIES, "99", "20"),
        theme("series.action_adventure", "Series de acción y aventura", MediaType.SERIES, "10759", "50")
    ) + MediaType.entries.map { type ->
        val movie = type == MediaType.MOVIE
        HomeRail("tmdb.home.${if (movie) "movie" else "series"}.classics_pre_1990",
            if (movie) "Clásicos del cine anteriores a 1990" else "Series clásicas anteriores a 1990", type,
            discover(type, mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to if (movie) "100" else "50",
                (if (movie) "primary_release_date.lte" else "first_air_date.lte") to "1989-12-31")), excludeJapaneseAnime = true)
    }
    private val decades = listOf(1990, 2000, 2010, 2020).flatMap { start -> MediaType.entries.map { type ->
        val movie = type == MediaType.MOVIE
        val suffix = if (start == 1990) "90s" else "${start}s"
        val dates = if (movie) "primary_release_date" else "first_air_date"
        HomeRail("tmdb.best_${if (movie) "movie" else "series"}_$suffix", "Las mejores ${if (movie) "películas" else "series"} de los ${if (start == 1990) "90" else start}",
            type, discover(type, mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "100", "$dates.gte" to "$start-01-01", "$dates.lte" to "${start + 9}-12-31")),
            SectionStyle.FEATURED, excludeJapaneseAnime = true)
    } }
    private val editorialOrder = listOf(
        "tmdb.best_movie_90s", "tmdb.best_series_90s", "tmdb.best_movie_2000s", "tmdb.home.movie.horror", "tmdb.home.movie.family",
        "tmdb.best_series_2000s", "tmdb.home.series.comedy", "tmdb.home.series.drama", "tmdb.best_movie_2010s",
        "tmdb.home.movie.science_fiction_fantasy", "tmdb.home.movie.romance", "tmdb.best_series_2010s",
        "tmdb.home.series.documentary", "tmdb.home.series.action_adventure", "tmdb.best_movie_2020s",
        "tmdb.home.movie.classics_pre_1990", "tmdb.home.series.classics_pre_1990", "tmdb.best_series_2020s")

    fun animeRails(today: Date = Date()): List<HomeRail> {
        fun date(offset: Int) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(today.time + offset * 86_400_000L))
        fun anime(id: String, title: String, params: Map<String, String>, feed: AnimeFeed, style: SectionStyle = SectionStyle.POSTERS) =
            HomeRail(id, title, MediaType.SERIES, discover(MediaType.SERIES, mapOf("with_genres" to "16", "with_original_language" to "ja") + params), style, true, feed)
        fun jikan(path: String, params: Map<String, String> = emptyMap()) = AnimeFeed(jikan = HomeRequest(path, params + ("limit" to "25")))
        fun decade(start: Int): HomeRail = anime("jikan.best_anime_${if (start == 1990) "90s" else "${start}s"}",
            "Los mejores animes de ${if (start == 1990) "los 90's" else "los $start"}", mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "50",
                "first_air_date.gte" to "$start-01-01", "first_air_date.lte" to "${start + 9}-12-31"),
            jikan("anime", mapOf("start_date" to "$start-01-01", "end_date" to "${start + 9}-12-31", "order_by" to "score", "sort" to "desc", "min_score" to "7", "sfw" to "true")), SectionStyle.FEATURED)
        return listOf(
            anime("mal.airing_anime", "Anime en emisión", mapOf("sort_by" to "popularity.desc", "air_date.gte" to date(-90), "air_date.lte" to date(90)), AnimeFeed("POPULARITY_DESC", true)),
            anime("jikan.recommendations", "Anime recomendado por la comunidad", mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "200"), jikan("recommendations/anime")),
            anime("jikan.top_favorites", "Los animes más queridos del momento", mapOf("sort_by" to "vote_count.desc"), jikan("top/anime", mapOf("filter" to "favorite"))),
            anime("jikan.upcoming", "Próximos animes a estrenar", mapOf("sort_by" to "popularity.desc", "air_date.gte" to date(1), "air_date.lte" to date(180)), jikan("seasons/upcoming", mapOf("sfw" to "true"))),
            decade(1990),
            anime("mal.top_anime", "Los reyes del anime", mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "200"), AnimeFeed("POPULARITY_DESC"), SectionStyle.RANKED),
            anime("jikan.most_popular", "Anime popular", mapOf("sort_by" to "vote_count.desc", "vote_count.gte" to "100"), jikan("top/anime", mapOf("filter" to "bypopularity"))),
            decade(2000),
            anime("mal.trending_anime", "Animes en tendencia", mapOf("sort_by" to "popularity.desc"), AnimeFeed("TRENDING_DESC"), SectionStyle.RANKED),
            anime("mal.top_rated_anime", "Los animes mejor valorados", mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "200"), AnimeFeed("SCORE_DESC")),
            decade(2010),
            HomeRail("jikan.top_movies", "Películas anime más populares", MediaType.MOVIE,
                discover(MediaType.MOVIE, mapOf("with_genres" to "16", "with_original_language" to "ja", "sort_by" to "popularity.desc")),
                SectionStyle.RANKED, true, jikan("top/anime", mapOf("type" to "movie", "filter" to "bypopularity")))
        )
    }
    fun rails(today: Date = Date()): List<HomeRail> = base + editorialOrder.map { id -> (themes + decades).first { it.id == id } } + animeRails(today)
    fun rail(id: String) = rails().firstOrNull { it.id == id }
    fun browseTitle(id: String): String? = when {
        id.startsWith("platform:") -> platforms.firstOrNull { it.id == id.removePrefix("platform:") }?.name
        id.startsWith("genre:") -> genres.firstOrNull { it.id == id.removePrefix("genre:") }?.title
        id.startsWith("anime-genre:") -> animeGenres.firstOrNull { it.id == id.removePrefix("anime-genre:") }?.let { "Anime · ${it.title}" }
        else -> null
    }
    fun genreRequests(genre: CatalogGenre, page: Int) = MediaType.entries.map { type -> type to HomeRequest("discover/${type.path}",
        mapOf("sort_by" to "popularity.desc", "vote_count.gte" to "20", "include_adult" to "false", "page" to page.toString(), "with_genres" to if (type == MediaType.MOVIE) genre.movies else genre.series)) }
    fun platformRail(platform: StreamingPlatform, type: MediaType, top: Boolean = false, anime: Boolean = false): HomeRail {
        val movie = type == MediaType.MOVIE
        val params = mapOf("sort_by" to if (top) "vote_average.desc" else "popularity.desc", "with_watch_providers" to platform.providers,
            "with_watch_monetization_types" to "flatrate") +
            (if (top) mapOf("vote_count.gte" to if (movie || anime) "100" else "50") else emptyMap()) +
            (if (anime) mapOf("with_genres" to "16", "with_original_language" to "ja") else emptyMap())
        return HomeRail("streaming.${platform.id}.${if (anime) "anime" else if (movie) "movie" else "series"}.${if (anime) "top-10" else if (top) "top-rated" else "popular"}",
            if (anime) "Top 10 Anime" else "${if (movie) "Películas" else "Series"} ${if (top) "mejor valoradas" else "populares"}", type,
            discover(type, params + mapOf("region" to "PE", "watch_region" to "PE")), if (anime) SectionStyle.RANKED else SectionStyle.POSTERS,
            anime, excludeJapaneseAnime = platform.id != "crunchyroll" && !anime,
            fallbacks = listOf(discover(type, params + mapOf("region" to "US", "watch_region" to "US"))))
    }
    fun heroes(sections: List<CatalogSection>, day: String): List<MediaItem> {
        val base = sections.take(6).flatMap { it.items.take(8) }
        val anime = sections.filter { it.id in setOf("mal.airing_anime", "mal.top_anime") }.flatMap { it.items.take(7) }
        val candidates = (base + anime).distinctBy { it.key }.filter { it.backdropUrl != null || it.posterUrl != null }
            .sortedBy { "$day|${if (it.isAnime) "anime" else if (it.type == MediaType.MOVIE) "movie" else "series"}|tmdb:${it.id}".hashCode() }
        return candidates.filterNot { it.isAnime }.take(5) + candidates.filter { it.isAnime }.take(5)
    }
    fun dedupeAnime(sections: List<CatalogSection>): List<CatalogSection> {
        val seen = mutableSetOf<String>()
        return sections.map { section ->
            if (!section.id.startsWith("mal.") && !section.id.startsWith("jikan.")) section
            else if (section.style == SectionStyle.FEATURED) section.copy(items = section.items.distinctBy { it.key }.sortedByDescending { it.score })
            else {
                val unique = section.items.distinctBy { it.key }
                val fresh = unique.filter { it.key !in seen }
                val items = (fresh + unique.filter { it.key in seen }.take((10 - fresh.size).coerceAtLeast(0))).take(ITEM_LIMIT)
                seen += items.map { it.key }
                section.copy(items = items)
            }
        }
    }
}
