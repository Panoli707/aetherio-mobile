package com.panoli707.aetherio.nativeapp.data

/** Always derive a season from the series artwork, never from the previously selected season. */
internal object SeasonArtwork {
    /** A mapped AniList entry is one coherent artwork scope, not a poster below a shared TMDB portrait. */
    fun mapped(base: MediaItem, season: Int, entry: MediaItem?, sharedTmdbSeason: Boolean,
        poster: String?, logo: String?, portrait: String?, logos: List<String> = emptyList()): MediaItem {
        val hasEntryArt = entry != null && listOf(entry.posterUrl, entry.portraitArtworkUrl).any { !it.isNullOrBlank() }
        return when {
            hasEntryArt -> apply(base, season, entry?.posterUrl, entry?.backdropUrl,
                entry?.logoUrl ?: logo?.takeUnless { sharedTmdbSeason },
                entry?.portraitArtworkUrl ?: portrait?.takeUnless { sharedTmdbSeason },
                entry?.logoAlternatives.orEmpty() + if (sharedTmdbSeason) emptyList() else logos,
                entry?.posterTitlePresence ?: ArtworkTitlePresence.UNKNOWN,
                entry?.portraitTitlePresence?.takeIf { entry.portraitArtworkUrl != null } ?: ArtworkTitlePresence.UNKNOWN,
                entry?.backdropTitlePresence ?: ArtworkTitlePresence.UNKNOWN)
            sharedTmdbSeason -> apply(base, season, backdrop = entry?.backdropUrl)
            else -> apply(base, season, poster = poster, logo = logo, portrait = portrait, logos = logos, backdrop = entry?.backdropUrl)
        }
    }

    fun apply(base: MediaItem, season: Int, poster: String? = null, backdrop: String? = null,
        logo: String? = null, portrait: String? = null, logos: List<String> = emptyList(),
        posterTitle: ArtworkTitlePresence = ArtworkTitlePresence.UNKNOWN,
        portraitTitle: ArtworkTitlePresence = ArtworkTitlePresence.UNKNOWN,
        backdropTitle: ArtworkTitlePresence = ArtworkTitlePresence.UNKNOWN): MediaItem {
        if (listOf(poster, backdrop, portrait).all { it.isNullOrBlank() }) return base.copy(seasonHint = season)
        val hasSeasonPortrait = listOf(poster, portrait).any { !it.isNullOrBlank() }
        return base.copy(posterUrl = if (hasSeasonPortrait) poster else base.posterUrl,
            backdropUrl = backdrop, portraitArtworkUrl = if (hasSeasonPortrait) portrait else base.portraitArtworkUrl,
            logoUrl = logo?.takeIf(String::isNotBlank) ?: base.logoUrl.takeUnless { hasSeasonPortrait && season != 1 }, seasonHint = season,
            // A seasonal poster is a complete printed cover. Do not put a series-wide title on top.
            posterTitlePresence = if (poster != null) posterTitle.takeUnless { it == ArtworkTitlePresence.UNKNOWN }
                ?: ArtworkTitlePresence.EMBEDDED else if (hasSeasonPortrait) posterTitle else base.posterTitlePresence,
            portraitTitlePresence = if (hasSeasonPortrait) portraitTitle else base.portraitTitlePresence,
            backdropTitlePresence = backdropTitle,
            logoAlternatives = (logos + if (hasSeasonPortrait && season != 1) emptyList() else listOfNotNull(base.logoUrl) + base.logoAlternatives)
                .filter { it.isNotBlank() && it != (logo?.takeIf(String::isNotBlank) ?: base.logoUrl) }.distinct().take(8))
    }
}
