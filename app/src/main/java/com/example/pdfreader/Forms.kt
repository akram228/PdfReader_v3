package com.example.pdfreader

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One fillable field of a PDF form. [kind]: 0 = text, 1 = check box, 2 = choice (list / radio). */
class FormField(val name: String, val kind: Int, val options: List<String>, val value: String)

private fun openForForm(context: Context, src: Uri, password: String?, tmp: File): PDDocument {
    PDFBoxResourceLoader.init(context.applicationContext)
    val input = context.contentResolver.openInputStream(src)
        ?: throw IllegalStateException(tr("cannot_open_file"))
    input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
    val d = PDDocument.load(tmp, password ?: "", MemoryUsageSetting.setupTempFileOnly())
    if (d.isEncrypted) d.setAllSecurityToBeRemoved(true)
    return d
}

/** Lists the fields that can be filled in. Empty list = the PDF has no form. Runs on a background thread. */
fun readFormFields(context: Context, src: Uri, password: String?): List<FormField> {
    val tmp = File(context.cacheDir, "form_src.pdf")
    var doc: PDDocument? = null
    try {
        val d = openForForm(context, src, password, tmp)
        doc = d
        val form = d.documentCatalog.acroForm ?: return emptyList()
        val out = ArrayList<FormField>()
        for (f in form.fieldTree) {
            if (f.isReadOnly) continue
            val name = f.fullyQualifiedName ?: continue
            when (f) {
                is PDTextField -> out.add(FormField(name, 0, emptyList(), f.value ?: ""))
                is PDCheckBox -> out.add(FormField(name, 1, emptyList(), if (f.isChecked) "true" else "false"))
                is PDRadioButton -> out.add(FormField(name, 2, f.onValues.toList(), f.value ?: ""))
                is PDChoice -> out.add(FormField(name, 2, f.options, f.value.firstOrNull() ?: ""))
                else -> {}
            }
        }
        return out
    } finally {
        runCatching { doc?.close() }
        runCatching { tmp.delete() }
    }
}

/** Writes the values into a copy of the PDF. Returns how many fields had to be left for the PDF viewer to draw. */
fun writeFormFields(context: Context, src: Uri, password: String?, out: Uri, values: Map<String, String>): Int {
    val tmp = File(context.cacheDir, "form_src.pdf")
    var doc: PDDocument? = null
    var soft = 0
    try {
        val d = openForForm(context, src, password, tmp)
        doc = d
        val form = d.documentCatalog.acroForm ?: throw IllegalStateException(tr("form_none"))
        for ((name, v) in values) {
            val f = form.getField(name) ?: continue
            try {
                when (f) {
                    is PDCheckBox -> if (v == "true") f.check() else f.unCheck()
                    else -> f.setValue(v)
                }
            } catch (e: Exception) {
                // e.g. the form font cannot draw this script: store the value, let the viewer draw it
                runCatching { f.cosObject.setString(COSName.V, v) }
                soft++
            }
        }
        if (soft > 0) form.setNeedAppearances(true)
        val os = context.contentResolver.openOutputStream(out, "w")
            ?: throw IllegalStateException(tr("cannot_write"))
        os.use { d.save(it) }
        return soft
    } finally {
        runCatching { doc?.close() }
        runCatching { tmp.delete() }
    }
}

@Composable
fun FormDialog(
    context: Context,
    uri: Uri,
    password: String?,
    busy: Boolean,
    status: String,
    onSave: (Map<String, String>) -> Unit,
    onClose: () -> Unit
) {
    var fields by remember(uri) { mutableStateOf<List<FormField>?>(null) }
    var error by remember(uri) { mutableStateOf("") }
    val values = remember(uri) { mutableStateMapOf<String, String>() }
    LaunchedEffect(uri) {
        val r = withContext(Dispatchers.IO) { runCatching { readFormFields(context, uri, password) } }
        val list = r.getOrNull()
        if (list == null) {
            error = r.exceptionOrNull()?.message ?: ""
            fields = emptyList()
        } else {
            list.forEach { values[it.name] = it.value }
            fields = list
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("📝  " + tr("form_fill")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                val list = fields
                if (list == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(tr("form_loading"), style = MaterialTheme.typography.bodySmall)
                } else if (list.isEmpty()) {
                    Text(if (error.isNotBlank()) tr("failed", error) else tr("form_none"))
                } else {
                    list.forEach { f ->
                        when (f.kind) {
                            1 -> Row(
                                Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                    values[f.name] = if (values[f.name] == "true") "false" else "true"
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = values[f.name] == "true",
                                    onCheckedChange = { values[f.name] = if (it) "true" else "false" },
                                    enabled = !busy
                                )
                                Text(f.name, modifier = Modifier.weight(1f))
                            }
                            2 -> {
                                var open by remember { mutableStateOf(false) }
                                Text(f.name, style = MaterialTheme.typography.labelMedium)
                                Box {
                                    OutlinedButton(enabled = !busy, onClick = { open = true }) {
                                        Text(values[f.name].orEmpty().ifBlank { "—" })
                                    }
                                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                        f.options.forEach { o ->
                                            DropdownMenuItem(
                                                text = { Text(o) },
                                                onClick = { values[f.name] = o; open = false }
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            else -> OutlinedTextField(
                                value = values[f.name].orEmpty(),
                                onValueChange = { values[f.name] = it },
                                label = { Text(f.name, maxLines = 1) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            )
                        }
                    }
                }
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && !fields.isNullOrEmpty(),
                onClick = { onSave(values.toMap()) }
            ) { Text(tr("form_save")) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("close")) } }
    )
}
