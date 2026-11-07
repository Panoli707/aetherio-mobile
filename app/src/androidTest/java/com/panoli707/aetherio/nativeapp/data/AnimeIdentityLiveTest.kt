package com.panoli707.aetherio.nativeapp.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Public metadata/stream listings only: never downloads media, starts torrents or touches accounts. */
class AnimeIdentityLiveTest {
    @Test fun iceAnimeKeepsBothSeasonsAndUsesImdbEpisodeNumbersAcrossPublicProviders() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveAnime0211") == "true")
        val repository = AetherioRepository()
        val detail = repository.detail(MediaItem("213805", MediaType.SERIES, "Koori no Jouheki 2nd Season", CatalogSource.ANILIST, isAnime = true))
        assertEquals("283428", detail.item.id)
        assertEquals("tt39123061", detail.item.imdbId)
        assertTrue(detail.seasons.map { it.number }.containsAll(listOf(1, 2)))
        assertTrue(detail.item.imdbNumbering)
        val second = repository.seasonContent(detail.item, 2)
        assertEquals("2026-10-01", second.episodes.first().airedOn)
        assertEquals(1, second.episodes.first().number)
        val query = repository.providerQuery(SourceQuery(detail.item, 2, 1), false)
        assertEquals(2, query.season); assertEquals(1, query.episode)
        assertTrue(SourceProtocol.endpoint(ProviderHub.config, query).toString().endsWith("tt39123061:2:1.json"))
        val directory = ProviderHubDirectoryRepository().refresh()
        val configs = listOf(ProviderHub.config) + directory.providers.filter { it.enabled }.mapNotNull { it.config }
        val reports = configs.map { config -> async {
            try {
                val result = SourceRepository(requestTimeoutMs = 55_000).sources(config, query)
                "${config.name}: ${result.sources.size} fuentes compatibles, ${result.unsupportedCount} rechazadas"
            } catch (_: Exception) { "${config.name}: consulta fallida" }
        } }.awaitAll()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("stream", "Rompiendo el hielo: dos temporadas; T2 E1 usa tt39123061:2:1\n" + reports.joinToString("\n") + "\n")
        })
    }
}
