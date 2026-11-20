package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import java.io.InputStream

/**
 * Finds comic panels by looking for gutters (white or black gaps) and cutting the page
 * recursively along them (XY-cut). Offline, no ML.
 *
 * - A clean gutter is a row / column that is almost entirely white or almost entirely black.
 * - A gutter partly blocked by art (a character or bubble poking through) still counts when most
 *   of it is white/black, it has no thin frame lines crossing it, and it has panel art or a frame
 *   right above and below it.
 * - Skies, scenery and textured backgrounds are never white/black enough, so full-bleed pages
 *   come back with no panels and are just shown whole.
 */
object PanelDetector {

    /** [panels] in reading order and [content] (page without margins), as fractions 0..1 of the image. */
    class Result(val panels: List<RectF>, val content: RectF)

    private const val MAX_SIDE = 1000
    private const val MAX_PANELS = 40
    private const val WHITE = 200
    private const val BLACK = 40

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
        // 1 = white, 2 = black, 0 = anything else
        val kind = ByteArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val l = (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
            kind[i] = if (l >= WHITE) 1 else if (l <= BLACK) 2 else 0
        }
        val ctx = Ctx(w, h, kind, maxOf(3, (minOf(w, h) * 0.01f).toInt()))

        val content = ctx.trim(Box(0, 0, w, h)) ?: return null
        val out = ArrayList<Box>()
        ctx.cut(content, out, 0, rtl)
        val cw = content.r - content.l
        val ch = content.b - content.t
        val contentArea = cw.toLong() * ch
        val panels = out.filter {
            (it.r - it.l).toLong() * (it.b - it.t) >= contentArea * 0.012 &&
                (it.r - it.l) >= cw * 0.06f &&
                (it.b - it.t) >= ch * 0.04f
        }
        if (panels.size < 2 || panels.size > MAX_PANELS) return null
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

    private class Ctx(val w: Int, val h: Int, val kind: ByteArray, val minGap: Int) {

        private fun cell(line: Int, pos: Int, alongY: Boolean): Int =
            if (alongY) kind[line * w + pos].toInt() else kind[pos * w + line].toInt()

        /** 1 / 2 when the line is (almost) entirely white / black over [from, to), else 0. */
        private fun strictKind(white: Int, black: Int, n: Int): Int {
            val need = n - maxOf(1, n / 200) // allow ~0.5% stray pixels (JPEG noise)
            return if (white >= need) 1 else if (black >= need) 2 else 0
        }

        private fun lineKind(line: Int, from: Int, to: Int, alongY: Boolean): Int {
            var wh = 0
            var bl = 0
            for (p in from until to) {
                when (cell(line, p, alongY)) {
                    1 -> wh++
                    2 -> bl++
                }
            }
            return strictKind(wh, bl, to - from)
        }

        /** Number of thin (frame-line-like) runs of non-[color] cells along the line. */
        private fun thinRuns(line: Int, from: Int, to: Int, alongY: Boolean, color: Int): Int {
            val lim = maxOf(2, (to - from) / 100)
            var runs = 0
            var p = from
            while (p < to) {
                if (cell(line, p, alongY) != color) {
                    var q = p
                    while (q < to && cell(line, q, alongY) != color) q++
                    if (q - p <= lim) runs++
                    p = q
                } else {
                    p++
                }
            }
            return runs
        }

        /** Shrinks [box] past gutter lines at its edges, stopping at a colour change (a panel frame). */
        fun trim(box: Box): Box? {
            var t = box.t
            var b = box.b
            var l = box.l
            var r = box.r
            var c = 0
            while (t < b) {
                val k = lineKind(t, l, r, true)
                if (k == 0 || (c != 0 && k != c)) break
                c = k
                t++
            }
            if (t >= b) return null
            c = 0
            while (b > t) {
                val k = lineKind(b - 1, l, r, true)
                if (k == 0 || (c != 0 && k != c)) break
                c = k
                b--
            }
            c = 0
            while (l < r) {
                val k = lineKind(l, t, b, false)
                if (k == 0 || (c != 0 && k != c)) break
                c = k
                l++
            }
            c = 0
            while (r > l) {
                val k = lineKind(r - 1, t, b, false)
                if (k == 0 || (c != 0 && k != c)) break
                c = k
                r--
            }
            return if (r > l) Box(l, t, r, b) else null
        }

