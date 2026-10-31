package com.cinemana.player.data

import kotlinx.serialization.json.JsonObject

data class SearchItem(
    val cinemanaId: String?,
    val titleEn: String?,
    val titleAr: String?,
    val otherTitle: String?,
    val year: String?,
    val imdbId: String?,
    val malId: String?,
    val externalUrl: String,
    val raw: JsonObject,
    val confidence: Double = 0.0,
    val matchMethod: String = "",
) {
    val displayTitle: String get() = titleEn ?: titleAr ?: "Untitled"
}

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int?,
    val raw: JsonObject,
)

/** One entry from transcoddedFiles: e.g. name "m480", resolution "480p", url. */
data class StreamSource(val name: String, val resolution: String, val url: String)

data class SubFile(val format: String, val url: String)
data class SubtitleEntry(val language: String, val label: String, val files: List<SubFile>)
data class Cue(val start: Double, val end: Double, val text: String)
data class Segment(val start: Double, val end: Double)
data class CustomSub(val id: String, val name: String, val lang: String)
data class Progress(val time: Double, val duration: Double, val updatedAt: Long)

fun fmtTime(sec: Double): String {
    if (sec.isNaN() || sec.isInfinite()) return "0:00"
    val s = maxOf(0.0, sec)
    val h = (s / 3600).toInt()
    val m = ((s % 3600) / 60).toInt()
    val ss = (s % 60).toInt()
    return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.US, h, m, ss)
    else "%d:%02d".format(java.util.Locale.US, m, ss)
}
