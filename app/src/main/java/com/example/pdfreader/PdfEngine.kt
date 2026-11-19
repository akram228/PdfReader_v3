package com.example.pdfreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfTextPage
import io.legere.pdfiumandroid.PdfiumCore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/* ---------- Small data classes ---------- */

/** Margins to cut away, as a fraction (0..0.4) of the page on each side. */
data class Crop(val l: Float = 0f, val t: Float = 0f, val r: Float = 0f, val b: Float = 0f) {
    val w: Float get() = (1f - l - r).coerceAtLeast(0.2f)
    val h: Float get() = (1f - t - b).coerceAtLeast(0.2f)
    val isNone: Boolean get() = l == 0f && t == 0f && r == 0f && b == 0f

    companion object {
        val NONE = Crop()
    }
}

/** One search result: the page and a short piece of text around it. */
class SearchHit(val page: Int, val snippet: String)

/** Text selected by the user on one page. [rects] are in raw PDF page coordinates. */
class SelState(val page: Int, val text: String, val rects: List<RectF>)

/** Result of trying to open a PDF. */
class OpenResult(val doc: PdfDoc?, val needPassword: Boolean, val wrongPassword: Boolean)

fun openPdf(context: Context, uri: Uri, password: String?): OpenResult {
    return try {
        OpenResult(PdfDoc(context, uri, password), false, false)
    } catch (t: Throwable) {
        val name = t.javaClass.simpleName
        val msg = t.message ?: ""
        val isPwd = name.contains("assword", ignoreCase = true) || msg.contains("assword", ignoreCase = true)
        OpenResult(null, isPwd, isPwd && !password.isNullOrEmpty())
    }
}

/* ---------- Page geometry (PDF space <-> picture space) ---------- */

/** [w]/[h] = size of the page as shown (points), [rot] = page rotation 0..3 (x 90 degrees clockwise). */
private class Geo(val w: Float, val h: Float, rotLoader: () -> Int) {
    /** Read only when text coordinates are needed (search / selection), never for drawing. */
    val rot: Int by lazy(rotLoader)
    /** Size of the page before rotation (text coordinates live in this space). */
    val wu: Float get() = if (rot % 2 == 0) w else h
    val hu: Float get() = if (rot % 2 == 0) h else w
}

/** PDF point (origin bottom-left, unrotated) -> 0..1 position on the shown picture (origin top-left). */
private fun toNorm(g: Geo, x: Float, y: Float): FloatArray = when (g.rot) {
    1 -> floatArrayOf(y / g.hu, x / g.wu)
    2 -> floatArrayOf(1f - x / g.wu, y / g.hu)
    3 -> floatArrayOf(1f - y / g.hu, 1f - x / g.wu)
    else -> floatArrayOf(x / g.wu, 1f - y / g.hu)
}

/** 0..1 position on the shown picture -> PDF point. */
private fun fromNorm(g: Geo, nx: Float, ny: Float): FloatArray = when (g.rot) {
    1 -> floatArrayOf(ny * g.wu, nx * g.hu)
    2 -> floatArrayOf((1f - nx) * g.wu, ny * g.hu)
    3 -> floatArrayOf((1f - ny) * g.wu, (1f - nx) * g.hu)
    else -> floatArrayOf(nx * g.wu, (1f - ny) * g.hu)
}

/** Raw PDF rectangle -> rectangle (0..1) on the shown picture, taking the margin crop into account. */
private fun normRect(g: Geo, r: RectF, crop: Crop): RectF {
    val xs = floatArrayOf(r.left, r.right)
    val ys = floatArrayOf(r.top, r.bottom)
    var l = Float.MAX_VALUE
    var t = Float.MAX_VALUE
    var rr = -Float.MAX_VALUE
    var b = -Float.MAX_VALUE
    for (x in xs) for (y in ys) {
        val p = toNorm(g, x, y)
        l = min(l, p[0]); rr = max(rr, p[0])
        t = min(t, p[1]); b = max(b, p[1])
    }
    return RectF((l - crop.l) / crop.w, (t - crop.t) / crop.h, (rr - crop.l) / crop.w, (b - crop.t) / crop.h)
}

/* ---------- Text helpers ---------- */

