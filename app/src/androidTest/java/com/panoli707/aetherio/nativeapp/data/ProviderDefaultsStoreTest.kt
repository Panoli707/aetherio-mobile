package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderDefaultsStoreTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = UUID.randomUUID().toString()
    private val names = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "${base.packageName}.hubtest.$id"
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = "hub_test_${id}_$name"
            names.add(isolated)
            return base.getSharedPreferences(isolated, mode)
        }
    }
    @After fun cleanFixtures() {
        names.forEach(base::deleteSharedPreferences)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "${context.packageName}.sources.v1"
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    @Test fun freshOfflineRegistrationIsDurableWrittenOnceAndNeedsNoManifestOrUserConfiguration() {
        val preferences = context.getSharedPreferences("aetherio_provider_defaults", 0)
        val changes = AtomicInteger()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changes.incrementAndGet() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            assertEquals(listOf(ProviderHub.config), ProviderDefaultsStore(context).initialize(emptyList()))
            assertTrue(preferences.getBoolean("hub_registered_v1", false))
            val afterFirst = preferences.all.toMap()
            repeat(3) { assertEquals(listOf(ProviderHub.config), ProviderDefaultsStore(context).initialize(listOf(ProviderHub.config))) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(afterFirst, preferences.all)
            assertEquals(1, changes.get())
            assertEquals(DesktopSources.knownIds, SourceSettingsStore(context).enabledDesktopSources)
            assertTrue(AddonStore(context).load().isEmpty()) // Managed registration never overwrites user storage.
        } finally { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    @Test fun migrationPreservesEncryptedCustomAddonsLegacyPreferencesAndUnrelatedData() {
        val custom = SourceProtocol.manifest("""{"id":"user.addon","name":"Private user addon","resources":["stream"],"types":["series"]}""",
            SourceProtocol.manifestUrl("https://example.org/private/manifest.json?token=synthetic-test-token"))
        AddonStore(context).save(listOf(custom))
        val encrypted = context.getSharedPreferences("${context.packageName}.sources.v1", 0)
        val before = encrypted.all.toMap()
        val settings = context.getSharedPreferences("aetherio_source_settings", 0)
        settings.edit().putStringSet("desktop", DesktopSources.knownIds).commit()
        val unrelated = context.getSharedPreferences("unrelated_fixture", 0)
        unrelated.edit().putString("account", "synthetic-account").putString("history", "synthetic-history").putString("library", "synthetic-library").commit()
        val otherBefore = unrelated.all.toMap()
        val migrated = ProviderDefaultsStore(context).initialize(AddonStore(context).load())
        assertEquals(listOf(ProviderHub.config, custom), migrated)
        assertEquals(migrated, ProviderDefaultsStore(context).initialize(AddonStore(context).load()))
        assertEquals(before, encrypted.all)
        assertEquals(otherBefore, unrelated.all)
        assertEquals(DesktopSources.knownIds, settings.getStringSet("desktop", null))
        assertEquals(DesktopSources.knownIds, SourceSettingsStore(context).enabledDesktopSources)
        SourceSettingsStore(context).enabledDesktopSources = setOf("animeav1")
        assertEquals(setOf("animeav1"), SourceSettingsStore(context).enabledDesktopSources)
        assertEquals(DesktopSources.knownIds, settings.getStringSet("desktop", null))
    }
}
