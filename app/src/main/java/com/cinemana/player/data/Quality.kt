package com.cinemana.player.data

val QUALITY_DISPLAY_ORDER = listOf("4k", "1080p", "720p", "480p", "360p")
val QUALITY_DEFAULT_PRIORITY = listOf("1080p", "720p", "480p", "360p", "4k")

/**
 * mp4_144 / mp4_240 -> excluded, 360 -> 360p, m480/480m -> 480p, 720 -> 720p,
 * 1080 -> 1080p, 4k/2160 -> 4k, anything else -> excluded.
 */
fun normalizeQuality(raw: String?): String? {
    val s = (raw ?: "").lowercase()
    if (Regex("4k|2160").containsMatchIn(s)) return "4k"
    val m = Regex("(\\d{3,4})").find(s) ?: return null
    return when (m.value.toInt()) {
        360 -> "360p"
        480 -> "480p"
        720 -> "720p"
        1080 -> "1080p"
        else -> null
    }
}

fun buildQualityMap(list: List<StreamSource>): Map<String, String> {
    val map = LinkedHashMap<String, String>()
    for (item in list) {
        val canon = normalizeQuality(item.resolution) ?: normalizeQuality(item.name)
        if (canon != null && !map.containsKey(canon)) map[canon] = item.url
    }
    return map
}

fun pickDefaultQuality(map: Map<String, String>): String? {
    for (q in QUALITY_DEFAULT_PRIORITY) if (map.containsKey(q)) return q
    return map.keys.firstOrNull()
}

fun qualityLabel(q: String?): String = when (q) {
    null -> "—"
    "4k" -> "4K"
    else -> q
}