        /** Per line of [box] (rows if [alongY], else columns): 0 = content, 1 = white gutter, 2 = black gutter. */
        private fun gutters(box: Box, alongY: Boolean): IntArray {
            val st = if (alongY) box.t else box.l
            val en = if (alongY) box.b else box.r
            val from = if (alongY) box.l else box.t
            val to = if (alongY) box.r else box.b
            val n = en - st
            val wh = IntArray(n)
            val bl = IntArray(n)
            val g = IntArray(n)
            val len = (to - from).coerceAtLeast(1)
            for (k in 0 until n) {
                var a = 0
                var d = 0
                for (p in from until to) {
                    when (cell(st + k, p, alongY)) {
                        1 -> a++
                        2 -> d++
                    }
                }
                wh[k] = a
                bl[k] = d
                g[k] = strictKind(a, d, len)
            }
            for (color in 1..2) {
                val fr = FloatArray(n) { k ->
                    val f = (if (color == 1) wh[k] else bl[k]).toFloat() / len
                    if (f >= 0.6f && g[k] == 0 && thinRuns(st + k, from, to, alongY, color) > 1) 0.5f else f
                }
                var i = 0
                while (i < n) {
                    if (fr[i] >= 0.6f) {
                        var j = i
                        while (j < n && fr[j] >= 0.6f) j++
                        if (j - i >= minGap) {
                            var before = 1f
                            for (q in maxOf(0, i - 3) until i) before = minOf(before, fr[q])
                            var after = 1f
                            for (q in j until minOf(n, j + 3)) after = minOf(after, fr[q])
                            val okBefore = i == 0 || before < 0.4f
                            val okAfter = j == n || after < 0.4f
                            if (okBefore && okAfter) {
                                for (q in i until j) if (g[q] == 0) g[q] = color
                            }
                        }
                        i = j
                    } else {
                        i++
                    }
                }
            }
            return g
        }

        /** Content runs along Y (rows) or X (columns), split wherever a gutter is at least [minGap] wide. */
        fun segments(box: Box, alongY: Boolean): List<IntRange> {
            val st = if (alongY) box.t else box.l
            val n = (if (alongY) box.b else box.r) - st
            val g = gutters(box, alongY)
            val segs = ArrayList<IntRange>()
            var s = -1
            var gap = 0
            for (k in 0 until n) {
                if (g[k] == 0) {
                    if (s < 0) {
                        s = k
                        while (s > 0 && g[s - 1] != g[0]) s--
                    } else if (gap >= minGap) {
                        // Keep frame lines with their panel: stop at the gutter's own colour.
                        val mid = g[k - gap + gap / 2]
                        var e = k - gap
                        while (e < k && g[e] != mid) e++
                        var ns = k
                        while (ns > e && g[ns - 1] != mid) ns--
                        segs += (st + s) until (st + e)
                        s = ns
                    }
                    gap = 0
                } else if (s >= 0) {
                    gap++
                }
            }
            if (s >= 0) {
                var e = n - gap
                while (e < n && g[e] != g[n - 1]) e++
                segs += (st + s) until (st + e)
            }
            return segs
        }

        /** True when [box] is surrounded by black frame lines: it's a finished panel. */
        private fun framed(box: Box): Boolean {
            if (box.t < 1 || box.l < 1 || box.b >= h || box.r >= w) return false
            fun blackFrac(line: Int, from: Int, to: Int, alongY: Boolean): Float {
                var d = 0
                for (p in from until to) if (cell(line, p, alongY) == 2) d++
                return d.toFloat() / (to - from).coerceAtLeast(1)
            }
            return blackFrac(box.t - 1, box.l, box.r, true) >= 0.9f &&
                blackFrac(box.b, box.l, box.r, true) >= 0.9f &&
                blackFrac(box.l - 1, box.t, box.b, false) >= 0.9f &&
                blackFrac(box.r, box.t, box.b, false) >= 0.9f
        }

        fun cut(box: Box, out: MutableList<Box>, depth: Int, rtl: Boolean) {
            if (depth > 8 || out.size > MAX_PANELS || (depth > 0 && framed(box))) {
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
