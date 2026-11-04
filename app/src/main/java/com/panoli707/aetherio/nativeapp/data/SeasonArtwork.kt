package com.panoli707.aetherio.nativeapp.data

/** Always derive a season from the series artwork, never from the previously selected season. */
internal object SeasonArtwork {
    /** A mapped AniList entry is one coherent artwork scope, not a poster below a shared TMDB portrait. */
    fun mapped(base: MediaItem, season: Int, entry: MediaItem?, sharedTmdbSeason: Boolean,
        poster: String?, logo: String?, portrait: String?): MediaItem {
        val hasEntryArt = entry != null && listOf(entry.posterUrl, entry.backdropUrl, entry.portraitArtworkUrl).any { !it.isNullOrBlank() }
        return when {
            hasEntryArt -> apply(base, season, entry?.posterUrl, entry?.backdropUrl, entry?.logoUrl, entry?.portraitArtworkUrl)
            sharedTmdbSeason -> apply(base, season)
            else -> apply(base, season, poster = poster, logo = logo, portrait = portrait)
        }
    }

    fun apply(base: MediaItem, season: Int, poster: String? = null, backdrop: String? = null,
        logo: String? = null, portrait: String? = null): MediaItem {
        if (listOf(poster, backdrop, portrait).all { it.isNullOrBlank() }) return base.copy(seasonHint = season)
        return base.copy(posterUrl = poster, backdropUrl = backdrop, portraitArtworkUrl = portrait,
            logoUrl = logo ?: base.logoUrl, seasonHint = season)
    }
}
