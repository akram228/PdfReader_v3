package com.example.pdfreader

import org.json.JSONArray
import org.json.JSONObject

/** Pen colours offered (ARGB). */
val PEN_COLORS: List<Int> = listOf(
    0xFFE53935.toInt(), // red
    0xFF1E88E5.toInt(), // blue
    0xFF43A047.toInt(), // green
    0xFF212121.toInt(), // black
    0xFFFFB300.toInt()  // amber
)

/**
 * One pen stroke. [pts] = x0,y0,x1,y1,... as fractions (0..1) of the WHOLE page (before margin crop).
 * [width] is a fraction of the page width.
 */
class InkStroke(val id: String, val page: Int, val color: Int, val width: Float, val pts: FloatArray)

fun strokesToJson(list: List<InkStroke>): String {
    val arr = JSONArray()
    list.forEach { s ->
        val p = JSONArray()
        s.pts.forEach { p.put(Math.round(it * 10000f) / 10000.0) }
        arr.put(
            JSONObject().put("id", s.id).put("page", s.page).put("color", s.color)
                .put("w", s.width.toDouble()).put("p", p)
        )
    }
    return arr.toString()
}

fun strokesFromJson(s: String?): List<InkStroke> = runCatching {
    val arr = JSONArray(s ?: "[]")
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val p = o.getJSONArray("p")
        InkStroke(
            o.getString("id"), o.getInt("page"), o.getInt("color"), o.getDouble("w").toFloat(),
            FloatArray(p.length()) { p.getDouble(it).toFloat() }
        )
    }
}.getOrDefault(emptyList())
