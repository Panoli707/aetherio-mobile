package com.panoli707.aetherio.nativeapp.data

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HomeCatalogCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val item = MediaItem("550", MediaType.MOVIE, "Una película", posterUrl = "https://image.tmdb.org/t/p/w500/test.jpg")
    @Test fun cacheRoundTripExpiresAndRejectsClockRollback() {
        var now = 1_000L
        val cache = HomeCatalogCache(temporary.root) { now }
        cache.write("2026-11-04:row", listOf(item))
        assertEquals(listOf(item), cache.read("2026-11-04:row"))
        now += HomeCatalogCache.TTL_MS
        assertNull(cache.read("2026-11-04:row"))
        now = 0
        assertNull(cache.read("2026-11-04:row"))
    }
    @Test fun corruptOrOversizedCacheIsDisposableAndCannotEscapeItsDirectory() {
        val cache = HomeCatalogCache(temporary.root)
        cache.write("../../local.properties", listOf(item))
        assertEquals(1, temporary.root.listFiles()!!.size)
        val file = temporary.root.listFiles()!!.single()
        assertTrue(file.name.matches(Regex("[a-f0-9]{64}\\.json")))
        file.writeText("not JSON")
        assertNull(cache.read("../../local.properties"))
        java.io.RandomAccessFile(file, "rw").use { it.setLength(HomeCatalogCache.MAX_FILE_BYTES + 1) }
        assertNull(cache.read("../../local.properties"))
    }
    @Test fun cacheUpdatesAndPrunesOnlyItsOwnMetadataFiles() {
        val cache = HomeCatalogCache(temporary.root)
        val preserved = File(temporary.root, "keep.txt").apply { writeText("not managed by the cache") }
        cache.write("row", listOf(item))
        cache.write("row", listOf(item.copy(title = "Actualizada")))
        assertEquals("Actualizada", cache.read("row")!!.single().title)
        repeat(170) { cache.write("row-$it", listOf(item)) }
        assertTrue(temporary.root.listFiles()!!.count { it.extension == "json" } <= 160)
        assertEquals("not managed by the cache", preserved.readText())
    }
}
