package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatalogJsonTest {
    @Test fun tmdbSearchKeepsNamespacesAndIgnoresPeopleAndMalformedEntries() {
        val items = CatalogJson.search(JSONObject("""{"results":[
            {"id":550,"media_type":"movie","title":"Fight Club","release_date":"1999-10-15","poster_path":"/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg","vote_average":8.4},
            {"id":550,"media_type":"tv","name":null,"original_name":"Title","overview":null,"vote_average":"NaN"},
            {"id":819,"media_type":"person","name":"Edward Norton"},
            {"id":"bad","media_type":"movie","title":"Invalid"},null]}"""))
        assertEquals(2, items.size)
        assertNotEquals(items[0].key, items[1].key)
        assertEquals("1999", items[0].year)
        assertEquals("https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg", items[0].posterUrl)
        assertEquals("", items[1].overview)
        assertEquals(0.0, items[1].score, 0.0)
        assertTrue(CatalogJson.catalog(JSONObject("{}"), MediaType.MOVIE, CatalogSource.TMDB).isEmpty())
    }

    @Test fun imagePolicyAndLogoLanguagePriorityRejectUnsafeValues() {
        listOf("http://example.org/a.jpg", "file:///a.jpg", "content://images/a", "https://user:pass@example.org/a", "https://@example.org/a", "//example.org/a", "null")
            .forEach { assertNull(CatalogJson.image(it)) }
        assertEquals("https://example.org/a.jpg", CatalogJson.image("https://example.org/a.jpg"))
        val logos = JSONObject("""{"logos":[{"iso_639_1":null,"file_path":"/neutral.png"},
            {"iso_639_1":"en","file_path":"/english.png"},{"iso_639_1":"es","file_path":"/spanish.png"}]}""")
        assertEquals("https://image.tmdb.org/t/p/w500/spanish.png", CatalogJson.logo(logos))
        val bad = JSONObject("""{"id":550,"title":"Fight Club","poster_path":"https://example.org/a.jpg"}""")
        assertNull(CatalogJson.item(bad, MediaType.MOVIE, CatalogSource.TMDB)?.posterUrl)
    }

    @Test fun tmdbDetailMergesMissingMetadataAndSelectsFirstRegularSeason() {
        val base = MediaItem("1396", MediaType.SERIES, "Breaking Bad", overview = "Existing overview", posterUrl = "https://example.org/poster.jpg")
        val detail = CatalogJson.detail(base, JSONObject("""{"id":1396,"overview":"","poster_path":null,"episode_run_time":[0,49],
            "seasons":[{"season_number":2,"name":"Season 2","episode_count":13},{"season_number":0},{"season_number":1,"episode_count":7}],
            "credits":{"cast":[{"name":"Bryan Cranston","character":"Walter White","profile_path":"/profile.jpg"},{}]}}"""))
        assertEquals(base.title, detail.item.title)
        assertEquals(base.overview, detail.item.overview)
        assertEquals(base.posterUrl, detail.item.posterUrl)
        assertEquals(49, detail.runtimeMinutes)
        assertEquals(listOf(1, 2), detail.seasons.map { it.number })
        assertEquals(1, detail.selectedSeason)
        assertEquals("Walter White", detail.cast.single().role)
        val episodes = CatalogJson.episodes(JSONObject("""{"episodes":[{"episode_number":2,"name":"Cat's in the Bag..."},
            {"episode_number":1,"name":"Pilot","still_path":"/pilot.jpg"},{"episode_number":0},null]}"""), 1)
        assertEquals(listOf("Pilot", "Cat's in the Bag..."), episodes.map { it.title })
    }

    @Test fun cinemetaKeepsCoversAndParsesRealVideoNamesAndSeasonGroups() {
        val catalog = JSONObject("""{"metas":[{"id":"tt0903747","name":"Breaking Bad","poster":"https://images.metahub.space/poster/small/tt0903747/img",
            "releaseInfo":"2008-2013","imdbRating":"9.5"}]}""")
        val base = CatalogJson.catalog(catalog, MediaType.SERIES, CatalogSource.CINEMETA).single()
        val meta = JSONObject("""{"meta":{"id":"tt0903747","name":"Breaking Bad","runtime":"49 min","cast":["Bryan Cranston","Aaron Paul"],
            "videos":[{"season":2,"episode":1,"title":"Seven Thirty-Seven"},
            {"season":1,"episode":2,"name":"Cat's in the Bag..."},{"season":1,"episode":1,"name":"Pilot","description":"Chemistry teacher"},
            {"season":0,"episode":1,"name":"Special"},{"title":"Trailer"}]}}""").getJSONObject("meta")
        val detail = CatalogJson.detail(base, meta)
        assertEquals(CatalogSource.CINEMETA, detail.item.source)
        assertEquals("2008", detail.item.year)
        assertEquals(base.posterUrl, detail.item.posterUrl)
        assertEquals(listOf(2, 1), detail.seasons.map { it.episodeCount })
        assertEquals(listOf("Pilot", "Cat's in the Bag..."), detail.episodes.map { it.title })
        assertEquals("Chemistry teacher", detail.episodes.first().overview)
        assertEquals("Seven Thirty-Seven", CatalogJson.episodes(meta, 2, true).single().title)
        assertTrue(CatalogJson.detail(base, JSONObject("""{"id":"tt0903747","name":"Breaking Bad"}""")).episodes.isEmpty())
    }

    @Test fun savedItemsRoundTripWithoutVideoFieldsOrNamespaceCollisions() {
        val movie = MediaItem("550", MediaType.MOVIE, "Fight Club", posterUrl = "https://example.org/poster.jpg", genres = listOf("Drama"))
        val series = movie.copy(type = MediaType.SERIES, isAnime = true)
        val cine = movie.copy(id = "tt0137523", source = CatalogSource.CINEMETA)
        val items = listOf(cine, series, movie)
        val encoded = CatalogJson.encode(items)
        assertEquals(items, CatalogJson.decode(encoded))
        assertFalse(encoded.contains("streams"))
        assertEquals(3, CatalogJson.decode(CatalogJson.encode(items + movie)).size)
        assertNull(CatalogJson.decode(CatalogJson.encode(listOf(movie.copy(posterUrl = "http://example.org/a")))).single().posterUrl)
        assertTrue(CatalogJson.decode("broken").isEmpty())
        assertTrue(CatalogJson.decode("""[{"id":"../invalid","name":"x","source":"TMDB","type":"MOVIE"}]""").isEmpty())
    }
}
