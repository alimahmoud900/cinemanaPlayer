package com.cinemana.player.data

import java.text.Normalizer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val AUTO_SELECT_THRESHOLD = 40.0

private val DIACRITICS = Regex("[\\u0300-\\u036f]")
private val NON_ALNUM = Regex("[^a-z0-9\\u0600-\\u06FF]+")
private val SPACES = Regex("\\s+")

fun normalizeText(v: String?): String {
    if (v.isNullOrBlank()) return ""
    var s = v.trim().lowercase()
    s = Normalizer.normalize(s, Normalizer.Form.NFKD).replace(DIACRITICS, "")
    s = s.replace(NON_ALNUM, " ")
    return s.replace(SPACES, " ").trim()
}

fun compactText(v: String?): String = normalizeText(v).replace(" ", "")

fun extractImdbId(v: String?): String? {
    if (v.isNullOrBlank()) return null
    return Regex("(tt\\d+)", RegexOption.IGNORE_CASE).find(v)?.groupValues?.get(1)?.lowercase()
}

fun extractMalId(v: String?): String? {
    if (v.isNullOrBlank()) return null
    return Regex("myanimelist\\.net/(?:anime|manga)/(\\d+)", RegexOption.IGNORE_CASE).find(v)?.groupValues?.get(1)
}

/** Ratcliff/Obershelp ratio, same algorithm as the HTML's sequenceMatcherRatio (Python difflib-style). */
fun sequenceMatcherRatio(a: String, b: String): Double {
    if (a.isEmpty() && b.isEmpty()) return 1.0
    if (a.isEmpty() || b.isEmpty()) return 0.0
    if (a == b) return 1.0
    var matches = 0
    fun queue(aLow: Int, aHigh: Int, bLow: Int, bHigh: Int) {
        if (aLow >= aHigh || bLow >= bHigh) return
        var maxLen = 0
        var bestA = aLow
        var bestB = bLow
        for (i in aLow until aHigh) {
            for (j in bLow until bHigh) {
                var k = 0
                while (i + k < aHigh && j + k < bHigh && a[i + k] == b[j + k]) k++
                if (k > maxLen) {
                    maxLen = k; bestA = i; bestB = j
                }
            }
        }
        if (maxLen > 0) {
            matches += maxLen
            queue(aLow, bestA, bLow, bestB)
            queue(bestA + maxLen, aHigh, bestB + maxLen, bHigh)
        }
    }
    queue(0, a.length, 0, b.length)
    return 2.0 * matches / (a.length + b.length)
}

fun titleSimilarity(queryTitle: String, c: SearchItem): Double {
    val q = normalizeText(queryTitle)
    val scores = ArrayList<Double>()
    for (t in listOf(c.titleEn, c.titleAr, c.otherTitle)) {
        if (t.isNullOrBlank()) continue
        scores.add(sequenceMatcherRatio(q, normalizeText(t)))
        scores.add(sequenceMatcherRatio(compactText(queryTitle), compactText(t)))
    }
    return scores.maxOrNull() ?: 0.0
}

/** Returns (confidence 0..100, method). Identical scoring rules to scoreCandidate() in the HTML. */
fun scoreCandidate(
    queryTitle: String,
    queryYear: String?,
    queryImdb: String?,
    queryMal: String?,
    c: SearchItem,
): Pair<Double, String> {
    val qImdb = (queryImdb ?: "").lowercase().trim()
    val qMal = (queryMal ?: "").trim()
    val cImdb = (c.imdbId ?: "").lowercase().trim()
    val cMal = (c.malId ?: "").trim()
    val qYear = (queryYear ?: "").trim()
    val cYear = (c.year ?: "").trim()

    val titleScore = titleSimilarity(queryTitle, c)
    val nq = normalizeText(queryTitle)
    val exactTitle = listOf(c.titleEn, c.titleAr, c.otherTitle)
        .filter { !it.isNullOrBlank() }
        .any { nq == normalizeText(it) }

    var yearMatch = false
    if (qYear.isNotEmpty() && cYear.isNotEmpty()) {
        val qi = qYear.toIntOrNull()
        val ci = cYear.toIntOrNull()
        yearMatch = if (qi != null && ci != null) Math.abs(qi - ci) <= 1 else qYear == cYear
    }

    if (qImdb.isNotEmpty() && cImdb.isNotEmpty() && qImdb == cImdb) return 100.0 to "imdb_exact"
    if (qMal.isNotEmpty() && cMal.isNotEmpty() && qMal == cMal) return 100.0 to "mal_exact"

    val conflict = (qImdb.isNotEmpty() && cImdb.isNotEmpty() && qImdb != cImdb) ||
        (qMal.isNotEmpty() && cMal.isNotEmpty() && qMal != cMal)
    if (conflict) {
        var s = titleScore * 15.0
        if (yearMatch) s += 5.0
        return minOf(s, 25.0) to "external_id_conflict"
    }

    var score = titleScore * 70.0
    if (exactTitle) score += 20.0
    if (yearMatch) score += 10.0
    return minOf(score, 99.0) to "title_year"
}

fun normalizeSearchItem(item: JsonObject): SearchItem {
    val external = item.str("imdbUrlRef") ?: item.str("externalUrl") ?: ""
    return SearchItem(
        cinemanaId = item.str("nb"),
        titleEn = item.str("en_title"),
        titleAr = item.str("ar_title"),
        otherTitle = item.str("other_title"),
        year = item.str("year"),
        imdbId = extractImdbId(external),
        malId = extractMalId(external),
        externalUrl = external,
        raw = item,
    )
}

/* ───────── image discovery (port of findImageInObject) ───────── */

private val IMAGE_KEYS = listOf(
    "imgObjUrl", "imgMediumThumbObjUrl", "imgThumbObjUrl", "season_img", "seasonImage",
    "season_poster", "img", "image", "poster", "poster_path", "thumbnail", "thumb", "banner", "cover",
)
private val HTTP_RE = Regex("^https?://", RegexOption.IGNORE_CASE)
private val IMG_EXT_RE = Regex("\\.(jpe?g|png|webp|gif)(\\?|$)", RegexOption.IGNORE_CASE)

private fun imageUrlOrNull(v: JsonElement?): String? {
    val p = v as? JsonPrimitive ?: return null
    if (p is JsonNull || !p.isString) return null
    val s = p.content
    return if (HTTP_RE.containsMatchIn(s) && IMG_EXT_RE.containsMatchIn(s)) s else null
}

fun findImage(el: JsonElement?, depth: Int = 0): String? {
    if (depth > 2) return null
    when (el) {
        is JsonObject -> {
            for (k in IMAGE_KEYS) imageUrlOrNull(el[k])?.let { return it }
            for ((_, v) in el) {
                imageUrlOrNull(v)?.let { return it }
                if (depth < 2 && (v is JsonObject || v is JsonArray)) findImage(v, depth + 1)?.let { return it }
            }
        }
        is JsonArray -> for (v in el) {
            imageUrlOrNull(v)?.let { return it }
            if (depth < 2 && (v is JsonObject || v is JsonArray)) findImage(v, depth + 1)?.let { return it }
        }
        else -> {}
    }
    return null
}
