package com.panoli707.aetherio.nativeapp.data

import java.util.Calendar
import java.util.TimeZone
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnimeSeasonTest {
    private val season = AnimeSeason(2026, "FALL")
    private val now = 1793610000L
    private fun anime() = JSONObject("""{"id":195516,"title":{"romaji":"Kusuriya no Hitorigoto 3rd Season"},"season":"FALL","seasonYear":2026,"status":"RELEASING","episodes":12,"nextAiringEpisode":{"episode":6,"airingAt":1793973600}}""")

    @Test fun japaneseCalendarRollsYearAndQuarterWithoutHardcodedCurrentSeason() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, 11, 31, 16, 0) }
        assertEquals(AnimeSeason(2027, "WINTER"), AnimeSeason.current(calendar.timeInMillis))
        calendar.apply { clear(); set(2026, 2, 31, 16, 0) }
        assertEquals(AnimeSeason(2026, "SPRING"), AnimeSeason.current(calendar.timeInMillis))
    }

    @Test fun onlyActuallyReleasingNonAdultEntriesFromTheCurrentQuarterAreAccepted() {
        assertNotNull(AnimeSeasonProtocol.seasonal(anime(), season, now))
        listOf("FINISHED", "NOT_YET_RELEASED", "CANCELLED", "HIATUS").forEach {
            assertNull(AnimeSeasonProtocol.seasonal(anime().put("status", it), season, now))
        }
        assertNull(AnimeSeasonProtocol.seasonal(anime().put("seasonYear", 2004), season, now))
        assertNull(AnimeSeasonProtocol.seasonal(anime().put("season", "SUMMER"), season, now))
        assertNull(AnimeSeasonProtocol.seasonal(anime().put("isAdult", true), season, now))
    }

    @Test fun weekdayComesFromTheActualNextAiringTimestampInJapanAndUnknownIsNotGuessed() {
        val entry = AnimeSeasonProtocol.seasonal(anime(), season, now)!!
        assertEquals(AnimeWeekday.FRIDAY, entry.weekday)
        assertEquals(6, entry.nextEpisode)
        assertNull(AnimeSeasonProtocol.seasonal(anime().put("nextAiringEpisode", JSONObject.NULL), season, now)!!.weekday)
        assertNull(AnimeSeasonProtocol.seasonal(anime(), season, 1794000000)!!.weekday)
        assertNull(AnimeSeasonProtocol.seasonal(anime(), season, 1794000000)!!.nextEpisode)
    }

    @Test fun latestAiredEpisodeUsesRealPastScheduleAndIsNeverNextMinusOne() {
        val json = anime().put("airingSchedule", JSONObject("""{"nodes":[{"episode":3,"airingAt":1790949600},{"episode":8,"airingAt":1793973600},{"episode":4,"airingAt":1791554400}],"pageInfo":{"hasNextPage":false}}"""))
        val entry = AnimeSeasonProtocol.seasonal(json, season, now)!!
        assertEquals(4, entry.latestEpisode)
        assertEquals(1791554400L, entry.latestAiringAt)
        assertEquals(6, entry.nextEpisode)
        assertNull(AnimeSeasonProtocol.seasonal(anime(), season, now)!!.latestEpisode)
        json.getJSONObject("airingSchedule").getJSONObject("pageInfo").put("hasNextPage", true)
        assertNull(AnimeSeasonProtocol.seasonal(json, season, now)!!.latestEpisode)
    }

    @Test fun animeCoverUsesExtraLargeWhenAvailableAndFallsBackToLarge() {
        val json = anime().put("coverImage", JSONObject("""{"extraLarge":"https://s4.anilist.co/large.jpg","large":"https://s4.anilist.co/small.jpg"}"""))
        assertEquals("https://s4.anilist.co/large.jpg", AnimeSeasonProtocol.item(json)!!.posterUrl)
        json.getJSONObject("coverImage").put("extraLarge", JSONObject.NULL)
        assertEquals("https://s4.anilist.co/small.jpg", AnimeSeasonProtocol.item(json)!!.posterUrl)
    }

    @Test fun detailOnlyOffersEpisodesWithPastAiringTimestampsNotTheEntireAnnouncedSeason() {
        val json = anime().put("airingSchedule", JSONObject("""{"nodes":[{"episode":1,"airingAt":1790949600},{"episode":2,"airingAt":1793973600}],"pageInfo":{"hasNextPage":false}}"""))
        val detail = AnimeSeasonProtocol.detail(json, now)
        assertEquals(listOf(1), detail.episodes.map { it.number })
        assertEquals(12, detail.item.episodeCount)
        assertEquals(CatalogSource.ANILIST, detail.item.source)
        assertEquals("Otoño 2026", detail.item.animeSeasonLabel)
        assertNull(detail.item.imdbId)
    }

    @Test fun aniListItemsAndProgressRoundTripWithoutCollidingWithTmdbOrLosingOldFavorites() {
        val item = AnimeSeasonProtocol.item(anime())!!
        assertEquals(item, CatalogJson.decode(CatalogJson.encode(listOf(item))).single())
        assertNotEquals(item.key, item.copy(source = CatalogSource.TMDB).key)
        val target = WatchTarget(item, 1, 1)
        assertTrue(WatchProgressRules.valid(target))
        assertEquals(target, WatchProgressCodec.decodeTarget(WatchProgressCodec.encodeTarget(target)))
    }

    @Test fun nativeAnimeLookupUsesExactEntryTitleAndLocalEpisodesWithoutInventingTvSeasonOffsets() {
        val item = AnimeSeasonProtocol.item(anime())!!
        val match = AnimeAv1Protocol.Title(item.title, "kusuriya-no-hitorigoto-3rd-season")
        val firstSeason = AnimeAv1Protocol.Title("Kusuriya no Hitorigoto", "kusuriya-no-hitorigoto")
        val query = SourceQuery(item, 1, 3)
        assertEquals(match, AnimeAv1Protocol.selectTitle(listOf(firstSeason, match), query))
        assertEquals(3, AnimeAv1Protocol.episodeFor(query))
        assertNull(AnimeAv1Protocol.selectTitle(listOf(firstSeason), query))
        assertNull(AnimeAv1Protocol.selectTitle(listOf(match), query.copy(season = 3)))
    }

    @Test fun bulkMarksNeverCrossSeasonsOrIncludeFutureAndUnknownEpisodes() {
        val episodes = listOf(EpisodeItem(1, 1, "One"), EpisodeItem(2, 1, "Two"), EpisodeItem(3, 1, "Three"),
            EpisodeItem(4, 1, "Future", released = false), EpisodeItem(1, 2, "Other season"))
        assertEquals(listOf(1, 2), EpisodeWatchRules.targets(episodes, episodes[2], EpisodeWatchScope.PREVIOUS).map { it.number })
        assertEquals(listOf(1, 2, 3), EpisodeWatchRules.targets(episodes, episodes[2], EpisodeWatchScope.SEASON).map { it.number })
        assertEquals(listOf(episodes[2]), EpisodeWatchRules.targets(episodes, episodes[2], EpisodeWatchScope.EPISODE))
        val json = JSONObject("""{"episodes":[{"episode_number":1,"air_date":"2000-01-01"},{"episode_number":2,"air_date":"2099-01-01"},{"episode_number":3}]}""")
        assertEquals(listOf(true, false, false), CatalogJson.episodes(json, 1).map { it.released })
    }
}
