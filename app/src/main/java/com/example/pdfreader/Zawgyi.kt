package com.example.pdfreader

/**
 * Zawgyi -> Unicode (Myanmar). Pure Kotlin, no library, no extra size.
 *
 * Covers the everyday letters: medials (ya-pin, ya-yit incl. the wide variants, wa-hswe, ha-htoe),
 * asat, the "e" vowel and medials moved to Unicode order, tall/short u and uu, nya/nnya/ra variants,
 * ဦ / ဉ, "ါ်" and the ့ + ် order.
 * NOT covered: stacked consonants (Pali words, e.g. ဒ္ဓ), kinzi (င်္) and rare Zawgyi-only glyphs.
 * [zawgyiHasUnsupported] tells the UI when the text contains such characters.
 */
private const val CONS = "[\u1000-\u1021\u1025-\u1027\u1029\u102a\u103f]"
private val ZG_SYLLABLE = Regex("([\u1031\u103c]*)($CONS)([\u103b-\u103e]*)")
private const val MEDIAL_ORDER = "\u103b\u103c\u103d\u103e"
private val ZG_LEFTOVER = Regex("[\u1060-\u1097]")
// "e" or ya-yit written before a consonant (not after one) only happens in Zawgyi
private val ZG_SIGNAL_E = Regex("(^|[^\u1000-\u1021\u103b-\u103e\u1060-\u1097])[\u1031\u103b\u107e-\u1084]")
private val ZG_SIGNAL_CHARS = Regex("[\u1033\u1034\u1060-\u1074\u1086-\u108f\u1097]")

fun zawgyiToUnicode(input: String): String {
    var s = input.replace("\u200b", "")
    if (s.none { it.code in 0x1000..0x109F }) return s

    s = s.replace(Regex("[\u103d\u1087]"), "\u103e")          // ha-htoe
    s = s.replace('\u103c', '\u103d')                          // wa-hswe
    s = s.replace(Regex("[\u103b\u107e-\u1084]"), "\u103c")    // ya-yit (written before the consonant)
    s = s.replace(Regex("[\u103a\u107d]"), "\u103b")           // ya-pin
    s = s.replace('\u1039', '\u103a')                          // asat

    s = s.replace('\u106a', '\u1009')   // ဉ
    s = s.replace('\u106b', '\u100a')   // ည
    s = s.replace('\u1090', '\u101b')   // ရ
    s = s.replace('\u108f', '\u1014')   // န
    s = s.replace('\u1033', '\u102f')   // tall ု
    s = s.replace('\u1034', '\u1030')   // tall ူ
    s = s.replace("\u1088", "\u103e\u102f")
    s = s.replace("\u1089", "\u103e\u1030")
    s = s.replace("\u108a", "\u103d\u103e")
    s = s.replace("\u105a", "\u102b\u103a")
    s = s.replace("\u1025\u102e", "\u1026")                    // ဦ
    s = s.replace(Regex("\u1025(?=[\u103a\u102c])"), "\u1009") // ဉ before asat / ါ

    // Zawgyi writes "e" and ya-yit BEFORE the consonant; Unicode writes them after it, medials in a fixed order.
    s = ZG_SYLLABLE.replace(s) { m ->
        val pre = m.groupValues[1]
        val c = m.groupValues[2]
        val med = m.groupValues[3]
        val sb = StringBuilder(c)
        for (x in MEDIAL_ORDER) {
            if (med.indexOf(x) >= 0 || (x == '\u103c' && pre.indexOf('\u103c') >= 0)) sb.append(x)
        }
        if (pre.indexOf('\u1031') >= 0) sb.append('\u1031')
        sb.toString()
    }
    return s.replace("\u1037\u103a", "\u103a\u1037")
}

/** Pass the CONVERTED text: true when it still holds Zawgyi-only characters this converter does not handle. */
fun zawgyiHasUnsupported(converted: String): Boolean = ZG_LEFTOVER.containsMatchIn(converted)

/** Rough guess: does this Myanmar text look like Zawgyi (and not Unicode)? */
fun looksZawgyi(s: String): Boolean {
    if (s.none { it.code in 0x1000..0x109F }) return false
    return ZG_SIGNAL_CHARS.containsMatchIn(s) || ZG_SIGNAL_E.containsMatchIn(s)
}

/** Zawgyi text becomes Unicode; Unicode text is left as it is. */
fun fixMyanmar(s: String): String = if (looksZawgyi(s)) zawgyiToUnicode(s) else s

private const val ZG_SAMPLE = "\u103B\u1019\u1014\u1039\u1019\u102C"       // Zawgyi
private const val UNI_SAMPLE = "\u1019\u103C\u1014\u103A\u1019\u102C"      // Unicode

/** The phone's own Unicode -> Zawgyi converter (Android ICU), if it has one. Found and checked at run time. */
private val icuToZawgyi: android.icu.text.Transliterator? by lazy {
    runCatching {
        val ids = java.util.Collections.list(android.icu.text.Transliterator.getAvailableIDs())
        val id = ids.firstOrNull { it.contains("zawgyi", ignoreCase = true) } ?: return@runCatching null
        val fwd = android.icu.text.Transliterator.getInstance(id, android.icu.text.Transliterator.FORWARD)
        val rev = android.icu.text.Transliterator.getInstance(id, android.icu.text.Transliterator.REVERSE)
        when {
            fwd.transliterate(UNI_SAMPLE) == ZG_SAMPLE -> fwd
            rev.transliterate(UNI_SAMPLE) == ZG_SAMPLE -> rev
            else -> null
        }
    }.getOrNull()
}

/**
 * Unicode -> Zawgyi. Uses the phone's converter when it has one; otherwise a basic one that covers the
 * everyday letters, medials and the "e" vowel (not stacked Pali consonants / kinzi).
 */
fun unicodeToZawgyi(input: String): String {
    icuToZawgyi?.let { t -> runCatching { t.transliterate(input) }.getOrNull()?.let { return it } }
    if (input.none { it.code in 0x1000..0x109F }) return input
    val sb = StringBuilder()
    for (c in input) {
        when (c) {
            '\u103A' -> sb.append('\u1039')   // asat
            '\u103B' -> sb.append('\u103A')   // ya-pin
            '\u103C' -> sb.append('\u103B')   // ya-yit
            '\u103D' -> sb.append('\u103C')   // wa-hswe
            '\u103E' -> sb.append('\u103D')   // ha-htoe
            else -> sb.append(c)
        }
    }
    // Unicode: consonant + medials + "e". Zawgyi: "e" + ya-yit + consonant + other medials.
    val re = Regex("($CONS)([\u103A\u103B\u103C\u103D]*)(\u1031)?")
    return re.replace(sb.toString()) { m ->
        val c = m.groupValues[1]
        val med = m.groupValues[2]
        val e = m.groupValues[3]
        val yaYit = if (med.indexOf('\u103B') >= 0) "\u103B" else ""
        e + yaYit + c + med.replace("\u103B", "")
    }.replace("\u1026", "\u1025\u102E")
}