private fun textOf(tp: PdfTextPage): String {
    val n = tp.textPageCountChars()
    if (n <= 0) return ""
    return (tp.textPageGetText(0, n) ?: "").replace('\u0000', ' ')
}

/** Collapses every run of white space to one space. [map] gives, for each new char, its index in the original text. */
private fun collapse(s: String): Pair<String, IntArray> {
    val sb = StringBuilder()
    val map = ArrayList<Int>()
    var prevSpace = true
    for (i in s.indices) {
        val c = s[i]
        if (c.isWhitespace()) {
            if (!prevSpace) {
                sb.append(' ')
                map.add(i)
                prevSpace = true
            }
        } else {
            sb.append(c)
            map.add(i)
            prevSpace = false
        }
    }
    return sb.toString() to map.toIntArray()
}

/** All places where [query] occurs in [text] (ignoring case and line breaks). Each result is (start, endExclusive). */
private fun findRanges(text: String, query: String): List<Pair<Int, Int>> {
    val q = collapse(query).first.trim()
    if (q.isEmpty()) return emptyList()
    val (ct, map) = collapse(text)
    val out = ArrayList<Pair<Int, Int>>()
    var from = 0
    while (from <= ct.length - q.length) {
        val i = ct.indexOf(q, from, ignoreCase = true)
        if (i < 0) break
        out.add(map[i] to (map[i + q.length - 1] + 1))
        from = i + q.length
        if (out.size >= 200) break
    }
    return out
}

private fun snippetOf(text: String, s: Int, e: Int): String {
    val a = max(0, s - 30)
    val b = min(text.length, e + 45)
    return text.substring(a, b).replace(Regex("\\s+"), " ").trim()
}

private fun rawRects(tp: PdfTextPage, start: Int, count: Int): List<RectF> {
    val n = tp.textPageCountRects(start, count)
    val out = ArrayList<RectF>()
    for (i in 0 until n) {
        val r = tp.textPageGetRect(i) ?: continue
        out.add(RectF(r.left, r.top, r.right, r.bottom))
    }
    return out
}

private fun charAtNorm(tp: PdfTextPage, g: Geo, nx: Float, ny: Float, crop: Crop): Int {
    val fx = crop.l + nx * crop.w
    val fy = crop.t + ny * crop.h
    val p = fromNorm(g, fx, fy)
    return tp.textPageGetCharIndexAtPos(p[0].toDouble(), p[1].toDouble(), 14.0, 14.0)
}

/* ---------- The PDF (PDFium engine) ---------- */

/**
 * PDFium is not thread-safe, so every call that touches the document is done while holding [mutex].
 */
class PdfDoc(context: Context, val uri: Uri, password: String?) {
    private val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: throw IllegalStateException(tr("cannot_open_file"))
    private val core = PdfiumCore(context.applicationContext)
    private val document: PdfDocument
    private val mutex = Mutex()
    private val appCtx: Context = context.applicationContext
    private val pwd: String? = password

    // Page rotation (0..3, x90 degrees clockwise). Read through PDFBox only when text positions are
    // needed (search highlights / selection), so opening and drawing pages stays fast on huge PDFs.
    private var pdBox: PDDocument? = null
    private var pdBoxFailed = false
    private val rotCache = ConcurrentHashMap<Int, Int>()

    @Synchronized
    private fun rotationOf(index: Int): Int {
        rotCache[index]?.let { return it }
        val r = runCatching {
            if (pdBox == null && !pdBoxFailed) {
                try {
                    PDFBoxResourceLoader.init(appCtx)
                    pdBox = appCtx.contentResolver.openInputStream(uri)!!.use { ins ->
                        if (pwd.isNullOrEmpty())
                            PDDocument.load(ins, MemoryUsageSetting.setupTempFileOnly())
                        else
                            PDDocument.load(ins, pwd, MemoryUsageSetting.setupTempFileOnly())
                    }
                } catch (t: Throwable) {
                    pdBoxFailed = true
                }
            }
            val d = pdBox
            if (d == null) 0 else ((d.getPage(index).rotation / 90) % 4 + 4) % 4
        }.getOrDefault(0)
        rotCache[index] = r
        return r
    }
    private val geoCache = ConcurrentHashMap<Int, Geo>()
    @Volatile private var closed = false
    val pageCount: Int

