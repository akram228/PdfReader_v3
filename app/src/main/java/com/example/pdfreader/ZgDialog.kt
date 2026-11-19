package com.example.pdfreader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Type or paste text, or take the selected text / this page, and change it between Zawgyi and Unicode. */
@Composable
fun ZawgyiDialog(initial: String, pageText: suspend () -> String, onClose: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var note by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Zawgyi ↔ Unicode") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; note = "" },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    maxLines = 12
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val r = zawgyiToUnicode(text)
                            note = if (zawgyiHasUnsupported(r)) tr("zg_partial") else ""
                            text = r
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(tr("zg_to_uni"), maxLines = 2) }
                    Button(
                        onClick = { text = unicodeToZawgyi(text); note = "" },
                        modifier = Modifier.weight(1f)
                    ) { Text(tr("zg_to_zg"), maxLines = 2) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        scope.launch { text = runCatching { pageText() }.getOrDefault(""); note = "" }
                    }) { Text(tr("zg_page")) }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text(tr("zg_copy")) }
                }
                if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("close")) } }
    )
}
