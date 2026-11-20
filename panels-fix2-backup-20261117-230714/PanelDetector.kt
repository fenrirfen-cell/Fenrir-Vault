package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import java.io.InputStream
import kotlin.math.abs

/**
 * Finds comic panels by looking for empty gutters (rows / columns that match the page
 * background, white or black) and cutting the page recursively (XY-cut). Offline, no ML.
 */
object PanelDetector {

    /** [panels] in reading order and [content] (page without margins), as fractions 0..1 of the image. */
    class Result(val panels: List<RectF>, val content: RectF)

    private const val MAX_SIDE = 1000
    private const val MAX_PANELS = 40

    fun detect(open: () -> InputStream, rtl: Boolean): Result? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        val ow = bounds.outWidth
        val oh = bounds.outHeight
        if (ow <= 0 || oh <= 0) return null
        var sample = 1
        while (maxOf(ow, oh) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = open().use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        return try {
            detect(bmp, rtl)
        } finally {
            bmp.recycle()
        }
    }

    fun detect(bmp: Bitmap, rtl: Boolean): Result? {
        val w = bmp.width
        val h = bmp.height
        if (w < 32 || h < 32) return null
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val lum = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            lum[i] = (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
        }
        // Page background = median brightness of the outer border.
        val border = IntArray(2 * w + 2 * h)
        var k = 0
        for (x in 0 until w) {
            border[k++] = lum[x]
            border[k++] = lum[(h - 1) * w + x]
        }
        for (y in 0 until h) {
            border[k++] = lum[y * w]
            border[k++] = lum[y * w + w - 1]
        }
        border.sort()
        val bg = border[border.size / 2]
        // Only trust pages with a clean white or black page background (real gutters).
        // Full-bleed art, coloured or textured backgrounds (sky, scenery) -> show the full page.
        if (bg in 36..214) return null
        var same = 0
        for (v in border) if (abs(v - bg) <= 20) same++
        if (same < border.size * 0.85f) return null
        val ink = BooleanArray(w * h) { abs(lum[it] - bg) > 30 }
        val ctx = Ctx(w, ink, maxOf(2, (minOf(w, h) * 0.008f).toInt()))

        val content = ctx.trim(Box(0, 0, w, h)) ?: return null
        val out = ArrayList<Box>()
        ctx.cut(content, out, 0, rtl)
        val cw = content.r - content.l
        val ch = content.b - content.t
        val minArea = cw.toLong() * ch * 0.012
        val panels = out.filter {
            (it.r - it.l).toLong() * (it.b - it.t) >= minArea &&
                (it.r - it.l) >= cw * 0.06f &&
                (it.b - it.t) >= ch * 0.04f
        }
        if (panels.size < 2 || panels.size > MAX_PANELS) return null
        // One panel covering almost the whole page means the split was just noise.
        val contentArea = cw.toLong() * ch
        if (panels.any { (it.r - it.l).toLong() * (it.b - it.t) > contentArea * 0.85 }) return null
        val pad = 2
        return Result(
            panels.map {
                RectF(
                    maxOf(0, it.l - pad).toFloat() / w,
                    maxOf(0, it.t - pad).toFloat() / h,
                    minOf(w, it.r + pad).toFloat() / w,
                    minOf(h, it.b + pad).toFloat() / h,
                )
            },
            RectF(content.l.toFloat() / w, content.t.toFloat() / h, content.r.toFloat() / w, content.b.toFloat() / h),
        )
    }

    private class Box(val l: Int, val t: Int, val r: Int, val b: Int)

    private class Ctx(val w: Int, val ink: BooleanArray, val minGap: Int) {

        fun rowCount(y: Int, l: Int, r: Int): Int {
            var n = 0
            val o = y * w
            for (x in l until r) if (ink[o + x]) n++
            return n
        }

        fun colCount(x: Int, t: Int, b: Int): Int {
            var n = 0
            for (y in t until b) if (ink[y * w + x]) n++
            return n
        }

        /** Shrinks [box] to its non-background content, or null if it's empty. */
        fun trim(box: Box): Box? {
            val rowTol = ((box.r - box.l) * 0.002f).toInt()
            var t = box.t
            while (t < box.b && rowCount(t, box.l, box.r) <= rowTol) t++
            if (t >= box.b) return null
            var b = box.b
            while (b > t && rowCount(b - 1, box.l, box.r) <= rowTol) b--
            val colTol = ((b - t) * 0.002f).toInt()
            var l = box.l
            while (l < box.r && colCount(l, t, b) <= colTol) l++
            var r = box.r
            while (r > l && colCount(r - 1, t, b) <= colTol) r--
            if (r <= l) return null
            return Box(l, t, r, b)
        }

        /** Content runs along Y (rows) or X (columns), split wherever an empty gutter is at least [minGap] wide. */
        fun segments(box: Box, alongY: Boolean): List<IntRange> {
            val start = if (alongY) box.t else box.l
            val end = if (alongY) box.b else box.r
            val tol = if (alongY) ((box.r - box.l) * 0.002f).toInt() else ((box.b - box.t) * 0.002f).toInt()
            val segs = ArrayList<IntRange>()
            var segStart = -1
            var gap = 0
            for (i in start until end) {
                val n = if (alongY) rowCount(i, box.l, box.r) else colCount(i, box.t, box.b)
                if (n > tol) {
                    if (segStart < 0) {
                        segStart = i
                    } else if (gap >= minGap) {
                        segs += segStart until (i - gap)
                        segStart = i
                    }
                    gap = 0
                } else if (segStart >= 0) {
                    gap++
                }
            }
            if (segStart >= 0) segs += segStart until (end - gap)
            return segs
        }

        fun cut(box: Box, out: MutableList<Box>, depth: Int, rtl: Boolean) {
            if (depth > 8 || out.size > MAX_PANELS) {
                out += box
                return
            }
            val rows = segments(box, true)
            if (rows.size > 1) {
                for (s in rows) trim(Box(box.l, s.first, box.r, s.last + 1))?.let { cut(it, out, depth + 1, rtl) }
                return
            }
            val cols = segments(box, false)
            if (cols.size > 1) {
                val ordered = if (rtl) cols.asReversed() else cols
                for (s in ordered) trim(Box(s.first, box.t, s.last + 1, box.b))?.let { cut(it, out, depth + 1, rtl) }
                return
            }
            out += box
        }
    }
}