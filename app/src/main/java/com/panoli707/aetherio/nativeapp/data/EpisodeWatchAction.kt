package com.panoli707.aetherio.nativeapp.data

enum class EpisodeWatchScope { EPISODE, PREVIOUS, SEASON }

object EpisodeWatchRules {
    fun targets(episodes: List<EpisodeItem>, selected: EpisodeItem, scope: EpisodeWatchScope): List<EpisodeItem> =
        episodes.filter { it.season == selected.season && (scope == EpisodeWatchScope.EPISODE || it.released) && when (scope) {
            EpisodeWatchScope.EPISODE -> it.number == selected.number
            EpisodeWatchScope.PREVIOUS -> it.number < selected.number
            EpisodeWatchScope.SEASON -> true
        } }.distinctBy { it.number }.sortedBy { it.number }
}
