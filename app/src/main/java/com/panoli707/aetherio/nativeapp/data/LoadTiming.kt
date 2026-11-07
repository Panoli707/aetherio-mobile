package com.panoli707.aetherio.nativeapp.data

/** Local, opt-in diagnostics. Labels are closed enums, never URLs, titles, IDs or credentials. */
internal object LoadTiming {
    enum class Stage { HOME_FIRST_CONTENT, HOME_COMPLETE, DETAIL_METADATA, DETAIL_USABLE, SEASON,
        IMAGE_REQUEST, IMAGE_LOAD, IMAGE_DECODE, IMAGE_MEMORY, IMAGE_DISK, IMAGE_NETWORK, IMAGE_BITMAP, IMAGE_FIRST_READY, IMAGE_OCR, CATALOG_QUEUE, JSON_PARSE,
        NETWORK_DNS, NETWORK_CONNECT, NETWORK_HEADERS, NETWORK_BODY, NETWORK_TOTAL, ARTWORK_SELECT, YOUTUBE_RESOLVE }
    enum class Origin { TMDB, ANILIST, JIKAN, CINEMETA, ARTWORK, OTHER }
    data class Sample(val stage: Stage, val elapsedMs: Long, val success: Boolean, val origin: Origin?)
    @Volatile var observer: ((Sample) -> Unit)? = null

    class Span internal constructor(private val stage: Stage, private val origin: Origin?) {
        private val start = System.nanoTime()
        fun finish(success: Boolean = true) {
            val sample = Sample(stage, ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(0), success, origin)
            // Diagnostics must not change the result or cancellation of a load.
            runCatching { observer?.invoke(sample) }
        }
    }
    fun start(stage: Stage, origin: Origin? = null) = Span(stage, origin)
    suspend fun <T> measure(stage: Stage, block: suspend () -> T): T {
        val span = start(stage)
        try { return block().also { span.finish() } }
        catch (error: Throwable) { span.finish(false); throw error }
    }
}
