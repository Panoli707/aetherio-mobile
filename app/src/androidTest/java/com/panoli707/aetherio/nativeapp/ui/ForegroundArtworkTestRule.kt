package com.panoli707.aetherio.nativeapp.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Xiaomi blocks ActivityScenario's first background start even under instrumentation.
 * Launch only the debug fixture through shell, without granting permissions or touching app data.
 */
internal class ForegroundArtworkTestRule : ExternalResource() {
    override fun before() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val command = "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
            "-n $packageName/androidx.activity.ComponentActivity"
        val output = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        check(!output.contains("Error:")) { "Could not foreground the test fixture: $output" }
        instrumentation.waitForIdleSync()
    }
}
