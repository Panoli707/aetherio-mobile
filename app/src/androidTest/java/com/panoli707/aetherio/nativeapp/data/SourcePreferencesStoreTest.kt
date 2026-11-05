package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourcePreferencesStoreTest {
    @Test fun persistedRankingAndFavoritesSurviveRecreatingStoreWithoutTouchingProviderOptIns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SourceSettingsStore(context)
        val previous = store.loadPreferences()
        val providers = store.enabledDesktopSources
        try {
            val value = SourcePreferences(SourceLanguage.LATIN, SourceQuality.UHD, false,
                favoriteSources = setOf("a".repeat(64)), favoriteProviders = setOf("b".repeat(64)))
            store.savePreferences(value)
            assertEquals(value, SourceSettingsStore(context).loadPreferences())
            assertEquals(providers, SourceSettingsStore(context).enabledDesktopSources)
            val storage = context.getSharedPreferences("aetherio_source_settings", Context.MODE_PRIVATE)
            assertEquals(setOf("a".repeat(64)), storage.getStringSet("favorite_sources_v1", emptySet()))
        } finally { store.savePreferences(previous) }
    }

    @Test fun unknownEnumsAndMalformedFingerprintsFallBackWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SourceSettingsStore(context)
        val previous = store.loadPreferences()
        try {
            context.getSharedPreferences("aetherio_source_settings", Context.MODE_PRIVATE).edit()
                .putString("language_v1", "future-language").putString("quality_v1", "invalid")
                .putStringSet("favorite_sources_v1", setOf("https://example.org/?token=private", "c".repeat(64))).commit()
            val result = store.loadPreferences()
            assertEquals(SourceLanguage.ANY, result.language)
            assertEquals(SourceQuality.ANY, result.quality)
            assertEquals(setOf("c".repeat(64)), result.favoriteSources)
        } finally { store.savePreferences(previous) }
    }
}
