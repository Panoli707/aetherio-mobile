package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val releaseUrl: String,
    val prerelease: Boolean
)

internal class UpdateRepository(
    client: OkHttpClient = OkHttpClient(),
    private val endpoint: HttpUrl = DEFAULT_RELEASES_URL
) {
    init {
        if (!endpoint.isHttps || endpoint.username.isNotEmpty() || endpoint.password.isNotEmpty() ||
            endpoint.fragment != null) invalidUpdate()
    }

    private val client = client.newBuilder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            if (!chain.request().url.isHttps) throw updateFailure()
            chain.proceed(chain.request())
        }
        .build()

    suspend fun available(currentVersionCode: Int): AppUpdate? = suspendCancellableCoroutine { continuation ->
        val call = try {
            this.client.newCall(Request.Builder().url(endpoint).get()
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Aetherio-Android")
                .build())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            continuation.resumeWithException(updateFailure())
            return@suspendCancellableCoroutine
        }
        // Keep cancellation attached until the body has reached EOF and parsing has finished.
        continuation.invokeOnCancellation { call.cancel() }
        try {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(updateFailure())
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use {
                            if (!it.isSuccessful) throw updateFailure()
                            val body = it.body ?: throw updateFailure()
                            if (body.contentLength() > UPDATE_BODY_LIMIT) throw updateFailure()
                            val source = body.source()
                            source.request(UPDATE_BODY_LIMIT.toLong() + 1)
                            if (source.buffer.size > UPDATE_BODY_LIMIT) throw updateFailure()
                            Result.success(parse(source.readUtf8(), currentVersionCode))
                        }
                    } catch (error: CancellationException) {
                        Result.failure(error)
                    } catch (_: Exception) {
                        Result.failure(updateFailure())
                    }
                    if (continuation.isActive) result.fold(
                        { continuation.resume(it) }, { continuation.resumeWithException(it) }
                    )
                }
            })
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resumeWithException(updateFailure())
        }
    }

    companion object {
        val DEFAULT_RELEASES_URL: HttpUrl =
            "https://api.github.com/repos/Panoli707/aetherio-mobile/releases?per_page=10".toHttpUrl()

        fun parse(raw: String, currentVersionCode: Int): AppUpdate? {
            try {
                val releases = updateJson(raw, UPDATE_BODY_LIMIT) as? JSONArray ?: invalidUpdate()
                if (releases.length() > 10) invalidUpdate()
                var best: AppUpdate? = null
                for (index in 0 until releases.length()) {
                    val release = releases.optJSONObject(index) ?: invalidUpdate()
                    val candidate = updateCandidate(release, currentVersionCode) ?: continue
                    if (candidate.versionCode > (best?.versionCode ?: currentVersionCode)) best = candidate
                }
                return best
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                invalidUpdate()
            }
        }
    }
}

private const val UPDATE_BODY_LIMIT = 1024 * 1024
private const val UPDATE_MARKER = "<!-- aetherio-update:"
private val updateTag = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")
private val updateApk = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.apk")

private fun invalidUpdate(): Nothing = throw IllegalArgumentException("Invalid update data")
private fun updateFailure() = IOException("Unable to check for updates")

private fun updateCandidate(release: JSONObject, installed: Int): AppUpdate? {
    if (release.opt("draft") != false) return null
    val prerelease = release.opt("prerelease") as? Boolean ?: return null
    val tag = release.opt("tag_name") as? String ?: return null
    if (!updateTag.matches(tag)) return null
    val body = release.opt("body") as? String ?: return null
    if (body.length > 128 * 1024) return null
    val start = body.indexOf(UPDATE_MARKER)
    if (start < 0 || body.indexOf(UPDATE_MARKER, start + UPDATE_MARKER.length) >= 0) return null
    if (start > 0 && body[start - 1] != '\n') return null
    val jsonStart = start + UPDATE_MARKER.length + 1
    if (!body.startsWith("$UPDATE_MARKER ", start)) return null
    val end = body.indexOf(" -->", jsonStart)
    if (end < 0 || end - jsonStart !in 2..1024) return null
    val after = end + 4
    if (after < body.length && body[after] != '\n' && !body.startsWith("\r\n", after)) return null
    val raw = body.substring(jsonStart, end)
    if (raw.any { it == '\r' || it == '\n' } || raw.toByteArray(Charsets.UTF_8).size > 1024) return null
    val metadata = try {
        updateJson(raw, 1024) as? JSONObject ?: return null
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        return null
    }
    if (metadata.length() != 5 || updateInteger(metadata.opt("schemaVersion")) != 1L ||
        metadata.opt("applicationId") != "com.panoli707.aetherio.nativeapp") return null
    val code = updateInteger(metadata.opt("versionCode")) ?: return null
    if (code !in 1..Int.MAX_VALUE.toLong() || code <= installed) return null
    val name = metadata.opt("versionName") as? String ?: return null
    // ASCII printable display text excludes controls, bidi overrides and invisible formatting.
    if (name.length !in 1..80 || name != name.trim() || name.any { it !in ' '..'~' }) return null
    val apk = metadata.opt("apkName") as? String ?: return null
    if (apk.length > 120 || !updateApk.matches(apk)) return null
    val assets = release.opt("assets") as? JSONArray ?: return null
    var uploaded = false
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: return null
        if (asset.opt("name") == apk && asset.opt("state") == "uploaded" &&
            (updateInteger(asset.opt("size")) ?: 0L) > 0L) uploaded = true
    }
    if (!uploaded) return null
    return AppUpdate(code.toInt(), name,
        "https://github.com/Panoli707/aetherio-mobile/releases/tag/$tag", prerelease)
}

private fun updateInteger(value: Any?): Long? = when (value) {
    is Int -> value.toLong()
    is Long -> value
    else -> null
}

private fun updateJson(raw: String, limit: Int): Any {
    if (raw.length > limit || raw.toByteArray(Charsets.UTF_8).size > limit) invalidUpdate()
    // Bound allocation before org.json constructs its tree, as in SourceProtocol.root.
    val separators = IntArray(16)
    var depth = 0
    var quoted = false
    var escaped = false
    raw.forEach { char ->
        if (quoted) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') quoted = false
        } else when (char) {
            '"' -> quoted = true
            '\'', ';', '\u0000' -> invalidUpdate()
            '{', '[' -> {
                if (depth == separators.size) invalidUpdate()
                separators[depth++] = 0
            }
            '}', ']' -> { if (depth == 0) invalidUpdate(); depth-- }
            ',' -> if (depth > 0 && ++separators[depth - 1] >= 100) invalidUpdate()
        }
    }
    if (quoted || depth != 0) invalidUpdate()
    val parser = JSONTokener(raw)
    val value = parser.nextValue()
    if (parser.nextClean() != '\u0000') invalidUpdate()
    return value
}
