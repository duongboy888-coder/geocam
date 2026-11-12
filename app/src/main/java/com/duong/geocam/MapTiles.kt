package com.duong.geocam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Ghép ô bản đồ OpenStreetMap quanh vị trí, có ghim đỏ ở giữa. Không có mạng -> null. */
object MapTiles {
    fun fetch(lat: Double, lon: Double, zoom: Int = 16, size: Int = 256): Bitmap? = try {
        val n = 1 shl zoom
        val px = (lon + 180.0) / 360.0 * n * 256
        val latR = Math.toRadians(lat)
        val py = (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * n * 256
        val left = px - size / 2.0
        val top = py - size / 2.0

        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.LTGRAY)
        var any = false
        for (tx in floor(left / 256).toInt()..floor((left + size - 1) / 256).toInt()) {
            for (ty in floor(top / 256).toInt()..floor((top + size - 1) / 256).toInt()) {
                if (ty < 0 || ty >= n) continue
                val wx = ((tx % n) + n) % n
                val tile = download(zoom, wx, ty) ?: continue
                any = true
                c.drawBitmap(tile, (tx * 256 - left).toFloat(), (ty * 256 - top).toFloat(), null)
                tile.recycle()
            }
        }
        if (!any) null else {
            val cx = size / 2f
            val cy = size / 2f
            c.drawCircle(cx, cy, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            c.drawCircle(cx, cy, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(229, 57, 53) })
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.DKGRAY }
            val label = "© OpenStreetMap"
            val tw = tp.measureText(label)
            c.drawRect(size - tw - 6f, size - 15f, size.toFloat(), size.toFloat(),
                Paint().apply { color = Color.argb(180, 255, 255, 255) })
            c.drawText(label, size - tw - 3f, size - 4f, tp)
            out
        }
    } catch (e: Exception) {
        null
    }

    private fun download(z: Int, x: Int, y: Int): Bitmap? {
        val conn = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("User-Agent", "GeoCam-Android/1.0 (personal app)")
        return try {
            if (conn.responseCode != 200) null else conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }
}
