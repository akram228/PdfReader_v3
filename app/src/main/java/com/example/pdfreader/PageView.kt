package com.example.pdfreader

import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the little toolbar above a selected text can do. */
enum class SelAction { COPY, SELECT_ALL, ROTATE, READ, HIGHLIGHT }

/** Everything the page pictures need to know besides the PDF itself. It is Compose state. */
class ViewState {
    /** Text being searched for ("" = nothing is marked). */
    var query by mutableStateOf("")

    /** Highlights of the open file. */
    val highlights = mutableStateListOf<Hl>()

    /** When true, long-press + drag on a page selects text. */
    var selectMode by mutableStateOf(false)

    /** Pages turned by the reader (page index -> 90/180/270 degrees). Only changes how a page is shown. */
    val pageRot = mutableStateMapOf<Int, Int>()

    /** Margins cut away from every page. */
    var crop by mutableStateOf(Crop.NONE)

    /** Where the finger was lifted (inside the page picture): the toolbar is placed there. */
    var selAnchor by mutableStateOf(Offset.Zero)

    /** True while the bigger copy / highlight window is open. */
    var selDialog by mutableStateOf(false)

    /** Set by the screen: what to do when a toolbar button is tapped. */
    var onSelAction: ((SelAction) -> Unit)? = null

    /** Text selected right now. */
    var selection by mutableStateOf<SelState?>(null)

    /** Pen drawings of the open file. */
    val strokes = mutableStateListOf<InkStroke>()

    /** When true, one finger draws instead of scrolling. */
    var penMode by mutableStateOf(false)
    var penColor by mutableIntStateOf(PEN_COLORS[0])
    var penWidth by mutableFloatStateOf(0.004f)

    /** Called after the drawings changed (so they can be saved). */
    var onInk: (() -> Unit)? = null
}

