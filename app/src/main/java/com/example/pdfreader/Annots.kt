package com.example.pdfreader

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

/** Colours offered for highlighting (ARGB). */
val HL_COLORS: List<Int> = listOf(
    0xFFFFEB3B.toInt(), // yellow
    0xFF81C784.toInt(), // green
    0xFF64B5F6.toInt(), // blue
    0xFFF48FB1.toInt()  // pink
)

/** A highlighted piece of text, with an optional note. [rects] are raw PDF page coordinates. */
data class Hl(
    val id: String,
    val page: Int,
    val rects: List<RectF>,
    val color: Int,
    val note: String,
    val text: String
)

fun hlToJson(list: List<Hl>): String {
    val arr = JSONArray()
    list.forEach { h ->
        val rs = JSONArray()
        h.rects.forEach { r ->
            rs.put(JSONArray().put(r.left.toDouble()).put(r.top.toDouble()).put(r.right.toDouble()).put(r.bottom.toDouble()))
        }
        arr.put(
            JSONObject().put("id", h.id).put("page", h.page).put("color", h.color)
                .put("note", h.note).put("text", h.text).put("rects", rs)
        )
    }
    return arr.toString()
}

fun hlFromJson(s: String?): List<Hl> = runCatching {
    val arr = JSONArray(s ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val rs = o.getJSONArray("rects")
        val rects = (0 until rs.length()).map { j ->
            val a = rs.getJSONArray(j)
            RectF(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat())
        }
        Hl(
            o.getString("id"), o.getInt("page"), rects, o.getInt("color"),
            o.optString("note", ""), o.optString("text", "")
        )
    }
}.getOrDefault(emptyList())

/**
 * Writes a copy of the PDF with every highlight (and its note) added as a standard PDF highlight annotation,
 * so other PDF apps show them too. Runs on a background thread.
 */
fun saveWithHighlights(context: Context, src: Uri, password: String?, hls: List<Hl>, out: Uri) {
    PDFBoxResourceLoader.init(context.applicationContext)
    val tmp = File(context.cacheDir, "hl_src.pdf")
    var doc: PDDocument? = null
    try {
        val input = context.contentResolver.openInputStream(src)
            ?: throw IllegalStateException(tr("cannot_open_file"))
        input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        val d = PDDocument.load(tmp, password ?: "", MemoryUsageSetting.setupTempFileOnly())
        doc = d
        if (d.isEncrypted) d.setAllSecurityToBeRemoved(true)

        for (h in hls) {
            if (h.page < 0 || h.page >= d.numberOfPages || h.rects.isEmpty()) continue
            val page = d.getPage(h.page)
            val quads = FloatArray(h.rects.size * 8)
            var minL = Float.MAX_VALUE
            var minB = Float.MAX_VALUE
            var maxR = -Float.MAX_VALUE
            var maxT = -Float.MAX_VALUE
            h.rects.forEachIndexed { i, r ->
                val l = min(r.left, r.right)
                val rr = max(r.left, r.right)
                val b = min(r.top, r.bottom)
                val t = max(r.top, r.bottom)
                quads[i * 8] = l; quads[i * 8 + 1] = t
                quads[i * 8 + 2] = rr; quads[i * 8 + 3] = t
                quads[i * 8 + 4] = l; quads[i * 8 + 5] = b
                quads[i * 8 + 6] = rr; quads[i * 8 + 7] = b
                minL = min(minL, l); minB = min(minB, b)
                maxR = max(maxR, rr); maxT = max(maxT, t)
            }
            val a = PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT)
            a.setQuadPoints(quads)
            a.setRectangle(PDRectangle(minL, minB, maxR - minL, maxT - minB))
            val red = ((h.color shr 16) and 0xFF) / 255f
            val green = ((h.color shr 8) and 0xFF) / 255f
            val blue = (h.color and 0xFF) / 255f
            a.setColor(PDColor(floatArrayOf(red, green, blue), PDDeviceRGB.INSTANCE))
            if (h.note.isNotBlank()) a.setContents(h.note)
            val list = page.annotations
            list.add(a)
            page.annotations = list
        }

        val os = context.contentResolver.openOutputStream(out, "w")
            ?: throw IllegalStateException(tr("cannot_write"))
        os.use { d.save(it) }
    } finally {
        runCatching { doc?.close() }
        runCatching { tmp.delete() }
    }
}
