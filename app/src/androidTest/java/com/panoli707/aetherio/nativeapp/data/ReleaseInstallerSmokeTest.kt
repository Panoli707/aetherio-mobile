package com.panoli707.aetherio.nativeapp.data

import android.content.ClipData
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.MainActivity
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit, emulator-only validation using Android's installer and its normal protection checks. */
@RunWith(AndroidJUnit4::class)
class ReleaseInstallerSmokeTest {
    @Suppress("DEPRECATION")
    @Test fun openVerifiedReleaseInSystemInstaller() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("manualReleaseInstall") == "true")
        assumeTrue(Build.MODEL.contains("sdk", ignoreCase = true))
        val context = instrumentation.targetContext
        assumeTrue((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        val source = File(context.cacheDir, "validation/Aetherio.apk")
        val code = requireNotNull(arguments.getString("releaseVersionCode")).toInt()
        val version = requireNotNull(arguments.getString("releaseVersionName"))
        val hash = requireNotNull(arguments.getString("releaseSha256"))
        assertTrue("The staged release APK must be readable", source.isFile && source.canRead())
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        assertEquals("Staged release digest", hash, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
        val archive = context.packageManager.getPackageArchiveInfo(source.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        assertNotNull("Android must parse the release APK", archive)
        assertEquals(context.packageName, archive!!.packageName)
        assertEquals(code.toLong(), archive.longVersionCode)
        val current = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        assertTrue("Release signer must match the installed application",
            updateSignersMatch(current.signingInfo?.apkContentsSigners, archive.signingInfo?.apkContentsSigners))
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode.toInt() else installed.versionCode
        val update = AppUpdate(code, version,
            "https://github.com/Panoli707/aetherio-mobile/releases/tag/v$version-preview.1", true,
            "https://github.com/Panoli707/aetherio-mobile/releases/download/v$version-preview.1/Aetherio.apk",
            hash, source.length())
        assertTrue("Grant Aetherio's installation-source permission using Android Settings first",
            Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls())
        val verified = withContext(Dispatchers.IO) {
            verifyUpdateApk(context, update, installedCode, source::inputStream, {})
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", verified)
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync {
            activity.startActivity(Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri("Aetherio release", uri) })
        }
        SystemClock.sleep(1000)
    }
}
