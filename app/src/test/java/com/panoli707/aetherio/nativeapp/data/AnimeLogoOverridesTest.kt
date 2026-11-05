package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class AnimeLogoOverridesTest {
    private val ids = listOf("213068" to "323937", "200294" to "327765", "179876" to "306565", "205909" to "320909")

    @Test fun eachExplicitAnimeAndItsTmdbSeriesUseTheSameLocalLogoWithoutChangingAnyArtwork() {
        val paths = ids.map { (anime, tmdb) ->
            val item = MediaItem(anime, MediaType.SERIES, "Un nombre localizado", CatalogSource.ANILIST,
                posterUrl = "poster", backdropUrl = "backdrop", logoUrl = "remote", portraitArtworkUrl = "clean",
                logoAlternatives = listOf("backup"))
            val snapshot = item.copy()
            val local = ArtworkImages.logos(item).first()
            assertTrue(local.startsWith("file:///android_asset/anime-logos/"))
            assertEquals(listOf(local, "remote", "backup"), ArtworkImages.logos(item))
            assertEquals(local, ArtworkImages.logos(item.copy(id = tmdb, source = CatalogSource.TMDB)).first())
            assertEquals(snapshot, item)
            local
        }
        assertEquals(4, paths.toSet().size)
    }

    @Test fun similarlyNamedAnimeMoviesOtherSourcesAndDifferentSeasonsAreUntouched() {
        val item = MediaItem("218938", MediaType.SERIES, "Hotaru no Yomeiri", CatalogSource.ANILIST, logoUrl = "original")
        assertEquals(listOf("original"), ArtworkImages.logos(item))
        assertNull(AnimeLogoOverrides.logo(item.copy(id = "205909", type = MediaType.MOVIE)))
        assertNull(AnimeLogoOverrides.logo(item.copy(id = "205909", source = CatalogSource.CINEMETA)))
        assertNull(AnimeLogoOverrides.logo(item.copy(id = "320909", source = CatalogSource.TMDB, seasonHint = 2)))
        assertNull(AnimeLogoOverrides.logo(item.copy(id = "320909", source = CatalogSource.TMDB, animeEntryId = "218938")))
    }
}
