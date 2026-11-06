package com.panoli707.aetherio.nativeapp.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ProviderHubSyncTest {
    @Test fun productionMetadataLoadsAllPublishedProvidersFromAnEmptyStoreAndSurvivesRestart() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveHub0203") == "true")
        // In-memory empty stores simulate a reinstall without clearing any installed app data.
        var directoryCache: String? = null
        var manifestCache: String? = null
        val repository = ProviderHubDirectoryRepository(
            readCache = { directoryCache }, writeCache = { directoryCache = it },
            readBrandingCache = { manifestCache }, writeBrandingCache = { manifestCache = it })
        assertTrue(repository.state.value.providers.isEmpty())
        assertTrue(repository.state.value.branding.isEmpty())
        val loaded = repository.refresh()
        assertFalse("Both public metadata documents must load", loaded.stale)
        val published = JSONObject(requireNotNull(directoryCache)).getJSONArray("addons")
        val runtime = JSONObject(requireNotNull(manifestCache)).getJSONArray("scrapers")
        assertEquals(published.length(), loaded.providers.size)
        assertEquals(runtime.length(), loaded.branding.size)
        for (i in 0 until published.length()) assertTrue(loaded.providers.any { it.id == published.getJSONObject(i).getString("id") })
        for (i in 0 until runtime.length()) assertTrue(loaded.branding.any { it.id == runtime.getJSONObject(i).getString("id") })
        val restarted = ProviderHubDirectoryRepository(readCache = { directoryCache }, readBrandingCache = { manifestCache })
        assertEquals(loaded.providers, restarted.state.value.providers)
        assertEquals(loaded.branding, restarted.state.value.branding)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("stream", "Hub public metadata verified from empty stores: ${loaded.providers.size} addons, ${loaded.branding.size} runtime providers; every published ID retained\n")
        })
    }
}
