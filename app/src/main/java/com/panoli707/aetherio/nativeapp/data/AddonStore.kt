package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import org.json.JSONTokener
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Synchronous by design; callers must use their IO dispatcher. */
internal class AddonStore(context: Context) {
    private val application = context.applicationContext
    private val alias = "${application.packageName}.sources.v1"
    private val aad = application.packageName.toByteArray(Charsets.UTF_8)
    private val preferences = application.getSharedPreferences(alias, Context.MODE_PRIVATE)

    fun load(): List<AddonConfig> = synchronized(LOCK) {
        sanitized {
            // An unused store must not create a Keystore key, including when the value is absent.
            val raw = preferences.getString("encrypted", null) ?: return@sanitized emptyList()
            if (raw.length > ENVELOPE_LIMIT) invalid()
            // The envelope contains only a version and Base64, never nested objects or arrays.
            if (raw.count { it == '{' } != 1 || raw.count { it == '}' } != 1 || raw.any { it == '[' || it == ']' }) invalid()
            val parser = JSONTokener(raw)
            val envelope = parser.nextValue() as? JSONObject ?: invalid()
            if (parser.nextClean() != '\u0000' || envelope.length() != 2 || envelope.opt("version") != 1) invalid()
            val encoded = envelope.opt("data") as? String ?: invalid()
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            if (bytes.size < IV_SIZE + 16 || bytes.size > SourceProtocol.STORE_LIMIT + IV_SIZE + 16) invalid()
            if (Base64.encodeToString(bytes, Base64.NO_WRAP) != encoded) invalid()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(0, IV_SIZE)))
            cipher.updateAAD(aad)
            val plaintext = cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
            SourceProtocol.decode(String(plaintext, Charsets.UTF_8))
        }
    }

    fun save(configs: List<AddonConfig>) {
        synchronized(LOCK) {
            sanitized {
                val plaintext = SourceProtocol.encode(configs).toByteArray(Charsets.UTF_8)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
                cipher.updateAAD(aad)
                if (cipher.iv.size != IV_SIZE) invalid()
                val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(plaintext), Base64.NO_WRAP)
                val envelope = JSONObject().put("version", 1).put("data", encoded).toString()
                if (!preferences.edit().putString("encrypted", envelope).commit()) invalid()
            }
        }
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null)
        if (existing != null) return existing as? SecretKey ?: invalid()
        if (!create || store.containsAlias(alias)) invalid()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    private inline fun <T> sanitized(block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        invalid()
    }

    private fun invalid(): Nothing = throw IllegalStateException("No se pudieron guardar o cargar las fuentes")

    private companion object {
        val LOCK = Any()
        const val IV_SIZE = 12
        const val ENVELOPE_LIMIT = 704 * 1024
    }
}
