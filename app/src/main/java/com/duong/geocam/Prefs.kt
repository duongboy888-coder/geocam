package com.duong.geocam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("geocam", Context.MODE_PRIVATE)

    private fun str(k: String) = sp.getString(k, "") ?: ""
    private fun putStr(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var title: String get() = str("title"); set(v) = putStr("title", v)
    var customLines: String get() = str("lines"); set(v) = putStr("lines", v)
    var note: String get() = str("note"); set(v) = putStr("note", v)
    var showDate: Boolean get() = bool("date", true); set(v) = putBool("date", v)
    var showAddress: Boolean get() = bool("addr", true); set(v) = putBool("addr", v)
    var showCoords: Boolean get() = bool("coords", true); set(v) = putBool("coords", v)
    var showMap: Boolean get() = bool("map", true); set(v) = putBool("map", v)
    var showLogo: Boolean get() = bool("logo", false); set(v) = putBool("logo", v)
    var flashMode: Int get() = sp.getInt("flash", 0); set(v) = sp.edit().putInt("flash", v).apply()
    var nameByInfo: Boolean get() = bool("fname", true); set(v) = putBool("fname", v)
    var folder: String
        get() = str("folder").ifBlank { "GeoCam" }
        set(v) = putStr("folder", v)

    companion object {
        private fun logoFile(ctx: Context) = File(ctx.filesDir, "logo.png")

        fun loadLogo(ctx: Context): Bitmap? {
            val f = logoFile(ctx)
            return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }

        fun saveLogo(ctx: Context, uri: Uri): Boolean = try {
            val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (src == null) false else {
                val max = 600f
                val scale = minOf(1f, max / maxOf(src.width, src.height))
                val bmp = if (scale < 1f) Bitmap.createScaledBitmap(
                    src, (src.width * scale).toInt(), (src.height * scale).toInt(), true
                ) else src
                FileOutputStream(logoFile(ctx)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                true
            }
        } catch (e: Exception) {
            false
        }

        fun removeLogo(ctx: Context) {
            logoFile(ctx).delete()
        }
    }
}
