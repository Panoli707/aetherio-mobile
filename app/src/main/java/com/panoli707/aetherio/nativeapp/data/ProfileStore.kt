package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

data class ProfileAppearance(val avatarStyle: Int = 0, val bannerStyle: Int = 0,
    val avatarPath: String? = null, val bannerPath: String? = null)

internal class ProfileStore(private val context: Context, storageName: String = "aetherio_profile_images") {
    private val preferences = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
    private val directory get() = File(context.filesDir, "profile-images").apply { check(mkdirs() || isDirectory) }
    private fun path(name: String?): String? = name?.takeIf { it.matches(Regex("[a-f0-9-]{36}\\.jpg")) }
        ?.let { File(directory, it) }?.takeIf(File::isFile)?.absolutePath
    fun load() = ProfileAppearance(preferences.getInt("avatarStyle", 0).coerceIn(0, 7), preferences.getInt("bannerStyle", 0).coerceIn(0, 5),
        path(preferences.getString("avatar", null)), path(preferences.getString("banner", null)))
    fun save(value: ProfileAppearance) {
        fun name(path: String?): String? = path?.let { File(it) }?.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile && it.isFile }?.name
        require(value.avatarStyle in 0..7 && value.bannerStyle in 0..5)
        val previous = listOfNotNull(preferences.getString("avatar", null), preferences.getString("banner", null))
        val next = listOfNotNull(name(value.avatarPath), name(value.bannerPath))
        check(preferences.edit().putInt("avatarStyle", value.avatarStyle).putInt("bannerStyle", value.bannerStyle)
            .putString("avatar", name(value.avatarPath)).putString("banner", name(value.bannerPath)).commit())
        previous.filterNot { it in next }.forEach { old -> path(old)?.let { File(it).delete() } }
    }
    /** Picker URI is copied, decoded with a size bound, and re-encoded. No broad gallery permission or durable external URI. */
    fun import(uri: Uri, avatar: Boolean): String {
        require(uri.scheme == "content")
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                require(output.size() + size <= 12 * 1024 * 1024) { "IMAGE_TOO_LARGE" }
                output.write(buffer, 0, size)
            }
            output.toByteArray()
        } ?: error("IMAGE_READ")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 1..20_000 && options.outHeight in 1..20_000 && options.outWidth.toLong() * options.outHeight <= 100_000_000)
        val limit = if (avatar) 768 else 2048
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > limit) options.inSampleSize *= 2
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
        val orientation = runCatching { ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply { when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        } }
        val bitmap = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { decoded.recycle() }
        val destination = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            return destination.absolutePath
        } catch (error: Exception) { destination.delete(); throw error }
        finally { bitmap.recycle() }
    }
}
