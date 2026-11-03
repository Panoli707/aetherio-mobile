package com.panoli707.aetherio.nativeapp.data

import java.io.IOException
import java.io.InterruptedIOException
import javax.net.ssl.SSLException

internal enum class AnimeCalendarFailure { CONNECTION, TIMEOUT, RATE_LIMIT, HTTP, GRAPHQL, INVALID_RESPONSE }

internal class AnimeCalendarException(
    val kind: AnimeCalendarFailure,
    val status: Int? = null,
    val retryAtMs: Long? = null,
    cause: Throwable? = null
) : IOException("No se pudo consultar el calendario de anime", cause) {
    val diagnostic: String get() = "kind=$kind http=${status ?: 0} cause=${cause?.javaClass?.simpleName ?: "none"}"
    val recoverable: Boolean get() = kind == AnimeCalendarFailure.TIMEOUT ||
        (kind == AnimeCalendarFailure.CONNECTION && cause !is SSLException) ||
        kind == AnimeCalendarFailure.RATE_LIMIT || (kind == AnimeCalendarFailure.HTTP && status in listOf(500, 502, 503, 504))

    fun userMessage(nowMs: Long = System.currentTimeMillis()): String = when (kind) {
        AnimeCalendarFailure.CONNECTION -> "No se pudo conectar con AniList. Revisa tu conexión e inténtalo de nuevo."
        AnimeCalendarFailure.TIMEOUT -> "AniList está tardando demasiado en responder. Inténtalo de nuevo."
        AnimeCalendarFailure.RATE_LIMIT -> {
            val remaining = ((retryAtMs ?: nowMs) - nowMs).coerceAtLeast(0)
            val seconds = remaining / 1000 + if (remaining % 1000 > 0) 1 else 0
            if (seconds > 0) "AniList ha limitado las consultas. Espera $seconds segundos antes de reintentar."
            else "AniList ha limitado las consultas. Espera un momento antes de reintentar."
        }
        AnimeCalendarFailure.HTTP -> "AniList no está disponible en este momento. Inténtalo de nuevo más tarde."
        AnimeCalendarFailure.GRAPHQL, AnimeCalendarFailure.INVALID_RESPONSE ->
            "AniList no ha devuelto un calendario válido. Inténtalo de nuevo más tarde."
    }

    companion object {
        fun from(error: Throwable): AnimeCalendarException = when (error) {
            is AnimeCalendarException -> error
            is InterruptedIOException -> AnimeCalendarException(AnimeCalendarFailure.TIMEOUT, cause = error)
            is IOException -> AnimeCalendarException(AnimeCalendarFailure.CONNECTION, cause = error)
            else -> AnimeCalendarException(AnimeCalendarFailure.INVALID_RESPONSE, cause = error)
        }
    }
}

internal data class AnimeRequestPolicy(
    val connectTimeoutMs: Long = 8_000,
    val readTimeoutMs: Long = 25_000,
    val callTimeoutMs: Long = 35_000,
    val operationTimeoutMs: Long = 90_000,
    val retries: Int = 2,
    val retryDelayMs: Long = 1_000,
    val maxAutomaticWaitMs: Long = 8_000
) {
    init {
        require(connectTimeoutMs > 0 && readTimeoutMs > 0 && callTimeoutMs > 0 && operationTimeoutMs > 0)
        require(retries in 0..2 && retryDelayMs >= 0 && maxAutomaticWaitMs >= 0)
    }
}
