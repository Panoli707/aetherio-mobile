package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-bound, authenticated storage. No fallback to plaintext on Keystore/disk errors. */
internal class PrivateStore(context: Context, private val name: String) {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val alias = "aetherio.$name.v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(id: String): String? {
        val encoded = preferences.getString(id, null) ?: return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size in 29..(8 * 1024 * 1024))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD("$name:$id".toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    @Synchronized fun write(id: String, text: String?) {
        val editor = preferences.edit()
        if (text == null) editor.remove(id) else {
            require(text.toByteArray(Charsets.UTF_8).size <= 6 * 1024 * 1024)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD("$name:$id".toByteArray(Charsets.UTF_8))
            editor.putString(id, Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
        }
        check(editor.commit()) { "PRIVATE_STORAGE" }
    }
}
