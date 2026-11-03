package com.panoli707.aetherio.nativeapp.data

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class TmdbCredentialCodecTest {
    private val token = "eyJ.fixture-read_token-123.signature"

    private fun pack(value: String = token, aad: String = "com.panoli707.aetherio.nativeapp.tmdb.v1"): ByteArray {
        val key = ByteArray(32) { (it + 17).toByte() }
        val mask = ByteArray(32) { (it * 3 + 1).toByte() }
        val nonce = ByteArray(12) { (it + 50).toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad.toByteArray(Charsets.US_ASCII))
        return byteArrayOf(1) + mask + ByteArray(32) { (key[it].toInt() xor mask[it].toInt()).toByte() } +
            nonce + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun reconstructsOnlyInMemoryAndAcceptsEmptyCredentialBuilds() {
        val encoded = pack().hex()
        assertFalse(encoded.contains(token))
        assertEquals(token, TmdbCredentialCodec.decode(encoded))
        assertEquals(token, TmdbCredentialCodec.decode(encoded.uppercase()))
        assertEquals("", TmdbCredentialCodec.decode(""))
    }

    @Test fun rejectsWrongVersionModifiedKeyNonceCiphertextAndAuthenticationTagWithoutLeakingMaterial() {
        val bytes = pack()
        listOf(0, 1, 33, 65, 77, bytes.lastIndex).forEach { index ->
            val modified = bytes.copyOf()
            modified[index] = (modified[index].toInt() xor 1).toByte()
            rejected(modified.hex())
        }
        rejected(pack(aad = "different-context").hex())
    }

    @Test fun rejectsMalformedOversizedAndUnsafePlaintext() {
        listOf("0", "00", "zz".repeat(110), "+1".repeat(110), "00".repeat(17_000), pack().hex().dropLast(2)).forEach(::rejected)
        listOf("fixture\r\nInjected", "contains spaces", "ñ", "").forEach { rejected(pack(it).hex()) }
    }

    @Test fun actualGeneratedMaterialDecodesWithoutExposingItsValueInTestOutput() {
        val decoded = EmbeddedTmdbToken.value
        assertTrue("Credential presence must match generated material", decoded.isEmpty() == EmbeddedTmdbMaterial.DATA.isEmpty())
        if (decoded.isNotEmpty()) {
            assertTrue("Expected valid credential shape", decoded.matches(Regex("[A-Za-z0-9._-]+")))
            assertFalse("Generated material must not contain the plaintext token", EmbeddedTmdbMaterial.DATA.contains(decoded))
        }
    }

    @Test fun defaultRepositoryUsesReconstructedCredentialOnlyForOfficialTmdbAndNeverInUrl() = runBlocking {
        var requests = 0
        val expected = EmbeddedTmdbToken.value
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            val request = chain.request()
            assertTrue("Unexpected API destination", request.url.host == if (expected.isEmpty()) "trkll.aetherio.workers.dev" else "api.themoviedb.org")
            assertTrue("Incorrect scoped credential", request.header("Authorization") == expected.takeIf { it.isNotEmpty() }?.let { "Bearer $it" })
            if (expected.isNotEmpty()) assertFalse("Credential must not enter query parameters", request.url.toString().contains(expected))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body("""{"results":[{"id":550,"title":"Fixture","media_type":"movie"}]}""".toResponseBody()).build()
        }.build()
        try {
            assertEquals("550", AetherioRepository(client = client).search("Fixture").single().id)
            assertEquals(1, requests)
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }

    private fun rejected(encoded: String) {
        val error = assertThrows(IllegalStateException::class.java) { TmdbCredentialCodec.decode(encoded) }
        assertEquals("No se pudo leer la credencial local de TMDB", error.message)
        assertNull(error.cause)
        assertTrue(error.suppressed.isEmpty())
        assertFalse(error.toString().contains(token))
        assertFalse(error.toString().contains(encoded))
    }
}
