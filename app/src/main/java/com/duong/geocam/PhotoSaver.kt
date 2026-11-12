package com.duong.geocam

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoSaver {
    /** Lưu JPEG vào DCIM/GeoCam, kèm EXIF ngày giờ + GPS. */
    /**
     * Tên file kiểu Timemark:
     * 20261112_163834, Đặng Châu Tuệ_BKS-50H93384-Dương.jpg
     */
    fun buildName(p: Prefs, time: Long, street: String?): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(time))
        if (!p.nameByInfo) return "GeoCam_$ts"
        val parts = mutableListOf<String>()
        if (!street.isNullOrBlank()) parts += street.trim()
        p.customLines.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { parts += it.replace(Regex("\\s*:\\s*"), "-") }
        var name = ts
        if (parts.isNotEmpty()) name += ", " + parts.joinToString("_")
        if (p.title.isNotBlank()) name += "-" + p.title.trim()
        return clean(name).take(180).trim()
    }

    fun cleanFolder(s: String) = clean(s).replace("..", "_").trim().ifBlank { "GeoCam" }

    private fun clean(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")

    fun save(ctx: Context, bmp: Bitmap, time: Long, loc: Location?, comment: String,
             baseName: String, folder: String): Uri? {
        val name = "$baseName.jpg"
        val tmp = File(ctx.cacheDir, "shot_$time.jpg")
        try {
            FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            ExifInterface(tmp.absolutePath).apply {
                val dt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(time))
                setAttribute(ExifInterface.TAG_DATETIME, dt)
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dt)
                setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dt)
                setAttribute(ExifInterface.TAG_SOFTWARE, "GeoCam")
                if (comment.isNotBlank()) setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, comment)
                if (loc != null) setGpsInfo(loc)
                saveAttributes()
            }

            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/" + cleanFolder(folder))
                put(MediaStore.Images.Media.DATE_TAKEN, time)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } finally {
            tmp.delete()
        }
    }
}
