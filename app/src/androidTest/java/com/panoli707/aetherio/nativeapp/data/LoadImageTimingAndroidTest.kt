package com.panoli707.aetherio.nativeapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class LoadImageTimingAndroidTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun failedLoadsIncludeElapsedTimeAndDoNotExposeTheUrl() = runBlocking {
        val samples = CopyOnWriteArrayList<LoadTiming.Sample>()
        val previous = LoadTiming.observer
        val loader = ImageLoader.Builder(context).eventListenerFactory { LoadImageTiming() }.components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    delay(60)
                    return ErrorResult(null, chain.request, IOException("synthetic private signature"))
                }
            })
        }.build()
        try {
            LoadTiming.observer = { samples.add(it) }
            loader.execute(ImageRequest.Builder(context).data("https://fixture.invalid/private?secret=fixture").build())
            val result = samples.single { it.stage == LoadTiming.Stage.IMAGE_LOAD }
            assertFalse(result.success)
            assertTrue(result.elapsedMs >= 50)
            assertFalse(samples.toString().contains("private"))
            assertFalse(samples.toString().contains("secret"))
        } finally { LoadTiming.observer = previous; loader.shutdown() }
    }

    @Test fun cancellationCompletesTheRequestMetric() = runBlocking {
        val samples = CopyOnWriteArrayList<LoadTiming.Sample>()
        val previous = LoadTiming.observer
        val started = CompletableDeferred<Unit>()
        val loader = ImageLoader.Builder(context).eventListenerFactory { LoadImageTiming() }.components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    started.complete(Unit)
                    awaitCancellation()
                }
            })
        }.build()
        try {
            LoadTiming.observer = { samples.add(it) }
            val job = launch { loader.execute(ImageRequest.Builder(context).data("https://fixture.invalid/cancel").build()) }
            withTimeout(5_000) { started.await() }
            job.cancelAndJoin()
            assertFalse(samples.single { it.stage == LoadTiming.Stage.IMAGE_LOAD }.success)
        } finally { LoadTiming.observer = previous; loader.shutdown() }
    }
}
