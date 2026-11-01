package com.panoli707.aetherio.nativeapp.player

import androidx.media3.common.MimeTypes

enum class VideoFormat(val label: String, val mimeType: String?) {
    AUTO("Automático", null),
    HLS("HLS", MimeTypes.APPLICATION_M3U8),
    DASH("DASH", MimeTypes.APPLICATION_MPD)
}
