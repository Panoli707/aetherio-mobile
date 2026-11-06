package com.panoli707.aetherio.nativeapp.player

import android.content.Context
import android.content.Intent
import com.panoli707.aetherio.nativeapp.data.DetailLinks
import com.panoli707.aetherio.nativeapp.data.MediaTrailer
import com.panoli707.aetherio.nativeapp.data.TrailerKind

/** Direct trailers share the native player, but never episode history, torrent or source sessions. */
internal object TrailerPlayback {
    fun intent(context: Context, trailer: MediaTrailer): Intent = when (trailer.kind) {
        TrailerKind.YOUTUBE -> {
            require(TrailerEmbed.validId(trailer.id) != null)
            Intent(context, VideoActivity::class.java)
                .putExtra(VideoActivity.EXTRA_YOUTUBE_ID, trailer.id)
                .putExtra(VideoActivity.EXTRA_TITLE, trailer.title)
                .putExtra(VideoActivity.EXTRA_PROVIDER, "Tráiler · YouTube")
                .putExtra(VideoActivity.EXTRA_TRAILER, true)
        }
        TrailerKind.DIRECT -> {
            val validated = requireNotNull(DetailLinks.directTrailer(trailer.url, trailer.title))
            Intent(context, VideoActivity::class.java)
                .putExtra(VideoActivity.EXTRA_URL, validated.url)
                .putExtra(VideoActivity.EXTRA_TITLE, validated.title)
                .putExtra(VideoActivity.EXTRA_PROVIDER, "Tráiler · Aetherio")
                .putExtra(VideoActivity.EXTRA_TRAILER, true)
        }
    }
}
