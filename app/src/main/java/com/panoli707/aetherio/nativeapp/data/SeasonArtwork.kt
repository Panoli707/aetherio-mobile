package com.panoli707.aetherio.nativeapp.data

/** Always derive a season from the series artwork, never from the previously selected season. */
internal object SeasonArtwork {
    /** A mapped AniList entry is one coherent artwork scope, not a poster below a shared TMDB portrait. */
    fun mapped(base: MediaItem, season: Int, entry: MediaItem?, sharedTmdbSeason: Boolean,
        poster: String?, logo: String?, portrait: String?, logos: List<String> = emptyList()): MediaItem {
        val hasEntryArt = entry != null && listOf(entry.posterUrl, entry.backdropUrl, entry.portraitArtworkUrl).any { !it.isNullOrBlank() }
        return when {
            hasEntryArt -> apply(base, season, entry?.posterUrl, entry?.backdropUrl, entry?.logoUrl, entry?.portraitArtworkUrl,
                entry?.logoAlternatives.orEmpty(), entry?.posterTitlePresence ?: ArtworkTitlePresence.UNKNOWN,
                entry?.portraitTitlePresence ?: ArtworkTitlePresence.UNKNOWN, entry?.backdropTitlePresence ?: ArtworkTitlePresence.UNKNOWN)
            sharedTmdbSeason -> apply(base, season)
            else -> apply(base, season, poster = poster, logo = logo, portrait = portrait, logos = logos)
        }
    }

    fun apply(base: MediaItem, season: Int, poster: String? = null, backdrop: String? = null,
        logo: String? = null, portrait: String? = null, logos: List<String> = emptyList(),
        posterTitle: ArtworkTitlePresence = ArtworkTitlePresence.UNKNOWN,
        portraitTitle: ArtworkTitlePresence = ArtworkTitlePresence.UNKNOWN,
        backdropTitle: ArtworkTitlePresence = ArtworkTitlePresence.UNKNOWN): MediaItem {
        if (listOf(poster, backdrop, portrait).all { it.isNullOrBlank() }) return base.copy(seasonHint = season)
        return base.copy(posterUrl = poster, backdropUrl = backdrop, portraitArtworkUrl = portrait,
            logoUrl = logo?.takeIf(String::isNotBlank) ?: base.logoUrl, seasonHint = season,
            posterTitlePresence = posterTitle, portraitTitlePresence = portraitTitle, backdropTitlePresence = backdropTitle,
            logoAlternatives = (logos + listOfNotNull(base.logoUrl) + base.logoAlternatives)
                .filter { it.isNotBlank() && it != (logo?.takeIf(String::isNotBlank) ?: base.logoUrl) }.distinct().take(8))
    }
}
