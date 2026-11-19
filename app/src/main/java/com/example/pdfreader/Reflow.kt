package com.example.pdfreader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Joins the broken lines of a PDF page into paragraphs. */
fun reflowText(raw: String): String {
    val sb = StringBuilder()
    val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    for (l0 in lines) {
        val l = l0.trim()
        if (l.isEmpty()) {
            if (sb.isNotEmpty() && !sb.endsWith("\n\n")) sb.append("\n\n")
            continue
        }
        if (sb.isNotEmpty() && !sb.endsWith("\n")) {
            val prev = sb[sb.length - 1]
            if (prev == '-' && sb.length >= 2 && sb[sb.length - 2].isLetter()) {
                sb.setLength(sb.length - 1) // word split with a hyphen at the end of a line
            } else if (prev in ".!?:;။။。！？؟”\"") {
                sb.append('\n')
            } else {
                sb.append(' ')
            }
        }
        sb.append(l)
    }
    return sb.toString().trim()
}

/** Full-screen "reading view": the text of the PDF, with a size you choose. */
@Composable
fun ReflowDialog(doc: PdfDoc, startPage: Int, dark: Boolean, onClose: () -> Unit) {
    var size by remember { mutableFloatStateOf(18f) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val bg = if (dark) Color(0xFF121212) else Color(0xFFFAF6EC)
    val fg = if (dark) Color(0xFFDDDDDD) else Color(0xFF222222)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(tr("font_size", size.toInt()), color = fg)
                    Slider(
                        value = size,
                        onValueChange = { size = it },
                        valueRange = 12f..36f,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClose) { Text(tr("close")) }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    items(count = doc.pageCount, key = { it }) { i ->
                        var text by remember(doc, i) { mutableStateOf<String?>(null) }
                        LaunchedEffect(doc, i) {
                            text = runCatching { reflowText(fixMyanmar(doc.pageText(i))) }.getOrDefault("")
                        }
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "— ${i + 1} —",
                                color = fg.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            SelectionContainer {
                                Text(
                                    text ?: "…",
                                    color = fg,
                                    fontSize = size.sp,
                                    lineHeight = (size * 1.5f).sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
