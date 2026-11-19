package com.example.pdfreader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/* ---------- Page ranges ("1-3, 5, 8-") ---------- */

/** Returns 0-based page numbers (sorted, no repeats). Empty list = the text is not valid. */
fun parsePageRanges(text: String, total: Int): List<Int> {
    val digits = StringBuilder()
    for (c in text) digits.append(if (c.isDigit()) c.digitToInt().digitToChar() else c)
    val t = digits.toString().replace(Regex("\\s*[-–—~]\\s*"), "-").trim()
    if (t.isEmpty()) return emptyList()
    val out = sortedSetOf<Int>()
    for (token in t.split(Regex("[,;\\s၊،]+"))) {
        if (token.isEmpty()) continue
        if (token.contains('-')) {
            val a = token.substringBefore('-')
            val b = token.substringAfter('-')
            val from = if (a.isEmpty()) 1 else a.toIntOrNull() ?: return emptyList()
            val to = if (b.isEmpty()) total else b.toIntOrNull() ?: return emptyList()
            if (from > to) return emptyList()
            for (p in from.coerceAtLeast(1)..to.coerceAtMost(total)) out.add(p - 1)
        } else {
            val p = token.toIntOrNull() ?: return emptyList()
            if (p in 1..total) out.add(p - 1)
        }
    }
    return out.toList()
}

enum class PageOp { EXTRACT, DELETE, ROTATE }

/** Extract / delete / rotate pages and write the result to [out]. Runs on a background thread. */
fun pdfPageOp(context: Context, src: Uri, password: String?, out: Uri, op: PageOp, pages: List<Int>, angle: Int) {
    PDFBoxResourceLoader.init(context.applicationContext)
    val tmp = File(context.cacheDir, "pageop_src.pdf")
    var doc: PDDocument? = null
    var dest: PDDocument? = null
    try {
        val input = context.contentResolver.openInputStream(src)
            ?: throw IllegalStateException(tr("cannot_open_file"))
        input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        val d = PDDocument.load(tmp, password ?: "", MemoryUsageSetting.setupTempFileOnly())
        doc = d
        if (d.isEncrypted) d.setAllSecurityToBeRemoved(true)
        val total = d.numberOfPages
        val valid = pages.filter { it in 0 until total }
        if (valid.isEmpty()) throw IllegalArgumentException(tr("pt_bad_range"))
        val result: PDDocument = when (op) {
            PageOp.EXTRACT -> {
                val nd = PDDocument(MemoryUsageSetting.setupTempFileOnly())
                dest = nd
                valid.forEach { nd.importPage(d.getPage(it)) }
                nd
            }
            PageOp.DELETE -> {
                if (valid.size >= total) throw IllegalArgumentException(tr("pt_bad_range"))
                valid.sortedDescending().forEach { d.removePage(it) }
                d
            }
            PageOp.ROTATE -> {
                valid.forEach {
                    val p = d.getPage(it)
                    p.rotation = ((p.rotation + angle) % 360 + 360) % 360
                }
                d
            }
        }
        val os = context.contentResolver.openOutputStream(out, "w")
            ?: throw IllegalStateException(tr("cannot_write"))
        os.use { result.save(it) }
    } finally {
        runCatching { dest?.close() }
        runCatching { doc?.close() }
        runCatching { tmp.delete() }
    }
}

/* ---------- Split into several files ---------- */

/**
 * Writes one PDF per group of pages into the folder [tree] (picked by the user).
 * Returns how many files were written. Runs on a background thread.
 */
