package com.panoli707.aetherio.nativeapp.data

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnimeLogoCatalogTest {
    @Test fun everyAuditedIdentityResolvesOnlyForItsTypeProviderAndSeason() {
        assertTrue(AnimeLogoCatalog.entries.isNotEmpty())
        for (entry in AnimeLogoCatalog.entries) {
            val expected = "file:///android_asset/anime-logos/${entry.asset}"
            val anime = MediaItem(entry.animeId, entry.type, "Nombre arbitrario", CatalogSource.ANILIST)
            assertEquals(expected, AnimeLogoCatalog.logo(anime))
            val tmdb = anime.copy(id = entry.tmdbId, source = CatalogSource.TMDB,
                seasonHint = entry.season, animeEntryId = entry.animeId)
            assertEquals(expected, AnimeLogoCatalog.logo(tmdb))
            assertNull(AnimeLogoCatalog.logo(tmdb.copy(animeEntryId = "999999999")))
            assertNull(AnimeLogoCatalog.logo(anime.copy(source = CatalogSource.CINEMETA)))
            assertNull(AnimeLogoCatalog.logo(anime.copy(type = if (entry.type == MediaType.MOVIE) MediaType.SERIES else MediaType.MOVIE)))
            if (entry.type == MediaType.SERIES) assertNull(AnimeLogoCatalog.logo(tmdb.copy(seasonHint = 999)))
            assertEquals(expected, ArtworkImages.logos(anime.copy(logoUrl = "https://example.org/fallback.png")).first())
        }
        assertNull(AnimeLogoCatalog.logo(MediaItem("999999999", MediaType.SERIES, "Cowboy Bebop", CatalogSource.ANILIST)))
    }

    @Test fun bundledFilesMatchTheirProvenanceHashesAndNoAssetIsOrphaned() {
        val raw = checkNotNull(javaClass.classLoader?.getResourceAsStream("anime-logo-catalog.json"))
            .bufferedReader().use { JSONObject(it.readText()) }.getJSONArray("logos")
        for (index in 0 until raw.length()) {
            val row = raw.getJSONObject(index)
            val file = File("src/main/assets/anime-logos", row.getString("asset"))
            assertTrue(file.isFile)
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(row.getString("sha256"), hash)
            assertTrue(row.getString("source").startsWith("https://"))
        }
        assertEquals(AnimeLogoCatalog.entries.map { it.asset }.toSet(),
            File("src/main/assets/anime-logos").listFiles()!!.filter { it.name.startsWith("anilist_") }.map { it.name }.toSet())
    }
}
