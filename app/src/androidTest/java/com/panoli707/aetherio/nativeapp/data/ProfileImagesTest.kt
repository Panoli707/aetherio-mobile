package com.panoli707.aetherio.nativeapp.data

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileImagesTest {
    @Test fun galleryImageIsCopiedOrientedPersistedAndResetWithoutDependingOnTheOriginalUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "qa-profile-${UUID.randomUUID()}"
        val fixture = File(context.cacheDir, "$name.jpg")
        val bitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        fixture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        ExifInterface(fixture).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg"); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg") }))
        val store = ProfileStore(context, name)
        var imported: File? = null
        var galleryDeleted = false
        try {
            context.contentResolver.openOutputStream(uri)!!.use { output -> fixture.inputStream().use { it.copyTo(output) } }
            val path = store.import(uri, avatar = true)
            imported = File(path)
            assertTrue(imported.isFile)
            val decoded = requireNotNull(BitmapFactory.decodeFile(path))
            assertEquals(60, decoded.width); assertEquals(120, decoded.height); decoded.recycle()
            store.save(ProfileAppearance(avatarPath = path))
            assertEquals(path, ProfileStore(context, name).load().avatarPath)
            context.contentResolver.delete(uri, null, null)
            galleryDeleted = true
            assertTrue("The copied avatar must not depend on gallery access", imported.isFile)
            store.save(ProfileAppearance())
            assertNull(store.load().avatarPath)
            assertFalse(imported.exists())
        } finally {
            if (!galleryDeleted) context.contentResolver.delete(uri, null, null)
            imported?.delete(); fixture.delete(); context.deleteSharedPreferences(name)
        }
    }
    @Test fun profileImportRejectsFileUrisAndKeepsPreviousAppearance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "qa-profile-${UUID.randomUUID()}"
        val store = ProfileStore(context, name)
        try {
            store.save(ProfileAppearance(avatarStyle = 3, bannerStyle = 2))
            assertThrows(IllegalArgumentException::class.java) { store.import(android.net.Uri.parse("file:///etc/hosts"), true) }
            assertEquals(ProfileAppearance(avatarStyle = 3, bannerStyle = 2), store.load())
        } finally { context.deleteSharedPreferences(name) }
    }
}
