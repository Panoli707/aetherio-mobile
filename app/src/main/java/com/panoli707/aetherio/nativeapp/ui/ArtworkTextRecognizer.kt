package com.panoli707.aetherio.nativeapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.panoli707.aetherio.nativeapp.data.ArtworkTextBox
import com.panoli707.aetherio.nativeapp.data.ArtworkTextPlacement
import com.panoli707.aetherio.nativeapp.data.ArtworkTitlePresence
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Bundled open-source Latin/Japanese OCR, serialized off the UI thread. No network or telemetry. */
internal object ArtworkTextRecognizer {
    data class Reading(val text: String, val complete: Boolean, val placement: ArtworkTitlePresence)
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, Reading>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Reading>?) = size > 128
    }

    suspend fun analyze(context: Context, url: String, image: ImageBitmap): ArtworkTitlePresence = read(context, url, image).placement

    suspend fun read(context: Context, url: String, image: ImageBitmap): Reading = withContext(Dispatchers.Default) {
        mutex.withLock {
            cache[url]?.let { return@withLock it }
            val original = image.asAndroidBitmap()
            val scale = minOf(1f, 960f / maxOf(original.width, original.height))
            val bitmap = Bitmap.createScaledBitmap(original, (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1), true)
            var engine: TessBaseAPI? = null
            try {
                // Cache files are derived only from APK assets, never downloaded or user-owned data.
                val root = File(context.cacheDir, "artwork-ocr-fast-410")
                val data = File(root, "tessdata")
                check(data.isDirectory || data.mkdirs())
                for (language in listOf("eng", "jpn")) {
                    val target = File(data, "$language.traineddata")
                    context.assets.open("artwork-ocr/$language.traineddata").use { input ->
                        if (target.length() != input.available().toLong()) target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                val deadline = SystemClock.elapsedRealtime() + 3_000
                val job = coroutineContext[kotlinx.coroutines.Job]
                engine = TessBaseAPI { if (SystemClock.elapsedRealtime() > deadline || job?.isActive == false) engine?.stop() }
                check(engine.init(root.absolutePath, "eng+jpn", TessBaseAPI.OEM_LSTM_ONLY))
                engine.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
                engine.setImage(bitmap)
                engine.getHOCRText(0) // Interruptible inference, unlike getUTF8Text().
                ensureActive()
                if (SystemClock.elapsedRealtime() > deadline) return@withLock Reading("", false, ArtworkTitlePresence.UNKNOWN)
                val boxes = mutableListOf<ArtworkTextBox>()
                val words = mutableListOf<String>()
                val iterator = engine.resultIterator
                if (iterator != null) try {
                    val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
                    iterator.begin()
                    do {
                        val rect = iterator.getBoundingBox(level)
                        val text = iterator.getUTF8Text(level).orEmpty()
                        if (iterator.confidence(level) >= 25f) words.add(text)
                        if (iterator.confidence(level) >= 35f) boxes.add(ArtworkTextBox(rect[0].toFloat() / bitmap.width,
                            rect[1].toFloat() / bitmap.height, rect[2].toFloat() / bitmap.width,
                            rect[3].toFloat() / bitmap.height, text.count { it.isLetterOrDigit() }))
                    } while (iterator.next(level))
                } finally { iterator.delete() }
                Reading(words.joinToString(" "), true, ArtworkTextPlacement.classify(boxes)).also { cache[url] = it }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Reading("", false, ArtworkTitlePresence.UNKNOWN) }
            catch (_: LinkageError) { Reading("", false, ArtworkTitlePresence.UNKNOWN) }
            finally { engine?.recycle(); if (bitmap !== original) bitmap.recycle() }
        }
    }
}