    init {
        val d: PdfDocument = try {
            if (password.isNullOrEmpty()) core.newDocument(pfd) else core.newDocument(pfd, password)
        } catch (t: Throwable) {
            runCatching { pfd.close() }
            throw t
        }
        document = d
        pageCount = d.getPageCount()
    }

    private suspend fun <T> locked(default: T, block: () -> T): T = mutex.withLock {
        if (closed) default else withContext(Dispatchers.IO) { block() }
    }

    private fun geoOf(index: Int, page: PdfPage): Geo = geoCache.getOrPut(index) {
        Geo(
            page.getPageWidthPoint().coerceAtLeast(1).toFloat(),
            page.getPageHeightPoint().coerceAtLeast(1).toFloat()
        ) { rotationOf(index) }
    }

    private fun geoFor(index: Int): Geo? {
        geoCache[index]?.let { return it }
        val page = document.openPage(index)
        return try {
            geoOf(index, page)
        } finally {
            page.close()
        }
    }

    // ----- picture cache: scrolling back to a page shows it at once (no blank flash, no jump) -----
    private class Rendered(val widthPx: Int, val crop: Crop, val bmp: Bitmap)

    private val cache = object : android.util.LruCache<Int, Rendered>(
        (Runtime.getRuntime().maxMemory() / 6).toInt().coerceIn(24 shl 20, 96 shl 20)
    ) {
        override fun sizeOf(key: Int, value: Rendered): Int = value.bmp.byteCount
    }
    private val ratios = ConcurrentHashMap<Int, Float>()

    /** Width / height of the last drawn page: used as the guess for pages that are not drawn yet. */
    @Volatile var lastRatio: Float = 0.707f

    fun ratioOf(index: Int): Float = ratios[index] ?: lastRatio
    fun cachedBitmap(index: Int): Bitmap? = cache.get(index)?.bmp
    fun isFresh(index: Int, widthPx: Int, crop: Crop): Boolean {
        val r = cache.get(index) ?: return false
        return r.widthPx == widthPx && r.crop == crop
    }

    /** Like [render], but remembers the picture (used for the pages on screen and the ones next to them). */
    suspend fun renderPage(index: Int, widthPx: Int, crop: Crop): Bitmap? {
        cache.get(index)?.let { if (it.widthPx == widthPx && it.crop == crop) return it.bmp }
        val b = render(index, widthPx, crop) ?: return null
        cache.put(index, Rendered(widthPx, crop, b))
        val r = b.width.toFloat() / b.height.toFloat()
        ratios[index] = r
        lastRatio = r
        return b
    }

