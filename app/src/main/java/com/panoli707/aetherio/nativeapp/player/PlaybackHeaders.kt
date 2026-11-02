package com.panoli707.aetherio.nativeapp.player

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.net.URI
import java.util.Locale

object PlaybackHeaders {
    private const val ERROR_MESSAGE = "Invalid playback headers"
    private val encodedControl = Regex("%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE)

    @JvmStatic
    fun validate(headers: Map<String, String>): Map<String, String> {
        try {
            require(headers.size <= 3)
            val result = linkedMapOf<String, String>()
            for ((key, value) in headers) {
                val canonical = when (key.lowercase(Locale.ROOT)) {
                    "referer" -> "Referer"
                    "origin" -> "Origin"
                    "user-agent" -> "User-Agent"
                    else -> throw IllegalArgumentException()
                }
                require(canonical !in result)
                require(value.length in 1..4096 && value.all { it in ' '..'~' })
                if (canonical != "User-Agent") {
                    require(!encodedControl.containsMatchIn(value))
                    val uri = URI(value)
                    val url = value.toHttpUrlOrNull() ?: throw IllegalArgumentException()
                    require(uri.scheme.equals("https", true) && !uri.host.isNullOrEmpty() &&
                        uri.rawUserInfo == null && uri.rawFragment == null &&
                        !uri.rawAuthority.orEmpty().endsWith(":") &&
                        (uri.port == -1 || uri.port in 1..65535) && url.isHttps)
                    if (canonical == "Origin") {
                        require(uri.rawPath.orEmpty() in listOf("", "/") && uri.rawQuery == null)
                    } else {
                        val host = url.host.removePrefix("[").removeSuffix("]").removeSuffix(".")
                        require(host != "localhost" && !host.endsWith(".localhost") &&
                            !host.endsWith(".local") && ('.' in host || ':' in host))
                        // Only parse numeric literals; header validation must never perform DNS.
                        if (':' in host || host.all { it in '0'..'9' || it == '.' }) {
                            if (':' !in host) {
                                val octets = host.split('.')
                                require(octets.size == 4 && octets.all { (it.toIntOrNull() ?: -1) in 0..255 })
                            }
                            val address = InetAddress.getByName(host)
                            require(!address.isAnyLocalAddress && !address.isLoopbackAddress &&
                                !address.isLinkLocalAddress && !address.isSiteLocalAddress &&
                                !address.isMulticastAddress)
                            val bytes = address.address
                            require(bytes.size != 16 || (bytes[0].toInt() and 0xfe) != 0xfc)
                            if (bytes.size == 4) {
                                val first = bytes[0].toInt() and 255
                                val second = bytes[1].toInt() and 255
                                require(first != 0 && first < 224 &&
                                    !(first == 100 && second in 64..127) &&
                                    !(first == 198 && second in 18..19))
                            }
                        }
                    }
                }
                result[canonical] = value
            }
            return result.toMap()
        } catch (_: Exception) {
            // Do not retain URI/parser exceptions or the supplied header values.
            throw IllegalArgumentException(ERROR_MESSAGE)
        }
    }
}
