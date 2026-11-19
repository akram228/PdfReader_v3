package com.example.pdfreader

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------- Tools menu: every feature is a button in here ---------- */

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ToolsDialog(
    hasDoc: Boolean,
    selectOn: Boolean,
    onSearch: () -> Unit,
    onSelect: () -> Unit,
    onHighlights: () -> Unit,
    onPen: () -> Unit,
    onReflow: () -> Unit,
    onOcr: () -> Unit,
    onForm: () -> Unit,
    onRead: () -> Unit,
    onThumbs: () -> Unit,
    onSaveImage: () -> Unit,
    onCrop: () -> Unit,
    onPageTools: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onPin: () -> Unit,
    onImages: () -> Unit,
    onBackup: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onCompress: () -> Unit,
    onClose: () -> Unit
) {
    ToolsDialogShell(onClose = onClose) {
        if (hasDoc) {
            ToolButton("🔍  " + tr("search_pdf"), onSearch)
            ToolButton((if (selectOn) "✅  " else "✂️  ") + tr("select_text"), onSelect)
            ToolButton("🖍  " + tr("highlights_notes"), onHighlights)
            ToolButton("✏️  " + tr("pen"), onPen)
            ToolButton("📖  " + tr("reflow"), onReflow)
            ToolButton("🔎  " + tr("ocr"), onOcr)
            ToolButton("📝  " + tr("form_fill"), onForm)
            ToolButton("🔊  " + tr("read_aloud"), onRead)
            ToolButton("▦  " + tr("thumbnails"), onThumbs)
            ToolButton("🖼  " + tr("save_image"), onSaveImage)
            ToolButton("📐  " + tr("crop_margins"), onCrop)
            ToolButton("📄  " + tr("page_tools"), onPageTools)
            ToolButton("✂️  " + tr("split_pdf"), onSplit)
            ToolButton("🗜  " + tr("compress_pdf"), onCompress)
            ToolButton("🖨  " + tr("print"), onPrint)
            ToolButton("📤  " + tr("share"), onShare)
            ToolButton("📌  " + tr("pin_shortcut"), onPin)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        ToolButton("📚  " + tr("merge_pdfs"), onMerge)
        ToolButton("📷  " + tr("img_to_pdf"), onImages)
        ToolButton("☁️  " + tr("backup"), onBackup)
    }
}

/** Tools window: dark picture behind the buttons. */
@Composable
private fun ToolsDialogShell(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.Black) {
                Box {
                    Image(
                        painter = painterResource(R.drawable.menu_bg),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.62f)))
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                        Text(
                            "🛠  " + tr("tools"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(
                            Modifier.weight(1f, fill = false).heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            content = content
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onClose) { Text(tr("close")) }
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Search ---------- */

@Composable
fun SearchDialog(
    initial: String,
    busy: Boolean,
    progress: String,
    done: Boolean,
    results: List<SearchHit>,
    onSearch: (String) -> Unit,
    onPick: (SearchHit) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    var q by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("🔍  " + tr("search_pdf")) },
        text = {
            Column {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it.take(100) },
                    label = { Text(tr("search_hint")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (q.isNotBlank() && !busy) onSearch(q.trim()) }),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = q.isNotBlank() && !busy, onClick = { onSearch(q.trim()) }) {
                        Text(tr("search_btn"))
                    }
                    OutlinedButton(onClick = { q = ""; onClear() }) { Text(tr("clear_marks")) }
                }
                Spacer(Modifier.height(8.dp))
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(progress, style = MaterialTheme.typography.bodySmall)
                } else if (done && results.isEmpty()) {
                    Text(tr("no_results"))
                } else if (results.isNotEmpty()) {
                    Text(tr("results_n", results.size), style = MaterialTheme.typography.labelMedium)
                }
                if (results.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(results) { hit ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(hit) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    tr("page_n", hit.page + 1),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    hit.snippet,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("close")) } }
    )
}

/* ---------- Highlights & notes ---------- */

