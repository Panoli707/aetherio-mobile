package com.panoli707.aetherio.nativeapp.data

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val identifier = UUID.randomUUID().toString()
    private val preferenceName = "aetherio_updates.test.$identifier"
    private val root = File(target.cacheDir, "updates/test-$identifier").apply { check(mkdirs()) }
    private val context = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            check(name == "aetherio_updates")
            return target.getSharedPreferences(preferenceName, mode)
        }
        override fun getCacheDir(): File = File(root, "cache").apply { check(isDirectory || mkdirs()) }
        override fun getExternalFilesDir(type: String?): File = File(root, "external/${type.orEmpty()}")
            .apply { check(isDirectory || mkdirs()) }
        override fun getSystemService(name: String): Any? {
            check(name != Context.DOWNLOAD_SERVICE) { "Real downloads are forbidden" }
            return super.getSystemService(name)
        }
    }
    private val queryEntered = CountDownLatch(1)
    private val releaseQuery = CountDownLatch(1)
    private val uriEntered = CountDownLatch(1)
    private val releaseUri = CountDownLatch(1)
    private val store = ViewModelStore()
    private lateinit var model: UpdateViewModel
    private lateinit var candidate: AppUpdate

    @After fun cleanUp() {
        releaseQuery.countDown()
        releaseUri.countDown()
        var job: Job? = null
        instrumentation.runOnMainSync {
            if (::model.isInitialized) job = model.viewModelScope.coroutineContext[Job]
            store.clear()
        }
        runBlocking { withTimeout(30_000) { job?.join() } }
        target.deleteSharedPreferences(preferenceName)
        root.deleteRecursively()
    }

    @Test fun permissionReturnBeforeRestoredVerificationQueuesExplicitInstall() {
        startModel()
        assertTrue(queryEntered.await(30, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            assertFalse(model.state.value.ready)
            model.requestInstall()
            assertFalse(model.state.value.installRequested)
        }
        releaseQuery.countDown()
        assertTrue(uriEntered.await(30, TimeUnit.SECONDS))
        assertTrue(model.state.value.verifying)
        assertFalse(model.state.value.ready)
        assertFalse(model.state.value.installRequested)
        releaseUri.countDown()
        assertTrue(awaitReady().installRequested)
    }

    @Test fun alreadyOfferedRestoreDoesNotRequestInstallUntilExplicitRequest() {
        startModel()
        assertTrue(queryEntered.await(30, TimeUnit.SECONDS))
        releaseQuery.countDown()
        assertTrue(uriEntered.await(30, TimeUnit.SECONDS))
        assertFalse(model.state.value.ready)
        assertFalse(model.state.value.installRequested)
        releaseUri.countDown()
        assertFalse(awaitReady().installRequested)
        instrumentation.runOnMainSync {
            assertFalse(model.state.value.installRequested)
            model.requestInstall()
            assertTrue(model.state.value.ready)
            assertTrue(model.state.value.installRequested)
        }
    }

    private fun startModel() {
        val source = File(target.applicationInfo.sourceDir).copyTo(File(root, "source.apk"))
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        candidate = AppUpdate(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME,
            "https://github.com/Panoli707/aetherio-mobile/releases/tag/v-test", false,
            "https://github.com/Panoli707/aetherio-mobile/releases/download/v-test/Aetherio-test.apk",
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }, source.length())
        val record = JSONObject().put("id", 42L).put("versionCode", candidate.versionCode)
            .put("versionName", candidate.versionName).put("releaseUrl", candidate.releaseUrl)
            .put("prerelease", candidate.prerelease).put("apkUrl", candidate.apkUrl)
            .put("sha256", candidate.sha256).put("size", candidate.size)
        assertEquals(8, record.length())
        assertTrue(context.getSharedPreferences("aetherio_updates", Context.MODE_PRIVATE).edit()
            .putString("pending", record.toString()).putBoolean("installationOffered", true).commit())
        val sourceUri = FileProvider.getUriForFile(target, "${target.packageName}.updates", source)
        val manager = object : UpdateDownloadManager {
            override fun request(uri: Uri): DownloadManager.Request = error("No network requests expected")
            override fun enqueue(request: DownloadManager.Request): Long = error("No downloads expected")
            override fun remove(id: Long) { throw AssertionError("Restored download must be retained") }
            override fun query(id: Long): Cursor {
                assertEquals(42L, id)
                queryEntered.countDown()
                check(releaseQuery.await(30, TimeUnit.SECONDS))
                return MatrixCursor(arrayOf(DownloadManager.COLUMN_STATUS,
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).apply {
                    addRow(arrayOf<Any>(DownloadManager.STATUS_SUCCESSFUL, candidate.size, candidate.size))
                }
            }
            override fun getUriForDownloadedFile(id: Long): Uri {
                assertEquals(42L, id)
                uriEntered.countDown()
                check(releaseUri.await(30, TimeUnit.SECONDS))
                return sourceUri
            }
        }
        val downloads = UpdateDownloads(context, BuildConfig.VERSION_CODE - 1, manager)
        instrumentation.runOnMainSync {
            model = UpdateViewModel(target.applicationContext as Application, UpdateRepository(), downloads)
            store.put("update", model)
        }
    }

    private fun awaitReady(): UpdateState {
        val state = runBlocking { withTimeout(30_000) {
            model.state.first { it.ready || it.error != null }
        } }
        assertNull(state.error)
        assertTrue(state.ready)
        assertFalse(state.downloading)
        assertFalse(state.verifying)
        assertEquals(candidate, state.available)
        assertTrue(File(context.cacheDir, "updates/${candidate.versionCode}.apk").isFile)
        assertTrue(context.getSharedPreferences("aetherio_updates", Context.MODE_PRIVATE)
            .getBoolean("installationOffered", false))
        return state
    }
}