@Composable
fun PageItem(doc: PdfDoc, index: Int, widthPx: Int, filter: ColorFilter?, fit: Boolean, vs: ViewState) {
    val crop = vs.crop
    val scope = rememberCoroutineScope()

    // The old bitmap stays visible (just scaled) while a sharper one is rendered: no flicker.
    // Pages seen a moment ago come straight from the cache, so scrolling back and forth stays smooth.
    var bmp by remember(doc, index) { mutableStateOf<Bitmap?>(doc.cachedBitmap(index)) }
    LaunchedEffect(doc, index, widthPx, crop) {
        if (doc.isFresh(index, widthPx, crop)) {
            doc.cachedBitmap(index)?.let { bmp = it }
        } else {
            // skip pages that are only flying past, or while pinching
            if (bmp == null) {
                // a small, fast draw first: the page appears almost at once, the sharp one replaces it
                delay(30)
                val quick = runCatching { doc.render(index, (widthPx / 3).coerceAtLeast(120), crop) }.getOrNull()
                if (quick != null) bmp = quick
            } else {
                delay(120)
            }
            val r = runCatching { doc.renderPage(index, widthPx, crop) }.getOrNull()
            if (r != null) bmp = r
        }
    }
    var live by remember(index) { mutableStateOf<List<Offset>>(emptyList()) }
    val b = bmp
    val rot = vs.pageRot[index] ?: 0
    if (b != null) {
        val shown = remember(b, rot) {
            if (rot == 0) b
            else runCatching {
                Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
            }.getOrDefault(b)
        }
        val img = remember(shown) { shown.asImageBitmap() }
        val ratio = shown.width.toFloat() / shown.height.toFloat()
        Box(
            if (fit) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                (if (fit) Modifier else Modifier.fillMaxWidth())
                    .aspectRatio(ratio)
                    .pointerInput(vs.penMode, index, crop, rot) {
                        if (vs.penMode && rot == 0) {
                            var pts = ArrayList<Offset>()
                            detectDragGestures(
                                onDragStart = { o -> pts = arrayListOf(o); live = pts.toList() },
                                onDrag = { change, _ -> pts.add(change.position); live = pts.toList(); change.consume() },
                                onDragCancel = { live = emptyList() },
                                onDragEnd = {
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    if (pts.size >= 2) {
                                        val arr = FloatArray(pts.size * 2)
                                        pts.forEachIndexed { i, p ->
                                            arr[2 * i] = crop.l + (p.x / w) * crop.w
                                            arr[2 * i + 1] = crop.t + (p.y / h) * crop.h
                                        }
                                        vs.strokes.add(
                                            InkStroke(System.nanoTime().toString(), index, vs.penColor, vs.penWidth, arr)
                                        )
                                        vs.onInk?.invoke()
                                    }
                                    live = emptyList()
                                }
                            )
                        }
                    }
                    // press and hold on the text, then drag: selects it (no need to switch a mode on first)
                    .pointerInput(vs.penMode, doc, index, crop, rot) {
                        if (!vs.penMode) {
                            var start = Offset.Zero
                            var cur = Offset.Zero
                            detectDragGesturesAfterLongPress(
                                onDragStart = { o -> start = o; cur = o },
                                onDrag = { change, _ -> cur = change.position; change.consume() },
                                onDragEnd = {
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val s = start
                                    val c = cur
                                    vs.selAnchor = c
                                    // the page is shown turned: map the finger back onto the unturned page
                                    val (sx, sy) = unrotatePoint(s.x / w, s.y / h, rot)
                                    val (cx, cy) = unrotatePoint(c.x / w, c.y / h, rot)
                                    scope.launch {
                                        val sel = runCatching {
                                            doc.selectBetween(index, sx, sy, cx, cy, crop)
                                        }.getOrNull()
                                        // no text under the finger: the toolbar still appears (select all / rotate)
                                        vs.selection = sel ?: SelState(index, "", emptyList())
                                    }
                                }
                            )
                        }
                    }
            ) {
                Image(
                    bitmap = img,
                    contentDescription = tr("page_n", index + 1),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    colorFilter = filter
                )
                PageOverlays(doc, index, vs, crop, live, rot)
                if (vs.selection?.page == index && !vs.selDialog) SelectionPopup(vs)
            }
        }
    } else {
        Box(
            // the empty slot already has the right height, so the list does not jump when the picture arrives
            if (fit) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(
                if (rot == 90 || rot == 270) 1f / doc.ratioOf(index) else doc.ratioOf(index)
            ),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

/** Point on the page as shown (0..1, turned [rot] degrees clockwise) -> point on the unturned page. */
private fun unrotatePoint(u: Float, v: Float, rot: Int): Pair<Float, Float> = when (rot) {
    90 -> v to (1f - u)
    180 -> (1f - u) to (1f - v)
    270 -> (1f - v) to u
    else -> u to v
}

/** Rectangle on the unturned page (0..1) -> rectangle on the page turned [rot] degrees clockwise. */
private fun rotateRect(r: RectF, rot: Int): RectF = when (rot) {
    90 -> RectF(1f - r.bottom, r.left, 1f - r.top, r.right)
    180 -> RectF(1f - r.right, 1f - r.bottom, 1f - r.left, 1f - r.top)
    270 -> RectF(r.top, 1f - r.right, r.bottom, 1f - r.left)
    else -> r
}

/** Search matches, highlights and the current selection, drawn on top of one page picture. */
@Composable
private fun PageOverlays(doc: PdfDoc, index: Int, vs: ViewState, crop: Crop, live: List<Offset>, rot: Int) {
    val query = vs.query
    var matches by remember(doc, index, query, crop) { mutableStateOf<List<RectF>>(emptyList()) }
    LaunchedEffect(doc, index, query, crop) {
        matches = if (query.isBlank()) emptyList()
        else runCatching { doc.queryRects(index, query, crop) }.getOrDefault(emptyList())
    }

    val hls = vs.highlights.filter { it.page == index }
    var hlRects by remember(doc, index) { mutableStateOf<List<Pair<Int, List<RectF>>>>(emptyList()) }
    LaunchedEffect(doc, index, hls, crop) {
        hlRects = hls.map { h ->
            h.color to runCatching { doc.normalize(index, h.rects, crop) }.getOrDefault(emptyList())
        }
    }

    val sel = vs.selection?.takeIf { it.page == index }
    var selRects by remember(doc, index) { mutableStateOf<List<RectF>>(emptyList()) }
    LaunchedEffect(doc, index, sel, crop) {
        selRects = if (sel == null) emptyList()
        else runCatching { doc.normalize(index, sel.rects, crop) }.getOrDefault(emptyList())
    }

    val ink = if (rot == 0) vs.strokes.filter { it.page == index } else emptyList()
    if (matches.isEmpty() && hlRects.isEmpty() && selRects.isEmpty() && ink.isEmpty() && live.isEmpty()) return

    Canvas(Modifier.fillMaxSize()) {
        fun paint(list: List<RectF>, color: Color) {
            for (r0 in list) {
                val r = rotateRect(r0, rot)
                drawRect(
                    color = color,
                    topLeft = Offset(r.left * size.width, r.top * size.height),
                    size = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height)
                )
            }
        }
        hlRects.forEach { (argb, rects) -> paint(rects, Color(argb).copy(alpha = 0.40f)) }
        paint(matches, Color(0xFFFF9800).copy(alpha = 0.45f))
        paint(selRects, Color(0xFF2196F3).copy(alpha = 0.35f))
        for (st in ink) {
            val path = Path()
            var i = 0
            while (i + 1 < st.pts.size) {
                val x = ((st.pts[i] - crop.l) / crop.w) * size.width
                val y = ((st.pts[i + 1] - crop.t) / crop.h) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                i += 2
            }
            drawPath(
                path, Color(st.color),
                style = DrawStroke(width = st.width * size.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        if (live.size >= 2) {
            val path = Path()
            live.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPath(
                path, Color(vs.penColor),
                style = DrawStroke(width = vs.penWidth * size.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/** Little toolbar (like the phone's own text menu) shown above the selected text. */
@Composable
private fun SelectionPopup(vs: ViewState) {
    val density = LocalDensity.current
    val barH = with(density) { 52.dp.roundToPx() }
    val gap = with(density) { 14.dp.roundToPx() }
    val half = with(density) { 150.dp.roundToPx() }
    val a = vs.selAnchor
    val above = a.y.toInt() - barH - gap
    val y = if (above < 0) a.y.toInt() + gap * 2 else above
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset((a.x.toInt() - half).coerceAtLeast(0), y),
        onDismissRequest = { vs.selection = null },
        properties = PopupProperties(focusable = true)
    ) {
        Surface(shape = RoundedCornerShape(26.dp), color = Color(0xFF1E2230), shadowElevation = 8.dp) {
            Row(
                Modifier.widthIn(max = 340.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasText = vs.selection?.text?.isNotBlank() == true
                listOf(
                    SelAction.COPY to "sel_copy",
                    SelAction.SELECT_ALL to "sel_all",
                    SelAction.ROTATE to "sel_rotate",
                    SelAction.READ to "sel_read",
                    SelAction.HIGHLIGHT to "sel_hl"
                ).filter { (act, _) -> hasText || act == SelAction.COPY || act == SelAction.SELECT_ALL || act == SelAction.ROTATE }
                    .forEach { (act, key) ->
                    TextButton(
                        onClick = { vs.onSelAction?.invoke(act) },
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(tr(key), color = Color.White, maxLines = 1, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
