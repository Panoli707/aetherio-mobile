package com.panoli707.aetherio.nativeapp.data

import okhttp3.HttpUrl
import org.json.JSONObject

data class DiscoverSelection(val type: String = "movie", val catalog: String = "tmdb:movie:popular", val genre: String = "")
data class DiscoverCatalog(val key: String, val name: String, val provider: String, val type: String,
    val genres: Map<String, String>, val sort: String = "popularity.desc", val minimumVotes: String? = null,
    val manifestUrl: String? = null, val catalogId: String? = null, val skipStep: Int? = null)
data class DiscoverPage(val items: List<MediaItem>, val hasMore: Boolean)
data class DiscoverState(val selection: DiscoverSelection = DiscoverSelection(), val catalogs: List<DiscoverCatalog> = DesktopDiscover.catalogs,
    val items: List<MediaItem> = emptyList(), val page: Int = 0, val hasMore: Boolean = false,
    val loading: Boolean = false, val loadingMore: Boolean = false, val error: String? = null,
    val catalogsLoading: Boolean = false, val catalogsError: String? = null)

/** Built-in queries and filters ported from desktop src/pages/Library/libraryLogic.ts. */
internal object DesktopDiscover {
    val movieGenres = linkedMapOf("Acción" to "28", "Aventura" to "12", "Animación" to "16", "Comedia" to "35",
        "Crimen" to "80", "Documental" to "99", "Drama" to "18", "Familia" to "10751", "Fantasía" to "14",
        "Historia" to "36", "Terror" to "27", "Música" to "10402", "Misterio" to "9648", "Romance" to "10749",
        "Ciencia ficción" to "878", "Película de TV" to "10770", "Suspenso" to "53", "Guerra" to "10752", "Western" to "37")
    val seriesGenres = linkedMapOf("Acción y aventura" to "10759", "Animación" to "16", "Comedia" to "35", "Crimen" to "80",
        "Documental" to "99", "Drama" to "18", "Familia" to "10751", "Infantil" to "10762", "Misterio" to "9648",
        "Noticias" to "10763", "Reality" to "10764", "Ciencia ficción y fantasía" to "10765", "Telenovela" to "10766",
        "Talk show" to "10767", "Guerra y política" to "10768", "Western" to "37")
    val animeGenres = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mystery", "Romance",
        "Sci-Fi", "Slice of Life", "Sports", "Supernatural").associateWith { it }
    val catalogs = listOf(
        DiscoverCatalog("tmdb:movie:popular", "Películas populares", "TMDB", "movie", movieGenres),
        DiscoverCatalog("tmdb:movie:top", "Películas mejor valoradas", "TMDB", "movie", movieGenres, "vote_average.desc", "300"),
        DiscoverCatalog("tmdb:series:popular", "Series populares", "TMDB", "tv", seriesGenres),
        DiscoverCatalog("tmdb:series:top", "Series mejor valoradas", "TMDB", "tv", seriesGenres, "vote_average.desc", "150"),
        DiscoverCatalog("anilist:popular", "Anime popular", "AniList", "anime", animeGenres, "POPULARITY_DESC"),
        DiscoverCatalog("anilist:trending", "Anime en tendencia", "AniList", "anime", animeGenres, "TRENDING_DESC"),
        DiscoverCatalog("anilist:top", "Anime mejor valorado", "AniList", "anime", animeGenres, "SCORE_DESC"),
        DiscoverCatalog("anilist:favorites", "Anime con más favoritos", "AniList", "anime", animeGenres, "FAVOURITES_DESC")
    )

    fun normalize(selection: DiscoverSelection, catalogs: List<DiscoverCatalog>): DiscoverSelection {
        val type = selection.type.takeIf { value -> catalogs.any { it.type == value } } ?: catalogs.firstOrNull()?.type.orEmpty()
        val catalog = catalogs.firstOrNull { it.type == type && it.key == selection.catalog } ?: catalogs.firstOrNull { it.type == type }
        return DiscoverSelection(type, catalog?.key.orEmpty(), selection.genre.takeIf { it in catalog?.genres.orEmpty() }.orEmpty())
    }

    fun request(catalog: DiscoverCatalog, page: Int, genre: String): HomeRequest {
        require(catalog.manifestUrl == null && catalog.type in setOf("movie", "tv") && page in 1..500)
        require(genre.isEmpty() || genre in catalog.genres)
        return HomeRequest("discover/${catalog.type}", mapOf("sort_by" to catalog.sort, "page" to "$page", "include_adult" to "false") +
            catalog.minimumVotes?.let { mapOf("vote_count.gte" to it) }.orEmpty() +
            catalog.genres[genre]?.let { mapOf("with_genres" to it) }.orEmpty())
    }

    fun addonCatalogs(addon: AddonConfig, json: JSONObject): List<DiscoverCatalog> {
        val array = json.optJSONArray("catalogs") ?: return emptyList()
        return (0 until minOf(array.length(), 100)).mapNotNull { i ->
            val raw = array.optJSONObject(i) ?: return@mapNotNull null
            val originalType = CatalogJson.text(raw, "type")
            val type = when (originalType) { "movie" -> "movie"; "series", "tv" -> "tv"; else -> return@mapNotNull null }
            val id = CatalogJson.text(raw, "id").takeIf { it.length in 1..256 && it !in setOf(".", "..") && it.none(Char::isISOControl) } ?: return@mapNotNull null
            val extras = raw.optJSONArray("extra")
            val required = raw.optJSONArray("extraRequired")
            val requiredNames = (0 until (required?.length() ?: 0)).mapNotNull { required?.optString(it) }.toMutableSet()
            var genres: Map<String, String> = emptyMap()
            var skip: Int? = null
            for (j in 0 until (extras?.length() ?: 0)) {
                val extra = extras?.optJSONObject(j) ?: continue
                val name = CatalogJson.text(extra, "name").lowercase()
                if (extra.optBoolean("isRequired")) requiredNames += name
                if (name == "genre") {
                    val options = extra.optJSONArray("options")
                    genres = (0 until minOf(options?.length() ?: 0, 100)).mapNotNull { (options?.opt(it) as? String)?.trim()?.takeIf { v -> v.length in 1..100 && v.none(Char::isISOControl) } }
                        .associateWith { it }
                }
                if (name == "skip") skip = extra.optInt("step", extra.optInt("skipStep", 100)).coerceIn(1, 1000)
            }
            // Unsupported required extras cannot be satisfied by this browser; never issue an invalid request.
            if (requiredNames.any { it.lowercase() !in setOf("genre", "skip") }) return@mapNotNull null
            if ("skip" in requiredNames && skip == null) skip = 100
            if ("genre" in requiredNames) return@mapNotNull null
            DiscoverCatalog("${addon.id}:$originalType:$id", CatalogJson.text(raw, "name").ifBlank { id }.take(200), addon.name,
                type, genres, manifestUrl = addon.manifestUrl, catalogId = "$originalType/$id", skipStep = skip)
        }.distinctBy { it.key }
    }

    fun addonEndpoint(catalog: DiscoverCatalog, page: Int, genre: String): HttpUrl {
        require(page in 1..500 && (genre.isEmpty() || genre in catalog.genres))
        val base = SourceProtocol.manifestUrl(requireNotNull(catalog.manifestUrl))
        val parts = requireNotNull(catalog.catalogId).split('/', limit = 2)
        require(parts.size == 2)
        val extras = buildList {
            if (genre.isNotEmpty()) add("genre" to genre)
            if (catalog.skipStep != null) add("skip" to ((page - 1) * catalog.skipStep).toString())
        }.joinToString("&") { (key, value) -> "$key=${java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")}" }
        return base.newBuilder().removePathSegment(base.pathSize - 1).addPathSegment("catalog")
            .addPathSegment(parts[0]).addPathSegment(parts[1] + if (extras.isEmpty()) ".json" else "")
            .apply { if (extras.isNotEmpty()) addEncodedPathSegment("$extras.json") }.build()
    }
}
