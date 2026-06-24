package com.inkvault.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.inkvault.data.Point
import com.inkvault.data.StrokeEntity

/**
 * White-background, auto-fit raster of a page's ink (A4 portrait), always light-mode — shared by
 * Print and the zone-triggered Share/Email. Default ink (color 0) prints black; colored ink prints
 * solid. Returns null when there's nothing to render.
 */
object PageRender {

    fun renderPage(
        strokes: List<StrokeEntity>,
        points: (StrokeEntity) -> List<Point>,
        width: Int = 1240,   // ~150dpi A4 portrait
        height: Int = 1754,
    ): Bitmap? {
        val all = strokes.map(points)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        all.forEach { it.forEach { p -> minX = minOf(minX, p.x); minY = minOf(minY, p.y); maxX = maxOf(maxX, p.x); maxY = maxOf(maxY, p.y) } }
        if (minX > maxX || minY > maxY) return null

        val pad = 0.06f
        val spanX = (maxX - minX).coerceAtLeast(1e-3f)
        val spanY = (maxY - minY).coerceAtLeast(1e-3f)
        val scale = minOf(width * (1 - 2 * pad) / spanX, height * (1 - 2 * pad) / spanY)
        val offX = (width - spanX * scale) / 2f
        val offY = (height - spanY * scale) / 2f

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val baseWidth = width * 0.0025f
        strokes.forEachIndexed { i, s ->
            val raw = all[i]
            if (raw.isEmpty()) return@forEachIndexed
            paint.color = if (s.color != 0) s.color else Color.BLACK
            // Same pressure-tapered outline as on screen, filled.
            val xs = FloatArray(raw.size) { (raw[it].x - minX) * scale + offX }
            val ys = FloatArray(raw.size) { (raw[it].y - minY) * scale + offY }
            val pr = FloatArray(raw.size) { raw[it].pressure }
            val o = com.inkvault.ink.strokeOutline(xs, ys, pr, baseWidth * s.width)
            if (o.size < 4) return@forEachIndexed
            val path = Path()
            path.moveTo(o[0], o[1])
            var j = 2
            while (j < o.size) { path.lineTo(o[j], o[j + 1]); j += 2 }
            path.close()
            canvas.drawPath(path, paint)
        }
        return bmp
    }
}
