import org.gradle.api.file.RelativePath
import java.security.MessageDigest

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Recompile the unmodified upstream Kotlin wrapper with Aetherio's Kotlin 2.1.
// Only the checksum-verified native libraries are extracted from the upstream AAR.
val upstreamAar = rootProject.file("app/libs/lib-nuvio-engine-android-0.1.6.aar")
val extractNative by tasks.registering(Sync::class) {
    doFirst {
        val digest = MessageDigest.getInstance("SHA-256").digest(upstreamAar.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(digest == "66b049a3625bfdc39c3b5d25a0af9bbc127c530512617e9f57cd988c019d33d6") { "Nuvio AAR checksum mismatch" }
    }
    from(zipTree(upstreamAar)) {
        include("jni/**")
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/nuvio-jni"))
}

android {
    namespace = "com.nuvio.engine"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main").jniLibs.srcDir(extractNative.map { it.destinationDir })
}
tasks.named("preBuild") { dependsOn(extractNative) }
dependencies { api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2") }