fun pdfSplit(
    context: Context, src: Uri, password: String?, tree: Uri,
    groups: List<List<Int>>, baseName: String, progress: (String) -> Unit
): Int {
    PDFBoxResourceLoader.init(context.applicationContext)
    val tmp = File(context.cacheDir, "split_src.pdf")
    var doc: PDDocument? = null
    try {
        val input = context.contentResolver.openInputStream(src)
            ?: throw IllegalStateException(tr("cannot_open_file"))
        input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        val d = PDDocument.load(tmp, password ?: "", MemoryUsageSetting.setupTempFileOnly())
        doc = d
        if (d.isEncrypted) d.setAllSecurityToBeRemoved(true)
        val total = d.numberOfPages
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val base = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "document" }
        var made = 0
        groups.forEachIndexed { gi, g ->
            val pages = g.filter { it in 0 until total }
            if (pages.isEmpty()) return@forEachIndexed
            progress(tr("split_progress", gi + 1, groups.size))
            val name = "${base}_${gi + 1}_p${pages.first() + 1}-${pages.last() + 1}.pdf"
            val part = PDDocument(MemoryUsageSetting.setupTempFileOnly())
            try {
                pages.forEach { part.importPage(d.getPage(it)) }
                val outUri = DocumentsContract.createDocument(context.contentResolver, parent, "application/pdf", name)
                    ?: throw IllegalStateException(tr("cannot_write"))
                val os = context.contentResolver.openOutputStream(outUri, "w")
                    ?: throw IllegalStateException(tr("cannot_write"))
                os.use { part.save(it) }
                made++
            } finally {
                runCatching { part.close() }
            }
        }
        return made
    } finally {
        runCatching { doc?.close() }
        runCatching { tmp.delete() }
    }
}

/* ---------- Make the file smaller (re-compress the pictures inside) ---------- */

class CompressResult(val before: Long, val after: Long, val smaller: Boolean)

fun fmtSize(b: Long): String = when {
    b >= 1_048_576L -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024L -> "${b / 1024} KB"
    else -> "$b B"
}

