package com.panoli707.aetherio.nativeapp.data

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withTimeoutOrNull

data class DesktopSourceProvider(val id: String, val name: String, val manifestUrl: String? = null)

object DesktopSources {
    // Public bundled configurations from Aetherio PC's addonStore.ts, not personal credentials.
    val providers = listOf(
        DesktopSourceProvider("penguplay", "PenguPlay", "https://trkll.aetherio.workers.dev/api/penguplay/manifest.json"),
        DesktopSourceProvider("hdhub", "HdHub", "https://hdhub.thevolecitor.qzz.io/eyJ0b3Jib3giOiJ1bnNldCIsInF1YWxpdGllcyI6IjIxNjBwLDEwODBwLDcyMHAiLCJzb3J0IjoiZGVzYyIsImNvbnRlbnQiOiJsYXRpbiIsImNhdGFsb2dzIjoiIn0/manifest.json"),
        DesktopSourceProvider("animes", "AnimES", "https://pigamer37.alwaysdata.net/onAirCatalogs=animeflv%2Canimeav1%2Chenaojara%2Ctioanime%2Canimejara%2Cjkanime/manifest.json"),
        DesktopSourceProvider("deepflix", "DeepFlix", "https://mg.homelabx.qzz.io/manifest.json"),
        DesktopSourceProvider("animeav1", "AnimeAV1")
    )
    val knownIds: Set<String> get() = providers.map { it.id }.toSet()
    val defaultIds: Set<String> get() = knownIds
}

internal class DesktopSourceRepository(
    private val repository: SourceRepository = SourceRepository(),
    private val animeAv1: AnimeAv1Repository = AnimeAv1Repository()
) {
    private val configurations = ConcurrentHashMap<String, AddonConfig>()

    suspend fun sources(id: String, query: SourceQuery): SourceResult {
        val provider = DesktopSources.providers.firstOrNull { it.id == id }
            ?: throw IOException("Proveedor de escritorio no valido")
        if (provider.id == "animeav1") return animeAv1.sources(query)
        val manifestUrl = provider.manifestUrl ?: throw IOException("Proveedor de escritorio no valido")
        return withTimeoutOrNull(30_000) {
            val config = configurations[id] ?: repository.configure(manifestUrl).also { configurations[id] = it }
            repository.sources(config, query)
        } ?: throw IOException("El proveedor de escritorio no respondio a tiempo")
    }
}
