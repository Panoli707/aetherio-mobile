package com.panoli707.aetherio.nativeapp.data

internal object LibraryCatalog {
    fun all(state: AppState): List<MediaItem> = SeriesIdentity.items(
        state.watchProgress.sortedByDescending { it.updatedAt }.filter { it.positionMs > 0 || it.watched }.map { it.target.item } +
            state.watchingAnime + state.savedItems)

    fun currentAnime(state: AppState): List<MediaItem> {
        val tracked = SeriesIdentity.items(state.watchingAnime).associateBy { it.key }
        return state.animeSeason.entries.sortedByDescending { it.latestAiringAt ?: 0 }.mapNotNull { entry ->
            val current = SeriesIdentity.item(entry.item)
            val following = tracked[current.key] ?: return@mapNotNull null
            val progress = state.watchProgress.filter { matchesCurrent(current, it.target) }
            val currentFollow = (current.seasonHint == null || following.seasonHint == current.seasonHint) &&
                (current.animeEntryId == null || following.animeEntryId == current.animeEntryId)
            val priorHistory = state.watchProgress.any { it.target.item.key == current.key && (it.positionMs > 0 || it.watched) }
            if (progress.none { it.positionMs > 0 || it.watched } && !currentFollow && priorHistory) return@mapNotNull null
            val total = entry.item.episodeCount
            if (total != null && progress.filter { it.watched }.distinctBy { it.target.episode }.size >= total) return@mapNotNull null
            current
        }.distinctBy { it.key }
    }

    fun currentProgress(state: AppState, current: List<MediaItem>): List<WatchProgress> = WatchProgressRules.continuing(state.watchProgress)
        .filter { progress -> current.any { matchesCurrent(it, progress.target) } }

    fun matchesCurrent(current: MediaItem, target: WatchTarget): Boolean {
        if (current.key != target.item.key || (current.seasonHint != null && current.seasonHint != target.season)) return false
        val parts = SeriesIdentity.index.parts(current)
        val part = parts.firstOrNull { it.animeId == current.animeEntryId } ?: return true
        val start = part.offset - SeriesIdentity.index.baseOffset(parts, part.season)
        val end = current.episodeCount?.let { start + it } ?: parts.filter { it.season == part.season && it.offset > part.offset }
            .minOfOrNull { it.offset - SeriesIdentity.index.baseOffset(parts, part.season) } ?: Int.MAX_VALUE
        return target.episode != null && target.episode > start && target.episode <= end
    }
}
