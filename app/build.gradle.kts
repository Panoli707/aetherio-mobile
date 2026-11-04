import java.util.Properties
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Reversible APK obfuscation, not a secret boundary. Never cache generated key material remotely.
abstract class GenerateTmdbMaterial : DefaultTask() {
    @get:Internal abstract val token: Property<String>
    @get:Input abstract val fingerprint: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        val plaintext = token.get().toByteArray(Charsets.UTF_8)
        val key = ByteArray(32)
        val mask = ByteArray(32)
        try {
            val encoded = if (plaintext.isEmpty()) "" else {
                val random = SecureRandom()
                random.nextBytes(key)
                random.nextBytes(mask)
                val nonce = ByteArray(12).also(random::nextBytes)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD("com.panoli707.aetherio.nativeapp.tmdb.v1".toByteArray(Charsets.US_ASCII))
                val masked = ByteArray(32) { (key[it].toInt() xor mask[it].toInt()).toByte() }
                (byteArrayOf(1) + mask + masked + nonce + cipher.doFinal(plaintext))
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
            }
            val file = outputDirectory.file("com/panoli707/aetherio/nativeapp/data/EmbeddedTmdbMaterial.java").get().asFile
            file.parentFile.mkdirs()
            file.writeText("""
                package com.panoli707.aetherio.nativeapp.data;
                // Generated obfuscated material; all reconstruction data is present in the APK.
                final class EmbeddedTmdbMaterial {
                    static final String DATA = "$encoded";
                    private EmbeddedTmdbMaterial() {}
                }
            """.trimIndent() + "\n", Charsets.UTF_8)
        } finally {
            plaintext.fill(0)
            key.fill(0)
            mask.fill(0)
        }
    }
}

val localConfiguration = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val publicDistribution = providers.gradleProperty("publicDistribution").map { it.toBooleanStrict() }.getOrElse(false)
val tmdbReadAccessToken = providers.environmentVariable("TMDB_READ_ACCESS_TOKEN")
    .orElse(localConfiguration.getProperty("tmdb.readAccessToken", "")).get().trim()
require(tmdbReadAccessToken.length <= 16_384 && tmdbReadAccessToken.matches(Regex("[A-Za-z0-9._-]*"))) {
    "La credencial local de TMDB tiene un formato no valido."
}
require(!publicDistribution || tmdbReadAccessToken.isNotEmpty()) {
    "La distribucion publica requiere configurar el token local de lectura de TMDB."
}

val generateTmdbMaterial = tasks.register<GenerateTmdbMaterial>("generateTmdbMaterial") {
    token.set(tmdbReadAccessToken)
    fingerprint.set(MessageDigest.getInstance("SHA-256").digest(tmdbReadAccessToken.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) })
    outputDirectory.set(layout.buildDirectory.dir("generated/tmdbMaterial"))
    outputs.cacheIf { false }
}

android {
    namespace = "com.panoli707.aetherio.nativeapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.panoli707.aetherio.nativeapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 30
        versionName = "0.15.1"
        manifestPlaceholders["tmdbCredentialsEmbedded"] = tmdbReadAccessToken.isNotEmpty()
        testInstrumentationRunner = when {
            providers.gradleProperty("scrollReleaseSmoke").orNull == "true" ->
                "com.panoli707.aetherio.nativeapp.ui.ScrollPerformanceInstrumentation"
            providers.gradleProperty("settingsReleaseSmoke").orNull == "true" ->
                "com.panoli707.aetherio.nativeapp.ui.SettingsReleaseInstrumentation"
            providers.gradleProperty("torrentReleaseSmoke").orNull == "true" ->
                "com.panoli707.aetherio.nativeapp.player.torrent.TorrentReleaseInstrumentation"
            else -> "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main").java.srcDir(generateTmdbMaterial.flatMap { it.outputDirectory })
}

tasks.named("preBuild").configure { dependsOn(generateTmdbMaterial) }

dependencies {
    implementation(project(":torrent-engine"))
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