    /** Draws one page. The picture is [widthPx] wide; [crop] cuts margins away (the picture is then shorter). */
    suspend fun render(index: Int, widthPx: Int, crop: Crop): Bitmap? = locked<Bitmap?>(null) {
        try {
            val page = document.openPage(index)
            try {
                val g = geoOf(index, page)
                val fullW = (widthPx / crop.w).toInt().coerceAtLeast(1)
                val fullH = (fullW * g.h / g.w).toInt().coerceAtLeast(1)
                val outH = (fullH * crop.h).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(widthPx, outH, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.renderPageBitmap(bmp, -(fullW * crop.l).toInt(), -(fullH * crop.t).toInt(), fullW, fullH, false)
                bmp
            } finally {
                page.close()
            }
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** All text of a page (for reading aloud). */
    suspend fun pageText(index: Int): String = locked<String>("") {
        val page = document.openPage(index)
        try {
            val tp = page.openTextPage()
            try {
                textOf(tp)
            } finally {
                tp.close()
            }
        } finally {
            page.close()
        }
    }

    /** Search one page. Returns a short piece of text for every match. */
    suspend fun searchPage(index: Int, query: String): List<SearchHit> = locked<List<SearchHit>>(emptyList()) {
        val page = document.openPage(index)
        try {
            val tp = page.openTextPage()
            try {
                val text = textOf(tp)
                findRanges(text, query).map { (s, e) -> SearchHit(index, snippetOf(text, s, e)) }
            } finally {
                tp.close()
            }
        } finally {
            page.close()
        }
    }

    /** Rectangles (0..1 on the shown picture) of every match of [query] on a page. */
    suspend fun queryRects(index: Int, query: String, crop: Crop): List<RectF> = locked<List<RectF>>(emptyList()) {
        val page = document.openPage(index)
        try {
            val g = geoOf(index, page)
            val tp = page.openTextPage()
            try {
                val text = textOf(tp)
                val out = ArrayList<RectF>()
                for ((s, e) in findRanges(text, query)) {
                    rawRects(tp, s, e - s).forEach { out.add(normRect(g, it, crop)) }
                }
                out
            } finally {
                tp.close()
            }
        } finally {
            page.close()
        }
    }

    /** Raw PDF rectangles -> rectangles (0..1) on the shown picture. */
    suspend fun normalize(index: Int, raw: List<RectF>, crop: Crop): List<RectF> = locked<List<RectF>>(emptyList()) {
        val g = geoFor(index)
        if (g == null) emptyList() else raw.map { normRect(g, it, crop) }
    }

    /**
     * Text between two touch points (both 0..1 on the shown picture).
     * If both points are the same spot, the whole word under it is selected.
     */
    suspend fun selectBetween(
        index: Int, x1: Float, y1: Float, x2: Float, y2: Float, crop: Crop
    ): SelState? = locked<SelState?>(null) {
        val page = document.openPage(index)
        try {
            val g = geoOf(index, page)
            val tp = page.openTextPage()
            try {
                val a = charAtNorm(tp, g, x1, y1, crop)
                val b = charAtNorm(tp, g, x2, y2, crop)
                if (a < 0 && b < 0) return@locked null
                var s = if (a < 0) b else if (b < 0) a else min(a, b)
                var e = if (a < 0) b else if (b < 0) a else max(a, b)
                if (s == e) {
                    val text = textOf(tp)
                    while (s > 0 && s - 1 < text.length && !text[s - 1].isWhitespace()) s--
                    while (e + 1 < text.length && !text[e + 1].isWhitespace()) e++
                }
                val count = e - s + 1
                val txt = (tp.textPageGetText(s, count) ?: "").replace('\u0000', ' ')
                if (txt.isBlank()) null else SelState(index, txt, rawRects(tp, s, count))
            } finally {
                tp.close()
            }
        } finally {
            page.close()
        }
    }

    /** Selects all the text of one page. */
    suspend fun selectAll(index: Int): SelState? = locked<SelState?>(null) {
        val page = document.openPage(index)
        try {
            val tp = page.openTextPage()
            try {
                val n = tp.textPageCountChars()
                if (n <= 0) return@locked null
                val txt = (tp.textPageGetText(0, n) ?: "").replace('\u0000', ' ')
                if (txt.isBlank()) null else SelState(index, txt, rawRects(tp, 0, n))
            } finally {
                tp.close()
            }
        } finally {
            page.close()
        }
    }

    /** Looks at a small picture of the page and returns where the ink is (0..1), or null for an empty page. */
    suspend fun contentBounds(index: Int): RectF? {
        val bmp = render(index, 400, Crop.NONE) ?: return null
        return withContext(Dispatchers.Default) {
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            val rows = IntArray(h)
            val cols = IntArray(w)
            for (y in 0 until h) for (x in 0 until w) {
                val c = px[y * w + x]
                val lum = ((c shr 16) and 0xFF) * 3 + ((c shr 8) and 0xFF) * 6 + (c and 0xFF)
                if (lum < 2350) { // darker than about 235/255
                    rows[y]++
                    cols[x]++
                }
            }
            val top = rows.indexOfFirst { it >= 2 }
            val bottom = rows.indexOfLast { it >= 2 }
            val left = cols.indexOfFirst { it >= 2 }
            val right = cols.indexOfLast { it >= 2 }
            bmp.recycle()
            if (top < 0 || left < 0 || bottom <= top || right <= left) null
            else RectF(left.toFloat() / w, top.toFloat() / h, (right + 1).toFloat() / w, (bottom + 1).toFloat() / h)
        }
    }

    /** Waits for a running call to finish, so the native code is never closed in the middle of work. */
    fun close() {
        closed = true
        runBlocking {
            mutex.withLock {
                runCatching { document.close() }
                runCatching { pdBox?.close() }
                runCatching { pfd.close() }
            }
        }
    }
}
