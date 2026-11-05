package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class HomePreferencesTest {
    private val movie = MediaItem("1", MediaType.MOVIE, "Película")
    private val anime = MediaItem("2", MediaType.SERIES, "Anime", isAnime = true)
    private val sections = listOf(
        CatalogSection("mal.top_anime", "Anime", listOf(anime)),
        CatalogSection("tmdb.top_movie", "Cine", listOf(movie)),
        CatalogSection("tmdb.top_series", "Series", listOf(movie))
    )

    @Test fun preferencePrioritizesWithoutHidingAndPreservesOriginalOrderWithinGroups() {
        assertEquals(listOf("tmdb.top_movie", "tmdb.top_series", "mal.top_anime"), HomePreferences().ordered(sections).map { it.id })
        assertEquals(sections.map { it.id }, HomePreferences(content = HomeContentPreference.ANIME).ordered(sections).map { it.id })
        assertEquals(sections.map { it.id }, HomePreferences(animeFirst = true).ordered(sections).map { it.id })
        assertEquals(listOf(movie, anime), HomePreferences().visibleHeroes(listOf(anime, movie)))
    }

    @Test fun heroModesFilterInsteadOfMerelySortingAndBothRetainsBothKinds() {
        val animeMovie = anime.copy(id = "3", type = MediaType.MOVIE)
        val series = movie.copy(id = "4", type = MediaType.SERIES)
        val all = listOf(movie, anime, series, animeMovie)
        assertEquals(listOf(anime, animeMovie), HomePreferences(content = HomeContentPreference.ANIME).visibleHeroes(all))
        assertEquals(listOf(movie, series), HomePreferences(content = HomeContentPreference.MOVIES_SERIES).visibleHeroes(all))
        assertEquals(all.toSet(), HomePreferences(content = HomeContentPreference.BOTH).visibleHeroes(all).toSet())
        assertTrue(HomePreferences(content = HomeContentPreference.ANIME).visibleHeroes(listOf(movie, series)).isEmpty())
    }

    @Test fun explicitOrderSurvivesRefreshAndUnknownCatalogsJoinWithoutLosingTheirDefaultPosition() {
        val preferred = HomePreferences().move("tmdb.top_series", -1, sections)
        assertEquals(listOf("tmdb.top_series", "tmdb.top_movie", "mal.top_anime"), preferred.ordered(sections).map { it.id })
        val refreshed = sections + CatalogSection("tmdb.new", "Nuevo", listOf(movie))
        assertEquals(listOf("tmdb.top_series", "tmdb.top_movie", "mal.top_anime", "tmdb.new"), preferred.ordered(refreshed).map { it.id })
        assertEquals(preferred, preferred.move("tmdb.top_series", -1, sections))
    }

    @Test fun hidingOnlyAffectsHomeRowsAndResetRestoresDefaults() {
        val hidden = HomePreferences(hiddenCatalogIds = setOf("mal.top_anime"), horizontalPosters = true)
        assertEquals(2, hidden.ordered(sections).size)
        assertEquals(3, HomePreferences().ordered(sections).size)
        assertFalse(HomePreferences().horizontalPosters)
    }

    @Test fun hidingAndMovingRetainsHiddenCatalogPositionWhenItIsRestored() {
        val preferred = HomePreferences(hiddenCatalogIds = setOf("tmdb.top_movie"))
            .move("tmdb.top_series", -1, sections)
        assertEquals(listOf("tmdb.top_series", "mal.top_anime"), preferred.ordered(sections).map { it.id })
        assertEquals(listOf("tmdb.top_series", "tmdb.top_movie", "mal.top_anime"),
            preferred.copy(hiddenCatalogIds = emptySet()).ordered(sections).map { it.id })
    }

    @Test fun missingCatalogsDuplicateOrdersAndInvalidMovesDoNotBreakRefresh() {
        val preferred = HomePreferences(catalogOrder = listOf("missing", "tmdb.top_series", "tmdb.top_series"))
        assertEquals(3, preferred.ordered(sections + sections).size)
        assertEquals(preferred, preferred.move("missing", 1, sections))
        assertEquals(preferred, preferred.move("tmdb.top_series", 4, sections))
        assertEquals(listOf("tmdb.top_series", "mal.top_anime", "tmdb.top_movie"),
            preferred.copy(content = HomeContentPreference.ANIME).ordered(sections).map { it.id })
    }
}
