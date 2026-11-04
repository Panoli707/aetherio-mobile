package com.panoli707.aetherio.nativeapp.ui

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit network audit; normal regression runs remain deterministic and offline. */
@RunWith(AndroidJUnit4::class)
class DesktopHomeLiveTest {
    @get:Rule val compose = createComposeRule()
    private var auditCache: File? = null
    @After fun removeOwnAuditCache() {
        auditCache?.let { directory ->
            directory.listFiles().orEmpty().forEach { file -> if (file.isFile) file.delete() }
            directory.delete()
        }
    }
    @Test fun auditRealCatalogQueriesCacheAndLastRailOnTheSharedEmulator() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("manualDesktopHome") == "true")
        assumeTrue(Build.MODEL.contains("sdk", true))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun callStart(call: Call) { counts.getOrPut(call.request().url.host) { AtomicInteger() }.incrementAndGet() }
        }).build()
        val cache = File(context.cacheDir, "home-audit-${java.util.UUID.randomUUID()}").also { auditCache = it }
        val repository = AetherioRepository(client = client, homeCache = HomeCatalogCache(cache))
        val start = SystemClock.elapsedRealtime()
        var firstItemsMs: Long? = null
        val home = runBlocking { withTimeout(180_000) { repository.home(force = true) { phase ->
            if (firstItemsMs == null && phase.sections.any { it.items.isNotEmpty() }) firstItemsMs = SystemClock.elapsedRealtime() - start
        } } }
        val elapsed = SystemClock.elapsedRealtime() - start
        val reportDir = File(context.filesDir, "home-catalog-qa").apply { mkdirs() }
        val report = JSONObject().put("reference", DesktopHomeCatalog.REFERENCE).put("elapsedMs", elapsed).put("firstItemsMs", firstItemsMs)
            .put("rowsWithData", home.sections.count { it.items.isNotEmpty() })
            .put("rows", JSONArray().apply { home.sections.forEach { row -> put(JSONObject()
                .put("id", row.id).put("title", row.title).put("source", row.subtitle).put("count", row.items.size)
                .put("error", row.error).put("keys", JSONArray(row.items.map { it.key }))) } })
        File(reportDir, "audit.json").writeText(report.toString(2))
        assertEquals(44, home.sections.size)
        assertEquals(44, home.sections.map { it.id }.distinct().size)
        assertTrue("The 32 movie/series rows must have real data", home.sections.take(32).all { it.items.isNotEmpty() })
        assertTrue(home.sections.all { it.items.size <= 20 && it.items.map { m -> m.key }.distinct().size == it.items.size })
        assertTrue(home.sections.flatMap { it.items }.all { it.id != "0" && it.title.isNotBlank() })
        assertTrue(home.sections.last().items.all { it.type == MediaType.MOVIE && it.isAnime })
        var cachedRows = 0
        val cachedStart = SystemClock.elapsedRealtime()
        runBlocking { withTimeout(180_000) { repository.home { phase ->
            if (cachedRows == 0) cachedRows = phase.sections.count { it.items.isNotEmpty() }
        } } }
        val cachedMs = SystemClock.elapsedRealtime() - cachedStart
        assertTrue(cachedRows >= home.sections.count { it.items.isNotEmpty() })
        val genre = runBlocking { withTimeout(90_000) { repository.browse("genre:action-adventure") } }.single()
        assertTrue(genre.items.any { it.type == MediaType.MOVIE })
        assertTrue(genre.items.any { it.type == MediaType.SERIES })
        val platform = runBlocking { withTimeout(90_000) { repository.browse("platform:netflix") } }
        assertEquals(5, platform.size)
        assertTrue(platform.any { it.items.isNotEmpty() })
        report.put("cachedRows", cachedRows).put("cachedElapsedMs", cachedMs)
            .put("rowsWithData", home.sections.count { it.items.isNotEmpty() }).put("genreItems", genre.items.size)
            .put("callsByHost", JSONObject(counts.mapValues { it.value.get() }))
            .put("rows", JSONArray().apply { home.sections.forEach { row -> put(JSONObject()
                .put("id", row.id).put("title", row.title).put("source", row.subtitle).put("count", row.items.size)
                .put("error", row.error).put("keys", JSONArray(row.items.map { it.key }))) } })
        File(reportDir, "audit.json").writeText(report.toString(2))
        compose.setContent { AetherioTheme { Surface(color = Ink) {
            HomeScreen(AppState(sections = home.sections, heroes = home.heroes, homeLoading = false), rememberLazyListState(),
                rememberPagerState { home.heroes.size }, false, {}, {}, {})
        } } }
        compose.waitUntil(60_000) {
            compose.onAllNodesWithTag("heroArtworkLoading", true).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("heroImage", true).fetchSemanticsNodes().isNotEmpty()
        }
        File(reportDir, "home-top.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithTag("homeScroll").performScrollToNode(hasTestTag("homeSection:jikan.top_movies"))
        compose.onNodeWithText("Películas anime más populares").assertIsDisplayed()
        File(reportDir, "home-last.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap()
            .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
