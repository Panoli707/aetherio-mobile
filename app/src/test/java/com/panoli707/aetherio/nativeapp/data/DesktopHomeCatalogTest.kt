package com.panoli707.aetherio.nativeapp.data

import java.text.SimpleDateFormat
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DesktopHomeCatalogTest {
    private val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-11-04")!!
    private val rails = DesktopHomeCatalog.rails(date)

    @Test fun referenceDefinesAll44DistinctRowsWithDesktopDefaultOrder() {
        assertEquals(44, rails.size)
        assertEquals(44, rails.map { it.id }.distinct().size)
        assertEquals(32, rails.count { !it.anime })
        assertEquals(12, rails.count { it.anime })
        assertEquals(listOf("tmdb.top_series", "tmdb.trending_movie", "tmdb.top_movie", "tmdb.trending_series"), rails.take(4).map { it.id })
        assertEquals(10, rails.count { it.id.startsWith("tmdb.discover.") })
        assertEquals(10, rails.count { it.id.startsWith("tmdb.home.") })
        assertEquals(8, rails.count { it.id.startsWith("tmdb.best_") })
        assertEquals(11, rails.count { it.style == SectionStyle.FEATURED })
        assertEquals(listOf("tmdb.best_movie_90s", "tmdb.best_series_90s", "tmdb.best_movie_2000s",
            "tmdb.home.movie.horror", "tmdb.home.movie.family", "tmdb.best_series_2000s"), rails.drop(14).take(6).map { it.id })
        assertEquals(listOf("mal.airing_anime", "jikan.recommendations", "jikan.top_favorites", "jikan.upcoming", "jikan.best_anime_90s",
            "mal.top_anime", "jikan.most_popular", "jikan.best_anime_2000s", "mal.trending_anime", "mal.top_rated_anime",
            "jikan.best_anime_2010s", "jikan.top_movies"), rails.takeLast(12).map { it.id })
        assertTrue(rails.zipWithNext().none { (a,b) -> a.style == SectionStyle.RANKED && b.style == SectionStyle.RANKED })
    }
    @Test fun providerRowsUsePeruThenUsAndOnlyThenNetworksOrCompanies() {
        val netflix = rails.first { it.id == "tmdb.discover.movie.streaming_netflix" }
        assertEquals("8", netflix.request.params["with_watch_providers"])
        assertEquals("PE", netflix.request.params["watch_region"])
        assertEquals("flatrate", netflix.request.params["with_watch_monetization_types"])
        assertEquals("US", netflix.fallbacks[0].params["watch_region"])
        assertEquals("213", netflix.fallbacks[1].params["with_companies"])
        val hbo = rails.first { it.id == "tmdb.discover.series.streaming_hbo_max" }
        assertEquals("1899|384", hbo.request.params["with_watch_providers"])
        assertEquals("49|3186", hbo.fallbacks[1].params["with_networks"])
    }
    @Test fun decadesGenresAndClassicThresholdsMatchTheDesktopQueries() {
        val movies = rails.first { it.id == "tmdb.best_movie_2000s" }
        assertEquals(mapOf("sort_by" to "vote_average.desc", "vote_count.gte" to "100",
            "primary_release_date.gte" to "2000-01-01", "primary_release_date.lte" to "2009-12-31"), movies.request.params)
        assertTrue(movies.excludeJapaneseAnime)
        assertEquals("878|14", rails.first { it.id == "tmdb.home.movie.science_fiction_fantasy" }.request.params["with_genres"])
        assertEquals("50", rails.first { it.id == "tmdb.home.series.classics_pre_1990" }.request.params["vote_count.gte"])
        assertEquals("1989-12-31", rails.first { it.id == "tmdb.home.series.classics_pre_1990" }.request.params["first_air_date.lte"])
    }
    @Test fun animeFeedsKeepOriginalSourcesAndDateWindowsButMovieFallbackCannotReturnTv() {
        assertEquals("2026-08-06", rails.first { it.id == "mal.airing_anime" }.request.params["air_date.gte"])
        assertEquals("2026-11-05", rails.first { it.id == "jikan.upcoming" }.request.params["air_date.gte"])
        assertEquals("recommendations/anime", rails.first { it.id == "jikan.recommendations" }.feed!!.jikan!!.path)
        assertEquals("favorite", rails.first { it.id == "jikan.top_favorites" }.feed!!.jikan!!.params["filter"])
        assertEquals("TRENDING_DESC", rails.first { it.id == "mal.trending_anime" }.feed!!.sort)
        val movies = rails.last()
        assertEquals(MediaType.MOVIE, movies.type)
        assertEquals("discover/movie", movies.request.path)
        assertEquals("movie", movies.feed!!.jikan!!.params["type"])
    }
    @Test fun allSevenPlatformsAndBothGenreExplorersHaveValidatedRoutes() {
        assertEquals(listOf("netflix", "hbo-max", "crunchyroll", "prime-video", "apple-tv-plus", "mubi", "disney-plus"), DesktopHomeCatalog.platforms.map { it.id })
        assertEquals(9, DesktopHomeCatalog.genres.size)
        assertEquals(15, DesktopHomeCatalog.animeGenres.size)
        assertEquals("Netflix", DesktopHomeCatalog.browseTitle("platform:netflix"))
        assertEquals("Anime · Ciencia ficción", DesktopHomeCatalog.browseTitle("anime-genre:Sci-Fi"))
        assertNull(DesktopHomeCatalog.browseTitle("platform:https://evil.test"))
        assertNull(DesktopHomeCatalog.browseTitle("genre:../../secrets"))
        val requests = DesktopHomeCatalog.genreRequests(DesktopHomeCatalog.genres.first { it.id == "mystery" }, 5)
        assertEquals("9648|53", requests[0].second.params["with_genres"])
        assertEquals("9648", requests[1].second.params["with_genres"])
        assertTrue(requests.all { it.second.params["page"] == "5" && it.second.params["include_adult"] == "false" })
    }
    @Test fun dailyHeroIsDeterministicBalancedAndNeverInventsArtwork() {
        fun items(anime: Boolean) = (1..15).map { MediaItem(it.toString(), if (anime) MediaType.SERIES else MediaType.MOVIE, "Título $it", isAnime = anime,
            backdropUrl = "https://image.tmdb.org/t/p/w1280/$it.jpg") }
        val sections = listOf(CatalogSection("tmdb.top_movie", "Popular", items(false)), CatalogSection("mal.airing_anime", "Emisión", items(true)))
        val first = DesktopHomeCatalog.heroes(sections, "2026-11-04")
        assertEquals(first, DesktopHomeCatalog.heroes(sections, "2026-11-04"))
        assertEquals(10, first.size)
        assertEquals(5, first.count { it.isAnime })
        assertEquals(10, first.map { it.key }.distinct().size)
        assertTrue(DesktopHomeCatalog.heroes(listOf(CatalogSection("x", "x", listOf(MediaItem("1", MediaType.MOVIE, "Sin imagen")))), "x").isEmpty())
    }
    @Test fun animeDedupeKeepsAtLeastTenAvailableItemsInsteadOfErasingLaterRows() {
        val items = (1..20).map { MediaItem(it.toString(), MediaType.SERIES, "Anime $it", isAnime = true) }
        val rows = DesktopHomeCatalog.dedupeAnime(listOf(CatalogSection("mal.airing_anime", "Emisión", items),
            CatalogSection("jikan.recommendations", "Comunidad", items)))
        assertEquals(20, rows[0].items.size)
        assertEquals(10, rows[1].items.size)
        assertEquals(10, rows[1].items.map { it.key }.distinct().size)
    }
    @Test fun jikanRecommendationsAcceptArrayAndObjectEntriesWithoutLeakingUnresolvedIds() {
        val parsed = HomeAnimeRepository.parse(JSONObject("""{"data":[{"entry":[{"mal_id":1,"title":"Uno"},{"mal_id":2,"title":"Dos"}]},{"entry":{"mal_id":1,"title":"Uno"}},{"mal_id":3,"title":"Tres","type":"Movie","year":2001}]}"""))
        assertEquals(listOf(1,2,3), parsed.map { it.malId })
        assertEquals(MediaType.MOVIE, parsed.last().item.type)
        assertTrue(parsed.all { it.item.id == "0" }) // Resolution is mandatory before these records can enter Home.
        assertTrue(HomeAnimeRepository.parse(JSONObject("""{"data":[{"mal_id":0,"title":"No válido"}]}""")).isEmpty())
    }
}