@Composable
fun HighlightsDialog(
    highlights: List<Hl>,
    busy: Boolean,
    onPick: (Hl) -> Unit,
    onDelete: (Hl) -> Unit,
    onSavePdf: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("🖍  " + tr("highlights_notes")) },
        text = {
            Column {
                if (highlights.isEmpty()) {
                    Text(tr("no_hl"))
                } else {
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(highlights, key = { it.id }) { h ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(h) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(h.color))
                                )
                                Column(Modifier.weight(1f).padding(12.dp)) {
                                    Text(
                                        tr("page_n", h.page + 1),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        h.text,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (h.note.isNotBlank()) {
                                        Text("📝 " + h.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                TextButton(onClick = { onDelete(h) }) { Text("✕") }
                            }
                            HorizontalDivider()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        enabled = !busy,
                        onClick = onSavePdf,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(tr("hl_save_pdf")) }
                    if (busy) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("close")) } }
    )
}

/* ---------- Selected text: copy / highlight / note / read ---------- */

@Composable
fun SelectionDialog(
    sel: SelState,
    onCopy: (String) -> Unit,
    onRead: (String) -> Unit,
    onHighlight: (Int, String) -> Unit,
    onClose: () -> Unit
) {
    var color by remember { mutableIntStateOf(HL_COLORS[0]) }
    var note by remember { mutableStateOf("") }
    // the text shown (and copied / read): the original, or the Zawgyi -> Unicode version of it
    var shown by remember(sel) { mutableStateOf(sel.text) }
    var converted by remember(sel) { mutableStateOf(false) }
    var partial by remember(sel) { mutableStateOf(false) }
    val zawgyi = remember(sel) { looksZawgyi(sel.text) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("selected_text")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(shown, maxLines = 8, overflow = TextOverflow.Ellipsis)
                if (zawgyi && !converted) {
                    Spacer(Modifier.height(4.dp))
                    Text(tr("zg_looks"), style = MaterialTheme.typography.labelMedium)
                }
                if (converted && partial) {
                    Spacer(Modifier.height(4.dp))
                    Text(tr("zg_partial"), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onCopy(shown) }) { Text("📋 " + tr("copy")) }
                    OutlinedButton(onClick = { onRead(shown) }) { Text("🔊 " + tr("read_aloud")) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val c = zawgyiToUnicode(sel.text)
                        shown = c
                        converted = true
                        partial = zawgyiHasUnsupported(c)
                    }) { Text("🔤 " + tr("zg_to_uni")) }
                    if (converted) {
                        OutlinedButton(onClick = {
                            shown = sel.text
                            converted = false
                            partial = false
                        }) { Text(tr("zg_original")) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HL_COLORS.forEach { c ->
                        val chosen = c == color
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { color = c }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text(tr("note_hint")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onHighlight(color, note.trim()) }) { Text("🖍 " + tr("highlight")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("close")) } }
    )
}

/* ---------- Read aloud ---------- */

@Composable
fun TtsDialog(
    speaking: Boolean,
    lang: AppLang,
    onLang: (AppLang) -> Unit,
    rate: Float,
    onRate: (Float) -> Unit,
    noVoice: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    var tmp by remember(rate) { mutableFloatStateOf(rate) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("🔊  " + tr("read_aloud")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (speaking) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Text("⏹  " + tr("stop_reading"))
                    }
                } else {
                    OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text("▶  " + tr("read_from_here"))
                    }
                }
                if (noVoice) {
                    Spacer(Modifier.height(8.dp))
                    Text(tr("no_voice"), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Text(tr("voice_speed", "%.1f".format(tmp)), style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = tmp,
                    onValueChange = { tmp = it },
                    onValueChangeFinished = { onRate(tmp) },
                    valueRange = 0.5f..2f
                )
                Text(tr("voice_lang"), style = MaterialTheme.typography.titleSmall)
                AppLang.values().forEach { l ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onLang(l) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = l == lang, onClick = { onLang(l) })
                        Text(l.native)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("close")) } }
    )
}

/* ---------- Page thumbnails ---------- */

