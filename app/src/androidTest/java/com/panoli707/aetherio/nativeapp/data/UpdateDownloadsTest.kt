package com.panoli707.aetherio.nativeapp.data

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.Signature
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.system.Os
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.BuildConfig
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SuppressLint("NewApi")
class UpdateDownloadsTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val identifier = UUID.randomUUID().toString()
    private val preferenceName = "aetherio_updates.test.$identifier"
    private val root = File(target.cacheDir, "updates/test-$identifier").apply { check(mkdirs()) }
    private var useFakeDownloads = false
    private var managerUnavailable = false
    private val provider by lazy { FakeDownloadsProvider() }
    // ContentResolver.wrap is public from API 29; provider-backed tests are SDK-gated below.
    private val isolatedResolver: ContentResolver by lazy { ContentResolver.wrap(provider) }
    private val isolatedManager: UpdateDownloadManager by lazy {
        object : UpdateDownloadManager {
            private val uri = Uri.parse("content://downloads/my_downloads")
            override fun request(uri: Uri): DownloadManager.Request = RecordingRequest(uri)
            override fun enqueue(request: DownloadManager.Request): Long =
                requireNotNull(isolatedResolver.insert(uri, (request as RecordingRequest).values))
                    .lastPathSegment!!.toLong()
            override fun query(id: Long): Cursor? =
                isolatedResolver.query(uri, null, "_id = ?", arrayOf(id.toString()), null)
            override fun remove(id: Long) {
                isolatedResolver.delete(uri, "_id = ?", arrayOf(id.toString()))
            }
            override fun getUriForDownloadedFile(id: Long): Uri? {
                provider.sourceIds.add(id)
                check(id == provider.rowId && id in provider.ids && provider.status == DownloadManager.STATUS_SUCCESSFUL)
                return provider.sourceUri
            }
        }
    }
    private val context = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            check(name == "aetherio_updates")
            return target.getSharedPreferences(preferenceName, mode)
        }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getExternalFilesDir(type: String?): File = File(root, "external/${type.orEmpty()}")
            .apply { mkdirs() }
        override fun getSystemService(name: String): Any? {
            if (name == Context.DOWNLOAD_SERVICE) {
                if (managerUnavailable) throw IllegalStateException("Download service unavailable")
                throw AssertionError("Tests must not access the real download service")
            }
            return super.getSystemService(name)
        }
    }
    private val preferences get() = context.getSharedPreferences("aetherio_updates", Context.MODE_PRIVATE)
    private val downloads get() = UpdateDownloads(context, manager = if (useFakeDownloads) isolatedManager else null)
    private val payload = ByteArray(130_000) { (it % 251).toByte() }
    private val update get() = AppUpdate(
        BuildConfig.VERSION_CODE + 1, "Test update",
        "https://github.com/Panoli707/aetherio-mobile/releases/tag/v-test", false,
        "https://github.com/Panoli707/aetherio-mobile/releases/download/v-test/Aetherio-test.apk",
        hash(payload.inputStream()), payload.size.toLong()
    )

    @After fun cleanUp() {
        target.deleteSharedPreferences(preferenceName)
        // Unlink this fixture's symlink without following it during recursive cleanup.
        val link = File(context.cacheDir, "updates")
        if (link.canonicalFile != link.absoluteFile) link.delete()
        root.deleteRecursively()
    }

    @Test fun automaticDefaultsFalsePersistsAndSurvivesCancelInIsolatedPreferences() {
        assertSame(context, context.applicationContext)
        assertFalse(downloads.automatic)
        downloads.automatic = true
        assertTrue(downloads.automatic)
        downloads.cancel()
        assertTrue(downloads.automatic)
        downloads.automatic = false
        assertFalse(downloads.automatic)
        assertEquals(setOf("automatic", "installationOffered"), preferences.all.keys)
    }

    @Test fun pendingRestoresAllMetadataWithoutContactingDownloadManager() {
        val candidate = update.copy(prerelease = true)
        store(record(candidate))
        assertEquals(42L to candidate, downloads.pending())
        assertEquals(42L to candidate, downloads.pending())
    }

    @Test fun unsafeAndIllTypedMetadataIsDiscardedWithoutRemovingAnyDownload() {
        downloads.automatic = true
        val base = update
        val invalid = listOf(
            record(base.copy(apkUrl = "https://example.org/private.apk?token=synthetic-secret")),
            record(base.copy(apkUrl = base.apkUrl.replace("https:", "http:"))),
            record(base.copy(releaseUrl = "https://github.com/Other/repo/releases/tag/v-test")),
            record(base.copy(sha256 = "not-a-hash")),
            record(base.copy(size = 0)),
            record(base.copy(size = 128L * 1024 * 1024 + 1)),
            record(base).put("id", -1),
            record(base).put("id", "42"),
            record(base).put("versionCode", base.versionCode + 0.5),
            record(base).put("size", base.size.toString()),
            record(base).put("prerelease", "false"),
            record(base).put("extra", true)
        )
        for (json in invalid) {
            downloads.installationOffered = true
            store(json)
            assertNull(downloads.pending())
            assertFalse(preferences.contains("pending"))
            assertTrue(downloads.automatic)
            assertFalse(downloads.installationOffered)
        }
    }

    @Test fun malformedOversizedNestedAndTrailingRecordsAreDiscarded() {
        for (raw in listOf("not json", " ".repeat(4097), "[{}]", "{\"id\":{}}", "/* comment */{}",
            "[".repeat(1500) + "0" + "]".repeat(1500), record(update).toString() + " trailing")) {
            downloads.installationOffered = true
            assertTrue(preferences.edit().putString("pending", raw).commit())
            assertNull(downloads.pending())
            assertFalse(preferences.contains("pending"))
            assertFalse(downloads.installationOffered)
        }
        assertTrue(preferences.edit().putLong("pending", 42).commit())
        assertNull(downloads.pending())
    }

    @Test fun invalidEnqueueCannotCancelAnExistingOwnedRecord() {
        val candidate = update
        store(record(candidate))
        rejects { downloads.enqueue(candidate.copy(size = 0), automatic = false) }
        rejects { downloads.enqueue(candidate.copy(versionCode = BuildConfig.VERSION_CODE), automatic = true) }
        assertEquals(42L to candidate, downloads.pending())
    }

    @Test fun unownedSnapshotFailsWithoutQueryingAnyRealDownload() {
        store(record(update))
        assertEquals(UpdateDownloadSnapshot(UpdateTransferStatus.FAILED, 0, 0), downloads.snapshot(43))
    }

    @Test fun verifyRequiresMatchingPendingIdAndEveryMetadataFieldBeforeQuerying() {
        val candidate = update
        store(record(candidate))
        rejects { runBlocking { downloads.verify(candidate, 43) } }
        rejects { runBlocking { downloads.verify(candidate.copy(versionName = "Different"), 42) } }
        rejects { runBlocking { downloads.verify(candidate.copy(sha256 = "0".repeat(64)), 42) } }
    }

    @Test fun cancelOnlyClearsOwnNumericStagedFilesAndPendingNotAutomaticOrOtherCache() {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val part = File(directory, "7.part").apply { writeText("fixture") }
        val apk = File(directory, "7.apk").apply { writeText("fixture") }
        val unrelated = File(context.cacheDir, "unrelated.apk").apply { writeText("keep") }
        val notes = File(directory, "notes.txt").apply { writeText("keep") }
        downloads.automatic = true
        downloads.installationOffered = true
        assertTrue(preferences.edit().putString("pending", "malformed").commit())
        downloads.cancel()
        assertFalse(part.exists())
        assertFalse(apk.exists())
        assertEquals("keep", unrelated.readText())
        assertEquals("keep", notes.readText())
        assertTrue(downloads.automatic)
        assertFalse(downloads.installationOffered)
        assertFalse(preferences.contains("pending"))
    }

    @Test fun unverifiedPrivateFileOrMetadataChangeNeverAuthorizesAnInstallIntent() {
        val candidate = update
        store(record(candidate))
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        File(directory, "${candidate.versionCode}.apk").writeBytes(payload)
        rejects { downloads.installIntent(candidate) }
        rejects { downloads.installIntent(candidate.copy(apkUrl = "file:///synthetic.apk")) }
    }

    @Test fun sizeHashAndNonApkRejectionsCloseSourceAndLeaveNoStagedFiles() {
        val candidate = update
        for (bad in listOf(candidate.copy(size = candidate.size - 1),
            candidate.copy(size = candidate.size + 1), candidate.copy(sha256 = "0".repeat(64)), candidate)) {
            var closed = false
            val input = object : ByteArrayInputStream(payload) {
                override fun close() { closed = true; super.close() }
            }
            rejects { copy(bad, openSource = { input }) }
            assertTrue(closed)
            assertNoStagedFiles()
        }
    }

    @Test fun cancellationPropagatesAndClosesStreamAndRemovesPart() {
        var checks = 0
        var closed = false
        val input = object : ByteArrayInputStream(payload) {
            override fun close() { closed = true; super.close() }
        }
        val cancellation = CancellationException("fixture cancellation")
        val caught = try {
            copy(update, openSource = { input }, ensureActive = {
                if (++checks == 3) throw cancellation
            })
            throw AssertionError("Expected cancellation")
        } catch (error: CancellationException) { error }
        assertEquals(cancellation.message, caught.message)
        assertTrue(closed)
        assertNoStagedFiles()
    }

    @Test fun symlinkedCacheDirectoryCannotEscapePrivateUpdateScope() {
        val outside = File(root, "outside").apply { mkdirs() }
        val sentinel = File(outside, "${update.versionCode}.apk").apply { writeText("keep") }
        Os.symlink(outside.absolutePath, File(context.cacheDir, "updates").absolutePath)
        var opened = false
        rejects { copy(update, openSource = { opened = true; payload.inputStream() }) }
        assertFalse(opened)
        assertEquals("keep", sentinel.readText())
    }

    @Test fun installedApkValidatesSignatureAndCopiesToCanonicalPrivatePathOnlyWithTestVersionGate() {
        val source = File(target.applicationInfo.sourceDir)
        val candidate = update.copy(versionCode = BuildConfig.VERSION_CODE,
            size = source.length(), sha256 = hash(source.inputStream()))
        // Production always uses BuildConfig.VERSION_CODE, so this same APK cannot be an upgrade.
        rejects { copy(candidate, installedVersionCode = BuildConfig.VERSION_CODE,
            openSource = { throw AssertionError("Outdated APK must not be opened") }) }
        val result = copy(candidate, installedVersionCode = BuildConfig.VERSION_CODE - 1,
            openSource = { source.inputStream() })
        assertEquals(File(context.cacheDir, "updates/${candidate.versionCode}.apk").canonicalFile, result)
        assertEquals(context.cacheDir.canonicalFile, result.parentFile!!.parentFile)
        assertEquals(candidate.size, result.length())
        assertEquals(candidate.sha256, hash(result.inputStream()))
        assertFalse(File(result.parentFile, "${candidate.versionCode}.part").exists())
        // The isolated helper cannot set the adapter's verified/pending install authorization.
        rejects { downloads.installIntent(candidate) }
    }

    @Test fun archiveVersionAndPackageMismatchAreRejectedEvenWithCorrectSizeAndHash() {
        val source = File(target.applicationInfo.sourceDir)
        val current = update.copy(versionCode = BuildConfig.VERSION_CODE,
            size = source.length(), sha256 = hash(source.inputStream()))
        rejects { copy(current.copy(versionCode = current.versionCode + 1),
            openSource = { source.inputStream() }) }
        assertNoStagedFiles()
        val wrongPackage = object : ContextWrapper(context) {
            override fun getPackageName(): String = "org.example.not.aetherio"
        }
        rejects { runBlocking { withContext(Dispatchers.IO) {
            verifyUpdateApk(wrongPackage, current, BuildConfig.VERSION_CODE - 1,
                openSource = { source.inputStream() }, ensureActive = {})
        } } }
        assertNoStagedFiles()
    }

    @Test fun signerSetsMustBePresentAndMatchExactlyNotJustOverlap() {
        val first = Signature(byteArrayOf(1, 2, 3))
        val second = Signature(byteArrayOf(4, 5, 6))
        assertTrue(updateSignersMatch(arrayOf(first, second), arrayOf(second, first)))
        assertFalse(updateSignersMatch(null, arrayOf(first)))
        assertFalse(updateSignersMatch(arrayOf(first), emptyArray()))
        assertFalse(updateSignersMatch(arrayOf(first), arrayOf(second)))
        assertFalse(updateSignersMatch(arrayOf(first, second), arrayOf(first)))
        assertFalse(updateSignersMatch(arrayOf(Signature(byteArrayOf())), arrayOf(Signature(byteArrayOf()))))
    }

    @Test fun installationOfferedPersistsSeparatelyFromEightFieldPendingRecord() {
        val candidate = update
        store(record(candidate))
        assertFalse(downloads.installationOffered)
        downloads.installationOffered = true
        assertTrue(downloads.installationOffered)
        assertEquals(42L to candidate, downloads.pending())
        assertTrue(downloads.installationOffered)
        val json = JSONObject(requireNotNull(preferences.getString("pending", null)))
        assertEquals(8, json.length())
        assertFalse(json.has("installationOffered"))
        downloads.installationOffered = false
        assertFalse(downloads.installationOffered)
        downloads.installationOffered = true
        assertTrue(preferences.edit().remove("pending").commit())
        assertNull(downloads.pending())
        assertFalse(downloads.installationOffered)
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun isolatedManagerFixtureQueriesAndRemovesWithoutAdapterSanitization() {
        useFakeDownloads = true
        val manager = isolatedManager
        requireNotNull(manager.query(42)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(DownloadManager.STATUS_RUNNING,
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)))
        }
        manager.remove(42)
        assertEquals(listOf(42L), provider.removedIds)
        assertEquals(setOf(999L), provider.ids)
        assertTrue(provider.cursors.all { it.isClosed })
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun obsoletePendingRemovesOnlyOwnedDownloadBeforeClearingState() {
        useFakeDownloads = true
        for (code in listOf(BuildConfig.VERSION_CODE, BuildConfig.VERSION_CODE - 1)) {
            val obsolete = update.copy(versionCode = code)
            val external = externalApk(code).apply { writeText("owned") }
            val unrelated = externalApk(code + 100).apply { writeText("keep") }
            val staged = File(context.cacheDir, "updates/$code.apk").apply {
                parentFile!!.mkdirs(); writeText("owned")
            }
            provider.ids.add(42L)
            provider.externalFiles[42L] = external
            provider.externalFiles[999L] = unrelated
            downloads.automatic = true
            downloads.installationOffered = true
            store(record(obsolete))
            provider.onDelete = {
                assertTrue(preferences.contains("pending"))
                assertTrue(downloads.installationOffered)
                assertTrue(staged.exists())
            }
            assertNull(downloads.pending())
            assertEquals(42L, provider.removedIds.last())
            assertFalse(external.exists())
            assertFalse(staged.exists())
            assertEquals("keep", unrelated.readText())
            assertTrue(provider.ids.contains(999L))
            assertFalse(preferences.contains("pending"))
            assertFalse(downloads.installationOffered)
            assertTrue(downloads.automatic)
        }
        assertEquals(listOf(42L, 42L), provider.removedIds)
    }

    @Test fun obsoletePendingStillClearsLocalAndExternalStateWhenManagerIsUnavailable() {
        managerUnavailable = true
        val obsolete = update.copy(versionCode = BuildConfig.VERSION_CODE)
        val external = externalApk(obsolete.versionCode).apply { writeText("owned") }
        val unrelated = externalApk(obsolete.versionCode + 1).apply { writeText("keep") }
        val staged = File(context.cacheDir, "updates/${obsolete.versionCode}.apk").apply {
            parentFile!!.mkdirs(); writeText("owned")
        }
        store(record(obsolete))
        downloads.automatic = true
        downloads.installationOffered = true
        assertNull(downloads.pending())
        assertFalse(preferences.contains("pending"))
        assertFalse(downloads.installationOffered)
        assertTrue(downloads.automatic)
        assertFalse(external.exists())
        assertFalse(staged.exists())
        assertEquals("keep", unrelated.readText())
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun snapshotMapsDownloadManagerStatesAndClosesEveryCursor() {
        useFakeDownloads = true
        val candidate = update
        store(record(candidate))
        for ((native, expected) in listOf(DownloadManager.STATUS_PENDING to UpdateTransferStatus.WAITING,
            DownloadManager.STATUS_RUNNING to UpdateTransferStatus.RUNNING,
            DownloadManager.STATUS_PAUSED to UpdateTransferStatus.WAITING,
            DownloadManager.STATUS_SUCCESSFUL to UpdateTransferStatus.COMPLETE,
            DownloadManager.STATUS_FAILED to UpdateTransferStatus.FAILED, 9999 to UpdateTransferStatus.FAILED)) {
            provider.status = native
            provider.bytes = if (native == DownloadManager.STATUS_SUCCESSFUL) candidate.size else 5
            provider.total = if (native == DownloadManager.STATUS_SUCCESSFUL) candidate.size else -1
            assertEquals(UpdateDownloadSnapshot(expected, provider.bytes, candidate.size), downloads.snapshot(42))
        }
        assertEquals(6, provider.cursors.size)
        assertTrue(provider.cursors.all { it.isClosed })
        assertTrue(provider.queriedIds.all { it == listOf(42L) })
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun snapshotRejectsCounterBoundsMissingRowsAndClosesMalformedCursors() {
        useFakeDownloads = true
        val candidate = update
        store(record(candidate))
        provider.status = DownloadManager.STATUS_SUCCESSFUL
        for ((bytes, total) in listOf(-1L to candidate.size, candidate.size + 1 to candidate.size,
            0L to -2L, 0L to candidate.size + 1, 0L to candidate.size - 1,
            candidate.size - 1 to candidate.size, candidate.size to -1L)) {
            provider.bytes = bytes
            provider.total = total
            assertEquals(UpdateTransferStatus.FAILED, downloads.snapshot(42).status)
        }
        provider.ids.remove(42L)
        assertEquals(UpdateTransferStatus.FAILED, downloads.snapshot(42).status)
        provider.ids.add(42L)
        provider.missingColumns = true
        rejects { downloads.snapshot(42) }
        assertTrue(provider.cursors.all { it.isClosed })
        assertTrue(provider.queriedIds.all { it == listOf(42L) })
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun cancelRestoredRecordRemovesOwnedIdOnlyAndResetsOfferedNotAutomatic() {
        useFakeDownloads = true
        val candidate = update
        val owned = externalApk(candidate.versionCode).apply { writeText("owned") }
        val unrelated = externalApk(candidate.versionCode + 1).apply { writeText("keep") }
        provider.externalFiles[42L] = owned
        provider.externalFiles[999L] = unrelated
        store(record(candidate))
        downloads.automatic = true
        downloads.installationOffered = true
        assertEquals(42L to candidate, downloads.pending())
        downloads.cancel()
        assertEquals(listOf(42L), provider.removedIds)
        assertEquals(setOf(999L), provider.ids)
        assertFalse(owned.exists())
        assertEquals("keep", unrelated.readText())
        assertNull(downloads.pending())
        assertFalse(downloads.installationOffered)
        assertTrue(downloads.automatic)
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun enqueueReplacesOwnedTransferPersistsMetadataAndResetsOffered() {
        useFakeDownloads = true
        val old = update
        val candidate = old.copy(versionCode = old.versionCode + 1)
        store(record(old))
        val external = externalApk(old.versionCode).apply { writeText("old") }
        provider.externalFiles[42L] = external
        val staged = File(context.cacheDir, "updates/${old.versionCode}.apk").apply {
            parentFile!!.mkdirs(); writeText("old")
        }
        downloads.automatic = true
        downloads.installationOffered = true
        val first = downloads.enqueue(candidate, automatic = true)
        assertEquals(43L, first)
        assertEquals(first to candidate, downloads.pending())
        assertEquals(8, JSONObject(requireNotNull(preferences.getString("pending", null))).length())
        assertFalse(downloads.installationOffered)
        assertFalse(external.exists())
        assertFalse(staged.exists())
        val values = provider.inserted.single()
        assertEquals(candidate.apkUrl, values.getAsString("uri"))
        assertEquals("Aetherio ${candidate.versionName}", values.getAsString("title"))
        assertEquals("application/vnd.android.package-archive", values.getAsString("mimetype"))
        assertEquals(Uri.fromFile(externalApk(candidate.versionCode)).toString(), values.getAsString("hint"))
        assertEquals(DownloadManager.Request.VISIBILITY_VISIBLE, requireNotNull(values.getAsInteger("visibility")).toInt())
        assertFalse(values.getAsBoolean("allow_metered"))
        assertFalse(values.getAsBoolean("allow_roaming"))
        assertFalse(values.keySet().any { it.startsWith("http_header_") })
        downloads.installationOffered = true
        val second = downloads.enqueue(candidate, automatic = false)
        assertEquals(44L, second)
        assertEquals(second to candidate, downloads.pending())
        assertFalse(downloads.installationOffered)
        assertTrue(provider.inserted.last().getAsBoolean("allow_metered"))
        assertFalse(provider.inserted.last().getAsBoolean("allow_roaming"))
        assertTrue(downloads.automatic)
        assertEquals(listOf(42L, 43L), provider.removedIds)
        assertTrue(provider.ids.contains(999L))
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun verifyRejectsIncompleteOrInvalidCountersBeforeOpeningAnySource() {
        useFakeDownloads = true
        val candidate = update
        store(record(candidate))
        provider.bytes = candidate.size
        provider.total = candidate.size
        for (native in listOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_FAILED)) {
            provider.status = native
            rejects { runBlocking { downloads.verify(candidate, 42) } }
        }
        provider.status = DownloadManager.STATUS_SUCCESSFUL
        provider.bytes++
        rejects { runBlocking { downloads.verify(candidate, 42) } }
        assertTrue(provider.sourceIds.isEmpty())
        assertTrue(provider.cursors.all { it.isClosed })
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun completedAdapterVerifiesPrivateApkAndReturnsReadableFileProviderInstallerIntent() {
        useFakeDownloads = true
        val candidate = installedFixture()
        store(record(candidate))
        val adapter = UpdateDownloads(context, installedVersionCode = candidate.versionCode - 1, manager = isolatedManager)
        assertEquals(42L to candidate, adapter.pending())
        assertEquals(UpdateTransferStatus.COMPLETE, adapter.snapshot(42).status)
        val file = runBlocking { adapter.verify(candidate, 42) }
        assertEquals(File(context.cacheDir, "updates/${candidate.versionCode}.apk").canonicalFile, file)
        assertEquals(candidate.sha256, hash(file.inputStream()))
        val intent = adapter.installIntent(candidate)
        val uri = requireNotNull(intent.data)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("content", uri.scheme)
        assertEquals("${target.packageName}.updates", uri.authority)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(1, requireNotNull(intent.clipData).itemCount)
        assertEquals(uri, requireNotNull(intent.clipData).getItemAt(0).uri)
        assertEquals("content", requireNotNull(provider.sourceUri).scheme)
        assertEquals("${target.packageName}.updates", requireNotNull(provider.sourceUri).authority)
        // Mutating the DownloadManager source after verification cannot change the install input.
        File(root, "source.apk").writeText("replaced source")
        assertEquals(candidate.sha256, hash(requireNotNull(target.contentResolver.openInputStream(uri))))
        val clipUri = requireNotNull(intent.clipData).getItemAt(0).uri
        assertEquals(candidate.sha256, hash(requireNotNull(target.contentResolver.openInputStream(clipUri))))
        assertEquals(listOf(42L), provider.sourceIds)
        assertTrue(provider.cursors.all { it.isClosed })
        assertFalse(adapter.installationOffered)
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun completedRestoreRetainsOfferedFlagAndRequiresReverificationForInstallerIntent() {
        useFakeDownloads = true
        val candidate = installedFixture()
        store(record(candidate))
        val first = UpdateDownloads(context, candidate.versionCode - 1, manager = isolatedManager)
        runBlocking { first.verify(candidate, 42) }
        first.automatic = true
        first.installationOffered = true
        val restored = UpdateDownloads(context, candidate.versionCode - 1, manager = isolatedManager)
        assertEquals(42L to candidate, restored.pending())
        assertTrue(restored.automatic)
        assertTrue(restored.installationOffered)
        assertEquals(UpdateTransferStatus.COMPLETE, restored.snapshot(42).status)
        rejects { restored.installIntent(candidate) }
        runBlocking { restored.verify(candidate, 42) }
        assertEquals("content", requireNotNull(restored.installIntent(candidate).data).scheme)
        assertTrue(restored.installationOffered)
        assertEquals(listOf(42L, 42L), provider.sourceIds)
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun adapterRejectsSourceSizeOrHashMismatchAndNeverAuthorizesInstallation() {
        useFakeDownloads = true
        val candidate = installedFixture()
        for (bad in listOf(candidate.copy(size = candidate.size + 1), candidate.copy(sha256 = "0".repeat(64)))) {
            provider.bytes = bad.size
            provider.total = bad.size
            store(record(bad))
            val adapter = UpdateDownloads(context, candidate.versionCode - 1, manager = isolatedManager)
            rejects { runBlocking { adapter.verify(bad, 42) } }
            rejects { adapter.installIntent(bad) }
            assertNoStagedFiles()
        }
        assertEquals(listOf(42L, 42L), provider.sourceIds)
        assertTrue(provider.cursors.all { it.isClosed })
    }

    private fun externalApk(code: Int): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates/$code.apk").apply {
            parentFile!!.mkdirs()
        }

    private fun installedFixture(): AppUpdate {
        val source = File(target.applicationInfo.sourceDir).copyTo(File(root, "source.apk"))
        val candidate = update.copy(versionCode = BuildConfig.VERSION_CODE, versionName = BuildConfig.VERSION_NAME,
            size = source.length(), sha256 = hash(source.inputStream()))
        provider.sourceUri = FileProvider.getUriForFile(target, "${target.packageName}.updates", source)
        provider.status = DownloadManager.STATUS_SUCCESSFUL
        provider.bytes = candidate.size
        provider.total = candidate.size
        return candidate
    }

    private class RecordingRequest(uri: Uri) : DownloadManager.Request(uri) {
        val values = ContentValues().apply { put("uri", uri.toString()) }

        override fun setDestinationInExternalFilesDir(context: Context, dirType: String?, subPath: String?): DownloadManager.Request =
            super.setDestinationInExternalFilesDir(context, dirType, subPath).also {
                val file = File(requireNotNull(context.getExternalFilesDir(dirType)), requireNotNull(subPath))
                values.put("hint", Uri.fromFile(file).toString())
            }
        override fun setTitle(title: CharSequence?): DownloadManager.Request =
            super.setTitle(title).also { values.put("title", title?.toString()) }
        override fun setMimeType(mimeType: String?): DownloadManager.Request =
            super.setMimeType(mimeType).also { values.put("mimetype", mimeType) }
        override fun setNotificationVisibility(visibility: Int): DownloadManager.Request =
            super.setNotificationVisibility(visibility).also { values.put("visibility", visibility) }
        override fun setAllowedOverMetered(allow: Boolean): DownloadManager.Request =
            super.setAllowedOverMetered(allow).also { values.put("allow_metered", allow) }
        override fun setAllowedOverRoaming(allowed: Boolean): DownloadManager.Request =
            super.setAllowedOverRoaming(allowed).also { values.put("allow_roaming", allowed) }
        override fun addRequestHeader(header: String?, value: String?): DownloadManager.Request =
            throw AssertionError("Update downloads must not include request headers")
    }

    private inner class FakeDownloadsProvider : ContentProvider() {
        var rowId = 42L
        var status = DownloadManager.STATUS_RUNNING
        var bytes = 0L
        var total = -1L
        var missingColumns = false
        var sourceUri: Uri? = null
        var onDelete: () -> Unit = {}
        val ids = mutableSetOf(42L, 999L)
        val externalFiles = mutableMapOf<Long, File>()
        val removedIds = mutableListOf<Long>()
        val queriedIds = mutableListOf<List<Long>>()
        val sourceIds = mutableListOf<Long>()
        val cursors = mutableListOf<MatrixCursor>()
        val inserted = mutableListOf<ContentValues>()

        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/vnd.android.package-archive"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            assertEquals("downloads", uri.authority)
            val requested = requireNotNull(selectionArgs).map { it.toLong() }
            assertTrue(requested.isNotEmpty())
            queriedIds.add(requested)
            val columns = if (missingColumns) arrayOf(DownloadManager.COLUMN_ID, DownloadManager.COLUMN_STATUS)
                else arrayOf(DownloadManager.COLUMN_ID, DownloadManager.COLUMN_STATUS,
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR, DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            return MatrixCursor(columns).also { cursor ->
                if (rowId in requested && rowId in ids) {
                    cursor.addRow(if (missingColumns) arrayOf<Any>(rowId, status)
                        else arrayOf<Any>(rowId, status, bytes, total))
                }
                cursors.add(cursor)
            }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            assertEquals("downloads", uri.authority)
            inserted.add(ContentValues(requireNotNull(values)))
            rowId++
            ids.add(rowId)
            return Uri.withAppendedPath(uri, rowId.toString())
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            assertEquals("downloads", uri.authority)
            val requested = requireNotNull(selectionArgs).map { it.toLong() }
            assertTrue(requested.isNotEmpty())
            onDelete()
            requested.forEach { id ->
                removedIds.add(id)
                ids.remove(id)
                externalFiles.remove(id)?.delete()
            }
            return requested.size
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<out String>?): Int = throw AssertionError("Unexpected provider update")
    }

    private fun record(candidate: AppUpdate): JSONObject = JSONObject().put("id", 42L)
        .put("versionCode", candidate.versionCode).put("versionName", candidate.versionName)
        .put("releaseUrl", candidate.releaseUrl).put("prerelease", candidate.prerelease)
        .put("apkUrl", candidate.apkUrl).put("sha256", candidate.sha256).put("size", candidate.size)

    private fun store(record: JSONObject) {
        assertTrue(preferences.edit().putString("pending", record.toString()).commit())
    }

    private fun hash(input: InputStream): String = input.use {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun copy(
        candidate: AppUpdate,
        installedVersionCode: Int = BuildConfig.VERSION_CODE,
        openSource: () -> InputStream = { payload.inputStream() },
        ensureActive: () -> Unit = {}
    ): File = runBlocking { withContext(Dispatchers.IO) {
        verifyUpdateApk(context, candidate, installedVersionCode, openSource, ensureActive)
    } }

    private fun assertNoStagedFiles() {
        assertTrue(File(context.cacheDir, "updates").listFiles().orEmpty().isEmpty())
    }

    private fun rejects(block: () -> Unit) {
        val error = try {
            block()
            throw AssertionError("Expected sanitized update failure")
        } catch (error: IOException) { error }
        // Coroutine stack recovery can copy the sanitized exception with itself as its cause.
        generateSequence<Throwable>(error) { it.cause }.forEach {
            assertEquals(IOException::class.java, it.javaClass)
            assertEquals("Unable to prepare update", it.message)
            assertTrue(it.suppressed.isEmpty())
        }
    }
}
