package com.panoli707.aetherio.nativeapp.player

data class PlayerTrackOption(val id: String, val label: String, val selected: Boolean)

data class VideoUiState(
    val title: String = "Reproductor",
    val subtitle: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val seekable: Boolean = false,
    val live: Boolean = false,
    val videoHeight: Int = 0,
    val speed: Float = 1f,
    val error: String? = null,
    val animeEpisode: Boolean = false,
    val opening: OpeningInterval? = null,
    val audioTracks: List<PlayerTrackOption> = emptyList(),
    val subtitleTracks: List<PlayerTrackOption> = emptyList(),
    val ending: OpeningInterval? = null
)

enum class VideoAction { TOGGLE_PLAY, REWIND, FORWARD, RETRY, FIND_SOURCES, CLOSE, ROTATE, SKIP_OPENING, SKIP_ENDING }