@Composable
private fun Thumb(doc: PdfDoc, index: Int, current: Boolean, onClick: () -> Unit) {
    var bmp by remember(doc, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(doc, index) {
        delay(60)
        bmp = runCatching { doc.render(index, 260, Crop.NONE) }.getOrNull()
    }
    Column(
        Modifier.padding(4.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val b = bmp
        val shape = Modifier
            .fillMaxWidth()
            .then(
                if (current) Modifier.border(3.dp, MaterialTheme.colorScheme.primary)
                else Modifier.border(1.dp, Color.Gray)
            )
        if (b != null) {
            Image(
                bitmap = remember(b) { b.asImageBitmap() },
                contentDescription = tr("page_n", index + 1),
                modifier = shape.aspectRatio(b.width.toFloat() / b.height),
                contentScale = ContentScale.FillBounds
            )
        } else {
            Box(shape.aspectRatio(0.707f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Text("${index + 1}", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ThumbsDialog(doc: PdfDoc, currentPage: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f),
        title = { Text("▦  " + tr("thumbnails")) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().height(440.dp)
            ) {
                items((0 until doc.pageCount).toList(), key = { it }) { i ->
                    Thumb(doc, i, i == currentPage) { onPick(i) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("close")) } }
    )
}

/* ---------- Crop margins ---------- */

@Composable
fun CropDialog(
    crop: Crop,
    onApply: (Crop) -> Unit,
    onAuto: suspend () -> Crop?,
    onClose: () -> Unit
) {
    var l by remember { mutableFloatStateOf(crop.l) }
    var t by remember { mutableFloatStateOf(crop.t) }
    var r by remember { mutableFloatStateOf(crop.r) }
    var b by remember { mutableFloatStateOf(crop.b) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    @Composable
    fun row(key: String, v: Float, onV: (Float) -> Unit) {
        Text(tr(key, (v * 100).toInt()), style = MaterialTheme.typography.labelMedium)
        Slider(value = v, onValueChange = onV, valueRange = 0f..0.35f)
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("📐  " + tr("crop_margins")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                row("crop_left", l) { l = it }
                row("crop_right", r) { r = it }
                row("crop_top", t) { t = it }
                row("crop_bottom", b) { b = it }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            val c = runCatching { onAuto() }.getOrNull()
                            if (c != null) { l = c.l; t = c.t; r = c.r; b = c.b }
                            busy = false
                        }
                    }) { Text("✨ " + tr("crop_auto")) }
                    OutlinedButton(enabled = !busy, onClick = { l = 0f; t = 0f; r = 0f; b = 0f }) {
                        Text(tr("crop_reset"))
                    }
                }
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(tr("detecting"), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onApply(Crop(l, t, r, b)) }) { Text(tr("ok")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("cancel")) } }
    )
}

/* ---------- Extract / delete / rotate pages ---------- */

@Composable
fun PageToolsDialog(
    total: Int,
    busy: Boolean,
    status: String,
    onRun: (PageOp, String, Int) -> Unit,
    onClose: () -> Unit
) {
    var op by remember { mutableStateOf(PageOp.EXTRACT) }
    var text by remember { mutableStateOf("") }
    var angle by remember { mutableIntStateOf(90) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("📄  " + tr("page_tools")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                listOf(
                    PageOp.EXTRACT to tr("pt_extract"),
                    PageOp.DELETE to tr("pt_delete"),
                    PageOp.ROTATE to tr("pt_rotate")
                ).forEach { (o, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { op = o },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = op == o, onClick = { op = o }, enabled = !busy)
                        Text(label)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(200) },
                    label = { Text(tr("pt_ranges")) },
                    supportingText = { Text("1 – $total") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                if (op == PageOp.ROTATE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        listOf(90, 180, 270).forEach { a ->
                            RadioButton(selected = angle == a, onClick = { angle = a }, enabled = !busy)
                            Text("$a°")
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && text.isNotBlank(), onClick = { onRun(op, text, angle) }) {
                Text(tr("create_pdf"))
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("close")) } }
    )
}

/* ---------- Split into several files ---------- */

@Composable
fun SplitDialog(
    total: Int,
    busy: Boolean,
    status: String,
    onRun: (Boolean, String) -> Unit,
    onClose: () -> Unit
) {
    var everyN by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("split_pdf")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                listOf(true to tr("split_every"), false to tr("split_ranges")).forEach { (flag, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { everyN = flag },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = everyN == flag, onClick = { everyN = flag }, enabled = !busy)
                        Text(label)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(300) },
                    label = { Text(if (everyN) tr("split_every_hint") else tr("split_ranges_hint")) },
                    supportingText = { Text("1 – $total") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(tr("split_note"), style = MaterialTheme.typography.bodySmall)
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && text.isNotBlank(), onClick = { onRun(everyN, text) }) {
                Text(tr("split_btn"))
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("close")) } }
    )
}

