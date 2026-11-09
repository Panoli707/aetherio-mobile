package com.panoli707.aetherio.nativeapp.data

import java.net.URI
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnimeTrackingTest {
    private val pending = OAuthPending(AnimeService.ANILIST, "s".repeat(96), "v".repeat(96), 1000)
    private fun callback(state: String = pending.state) = "aetherio://oauth/anilist#access_token=${"t".repeat(40)}&token_type=Bearer&expires_in=3600&state=$state"
    @Test fun mobileFlowsUseAniListImplicitAndMalPlainPkceWithoutEmbeddedSecrets() {
        val anilist = AnimeOAuth.authorize("56163", pending)
        assertTrue(anilist.startsWith("https://anilist.co/"))
        assertTrue(anilist.contains("response_type=token"))
        assertFalse(anilist.contains("redirect_uri"))
        assertEquals(setOf("client_id", "response_type", "state"), URI(anilist).rawQuery.split('&').map { it.substringBefore('=') }.toSet())
        val mal = AnimeOAuth.authorize("eaf348d964cb8409f563aeba210ba95f", pending.copy(service = AnimeService.MAL))
        assertTrue(mal.contains("response_type=code"))
        assertTrue(mal.contains("redirect_uri="))
        assertTrue(mal.contains("code_challenge=${pending.verifier}"))
        assertTrue(mal.contains("code_challenge_method=plain"))
        assertFalse(mal.contains("client_secret"))
        assertFalse(anilist.contains("client_secret"))
    }
    @Test fun validImplicitTokenAndCodeCallbacksAreParsed() {
        assertEquals("t".repeat(40), AnimeOAuth.callback(callback(), pending, 2000)["access_token"])
        val mal = pending.copy(service = AnimeService.MAL)
        assertEquals("code", AnimeOAuth.callback("aetherio://oauth/mal?code=code&state=${mal.state}", mal, 2000)["code"])
    }
    @Test fun implicitTokenIsBoundToStateAndCannotBeConfusedWithCodesOrForeignReturns() {
        val raw = callback()
        assertEquals("t".repeat(40), AnimeOAuth.callback(raw, pending, 2000)["access_token"])
        listOf(raw + "&state=${pending.state}", raw.replace("access_token=", "code="), raw.replace("token_type=Bearer&", ""),
            raw.replace(pending.state, "wrong"), raw.replace("/anilist", "/mal"), raw.replace("#", "?"),
            raw.replace("expires_in=3600", "expires_in=0")).forEach {
            assertThrows(IllegalArgumentException::class.java) { AnimeOAuth.callback(it, pending, 2000) }
        }
        assertEquals(64, AnimeOAuth.challenge(pending).length)
        assertNotEquals(AnimeOAuth.challenge(pending), AnimeOAuth.challenge(pending.copy(verifier = "different")))
    }
    @Test fun rejectsForeignRedirectsDuplicateParametersExpiredOrWrongState() {
        val invalid = listOf(callback("wrong"), callback() + "&access_token=stolen", callback().replace("/anilist", "/anilist/"),
            callback().replace("oauth", "oauth.evil"), callback().replace("aetherio:", "https:"), callback().replace("oauth/", "x@oauth/"),
            callback().replace("#", "?"), callback().replace("access_token=", "ticket="))
        invalid.forEach { raw -> assertThrows(IllegalArgumentException::class.java) { AnimeOAuth.callback(raw, pending, 2000) } }
        assertThrows(IllegalArgumentException::class.java) { AnimeOAuth.callback(callback(), pending, 602_000) }
        assertThrows(IllegalArgumentException::class.java) { AnimeOAuth.callback(callback(), pending, 0) }
    }
    @Test fun refusalNeverBecomesAConnectedSession() {
        assertThrows(IllegalArgumentException::class.java) {
            AnimeOAuth.callback("aetherio://oauth/anilist#error=access_denied&state=${pending.state}", pending, 2000)
        }
    }
    @Test fun scoreAndStatusesAreParsedUsingTheServicesActualScales() {
        val mal = AnimeTrackingApi.malEntry(JSONObject("""{"node":{"id":1,"title":"Test","num_episodes":12,"media_type":"tv"},"list_status":{"status":"on_hold","score":8,"num_episodes_watched":4}}"""))
        assertEquals(80.0, mal.score, 0.0)
        assertEquals(AnimeStatus.PAUSED, mal.status)
        assertEquals(4, mal.progress)
        assertNull(mal.aniListId)
        val ani = AnimeTrackingApi.aniEntry(JSONObject("""{"id":99,"idMal":1,"title":{"userPreferred":"Test"},"format":"MOVIE","episodes":1,"isFavourite":true}"""))
        assertTrue(ani.movie)
        assertTrue(ani.favorite)
        assertEquals(99, ani.aniListId)
        assertEquals(1, ani.malId)
    }
    @Test fun accountSnapshotRoundTripsWithoutTokensOrPlaybackData() {
        val connection = AnimeConnection(AnimeAccount(1, "Tester", "https://example.org/avatar.jpg"), listOf(AnimeEntry(2, 2, 3, "Anime",
            episodes = 12, progress = 5, score = 85.0, status = AnimeStatus.WATCHING, favorite = true)), updatedAt = 123)
        val raw = AnimeAccountCodec.encode(connection)
        assertEquals(connection, AnimeAccountCodec.decode(raw))
        assertFalse(raw.contains("access_token"))
        assertFalse(raw.contains("magnet"))
    }
    private val index = SeriesIndex(listOf(SeriesPart("101", "77", 1, 0, 1), SeriesPart("102", "77", 1, 12, 2)))
    @Test fun syncingRequiresExactEntryIdAndRespectsCoursAndNumbering() {
        val item = MediaItem("77", MediaType.SERIES, "Same title", isAnime = true, seriesNumbering = true)
        assertEquals(101 to 1, AnimeSyncRules.episode(WatchTarget(item, 1, 1), index))
        assertEquals(102 to 1, AnimeSyncRules.episode(WatchTarget(item, 2, 1), index))
        assertEquals(102 to 2, AnimeSyncRules.episode(WatchTarget(item.copy(seriesNumbering = false), 1, 14), index))
        assertNull(AnimeSyncRules.episode(WatchTarget(item.copy(id = "78"), 1, 1), index))
        assertNull(AnimeSyncRules.episode(WatchTarget(item.copy(id = "101", source = CatalogSource.ANILIST), 2, 1), index))
    }
    @Test fun skippingEpisodesDoesNotInventContiguousProgressOrCompleteAnotherCour() {
        val item = MediaItem("77", MediaType.SERIES, "Test", isAnime = true, seriesNumbering = true)
        val history = listOf(1, 2, 4, 5).map { WatchProgress(WatchTarget(item, 1, it), watched = true) }
        assertEquals(2, AnimeSyncRules.contiguous(AnimeEntry(101, 101, null, "First", episodes = 12), history, index))
        assertEquals(0, AnimeSyncRules.contiguous(AnimeEntry(102, 102, null, "Second", episodes = 12), history, index))
    }
    @Test fun addingAnimeChoosesPlannedWatchingOrCompletedFromLocalProgress() {
        val series = AnimeEntry(1, 1, null, "Series", episodes = 12)
        assertEquals(AnimeStatus.PLANNED, AnimeSyncRules.initialStatus(series, 0))
        assertEquals(AnimeStatus.WATCHING, AnimeSyncRules.initialStatus(series, 4))
        assertEquals(AnimeStatus.COMPLETED, AnimeSyncRules.initialStatus(series, 12))
        assertEquals(AnimeStatus.COMPLETED, AnimeSyncRules.initialStatus(series.copy(movie = true, episodes = 1), 1))
    }
}
