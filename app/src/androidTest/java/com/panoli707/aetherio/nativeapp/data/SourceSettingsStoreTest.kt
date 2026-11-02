package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceSettingsStoreTest {
    @Test fun cleartextPolicyIsLimitedToTheIdentifiedVideoCdn() {
        val policy = NetworkSecurityPolicy.getInstance()
        assertTrue(policy.isCleartextTrafficPermitted("usa7-as09.shegu.net"))
        listOf("api.themoviedb.org", "api.github.com", "github.com", "example.org", "shegu.net.evil.example").forEach {
            assertFalse(policy.isCleartextTrafficPermitted(it))
        }
    }

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferencesName = "source_settings_test_${UUID.randomUUID()}"
    private val context = object : ContextWrapper(targetContext) {
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            require(name == "aetherio_source_settings")
            return baseContext.getSharedPreferences(preferencesName, mode)
        }
    }
    private val preferences = targetContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    @After fun cleanUp() {
        targetContext.deleteSharedPreferences(preferencesName)
    }

    @Test fun freshPreferencesDefaultToAllFourDesktopSourcesWithoutWritingThem() {
        assertEquals(setOf("penguplay", "hdhub", "animes", "deepflix"), SourceSettingsStore(context).enabledDesktopSources)
        assertFalse(preferences.contains("desktop"))
    }

    @Test fun selectedAndEmptySetsPersistAcrossInstancesAndUnknownIdsAreFiltered() {
        val store = SourceSettingsStore(context)
        store.enabledDesktopSources = setOf("penguplay", "animes", "unknown-provider")
        assertEquals(setOf("penguplay", "animes"), preferences.getStringSet("desktop", null))
        assertEquals(setOf("penguplay", "animes"), SourceSettingsStore(context).enabledDesktopSources)

        store.enabledDesktopSources = emptySet()
        assertTrue(preferences.contains("desktop"))
        assertEquals(emptySet<String>(), SourceSettingsStore(context).enabledDesktopSources)

        store.enabledDesktopSources = setOf("unknown-provider")
        assertEquals(emptySet<String>(), preferences.getStringSet("desktop", null))
        assertEquals(emptySet<String>(), SourceSettingsStore(context).enabledDesktopSources)

        assertTrue(preferences.edit().putStringSet("desktop", setOf("hdhub", "unknown-provider")).commit())
        assertEquals(setOf("hdhub"), SourceSettingsStore(context).enabledDesktopSources)
    }

    @Test fun inputAndReturnedSetsAreIndependentSnapshotsAndCannotMutateStoredSelections() {
        val store = SourceSettingsStore(context)
        val input = mutableSetOf("penguplay", "hdhub")
        store.enabledDesktopSources = input
        input.clear()
        assertEquals(setOf("penguplay", "hdhub"), store.enabledDesktopSources)

        val snapshot = store.enabledDesktopSources
        (snapshot as MutableSet<String>).clear()
        assertEquals(setOf("penguplay", "hdhub"), store.enabledDesktopSources)
        assertEquals(setOf("penguplay", "hdhub"), SourceSettingsStore(context).enabledDesktopSources)

        val previous = store.enabledDesktopSources
        store.enabledDesktopSources = setOf("deepflix")
        assertEquals(setOf("penguplay", "hdhub"), previous)
        assertEquals(setOf("deepflix"), store.enabledDesktopSources)
    }
}
