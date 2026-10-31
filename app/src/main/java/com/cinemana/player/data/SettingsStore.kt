package com.cinemana.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cinemana_player")

/** Everything the HTML kept under cinemana_player_settings_v1 (volume is system-controlled on Android). */
data class Prefs(
    val fontAr: String = "sans",
    val fontLatin: String = "sans",
    val sizeIndex: Int = 2,
    val weight: Int = 600,
    val colorHex: String = "#ffffff",
    val bg: Int = 0,
    val subScrim: Int = 50,
    val positionIndex: Int = 1,
    val quality: String? = null,
    /** null = the user never chose -> default to Arabic when available. Empty = user turned subtitles off. */
    val activeTracks: Set<String>? = null,
    val familyMode: Boolean = true,
    val autoplayNext: Boolean = true,
    val playbackRate: Float = 1f,
)

class SettingsStore(private val context: Context) {
    private object K {
        val fontAr = stringPreferencesKey("fontAr")
        val fontLatin = stringPreferencesKey("fontLatin")
        val size = intPreferencesKey("sizeIndex")
        val weight = intPreferencesKey("weight")
        val color = stringPreferencesKey("color")
        val bg = intPreferencesKey("bg")
        val scrim = intPreferencesKey("subScrim")
        val pos = intPreferencesKey("positionIndex")
        val quality = stringPreferencesKey("quality")
        val tracks = stringSetPreferencesKey("activeTracks")
        val family = booleanPreferencesKey("familyMode")
        val autoplay = booleanPreferencesKey("autoplayNext")
        val rate = floatPreferencesKey("playbackRate")
        val progress = stringPreferencesKey("progress_v1")
    }

    val prefsFlow: Flow<Prefs> = context.dataStore.data.map { p ->
        val d = Prefs()
        Prefs(
            fontAr = p[K.fontAr] ?: d.fontAr,
            fontLatin = p[K.fontLatin] ?: d.fontLatin,
            sizeIndex = p[K.size] ?: d.sizeIndex,
            weight = p[K.weight] ?: d.weight,
            colorHex = p[K.color] ?: d.colorHex,
            bg = p[K.bg] ?: d.bg,
            subScrim = p[K.scrim] ?: d.subScrim,
            positionIndex = p[K.pos] ?: d.positionIndex,
            quality = p[K.quality],
            activeTracks = p[K.tracks],
            familyMode = p[K.family] ?: d.familyMode,
            autoplayNext = p[K.autoplay] ?: d.autoplayNext,
            playbackRate = p[K.rate] ?: d.playbackRate,
        )
    }

    suspend fun save(p: Prefs) {
        context.dataStore.edit { e ->
            e[K.fontAr] = p.fontAr
            e[K.fontLatin] = p.fontLatin
            e[K.size] = p.sizeIndex
            e[K.weight] = p.weight
            e[K.color] = p.colorHex
            e[K.bg] = p.bg
            e[K.scrim] = p.subScrim
            e[K.pos] = p.positionIndex
            if (p.quality != null) e[K.quality] = p.quality else e.remove(K.quality)
            if (p.activeTracks != null) e[K.tracks] = p.activeTracks else e.remove(K.tracks)
            e[K.family] = p.familyMode
            e[K.autoplay] = p.autoplayNext
            e[K.rate] = p.playbackRate
        }
    }

    /* ───── resume positions (max 300 entries, oldest dropped) ───── */

    suspend fun loadProgress(videoId: String): Progress? {
        val raw = context.dataStore.data.first()[K.progress] ?: return null
        return try {
            val o = JSONObject(raw).optJSONObject(videoId) ?: return null
            Progress(o.optDouble("t", 0.0), o.optDouble("d", 0.0), o.optLong("u", 0L))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveProgress(videoId: String, time: Double, duration: Double) {
        context.dataStore.edit { e ->
            val all = try {
                JSONObject(e[K.progress] ?: "{}")
            } catch (ex: Exception) {
                JSONObject()
            }
            all.put(
                videoId,
                JSONObject().put("t", time).put("d", duration).put("u", System.currentTimeMillis())
            )
            if (all.length() > 300) {
                val keys = all.keys().asSequence().toList()
                val oldestFirst = keys.sortedBy { all.optJSONObject(it)?.optLong("u", 0L) ?: 0L }
                for (k in oldestFirst.take(all.length() - 300)) all.remove(k)
            }
            e[K.progress] = all.toString()
        }
    }
}
