package com.panoli707.aetherio.nativeapp.player

import java.net.URI
import java.net.URISyntaxException

object MediaUrlPolicy {
    private const val MAX_URL_LENGTH = 8192
    private const val ERROR_MESSAGE = "URL no válida. Usa una URL HTTPS directa válida."

    @JvmStatic
    fun validate(value: String, allowDesktopHttp: Boolean = false): String {
        // Check before trimming so leading/trailing controls cannot disappear.
        if (value.length > MAX_URL_LENGTH ||
            value.any { it <= '\u001f' || it in '\u007f'..'\u009f' }
        ) {
            throw IllegalArgumentException(ERROR_MESSAGE)
        }

        val validated = value.trim()
        val uri = try {
            URI(validated)
        } catch (_: URISyntaxException) {
            throw IllegalArgumentException(ERROR_MESSAGE)
        }
        val desktopHttp = allowDesktopHttp && uri.scheme.equals("http", ignoreCase = true) &&
            (uri.host.equals("shegu.net", ignoreCase = true) || uri.host?.lowercase()?.endsWith(".shegu.net") == true)
        if ((!uri.scheme.equals("https", ignoreCase = true) && !desktopHttp) ||
            uri.host.isNullOrEmpty() || uri.rawUserInfo != null ||
            uri.rawAuthority.orEmpty().endsWith(":") ||
            (uri.port != -1 && uri.port !in 1..65535)
        ) {
            throw IllegalArgumentException(ERROR_MESSAGE)
        }
        return validated
    }
}
