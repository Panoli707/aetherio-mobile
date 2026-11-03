package com.panoli707.aetherio.nativeapp.data

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Reversible obfuscation only: a modified client can still recover the token at runtime. */
internal object EmbeddedTmdbToken {
    val value: String by lazy { TmdbCredentialCodec.decode(EmbeddedTmdbMaterial.DATA) }
}

internal object TmdbCredentialCodec {
    private const val MAX_TOKEN_BYTES = 16_384
    private const val PREFIX_BYTES = 1 + 32 + 32 + 12
    private const val ERROR = "No se pudo leer la credencial local de TMDB"

    fun decode(encoded: String): String {
        if (encoded.isEmpty()) return "" // A build without credentials retains the existing unauthenticated path.
        var packed: ByteArray? = null
        var key: ByteArray? = null
        var plaintext: ByteArray? = null
        try {
            require(encoded.length % 2 == 0 && encoded.length in (2 * (PREFIX_BYTES + 17))..(2 * (PREFIX_BYTES + 16 + MAX_TOKEN_BYTES)))
            require(encoded.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
            val bytes = ByteArray(encoded.length / 2) { encoded.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            packed = bytes
            require(bytes[0] == 1.toByte())
            val decodedKey = ByteArray(32) { (bytes[1 + it].toInt() xor bytes[33 + it].toInt()).toByte() }
            key = decodedKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(decodedKey, "AES"), GCMParameterSpec(128, bytes.copyOfRange(65, PREFIX_BYTES)))
            cipher.updateAAD("com.panoli707.aetherio.nativeapp.tmdb.v1".toByteArray(Charsets.US_ASCII))
            val decoded = cipher.doFinal(bytes, PREFIX_BYTES, bytes.size - PREFIX_BYTES)
            plaintext = decoded
            require(decoded.size in 1..MAX_TOKEN_BYTES && decoded.all {
                it.toInt() in 'A'.code..'Z'.code || it.toInt() in 'a'.code..'z'.code ||
                    it.toInt() in '0'.code..'9'.code || it.toInt() in listOf('.'.code, '_'.code, '-'.code)
            })
            return decoded.toString(Charsets.US_ASCII)
        } catch (_: Exception) {
            // Never retain crypto/parser causes or supplied material in errors or logs.
            throw IllegalStateException(ERROR)
        } finally {
            packed?.fill(0)
            key?.fill(0)
            plaintext?.fill(0)
        }
    }
}
