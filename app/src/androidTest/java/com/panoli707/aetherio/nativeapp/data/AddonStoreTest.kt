package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddonStoreTest {
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        private val testPackageName = "${baseContext.packageName}.addonstoretest.${UUID.randomUUID()}"
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = testPackageName
    }
    private val alias = "${context.packageName}.sources.v1"
    private val preferences = context.getSharedPreferences(alias, Context.MODE_PRIVATE)
    private val privateUrl = "https://example.org/private/manifest.json?token=synthetic-private-secret"
    private val addon = SourceProtocol.manifest(
        """{"id":"org.example.synthetic","name":"Synthetic private provider","types":["movie","series"],"resources":["stream"]}""",
        SourceProtocol.manifestUrl(privateUrl)
    )

    @After fun cleanUp() {
        try {
            context.deleteSharedPreferences(alias)
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }

    @Test fun newInstanceLoadsEncryptedConfigAndEachSaveUsesFreshIv() {
        AddonStore(context).save(listOf(addon))
        val first = requireNotNull(preferences.getString("encrypted", null))
        assertEquals(listOf(addon), AddonStore(context).load())

        AddonStore(context).save(listOf(addon))
        val second = requireNotNull(preferences.getString("encrypted", null))
        assertEquals(listOf(addon), AddonStore(context).load())

        val ciphertexts = listOf(first, second).map { raw ->
            val bytes = Base64.decode(JSONObject(raw).getString("data"), Base64.NO_WRAP)
            for (secret in listOf(privateUrl, addon.name, "synthetic-private-secret")) {
                assertFalse(raw.contains(secret))
                assertFalse(String(bytes, Charsets.ISO_8859_1).contains(secret))
            }
            bytes
        }
        assertNotEquals(first, second)
        assertFalse(ciphertexts[0].copyOfRange(0, 12).contentEquals(ciphertexts[1].copyOfRange(0, 12)))
    }

    @Test fun alteredCiphertextFailsWithGenericErrorWithoutCauseOrSecrets() {
        AddonStore(context).save(listOf(addon))
        val envelope = JSONObject(requireNotNull(preferences.getString("encrypted", null)))
        val bytes = Base64.decode(envelope.getString("data"), Base64.NO_WRAP)
        // Keep the envelope and IV valid so authentication, not JSON parsing, rejects the change.
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        envelope.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
        assertTrue(preferences.edit().putString("encrypted", envelope.toString()).commit())

        val error = assertThrows(IllegalStateException::class.java) { AddonStore(context).load() }
        assertEquals("No se pudieron guardar o cargar las fuentes", error.message)
        assertNull(error.cause)
        assertTrue(error.suppressed.isEmpty())
        for (secret in listOf(privateUrl, addon.name, "synthetic-private-secret", "example.org")) {
            assertFalse(error.toString().contains(secret))
        }
    }
}
