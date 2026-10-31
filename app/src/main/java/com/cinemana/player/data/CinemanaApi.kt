package com.cinemana.player.data

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Port of the "CINEMANA API LAYER" from the HTML file.
 * Same endpoints, same 5-minute response cache, same fallbacks.
 */
class CinemanaApi {
    companion object {
        const val BASE_URL = "https://cinemana.shabakaty.cc/api/android"
        const val ADVANCED_SEARCH = "$BASE_URL/AdvancedSearch"
        private const val CACHE_TTL = 300_000L
        const val MAX_SEARCH_PAGES = 5

        fun transcodedFiles(id: String) = "$BASE_URL/transcoddedFiles/id/$id"
        fun allVideoInfo(id: String) = "$BASE_URL/allVideoInfo/id/$id"
        fun translationFiles(id: String) = "$BASE_URL/translationFiles/id/$id"
        fun skippingDurations(id: String) = "$BASE_URL/skippingDurations/id/$id"
        fun videoSeason(id: String) = "$BASE_URL/videoSeason/id/$id"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = ConcurrentHashMap<String, Pair<Long, JsonElement>>()

    fun clearCache() = cache.clear()

    /* ───── low-level HTTP ───── */

    private suspend fun httpGet(url: String, accept: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("Accept", accept).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: ""
        }
    }

    private suspend fun httpGetWithRetry(url: String, retries: Int, delayMs: Long, accept: String): String {
        var last: Exception? = null
        for (i in 0 until retries) {
            try {
                return httpGet(url, accept)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                if (i < retries - 1) delay(delayMs * (i + 1))
            }
        }
        throw last ?: IOException("Request failed")
    }

    private suspend fun getJson(url: String, params: Map<String, String> = emptyMap()): JsonElement {
        val builder = url.toHttpUrl().newBuilder()
        for ((k, v) in params) builder.addQueryParameter(k, v)
        val full = builder.build().toString()
        val now = System.currentTimeMillis()
        val hit = cache[full]
        if (hit != null && now - hit.first < CACHE_TTL) return hit.second
        val data = json.parseToJsonElement(httpGet(full, "application/json"))
        cache[full] = now to data
        return data
    }

    /* ───── search ───── */

    private suspend fun searchPage(title: String, year: Int?, type: String, page: Int): List<SearchItem> {
        val params = linkedMapOf(
            "level" to "0",
            "videoTitle" to title,
            "year" to (if (year != null) "$year,$year" else ""),
            "type" to type,
            "page" to page.toString(),
        )
        val data = getJson(ADVANCED_SEARCH, params)
        return parseResponseItems(data).filterIsInstance<JsonObject>().map { normalizeSearchItem(it) }
    }

