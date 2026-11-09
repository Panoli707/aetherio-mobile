package com.panoli707.aetherio.nativeapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, read-only metadata checks on the configured physical device. */
@RunWith(AndroidJUnit4::class)
class WatchingCalendarLiveTest {
    private fun enabled() = assumeTrue(InstrumentationRegistry.getArguments().getString("manualWatchingCalendar") == "true")

    @Test fun animeRangeReturnsRealScheduledEventsOnTheDevice() = runBlocking {
        enabled()
        val range = WatchingDates.request(WatchingCategory.ANIME, WatchingDates.date(System.currentTimeMillis(), AnimeSeason.japan), false)
        val responseDiagnostic = java.util.concurrent.atomic.AtomicReference("")
        val client = metadataHttpClient().newBuilder().addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (!response.isSuccessful) {
                val request = okio.Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
                responseDiagnostic.set("${response.peekBody(4096).string()} request=$request")
            }
            response
        }.build()
        val events = try { AnimeSeasonRepository(client).calendar(range) }
            catch (error: AnimeCalendarException) { throw AssertionError("AniList calendar: ${error.diagnostic} ${responseDiagnostic.get()}", error) }
        assertTrue("Expected confirmed events for the current Japanese week", events.isNotEmpty())
        assertTrue(events.all { it.item.isAnime && it.date >= range.start && it.date < range.end })
    }

    @Test fun movieTvFirstPageReturnsRealDatesAndPaginationOnTheDevice() = runBlocking {
        enabled()
        val range = WatchingDates.request(WatchingCategory.MOVIES_TV, WatchingDates.date(System.currentTimeMillis()), false)
        val page = AetherioRepository().watchingCalendar(range)
        assertTrue("Expected at least one release or episode in the current week", page.events.isNotEmpty())
        assertTrue(page.events.all { !it.item.isAnime && it.at == null && it.date >= range.start && it.date < range.end })
    }
}
