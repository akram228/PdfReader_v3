package com.example.pdfreader

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private val OCR_SCRIPTS = listOf("Latin / English", "中文", "日本語", "한국어", "हिन्दी")

/** Reads the text in a picture (on the phone, no internet). [script] is an index of [OCR_SCRIPTS]. */
suspend fun recognizeText(bitmap: Bitmap, script: Int): String = suspendCancellableCoroutine { c ->
    val client = when (script) {
        1 -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        2 -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        3 -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        4 -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    client.process(InputImage.fromBitmap(bitmap, 0))
        .addOnSuccessListener { r ->
            client.close()
            c.resume(r.text)
        }
        .addOnFailureListener { e ->
            client.close()
            c.resumeWithException(e)
        }
}

@Composable
fun OcrDialog(doc: PdfDoc, page: Int, onRead: (String) -> Unit, onClose: () -> Unit) {
    var script by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("🔎  " + tr("ocr")) },
        text = {
            Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
                Text(tr("ocr_script"), style = MaterialTheme.typography.titleSmall)
                OCR_SCRIPTS.forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { script = i },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = script == i, onClick = { script = i }, enabled = !busy)
                        Text(label)
                    }
                }
                Text(tr("ocr_note"), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        result = ""
                        message = tr("ocr_busy")
                        scope.launch {
                            try {
                                val bmp = doc.render(page, 1800, Crop.NONE)
                                if (bmp == null) {
                                    message = tr("failed", "")
                                } else {
                                    val t = recognizeText(bmp, script).trim()
                                    result = t
                                    message = if (t.isEmpty()) tr("ocr_empty") else ""
                                }
                            } catch (e: Throwable) {
                                message = tr("failed", e.message ?: "")
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text(tr("ocr_run") + " (" + tr("page_n", page + 1) + ")") }
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
                if (result.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer { Text(result) }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(result)) }) {
                            Text("📋 " + tr("copy"))
                        }
                        OutlinedButton(onClick = { onRead(result) }) { Text("🔊 " + tr("read_aloud")) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("close")) } }
    )
}
