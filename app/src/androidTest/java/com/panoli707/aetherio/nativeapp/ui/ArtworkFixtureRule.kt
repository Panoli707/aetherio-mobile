package com.panoli707.aetherio.nativeapp.ui

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.AetherioApplication
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.net.URI

/** Deterministic local artwork for layout tests. Real selection/OCR is tested separately. */
class ArtworkFixtureRule : TestWatcher() {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AetherioApplication
    override fun starting(description: Description) {
        val choices = mutableMapOf<String, CanonicalArtwork>()
        val mutex = Mutex()
        app.artworkProviderOverride = ArtworkProvider { item -> mutex.withLock {
            choices.getOrPut(CanonicalArtworkIdentity.key(item)) {
                val url = item.portraitArtworkUrl ?: item.posterUrl ?: item.backdropUrl
                val ratio = runCatching {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(File(URI(url!!)).absolutePath, options)
                    (options.outWidth.toFloat() / options.outHeight).takeIf { it > 0 } ?: 2f / 3f
                }.getOrDefault(2f / 3f)
                CanonicalArtwork(item.title, url, ratio)
            }
        } }
    }
    override fun finished(description: Description) { app.artworkProviderOverride = null }
}
