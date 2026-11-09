package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class MediaListStateTest {
    @Test fun statesRoundTripAndDefaultToPending() {
        val item = MediaItem("123", MediaType.MOVIE, "Película")
        val values = mapOf(item.key to MediaListState(AnimeStatus.PAUSED, 1, 80.0))
        assertEquals(values, MediaListCodec.decode(MediaListCodec.encode(values)))
        assertEquals(AnimeStatus.PLANNED, MediaListState().status)
    }

    @Test fun localStatesFilterMoviesAndSeriesWithoutAnAnimeAccount() {
        val movie = MediaItem("123", MediaType.MOVIE, "Película")
        val series = MediaItem("124", MediaType.SERIES, "Serie")
        val state = AppState(savedItems = listOf(movie, series), mediaStates = mapOf(movie.key to MediaListState(AnimeStatus.DROPPED)))
        val entries = LibraryFiltering.entries(state)
        assertEquals(listOf(movie.key), LibraryFiltering.filter(entries, LibraryFilters(status = AnimeStatus.DROPPED.name)).entries.map { it.item.key })
        assertEquals(listOf(series.key), LibraryFiltering.filter(entries, LibraryFilters(status = AnimeStatus.PLANNED.name)).entries.map { it.item.key })
        assertTrue(LibraryFiltering.filter(entries, LibraryFilters(status = AnimeStatus.COMPLETED.name)).entries.isEmpty())
    }

    @Test fun animeSeasonsUseIndependentStateKeysAndLibraryFiltersIncludeSeasonStates() {
        val anime = MediaItem("456", MediaType.SERIES, "Anime", isAnime = true)
        val first = mediaStateKey(anime, 1)
        val second = mediaStateKey(anime, 2)
        assertNotEquals(first, second)
        val states = mapOf(first to MediaListState(AnimeStatus.COMPLETED), second to MediaListState(AnimeStatus.DROPPED))
        assertEquals(states, MediaListCodec.decode(MediaListCodec.encode(states)))
        val entry = LibraryFiltering.entries(AppState(savedItems = listOf(anime), mediaStates = states))
        assertEquals(listOf(anime.key), LibraryFiltering.filter(entry, LibraryFilters(status = AnimeStatus.COMPLETED.name)).entries.map { it.item.key })
        assertEquals(listOf(anime.key), LibraryFiltering.filter(entry, LibraryFilters(status = AnimeStatus.DROPPED.name)).entries.map { it.item.key })
        val removed = LibraryFiltering.entries(AppState(savedItems = listOf(anime), mediaStates = states,
            hiddenLibraryItems = setOf(anime.key)))
        assertTrue(removed.isEmpty())
    }

    @Test fun savedAnimeDefaultsToPendingAndBecomesWatchingAfterASeasonEpisodeIsWatched() {
        val anime = MediaItem("789", MediaType.SERIES, "Anime", isAnime = true)
        val target = WatchTarget(anime, 2, 1)
        val watched = WatchProgress(target, watched = true)
        val saved = AppState(savedItems = listOf(anime))
        assertEquals(AnimeStatus.PLANNED, LibraryFiltering.entries(saved).single().status)
        val inProgress = LibraryFiltering.entries(saved.copy(watchProgress = listOf(watched))).single()
        assertTrue(inProgress.seasonStatuses.contains(AnimeStatus.WATCHING))
        assertEquals(listOf(anime.key), LibraryFiltering.filter(listOf(inProgress),
            LibraryFilters(status = AnimeStatus.WATCHING.name)).entries.map { it.item.key })
        val explicit = mapOf(mediaStateKey(anime, 2) to MediaListState(AnimeStatus.COMPLETED))
        assertEquals(AnimeStatus.COMPLETED, effectiveMediaListState(anime, 2, explicit, listOf(watched), true).status)
    }
}
