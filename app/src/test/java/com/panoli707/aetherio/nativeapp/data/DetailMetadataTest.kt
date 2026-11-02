package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DetailMetadataTest {
    private val series = MediaItem("105248", MediaType.SERIES, "Serie de prueba")

    @Test fun trailersOnlyUseRealYoutubeKeysAndDoNotTurnEmbedsIntoPlayableMedia() {
        val json = JSONObject("""{"videos":{"results":[
          {"site":"YouTube","type":"Trailer","key":"abcDE123_-x","name":"Tráiler oficial","official":true,"iso_639_1":"es"},
          {"site":"YouTube","type":"Trailer","key":"https://evil.example/video"},
          {"site":"Vimeo","type":"Trailer","key":"abcDE123_-x"},
          {"site":"YouTube","type":"Clip","key":"abcDE123_-y"}]}}""")
        val trailer = DetailMetadata.enrich(MediaDetail(series), json).trailers.single()
        assertEquals("https://www.youtube.com/watch?v=abcDE123_-x", trailer.url)
        assertEquals("https://i.ytimg.com/vi/abcDE123_-x/hqdefault.jpg", trailer.thumbnailUrl)
    }

    @Test fun externalLinksCannotOpenArbitraryHostsSessionsOrInjectedQueries() {
        val valid = listOf("https://www.youtube.com/watch?v=abcDE123_-x", "https://www.themoviedb.org/tv/105248/watch?locale=ES",
            "https://www.themoviedb.org/movie/550/watch", "https://www.themoviedb.org/review/123abc")
        valid.forEach { assertEquals(it, DetailLinks.validate(it)) }
        listOf("http://www.youtube.com/watch?v=abcDE123_-x", "https://www.youtube.com.evil.org/watch?v=abcDE123_-x",
            "https://user@www.youtube.com/watch?v=abcDE123_-x", "https://www.youtube.com/watch?v=abcDE123_-x&token=secret",
            "https://www.themoviedb.org/login", "https://www.themoviedb.org/tv/1/watch?token=secret",
            "https://www.youtube.com:8443/watch?v=abcDE123_-x", "https://www.themoviedb.org/review/abc#fragment").forEach { assertNull(DetailLinks.validate(it)) }
    }

    @Test fun availabilityUsesActualCountryAndOfferTypeAndNeverInventsDirectPlatformPlayback() {
        val json = JSONObject("""{"watch/providers":{"results":{"ES":{"link":"https://www.themoviedb.org/tv/105248/watch?locale=ES",
          "flatrate":[{"provider_id":8,"provider_name":"Netflix","logo_path":"/netflix.png"}],
          "buy":[{"provider_id":8,"provider_name":"Netflix","logo_path":"/netflix.png"}]},
          "US":{"link":"https://evil.example/watch","free":[{"provider_id":9,"provider_name":"Example"}]}}}}""")
        val regions = DetailMetadata.enrich(MediaDetail(series), json).availability
        assertEquals(listOf("ES", "US"), regions.map { it.country })
        assertEquals(listOf("Suscripción", "Compra"), regions.first().platforms.map { it.offer })
        assertNull(regions.last().link)
        assertEquals("https://image.tmdb.org/t/p/w185/netflix.png", regions.first().platforms.first().logoUrl)
    }

    @Test fun aboutReviewsAndRelatedComeFromTheResponseWithoutFakeDatesOrRecommendations() {
        val json = JSONObject("""{"status":"Returning Series","vote_count":2100,"production_countries":[{"iso_3166_1":"JP","name":"Japan"}],
          "production_companies":[{"name":"Studio real"}],"content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]},
          "reviews":{"results":[{"id":"abc","author":"Usuario real","content":"<p>Texto público</p>","created_at":"2026-10-19T01:00:00Z","url":"https://www.themoviedb.org/review/abc"}]},
          "recommendations":{"results":[{"id":2,"name":"Otra serie"},{"id":105248,"name":"La misma"}]}}""")
        val detail = DetailMetadata.enrich(MediaDetail(series), json)
        assertTrue(DetailFact("Estado", "En emisión") in detail.facts)
        assertTrue(DetailFact("Producción", "Studio real") in detail.facts)
        assertEquals(2100, detail.voteCount)
        assertEquals("TV-MA (US)", detail.certification)
        assertEquals("Texto público", detail.reviews.single().content)
        assertEquals("2026-10-19", detail.reviews.single().publishedOn)
        assertEquals("2", detail.related.single().id)
    }

    @Test fun absentMetadataProducesNoInventedTrailersProvidersOrCommentsAndMovieRatingsUseReleaseDates() {
        val empty = DetailMetadata.enrich(MediaDetail(series), JSONObject())
        assertTrue(empty.trailers.isEmpty() && empty.reviews.isEmpty() && empty.availability.isEmpty() && empty.related.isEmpty())
        val movie = series.copy(type = MediaType.MOVIE)
        val json = JSONObject("""{"release_dates":{"results":[{"iso_3166_1":"US","release_dates":[{"certification":"R"}]},{"iso_3166_1":"ES","release_dates":[{"certification":"16"}]}]}}""")
        assertEquals("16 (ES)", DetailMetadata.enrich(MediaDetail(movie), json).certification)
    }
}
