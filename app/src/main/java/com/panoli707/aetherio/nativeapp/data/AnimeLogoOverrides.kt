package com.panoli707.aetherio.nativeapp.data

/** Four explicitly verified identities; never match by title or replace any cover artwork. */
internal object AnimeLogoOverrides {
    private data class Logo(val aniListId: String, val tmdbId: String, val asset: String)
    private val logos = listOf(
        Logo("213068", "323937", "dandivine"),
        Logo("200294", "327765", "chojo"),
        Logo("179876", "306565", "dark_machine"),
        Logo("205909", "320909", "firefly_wedding")
    )

    fun logo(item: MediaItem): String? {
        if (item.type != MediaType.SERIES) return null
        val match = when (item.source) {
            CatalogSource.ANILIST -> logos.firstOrNull { it.aniListId == item.id }
            CatalogSource.TMDB -> {
                logos.firstOrNull { it.tmdbId == item.id }
            }
            else -> null
        }
        return match?.let { "file:///android_asset/anime-logos/${it.asset}.png" }
    }
}
