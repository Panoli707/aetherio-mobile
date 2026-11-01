package com.panoli707.aetherio.nativeapp.data

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Looper
import androidx.core.content.FileProvider
import com.panoli707.aetherio.nativeapp.BuildConfig
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener

internal enum class UpdateTransferStatus { RUNNING, WAITING, COMPLETE, FAILED }

internal data class UpdateDownloadSnapshot(
    val status: UpdateTransferStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long
)

internal interface UpdateDownloadManager {
    fun request(uri: Uri): DownloadManager.Request = DownloadManager.Request(uri)
    fun enqueue(request: DownloadManager.Request): Long
    fun query(id: Long): Cursor?
    fun remove(id: Long)
    fun getUriForDownloadedFile(id: Long): Uri?
}

internal class UpdateDownloads(
    context: Context,
    private val installedVersionCode: Int = BuildConfig.VERSION_CODE,
    manager: UpdateDownloadManager? = null
) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("aetherio_updates", Context.MODE_PRIVATE)
    private val manager = manager ?: object : UpdateDownloadManager {
        private val service get() = this@UpdateDownloads.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        override fun enqueue(request: DownloadManager.Request): Long = service.enqueue(request)
        override fun query(id: Long): Cursor? = service.query(DownloadManager.Query().setFilterById(id))
        override fun remove(id: Long) { service.remove(id) }
        override fun getUriForDownloadedFile(id: Long): Uri? = service.getUriForDownloadedFile(id)
    }
    private var verified: Pair<Long, AppUpdate>? = null

    var automatic: Boolean
        get() = updateDownloadOperation { preferences.getBoolean("automatic", false) }
        set(value) = updateDownloadOperation {
            check(preferences.edit().putBoolean("automatic", value).commit())
        }

    var installationOffered: Boolean
        get() = updateDownloadOperation { preferences.getBoolean("installationOffered", false) }
        set(value) = updateDownloadOperation {
            check(preferences.edit().putBoolean("installationOffered", value).commit())
        }

    fun pending(): Pair<Long, AppUpdate>? = updateDownloadOperation {
        val record = readRecord()
        if (record == null || record.second.versionCode <= installedVersionCode) {
            if (record != null) removeOwned(record, bestEffort = true)
            if (preferences.contains("pending") || installationOffered) {
                check(preferences.edit().remove("pending").putBoolean("installationOffered", false).commit())
                verified = null
                clearStaged()
            }
            // Never pass an ID recovered from a malformed record to DownloadManager.
            null
        } else record
    }

    fun enqueue(update: AppUpdate, automatic: Boolean): Long = updateDownloadOperation {
        check(validDownload(update) && update.versionCode > installedVersionCode)
        cancel()
        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw updateDownloadFailure()
        val directory = File(external.canonicalFile, "updates")
        check(directory.canonicalFile == directory.absoluteFile)
        check(directory.isDirectory || directory.mkdirs())
        val destination = File(directory, "${update.versionCode}.apk")
        check(destination.canonicalFile == destination.absoluteFile)
        check(!destination.exists() || destination.delete())
        val request = manager.request(Uri.parse(update.apkUrl))
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                "updates/${update.versionCode}.apk")
            .setTitle("Aetherio ${update.versionName}")
            .setMimeType(APK_MIME)
            // A completion notification could open the unverified external APK directly.
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(!automatic)
            .setAllowedOverRoaming(false)
        val id = manager.enqueue(request)
        try {
            check(id > 0)
            val record = JSONObject().put("id", id).put("versionCode", update.versionCode)
                .put("versionName", update.versionName).put("releaseUrl", update.releaseUrl)
                .put("prerelease", update.prerelease).put("apkUrl", update.apkUrl)
                .put("sha256", update.sha256).put("size", update.size).toString()
            check(record.toByteArray(Charsets.UTF_8).size <= RECORD_LIMIT)
            check(preferences.edit().putString("pending", record).putBoolean("installationOffered", false).commit())
            id
        } catch (error: Exception) {
            // A failed commit may still have changed SharedPreferences' in-memory map.
            try {
                preferences.edit().remove("pending").commit()
            } finally {
                if (id > 0) manager.remove(id)
            }
            throw error
        }
    }

    fun snapshot(id: Long): UpdateDownloadSnapshot = updateDownloadOperation {
        val update = pending()?.takeIf { it.first == id }?.second
            ?: return@updateDownloadOperation failedSnapshot()
        manager.query(id)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use failedSnapshot()
            val bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (bytes !in 0..update.size || total < -1 || (total > 0 && total != update.size)) {
                return@use failedSnapshot()
            }
            val transfer = when (status) {
                DownloadManager.STATUS_RUNNING -> UpdateTransferStatus.RUNNING
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> UpdateTransferStatus.WAITING
                DownloadManager.STATUS_SUCCESSFUL -> if (bytes == update.size && total == update.size)
                    UpdateTransferStatus.COMPLETE else UpdateTransferStatus.FAILED
                else -> UpdateTransferStatus.FAILED
            }
            UpdateDownloadSnapshot(transfer, bytes, update.size)
        } ?: failedSnapshot()
    }

    suspend fun verify(update: AppUpdate, id: Long): File = withContext(Dispatchers.IO) {
        val jobContext = currentCoroutineContext()
        updateDownloadOperation {
            jobContext.ensureActive()
            check(pending() == (id to update))
            check(snapshot(id).status == UpdateTransferStatus.COMPLETE)
            val uri = manager.getUriForDownloadedFile(id) ?: throw updateDownloadFailure()
            check(uri.scheme == "content")
            verified = null
            val file = verifyUpdateApk(context, update, installedVersionCode,
                openSource = { context.contentResolver.openInputStream(uri) ?: throw updateDownloadFailure() },
                ensureActive = { jobContext.ensureActive() })
            verified = id to update
            file
        }
    }

    fun installIntent(update: AppUpdate): Intent = updateDownloadOperation {
        val record = pending()
        check(record != null && record == verified && record.second == update)
        val file = updateCacheFile(context, update.versionCode, "apk")
        check(file.isFile && file.length() == update.size)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri("Aetherio update", uri) }
    }

    fun cancel() = updateDownloadOperation {
        val record = readRecord()
        if (record != null) removeOwned(record)
        verified = null
        clearStaged()
        check(preferences.edit().remove("pending").putBoolean("installationOffered", false).commit())
    }

    private fun removeOwned(record: Pair<Long, AppUpdate>, bestEffort: Boolean = false) {
        try {
            manager.remove(record.first)
        } catch (error: Exception) {
            if (!bestEffort || error is CancellationException) throw error
        }
        try {
            val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
            val directory = File(external.canonicalFile, "updates")
            val file = File(directory, "${record.second.versionCode}.apk")
            if (directory.canonicalFile == directory.absoluteFile &&
                file.canonicalFile == file.absoluteFile && file.isFile) check(file.delete())
        } catch (error: Exception) {
            if (!bestEffort || error is CancellationException) throw error
        }
    }

    private fun clearStaged() {
        val directory = updateCacheDirectory(context)
        directory.listFiles()?.forEach { file ->
            if (Regex("[1-9][0-9]{0,9}\\.(apk|part)").matches(file.name) &&
                file.canonicalFile == file.absoluteFile && file.isFile) check(file.delete())
        }
    }

    private fun readRecord(): Pair<Long, AppUpdate>? = try {
        val raw = preferences.getString("pending", null)
        if (raw == null || raw.length > RECORD_LIMIT || raw.toByteArray(Charsets.UTF_8).size > RECORD_LIMIT) null
        else {
            // This is a flat record. Bound nesting before Android's recursive JSON parser.
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
                    '{' -> check(++depth == 1)
                    '}' -> check(--depth == 0)
                    '[', ']', '\'', '/', '#', '\u0000' -> throw updateDownloadFailure()
                }
            }
            check(!quoted && depth == 0)
            val parser = JSONTokener(raw)
            val json = parser.nextValue() as? JSONObject ?: throw updateDownloadFailure()
            check(parser.nextClean() == '\u0000' && json.length() == 8)
            fun number(key: String): Long = when (val value = json.get(key)) {
                is Int -> value.toLong()
                is Long -> value
                else -> throw updateDownloadFailure()
            }
            val id = number("id")
            val code = number("versionCode")
            check(id > 0 && code in 1..Int.MAX_VALUE.toLong())
            val update = AppUpdate(code.toInt(), json.get("versionName") as String,
                json.get("releaseUrl") as String, json.get("prerelease") as Boolean,
                json.get("apkUrl") as String, json.get("sha256") as String, number("size"))
            check(validDownload(update))
            id to update
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