/* ---------- Compress ---------- */

@Composable
fun CompressDialog(
    level: Int,
    onLevel: (Int) -> Unit,
    busy: Boolean,
    status: String,
    onRun: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("compress_pdf")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                listOf(0 to tr("compress_light"), 1 to tr("compress_balanced"), 2 to tr("compress_strong"))
                    .forEach { (lv, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) { onLevel(lv) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = level == lv, onClick = { onLevel(lv) }, enabled = !busy)
                            Text(label)
                        }
                    }
                Spacer(Modifier.height(8.dp))
                Text(tr("compress_info"), style = MaterialTheme.typography.bodySmall)
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onRun) { Text(tr("compress_btn")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("close")) } }
    )
}

/* ---------- Images -> PDF ---------- */

@Composable
fun ImagesDialog(
    files: SnapshotStateList<MergeSrc>,
    busy: Boolean,
    status: String,
    onAdd: () -> Unit,
    onCreate: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("📷  " + tr("img_to_pdf")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (files.isEmpty()) Text(tr("img_hint"))
                files.forEachIndexed { i, f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}. ${f.name}",
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(enabled = !busy && i > 0, onClick = {
                            val x = files.removeAt(i)
                            files.add(i - 1, x)
                        }) { Text("▲") }
                        TextButton(enabled = !busy && i < files.size - 1, onClick = {
                            val x = files.removeAt(i)
                            files.add(i + 1, x)
                        }) { Text("▼") }
                        TextButton(enabled = !busy, onClick = { files.removeAt(i) }) { Text("✕") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAdd, enabled = !busy) { Text(tr("add_images")) }
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && files.isNotEmpty(), onClick = onCreate) { Text(tr("create_pdf")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("close")) } }
    )
}

/* ---------- Backup / restore ---------- */

@Composable
fun BackupDialog(onSave: () -> Unit, onRestore: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("☁️  " + tr("backup")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("backup_hint"), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("⬆  " + tr("backup_save")) }
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("⬇  " + tr("backup_restore")) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("close")) } }
    )
}

/* ---------- Password ---------- */

@Composable
fun PasswordDialog(wrong: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("🔒  " + tr("password_title")) },
        text = {
            Column {
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text(tr("password_hint")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (pw.isNotEmpty()) onSubmit(pw) }),
                    isError = wrong,
                    modifier = Modifier.fillMaxWidth()
                )
                if (wrong) {
                    Text(
                        tr("wrong_password"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = pw.isNotEmpty(), onClick = { onSubmit(pw) }) { Text(tr("open_btn")) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("cancel")) } }
    )
}

/* ---------- Pen / drawing ---------- */

@Composable
fun PenDialog(
    on: Boolean,
    color: Int,
    width: Float,
    onToggle: () -> Unit,
    onColor: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onClearPage: () -> Unit,
    onClose: () -> Unit
) {
    var w by remember(width) { mutableFloatStateOf(width) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("✏️  " + tr("pen")) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                    Text(if (on) tr("pen_off_btn") else tr("pen_on_btn"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PEN_COLORS.forEach { c ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { onColor(c) }
                        )
                    }
                }
                Text(tr("pen_width"), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = w,
                    onValueChange = { w = it },
                    onValueChangeFinished = { onWidth(w) },
                    valueRange = 0.002f..0.012f
                )
                OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) { Text("↩  " + tr("pen_undo")) }
                OutlinedButton(onClick = onClearPage, modifier = Modifier.fillMaxWidth()) { Text("🗑  " + tr("pen_clear")) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("close")) } }
    )
}
