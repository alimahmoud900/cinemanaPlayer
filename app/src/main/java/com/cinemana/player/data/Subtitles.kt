package com.cinemana.player.data

val LANG_LABELS = mapOf(
    "ar" to "Arabic", "en" to "English", "fr" to "French", "es" to "Spanish", "de" to "German",
    "tr" to "Turkish", "fa" to "Persian", "ku" to "Kurdish", "ur" to "Urdu", "unknown" to "Unknown",
)
val RTL_LANGS = setOf("ar", "fa", "ur")

data class Step(val label: String, val pct: Int)

/** Subtitle size steps (100% = base size). */
val SIZE_STEPS = listOf(
    Step("Very small", 80), Step("Small", 100), Step("Medium", 125),
    Step("Big", 150), Step("Very big", 175), Step("Max", 200),
)

/** Vertical position steps: percentage of the player height measured from the bottom edge. */
val POSITION_STEPS = listOf(
    Step("Very low", 4), Step("Low", 14), Step("Medium", 26), Step("High", 42), Step("Very high", 62),
)

val SPEED_STEPS = listOf(0.5f to "0.5x", 0.75f to "0.75x", 1f to "Normal", 1.25f to "1.25x", 1.5f to "1.5x", 2f to "2x")

fun speedLabel(rate: Float): String = SPEED_STEPS.firstOrNull { it.first == rate }?.second ?: "Normal"

fun sortLanguagesArabicFirst(langs: Collection<String>): List<String> =
    langs.sortedWith { a, b ->
        when {
            a == b -> 0
            a == "ar" -> -1
            b == "ar" -> 1
            else -> (LANG_LABELS[a] ?: a).compareTo(LANG_LABELS[b] ?: b)
        }
    }

/* ───────── SRT / VTT parsing (ports of parseSRT / parseVTT) ───────── */

private val TS_RE = Regex("(?:(\\d+):)?(\\d{2}):(\\d{2})[.,](\\d{3})")
private val TAG_RE = Regex("<[^>]+>")
private val BLOCK_SPLIT = Regex("\\n\\s*\\n")

/** Accepts HH:MM:SS,mmm / HH:MM:SS.mmm and the WebVTT shorthand MM:SS.mmm. */
fun parseSubtitleTimestamp(str: String): Double? {
    val m = TS_RE.find(str.trim()) ?: return null
    val h = m.groupValues[1].ifEmpty { "0" }.toInt()
    return h * 3600.0 + m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt() + m.groupValues[4].toInt() / 1000.0
}

private fun parseCueTiming(line: String): Pair<Double, Double>? {
    val parts = line.split("-->")
    if (parts.size < 2) return null
    val s = parseSubtitleTimestamp(parts[0]) ?: return null
    val e = parseSubtitleTimestamp(parts[1]) ?: return null
    return s to e
}

fun parseSRT(textIn: String): List<Cue> {
    val text = textIn.removePrefix("\uFEFF").replace("\r", "")
    val cues = ArrayList<Cue>()
    for (block in text.split(BLOCK_SPLIT)) {
        val lines = block.split("\n").filter { it.isNotBlank() }
        if (lines.size < 2) continue
        var idx = 0
        if (lines[0].trim().all { it.isDigit() }) idx = 1
        val timing = lines.getOrNull(idx)?.let { parseCueTiming(it) } ?: continue
        val body = lines.drop(idx + 1).joinToString("\n").replace(TAG_RE, "")
        if (body.isBlank()) continue
        cues.add(Cue(timing.first, timing.second, body))
    }
    return cues
}

fun parseVTT(textIn: String): List<Cue> {
    val text = textIn.removePrefix("\uFEFF").replace("\r", "")
    val cues = ArrayList<Cue>()
    for (block in text.split(BLOCK_SPLIT)) {
        val lines = block.split("\n").filter { it.isNotBlank() }
        if (lines.size < 2) continue
        if (lines[0].startsWith("WEBVTT") || lines[0].startsWith("NOTE")) continue
        val timeIdx = lines.indexOfFirst { it.contains("-->") }
        if (timeIdx == -1) continue
        val timing = parseCueTiming(lines[timeIdx]) ?: continue
        val body = lines.drop(timeIdx + 1).joinToString("\n").replace(TAG_RE, "")
        if (body.isBlank()) continue
        cues.add(Cue(timing.first, timing.second, body))
    }
    return cues
}

fun parseSubtitleText(text: String, nameOrUrl: String, format: String? = null): List<Cue> {
    val isVtt = format == "vtt" || nameOrUrl.lowercase().contains(".vtt") ||
        text.removePrefix("\uFEFF").trim().startsWith("WEBVTT")
    return if (isVtt) parseVTT(text) else parseSRT(text)
}

/** Detects the script of an uploaded file from its cue text, falling back to filename hints. */
fun detectSubtitleLang(cues: List<Cue>, filename: String): String {
    val sample = cues.take(20).joinToString(" ") { it.text }
    val hasArabic = Regex("[\\u0600-\\u06FF\\u0750-\\u077F]").containsMatchIn(sample)
    val hasLatin = Regex("[A-Za-z]{2,}").containsMatchIn(sample)
    if (hasArabic && !hasLatin) return "ar"
    if (hasLatin && !hasArabic) return "en"
    if (Regex("(^|[^a-z])(ar|arab|arabic)([^a-z]|$)|عرب", RegexOption.IGNORE_CASE).containsMatchIn(filename)) return "ar"
    if (Regex("(^|[^a-z])(en|eng|english)([^a-z]|$)", RegexOption.IGNORE_CASE).containsMatchIn(filename)) return "en"
    return "unknown"
}