private const val APK_MIME = "application/vnd.android.package-archive"
private const val RECORD_LIMIT = 4096
private const val MAX_UPDATE_SIZE = 128L * 1024 * 1024
private val updateDownloadsLock = Any()

private fun validDownload(update: AppUpdate) =
    update.size in 1..MAX_UPDATE_SIZE && UpdateRepository.isValidDownload(update)

private fun failedSnapshot() = UpdateDownloadSnapshot(UpdateTransferStatus.FAILED, 0, 0)
private fun updateDownloadFailure() = IOException("Unable to prepare update")

private inline fun <T> updateDownloadOperation(block: () -> T): T = synchronized(updateDownloadsLock) {
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        throw updateDownloadFailure()
    }
}

private fun updateCacheDirectory(context: Context): File = File(context.cacheDir.canonicalFile, "updates").also {
    check(it.canonicalFile == it.absoluteFile)
}

private fun updateCacheFile(context: Context, code: Int, extension: String): File =
    File(updateCacheDirectory(context), "$code.$extension").also {
        check(code > 0 && it.canonicalFile == it.absoluteFile)
    }

// Only the production adapter supplies a DownloadManager source. Tests can use isolated streams
// and a lower installed version without relaxing the production BuildConfig gate.
@Suppress("DEPRECATION")
internal fun verifyUpdateApk(
    context: Context,
    update: AppUpdate,
    installedVersionCode: Int,
    openSource: () -> InputStream,
    ensureActive: () -> Unit
): File = updateDownloadOperation {
    check(Looper.myLooper() != Looper.getMainLooper())
    check(validDownload(update) && update.versionCode > installedVersionCode)
    val part = updateCacheFile(context, update.versionCode, "part")
    val apk = updateCacheFile(context, update.versionCode, "apk")
    check(part.parentFile!!.isDirectory || part.parentFile!!.mkdirs())
    check(!part.exists() || part.delete())
    check(!apk.exists() || apk.delete())
    var accepted = false
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        ensureActive()
        openSource().use { input ->
            part.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    ensureActive()
                    if (read < 0) break
                    check(read <= update.size - count)
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    count += read
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(count == update.size && hash.equals(update.sha256, ignoreCase = true))
        ensureActive()
        check(part.setReadOnly() && part.renameTo(apk))
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: throw updateDownloadFailure()
        check(archive.packageName == context.packageName)
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        check(code == update.versionCode.toLong() && code > installedVersionCode)
        val installed = pm.getPackageInfo(context.packageName, flags)
        fun signers(info: PackageInfo): Array<Signature>? = if (Build.VERSION.SDK_INT >= 28)
            info.signingInfo?.apkContentsSigners else info.signatures
        check(updateSignersMatch(signers(installed), signers(archive)))
        ensureActive()
        accepted = true
        apk
    } finally {
        part.delete()
        if (!accepted) apk.delete()
    }
}

internal fun updateSignersMatch(installed: Array<Signature>?, archive: Array<Signature>?): Boolean {
    if (installed.isNullOrEmpty() || archive.isNullOrEmpty()) return false
    if ((installed + archive).any { it.toByteArray().isEmpty() }) return false
    return installed.map { it.toCharsString() }.toSet() == archive.map { it.toCharsString() }.toSet()
}
