package com.panoli707.aetherio.nativeapp.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddedTmdbTokenTest {
    @Test fun generatedCredentialCanBeReconstructedWithAndroidCryptoWithoutLoggingIt() {
        val decoded = EmbeddedTmdbToken.value
        assertTrue("Credential presence mismatch", decoded.isEmpty() == EmbeddedTmdbMaterial.DATA.isEmpty())
        assertTrue("Invalid credential format", decoded.isEmpty() || decoded.matches(Regex("[A-Za-z0-9._-]+")))
    }
}
