package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiscoverCatalogTest {
    private val addon = AddonConfig("test", "test", "Pruebas", "https://example.com/config/manifest.json", setOf(MediaType.MOVIE))

    @Test fun builtInsMatchDesktopTypesSortsAndVoteThresholds() {
        assertEquals(8, DesktopDiscover.catalogs.size)
        val movie = DesktopDiscover.catalogs.first { it.key == "tmdb:movie:top" }
        val request = DesktopDiscover.request(movie, 3, "Terror")
        assertEquals("discover/movie", request.path)
        assertEquals("vote_average.desc", request.params["sort_by"])
        assertEquals("300", request.params["vote_count.gte"])
        assertEquals("27", request.params["with_genres"])
        assertEquals("3", request.params["page"])
        assertEquals("FAVOURITES_DESC", DesktopDiscover.catalogs.last().sort)
    }

    @Test fun changingTypeOrCatalogNormalizesInvalidSelections() {
        val selection = DesktopDiscover.normalize(DiscoverSelection("tv", "tmdb:movie:top", "Terror"), DesktopDiscover.catalogs)
        assertEquals("tmdb:series:popular", selection.catalog)
        assertEquals("", selection.genre)
        assertEquals("movie", DesktopDiscover.normalize(DiscoverSelection("deleted", "bad"), DesktopDiscover.catalogs).type)
    }

    @Test fun excludesSearchAndUnsupportedRequiredExtrasButSupportsSkipAndGenres() {
        val catalogs = DesktopDiscover.addonCatalogs(addon, JSONObject("""{"catalogs":[
            {"id":"search","type":"movie","extra":[{"name":"search","isRequired":true}]},
            {"id":"query","type":"series","extraRequired":["query"]},
            {"id":"token","type":"movie","extraRequired":["token"]},
            {"id":"feed","type":"movie","name":"Feed","extra":[{"name":"genre","options":["Science Fiction"]},{"name":"skip","step":20}]}
        ]}"""))
        assertEquals(1, catalogs.size)
        val url = DesktopDiscover.addonEndpoint(catalogs.single(), 2, "Science Fiction")
        assertEquals("/config/catalog/movie/feed/genre=Science%20Fiction&skip=20.json", url.encodedPath)
        assertNull(url.query)
    }

    @Test fun noSkipAddonUsesSinglePageEndpointAndEscapesIds() {
        val catalog = DesktopDiscover.addonCatalogs(addon, JSONObject("""{"catalogs":[{"id":"a/b","type":"series"}]}""")).single()
        assertNull(catalog.skipStep)
        assertEquals("/config/catalog/series/a%2Fb.json", DesktopDiscover.addonEndpoint(catalog, 1, "").encodedPath)
    }

    @Test fun duplicateAndDotSegmentCatalogIdsCannotCrashSelectorsOrEscapeTheCatalogPath() {
        val catalogs = DesktopDiscover.addonCatalogs(addon, JSONObject("""{"catalogs":[
            {"id":"feed","type":"movie"},{"id":"feed","type":"movie"},{"id":"..","type":"movie"}
        ]}"""))
        assertEquals(1, catalogs.size)
    }

    @Test fun tmdbGenreIdsAreAvailableInLibraryWithoutFetchingEveryDetail() {
        val item = CatalogJson.item(JSONObject("""{"id":123,"title":"Prueba","genre_ids":[27,18,99999]}"""), MediaType.MOVIE, CatalogSource.TMDB)!!
        assertEquals(listOf("Terror", "Drama"), item.genres)
        assertEquals(item.genres, CatalogJson.decode(CatalogJson.encode(listOf(item))).single().genres)
    }
}