/** Big pictures are scaled down and saved as JPEG again. Text, links and headings are not touched. */
private fun recompressImage(doc: PDDocument, img: PDImageXObject, maxSide: Int, quality: Float): PDImageXObject? {
    if (img.isStencil) return null
    if (img.width < 400 && img.height < 400) return null
    if (img.bitsPerComponent == 1) return null
    if (img.softMask != null || img.mask != null) return null
    val oldLen = img.cosObject.getInt(COSName.LENGTH, 0)
    val orig = img.image ?: return null
    var work = orig
    try {
        val big = maxOf(work.width, work.height)
        if (big > maxSide) {
            val k = maxSide.toFloat() / big
            val scaled = Bitmap.createScaledBitmap(
                work, (work.width * k).toInt().coerceAtLeast(1), (work.height * k).toInt().coerceAtLeast(1), true
            )
            if (scaled !== work) work = scaled
        }
        if (work.hasAlpha()) {
            val flat = Bitmap.createBitmap(work.width, work.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(flat)
            c.drawColor(Color.WHITE)
            c.drawBitmap(work, 0f, 0f, null)
            if (work !== orig) work.recycle()
            work = flat
        }
        val made = JPEGFactory.createFromImage(doc, work, quality)
        val newLen = made.cosObject.getInt(COSName.LENGTH, 0)
        if (oldLen > 0 && newLen > 0 && newLen >= oldLen) return null
        return made
    } finally {
        if (work !== orig) runCatching { work.recycle() }
        runCatching { orig.recycle() }
    }
}

/** Puts [img] in place of the picture called [name] (plain PDF dictionary edit). */
private fun replaceXObject(res: PDResources, name: COSName, img: PDImageXObject) {
    val xo = res.cosObject.getDictionaryObject(COSName.XOBJECT) as? COSDictionary ?: return
    xo.setItem(name, img.cosObject)
}

private fun compressResources(
    doc: PDDocument, res: PDResources?, maxSide: Int, quality: Float,
    done: java.util.IdentityHashMap<Any, PDImageXObject?>,
    seen: java.util.IdentityHashMap<Any, Boolean>, depth: Int
) {
    if (res == null || depth > 3) return
    if (seen.put(res.cosObject, true) != null) return
    for (name in res.xObjectNames.toList()) {
        val xo = runCatching { res.getXObject(name) }.getOrNull() ?: continue
        if (xo is PDFormXObject) {
            compressResources(doc, xo.resources, maxSide, quality, done, seen, depth + 1)
            continue
        }
        if (xo !is PDImageXObject) continue
        val key: Any = xo.cosObject
        if (done.containsKey(key)) {
            done[key]?.let { replaceXObject(res, name, it) }
            continue
        }
        val repl = runCatching { recompressImage(doc, xo, maxSide, quality) }.getOrNull()
        done[key] = repl
        if (repl != null) replaceXObject(res, name, repl)
    }
}

/**
 * [level] 0 = light, 1 = balanced, 2 = strong. If the result is not smaller than the original,
 * the original bytes are written instead (so the file you picked is always a good PDF).
 */
fun pdfCompress(
    context: Context, src: Uri, password: String?, out: Uri, level: Int, progress: (String) -> Unit
): CompressResult {
    PDFBoxResourceLoader.init(context.applicationContext)
    val tmp = File(context.cacheDir, "compress_src.pdf")
    val res = File(context.cacheDir, "compress_out.pdf")
    var doc: PDDocument? = null
    try {
        val input = context.contentResolver.openInputStream(src)
            ?: throw IllegalStateException(tr("cannot_open_file"))
        input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
        val before = tmp.length()
        val d = PDDocument.load(tmp, password ?: "", MemoryUsageSetting.setupTempFileOnly())
        doc = d
        if (d.isEncrypted) d.setAllSecurityToBeRemoved(true)

        val maxSide = when (level) { 0 -> 2200; 2 -> 1100; else -> 1600 }
        val quality = when (level) { 0 -> 0.80f; 2 -> 0.45f; else -> 0.62f }
        val done = java.util.IdentityHashMap<Any, PDImageXObject?>()
        val seen = java.util.IdentityHashMap<Any, Boolean>()
        val n = d.numberOfPages
        for (i in 0 until n) {
            if (i % 3 == 0) progress(tr("compress_progress", i + 1, n))
            compressResources(d, d.getPage(i).resources, maxSide, quality, done, seen, 0)
        }

        progress(tr("saving"))
        res.outputStream().use { d.save(it) }
        val after = res.length()
        val smaller = after < before
        val os = context.contentResolver.openOutputStream(out, "w")
            ?: throw IllegalStateException(tr("cannot_write"))
        os.use { o -> (if (smaller) res else tmp).inputStream().use { it.copyTo(o) } }
        return CompressResult(before, if (smaller) after else before, smaller)
    } finally {
        runCatching { doc?.close() }
        runCatching { tmp.delete() }
        runCatching { res.delete() }
    }
}

/* ---------- Images -> PDF ---------- */

/** Loads a photo (down-sampled) and turns it the right way up using its EXIF tag. */
fun loadOrientedBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val bmp = loadScaledBitmap(context, uri, maxSide) ?: return null
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        else -> return bmp
    }
    return runCatching { Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true) }.getOrDefault(bmp)
}

/** One page per picture, in the given order. Runs on a background thread. */
fun imagesToPdf(context: Context, sources: List<MergeSrc>, out: Uri, progress: (String) -> Unit) {
    PDFBoxResourceLoader.init(context.applicationContext)
    val doc = PDDocument(MemoryUsageSetting.setupTempFileOnly())
    try {
        sources.forEachIndexed { i, s ->
            progress(tr("reading", i + 1, sources.size))
            val src = loadOrientedBitmap(context, s.uri, 2400)
                ?: throw IllegalStateException(tr("cannot_read", s.name))
            // put the picture on white, so transparent PNGs do not turn black
            val flat = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flat)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(src, 0f, 0f, null)
            src.recycle()

            val wPt = 595f
            val hPt = wPt * flat.height / flat.width
            val page = PDPage(PDRectangle(wPt, hPt))
            doc.addPage(page)
            val img = JPEGFactory.createFromImage(doc, flat, 0.88f)
            val cs = PDPageContentStream(doc, page)
            try {
                cs.drawImage(img, 0f, 0f, wPt, hPt)
            } finally {
                cs.close()
            }
            flat.recycle()
        }
        progress(tr("saving"))
        val os = context.contentResolver.openOutputStream(out, "w")
            ?: throw IllegalStateException(tr("cannot_write"))
        os.use { doc.save(it) }
    } finally {
        runCatching { doc.close() }
    }
}

