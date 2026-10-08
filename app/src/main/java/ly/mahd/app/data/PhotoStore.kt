package ly.mahd.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import ly.mahd.app.logic.PhotoRules
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The children's photos, as JPEG files in the app's private folder ([dir] = files/photos): other apps and the
 * phone's gallery cannot see them. They are left out of Android's automatic backup on purpose (it stops backing up
 * anything once an app has more than 25 MB) and out of the backup file; [exportToGallery] lets the mother keep copies.
 *
 * Pictures are made smaller on the way in (longest side [MAX_SIDE] px, about 0.3–0.5 MB) and saved without their
 * EXIF data, so the place where a photo was taken is not kept. All functions here do file work: call them off the
 * main thread.
 */
class PhotoStore(private val dir: File) {

    /** A picture copied into the album: its file name and the day it was taken, if the picture says. */
    data class Imported(val file: String, val dateTaken: LocalDate?)

    /** Screen-sized copies, so scrolling the album does not decode the same files again and again. */
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun file(name: String) = File(dir, name)

    /** Copies one picture into the album. [open] must give a fresh stream each time it is called. */
    fun import(open: () -> InputStream?): Imported {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        requireNotNull(open()) { "unreadable" }.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(max(bounds.outWidth, bounds.outHeight), MAX_SIDE)
        }
        val decoded = requireNotNull(open()?.use { BitmapFactory.decodeStream(it, null, options) }) { "not an image" }
        val exif = open()?.use { runCatching { ExifInterface(it) }.getOrNull() }
        val upright = rotate(scaleDown(decoded, MAX_SIDE), exif?.rotationDegrees() ?: 0)

        dir.mkdirs()
        val name = "${UUID.randomUUID()}.jpg"
        FileOutputStream(File(dir, name)).use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        val taken = exif?.let {
            PhotoRules.dateFromExif(it.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: it.getAttribute(ExifInterface.TAG_DATETIME))
        }
        return Imported(name, taken)
    }

    /** A copy for the screen whose shorter side is at least [minSide] px (or the whole photo if smaller), cached. */
    fun load(name: String, minSide: Int): Bitmap? {
        val key = "$name@$minSide"
        cache.get(key)?.let { return it }
        val f = File(dir, name)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(min(bounds.outWidth, bounds.outHeight), minSide)
        }
        val bitmap = BitmapFactory.decodeFile(f.path, options) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    fun delete(name: String) {
        cache.snapshot().keys.filter { it.startsWith("$name@") }.forEach { cache.remove(it) }
        File(dir, name).delete()
    }

    /**
     * Deletes every file nobody points at any more (after a profile is deleted or a backup restored). With
     * [minAgeMillis], only files at least that old, so a photo being imported right now is never touched.
     */
    fun deleteAllExcept(keep: Set<String>, minAgeMillis: Long = 0) {
        val newest = System.currentTimeMillis() - minAgeMillis
        dir.listFiles()?.filter { it.name !in keep && it.lastModified() <= newest }?.forEach { delete(it.name) }
    }

    /**
     * Copies [photos] into the phone's gallery (album "Atwar"), where Google Photos and phone transfers can pick
     * them up. Android 9 and older need the storage permission first. Returns how many were copied.
     */
    fun exportToGallery(context: Context, photos: List<Photo>): Int = photos.count { photo ->
        val source = File(dir, photo.file)
        if (!source.exists()) return@count false
        val name = "atwar-${photo.date}-${photo.id}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@count false
            val written = resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) }; true } ?: false
            if (!written) {
                resolver.delete(uri, null, null)
                return@count false
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } else {
            @Suppress("DEPRECATION")
            val album = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), GALLERY_ALBUM)
            album.mkdirs()
            val target = File(album, name)
            source.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf("image/jpeg"), null)
        }
        true
    }

    private fun ExifInterface.rotationDegrees(): Int = when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    companion object {
        /** Longest side of a stored photo, in pixels: sharp on any phone screen, small on disk. */
        const val MAX_SIDE = 1600
        private const val JPEG_QUALITY = 85
        private const val GALLERY_ALBUM = "Atwar"

        /** The biggest power of two that still leaves [side] at least [target] px after decoding. */
        fun sampleSize(side: Int, target: Int): Int {
            var sample = 1
            while (side / (sample * 2) >= target) sample *= 2
            return sample
        }

        private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
            val longest = max(bitmap.width, bitmap.height)
            if (longest <= maxSide) return bitmap
            val ratio = maxSide.toFloat() / longest
            return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt(), (bitmap.height * ratio).roundToInt(), true)
        }

        private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
            if (degrees == 0) return bitmap
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
