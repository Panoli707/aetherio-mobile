package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HomePreferencesStoreTest {
    @Test fun preferencesPersistAndResetWithoutTouchingUserStorage() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "design_test_${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(ignored: String, mode: Int) = target.getSharedPreferences(name, mode)
        }
        try {
            val store = HomePreferencesStore(isolated)
            assertEquals(HomePreferences(), store.load())
            val value = HomePreferences(HomeContentPreference.ANIME, true, true,
                listOf("tmdb.top_series", "mal.top_anime"), setOf("tmdb.top_movie"))
            assertTrue(store.save(value))
            assertEquals(value, HomePreferencesStore(isolated).load())
            assertTrue(store.save(HomePreferences()))
            assertEquals(HomePreferences(), HomePreferencesStore(isolated).load())
            target.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("content", "UNKNOWN").commit()
            assertEquals(HomeContentPreference.BOTH, store.load().content)
        } finally {
            target.deleteSharedPreferences(name)
        }
    }
}
