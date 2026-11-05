package com.panoli707.aetherio.nativeapp.data

enum class MediaType(val path: String, val label: String) {
    MOVIE("movie", "Película"),
    SERIES("tv", "Serie")
}

enum class CatalogSource { TMDB, CINEMETA, ANILIST }
enum class SectionStyle { POSTERS, RANKED, FEATURED }
/** Image content, not a guess based on a provider field or its language tag. */
enum class ArtworkTitlePresence { UNKNOWN, EMBEDDED, ABSENT, TOP, BOTTOM, CENTER }

data class MediaItem(
    val id: String,
    val type: MediaType,
    val title: String,
    val source: CatalogSource = CatalogSource.TMDB,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val overview: String = "",
    val year: String = "",
    val score: Double = 0.0,
    val genres: List<String> = emptyList(),
    val isAnime: Boolean = false,
    val imdbId: String? = null,
    val episodeCount: Int? = null,
    val animeSeasonLabel: String = "",
    val seasonHint: Int? = null,
    val animeEntryId: String? = null,
    val seriesNumbering: Boolean = false,
    val voteCount: Int? = null,
    val certification: String? = null,
    val portraitArtworkUrl: String? = null,
    val posterTitlePresence: ArtworkTitlePresence = ArtworkTitlePresence.UNKNOWN,
    val portraitTitlePresence: ArtworkTitlePresence = ArtworkTitlePresence.UNKNOWN,
    val backdropTitlePresence: ArtworkTitlePresence = ArtworkTitlePresence.UNKNOWN,
    val logoAlternatives: List<String> = emptyList(),
    val portraitAlternatives: List<String> = emptyList()
) {
    val key: String get() = "${source.name}:${type.path}:$id"
}

data class CatalogSection(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    val style: SectionStyle = SectionStyle.POSTERS,
    val subtitle: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

data class CastMember(val name: String, val role: String, val imageUrl: String? = null)
data class SeasonInfo(val number: Int, val name: String, val episodeCount: Int, val posterUrl: String? = null)
data class EpisodeItem(
    val number: Int,
    val season: Int,
    val title: String,
    val overview: String = "",
    val imageUrl: String? = null,
    val released: Boolean = true,
    val airedOn: String = "",
    val runtimeMinutes: Int? = null
)

data class MediaDetail(
    val item: MediaItem,
    val runtimeMinutes: Int? = null,
    val cast: List<CastMember> = emptyList(),
    val seasons: List<SeasonInfo> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val selectedSeason: Int? = null,
    val trailers: List<MediaTrailer> = emptyList(),
    val facts: List<DetailFact> = emptyList(),
    val availability: List<WatchRegion> = emptyList(),
    val reviews: List<MediaReview> = emptyList(),
    val related: List<MediaItem> = emptyList(),
    val voteCount: Int? = null,
    val certification: String? = null,
    val artworkBase: MediaItem? = null
)

enum class TrailerKind { YOUTUBE, DIRECT }
data class MediaTrailer(val id: String, val title: String, val url: String, val thumbnailUrl: String,
    val kind: TrailerKind = TrailerKind.YOUTUBE)
data class DetailFact(val label: String, val value: String)
data class WatchPlatform(val id: Int, val name: String, val logoUrl: String?, val offer: String)
data class WatchRegion(val country: String, val link: String?, val platforms: List<WatchPlatform>)
data class MediaReview(val id: String, val author: String, val content: String, val publishedOn: String, val url: String?)

data class HomeCatalog(val sections: List<CatalogSection>, val heroes: List<MediaItem>, val partial: Boolean = false)

data class CatalogBrowseState(val id: String? = null, val title: String = "", val sections: List<CatalogSection> = emptyList(),
    val loading: Boolean = false, val error: String? = null)

data class AppState(
    val sections: List<CatalogSection> = emptyList(),
    val homePreferences: HomePreferences = HomePreferences(),
    val homePreferencesSaving: Boolean = false,
    val homePreferencesError: String? = null,
    val heroes: List<MediaItem> = emptyList(),
    val homeLoading: Boolean = true,
    val homeError: String? = null,
    val browse: CatalogBrowseState = CatalogBrowseState(),
    val selectedItem: MediaItem? = null,
    val detail: MediaDetail? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val episodesLoading: Boolean = false,
    val pendingSeason: Int? = null,
    val searchQuery: String = "",
    val searchResults: List<MediaItem> = emptyList(),
    val searchLoading: Boolean = false,
    val searchError: String? = null,
    val savedItems: List<MediaItem> = emptyList(),
    val watchProgress: List<WatchProgress> = emptyList(),
    val watchingAnime: List<MediaItem> = emptyList(),
    val animeSeason: AnimeSeasonState = AnimeSeasonState(),
    val animeToday: AnimeTodayState = AnimeTodayState(),
    val profileName: String = "Usuario",
    val addons: List<AddonConfig> = listOf(ProviderHub.config),
    val hubProviders: List<HubProvider> = emptyList(),
    val hubRuntimeProviders: List<HubBranding> = emptyList(),
    val hubDirectoryLoading: Boolean = false,
    val hubDirectoryStale: Boolean = false,
    val enabledDesktopSources: Set<String> = DesktopSources.defaultIds,
    val desktopSourceErrors: Set<String> = emptySet(),
    val addonsLoading: Boolean = true,
    val addonSaving: Boolean = false,
    val addonOperation: Long = 0,
    val addonError: String? = null,
    val sourceQuery: SourceQuery? = null,
    val sourcePreferences: SourcePreferences = SourcePreferences(),
    val sourcesLoading: Boolean = false,
    val sources: List<StreamSource> = emptyList(),
    val sourceError: String? = null
)
