package com.duong.geocam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Location
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Watermark {

    data class Info(val title: String, val lines: List<String>) {
        fun asText(): String = (listOf(title) + lines).filter { it.isNotBlank() }.joinToString("\n")
    }

    private val vi = Locale("vi", "VN")

    fun buildInfo(p: Prefs, loc: Location?, address: String?, time: Long): Info {
        val lines = mutableListOf<String>()
        if (p.showDate) {
            lines += SimpleDateFormat("EEEE, dd/MM/yyyy  HH:mm:ss", vi).format(Date(time))
                .replaceFirstChar { it.uppercase(vi) }
        }
        if (p.showAddress && !address.isNullOrBlank()) lines += address
        if (p.showCoords) {
            lines += if (loc != null) String.format(
                Locale.US, "%.6f, %.6f  (±%dm)", loc.latitude, loc.longitude, loc.accuracy.toInt()
            ) else "Đang chờ GPS..."
        }
        p.customLines.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines += it }
        if (p.note.isNotBlank()) lines += "Ghi chú: " + p.note.trim().replace("\n", " ")
        return Info(p.title.trim(), lines)
    }

    /** Vẽ khung thông tin (góc dưới trái) + logo (góc trên phải) lên ảnh. */
    fun draw(src: Bitmap, info: Info, map: Bitmap?, logo: Bitmap?): Bitmap {
        val bmp = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true).also { src.recycle() }
        val c = Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val s = minOf(w, h) / 1080f
        val margin = 32f * s
        val pad = 24f * s

        logo?.let {
            val lw = w * 0.18f
            val lh = lw * it.height / it.width
            c.drawBitmap(
                it, null, RectF(w - margin - lw, margin, w - margin, margin + lh),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 213, 79)
            textSize = 46f * s
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(3f * s, 0f, 1f * s, Color.BLACK)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 33f * s
            setShadowLayer(3f * s, 0f, 1f * s, Color.BLACK)
        }

        val mapSize = if (map != null) 280f * s else 0f
        val maxTextW = w - 2 * margin - 2 * pad - (if (map != null) mapSize + pad else 0f)

        val rows = mutableListOf<Pair<String, Paint>>()
        if (info.title.isNotEmpty()) wrap(info.title, titlePaint, maxTextW).forEach { rows += it to titlePaint }
        info.lines.forEach { line -> wrap(line, textPaint, maxTextW).forEach { rows += it to textPaint } }
        if (rows.isEmpty() && map == null) return bmp

        val textH = rows.sumOf { it.second.fontSpacing.toDouble() }.toFloat()
        val textW = rows.maxOfOrNull { it.second.measureText(it.first) } ?: 0f
        val innerH = maxOf(textH, mapSize)
        val boxW = 2 * pad + textW + (if (map != null) mapSize + (if (rows.isEmpty()) 0f else pad) else 0f)
        val boxH = innerH + 2 * pad
        val left = margin
        val bottom = h - margin
        val top = bottom - boxH

        c.drawRoundRect(
            RectF(left, top, left + boxW, bottom), 18f * s, 18f * s,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 0, 0, 0) }
        )

        var x = left + pad
        if (map != null) {
            val my = top + pad + (innerH - mapSize) / 2
            c.drawBitmap(map, null, RectF(x, my, x + mapSize, my + mapSize), Paint(Paint.FILTER_BITMAP_FLAG))
            x += mapSize + pad
        }
        var y = top + pad + (innerH - textH) / 2
        for ((t, p) in rows) {
            y += p.fontSpacing
            c.drawText(t, x, y - p.descent(), p)
        }
        return bmp
    }

    private fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        if (p.measureText(text) <= maxW) return listOf(text)
        val out = mutableListOf<String>()
        var cur = ""
        for (word in text.split(" ")) {
            val test = if (cur.isEmpty()) word else "$cur $word"
            if (p.measureText(test) <= maxW || cur.isEmpty()) cur = test
            else { out += cur; cur = word }
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }
}