/* ---------- Share / Print ---------- */

fun copyToCache(context: Context, uri: Uri, fileName: String): File {
    val dir = File(context.cacheDir, "shared")
    dir.mkdirs()
    dir.listFiles()?.forEach { runCatching { it.delete() } }
    val safe = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "document.pdf" }
    val f = File(dir, if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf")
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException(tr("cannot_open_file"))
    input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    return f
}

fun shareFile(context: Context, f: File) {
    val u = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, u)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** Gives the PDF file to the Android print system (print to a printer or "Save as PDF"). */
class UriPrintAdapter(
    private val context: Context,
    private val uri: Uri,
    private val name: String
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        Thread {
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("cannot open")
                input.use { i ->
                    FileOutputStream(destination!!.fileDescriptor).use { o -> i.copyTo(o) }
                }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.message)
            }
        }.start()
    }
}

fun printPdf(context: Context, uri: Uri, name: String) {
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(name, UriPrintAdapter(context, uri, name), null)
}

/* ---------- Text to speech ---------- */

fun AppLang.locale(): Locale = when (this) {
    AppLang.EN -> Locale.ENGLISH
    AppLang.ZH -> Locale.CHINESE
    AppLang.JA -> Locale.JAPANESE
    AppLang.KO -> Locale.KOREAN
    else -> Locale(code)
}

class Tts(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var cont: CancellableContinuation<Unit>? = null
    private var lastId = ""
    private var counter = 0

    var ready by mutableStateOf(false)
    var speaking by mutableStateOf(false)

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) {
            if (utteranceId == lastId) finish()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (utteranceId == lastId) finish()
        }
    }

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            main.post {
                ready = status == TextToSpeech.SUCCESS
                if (ready) engine?.setOnUtteranceProgressListener(listener)
            }
        }
    }

    private fun finish() {
        main.post {
            speaking = false
            val c = cont
            cont = null
            if (c != null && c.isActive) c.resume(Unit)
        }
    }

    /** Picks the voice language and speed. Returns false if no voice for that language is installed. */
    fun configure(locale: Locale, rate: Float): Boolean {
        val e = engine ?: return false
        val r = e.setLanguage(locale)
        e.setSpeechRate(rate)
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun chunk(text: String): List<String> {
        val max = 3000
        val out = ArrayList<String>()
        var s = text.replace(Regex("\\s+"), " ").trim()
        while (s.isNotEmpty()) {
            if (s.length <= max) {
                out.add(s)
                break
            }
            var cut = s.lastIndexOfAny(charArrayOf('.', '!', '?', '။', '。', '।', '؟'), max)
            if (cut < max / 2) cut = s.lastIndexOf(' ', max)
            if (cut < max / 2) cut = max - 1
            out.add(s.substring(0, cut + 1).trim())
            s = s.substring(cut + 1).trim()
        }
        return out
    }

    private fun enqueue(text: String): Boolean {
        val e = engine
        if (e == null || !ready) return false
        val parts = chunk(text)
        if (parts.isEmpty()) return false
        val ids = parts.map { "u${++counter}" }
        lastId = ids.last()
        speaking = true
        parts.forEachIndexed { i, p ->
            e.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, ids[i])
        }
        return true
    }

    /** Speaks [text] and returns when it is finished (or when the calling coroutine is cancelled). */
    suspend fun speakAndWait(text: String) {
        suspendCancellableCoroutine<Unit> { c ->
            cont = c
            c.invokeOnCancellation {
                main.post {
                    engine?.stop()
                    speaking = false
                }
            }
            if (!enqueue(text)) {
                cont = null
                c.resume(Unit)
            }
        }
    }

    /** Speaks [text] and returns at once. */
    fun speak(text: String) {
        cont = null
        enqueue(text)
    }

    fun stop() {
        engine?.stop()
        speaking = false
    }

    fun shutdown() {
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }
}