    suspend fun searchAllPages(title: String, year: Int?, type: String, maxPages: Int = MAX_SEARCH_PAGES): List<SearchItem> {
        val results = ArrayList<SearchItem>()
        val seen = HashSet<String>()
        for (page in 0 until maxOf(1, maxPages)) {
            val batch = try {
                searchPage(title, year, type, page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (page == 0) throw e else break
            }
            if (batch.isEmpty()) break
            for (item in batch) {
                val key = item.cinemanaId ?: "${item.titleEn}-${item.titleAr}-${item.year}"
                if (seen.add(key)) results.add(item)
            }
        }
        return results
    }

    /* ───── episodes ───── */

    suspend fun fetchEpisodes(seriesId: String): List<Episode> {
        val items = parseResponseItems(getJson(videoSeason(seriesId)))
        val eps = ArrayList<Episode>()
        for (el in items) {
            val item = el as? JsonObject ?: continue
            val id = item.str("nb") ?: item.str("id") ?: item.str("videoId") ?: continue
            eps.add(
                Episode(
                    id = id,
                    title = item.str("en_title") ?: item.str("ar_title") ?: item.str("title") ?: "Episode",
                    season = item.str("season").toIntLoose() ?: 0,
                    episode = item.str("episodeNummer").toIntLoose() ?: item.str("episode").toIntLoose(),
                    raw = item,
                )
            )
        }
        return eps
    }

    /* ───── playback sources ───── */

    suspend fun fetchPlayback(videoId: String): List<StreamSource> {
        val items = parseResponseItems(getJson(transcodedFiles(videoId)))
        val out = ArrayList<StreamSource>()
        for (el in items) {
            val item = el as? JsonObject ?: continue
            val url = item.str("videoUrl") ?: continue
            out.add(StreamSource(item.str("name") ?: "", item.str("resolution") ?: "", url))
        }
        return out
    }

    /** allVideoInfo: posters, subtitle tracks, intro-skip and family-skip segments. */
    suspend fun fetchVideoInfo(videoId: String): JsonObject? = try {
        getJson(allVideoInfo(videoId)) as? JsonObject
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun extractPoster(info: JsonObject?): String? =
        info?.str("imgObjUrl") ?: info?.str("imgMediumThumbObjUrl") ?: info?.str("imgThumbObjUrl")

    /* ───── subtitles ───── */

    private fun buildSubtitleMap(info: JsonElement?): Map<String, SubtitleEntry> {
        val list = ((info as? JsonObject)?.get("translations") as? JsonArray) ?: return emptyMap()
        val labels = LinkedHashMap<String, String>()
        val files = LinkedHashMap<String, MutableList<SubFile>>()
        for (el in list) {
            val t = el as? JsonObject ?: continue
            val lang = t.str("type")?.lowercase() ?: continue
            val file = t.str("file") ?: continue
            val format = if (t.str("extention")?.lowercase() == "vtt") "vtt" else "srt"
            if (!labels.containsKey(lang)) labels[lang] = t.str("name") ?: lang
            files.getOrPut(lang) { ArrayList() }.add(SubFile(format, file))
        }
        val out = LinkedHashMap<String, SubtitleEntry>()
        for ((lang, fs) in files) {
            // vtt first (more standard), srt kept as a fallback file.
            val sorted = fs.sortedBy { if (it.format == "vtt") 0 else 1 }
            out[lang] = SubtitleEntry(lang, labels[lang] ?: lang, sorted)
        }
        return out
    }

    suspend fun fetchSubtitleMap(videoId: String, info: JsonObject?): Map<String, SubtitleEntry> {
        val map = buildSubtitleMap(info)
        if (map.isNotEmpty()) return map
        return try {
            buildSubtitleMap(getJson(translationFiles(videoId)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun fetchAndParseSubtitle(url: String, format: String): List<Cue> {
        // Subtitle files are plain text; don't send Accept: application/json.
        val text = httpGetWithRetry(url, 2, 500, "text/vtt, text/plain, */*")
        return parseSubtitleText(text, url, format)
    }

    /* ───── intro / family skip ───── */

    fun extractIntroSkip(info: JsonObject?): List<Segment> {
        if (info == null || !info["hasIntroSkipping"].isTruthyFlag()) return emptyList()
        val arr = info["introSkipping"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val s = o.str("start")?.toDoubleOrNull()
            val e = o.str("end")?.toDoubleOrNull()
            if (s != null && e != null && e > s) Segment(s, e) else null
        }
    }

    private fun segmentsFrom(sd: JsonObject?): List<Segment> {
        val starts = sd?.get("start") as? JsonArray ?: return emptyList()
        val ends = sd?.get("end") as? JsonArray ?: return emptyList()
        val out = ArrayList<Segment>()
        for (i in starts.indices) {
            val s = starts[i].asStringOrNull()?.toDoubleOrNull()
            val e = ends.getOrNull(i).asStringOrNull()?.toDoubleOrNull()
            if (s != null && e != null && e > s) out.add(Segment(s, e))
        }
        return out
    }

    suspend fun fetchFamilySkip(videoId: String, info: JsonObject?): List<Segment> {
        if (info != null && info["parent_skipping"].isTruthyFlag()) {
            val segs = segmentsFrom(info["skippingDurations"] as? JsonObject)
            if (segs.isNotEmpty()) return segs
        }
        return try {
            segmentsFrom(getJson(skippingDurations(videoId)) as? JsonObject)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
}
