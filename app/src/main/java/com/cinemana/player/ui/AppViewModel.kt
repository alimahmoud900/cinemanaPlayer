@file:OptIn(UnstableApi::class)

package com.cinemana.player.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.cinemana.player.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Panel { None, Settings, Episodes }

data class EpRef(val season: Int, val ep: Episode)

data class UiState(
    val contentType: String = "series",     // what the Movie/Series toggle currently says
    val loadedType: String = "series",      // what the loaded title actually is
    val searching: Boolean = false,
    val match: SearchItem? = null,
    val bannerPoster: String? = null,
    val episodes: Map<Int, List<Episode>> = emptyMap(),   // season -> episodes (both sorted)
    val browseSeason: Int? = null,
    val currentSeason: Int? = null,
    val currentEpisode: Episode? = null,

    val playerVisible: Boolean = false,
    val fullscreen: Boolean = false,
    val inPip: Boolean = false,
    val panel: Panel = Panel.None,
    val loadingMedia: Boolean = false,
    val error: String? = null,

    val qualityMap: Map<String, String> = emptyMap(),
    val currentQuality: String? = null,

    val subtitleMap: Map<String, SubtitleEntry> = emptyMap(),
    val cues: Map<String, List<Cue>> = emptyMap(),
    val activeTracks: Set<String> = emptySet(),
    val customFiles: List<CustomSub> = emptyList(),
    val uploadStatus: String = "",

    val introSegments: List<Segment> = emptyList(),
    val familySegments: List<Segment> = emptyList(),
    val activeIntro: Segment? = null,
    val familyFlash: Int = 0,
    val nextUp: EpRef? = null,
    val speedBoost: Boolean = false,
)

data class PlaybackUi(
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
) {
    val playing: Boolean get() = playWhenReady && !ended
}

/* ───────── pure helpers over UiState (series navigation / images) ───────── */

fun nextEpisodeRef(ui: UiState): EpRef? {
    val cs = ui.currentSeason ?: return null
    val cur = ui.currentEpisode ?: return null
    val list = ui.episodes[cs] ?: return null
    val idx = list.indexOfFirst { it.id == cur.id }
    if (idx > -1 && idx + 1 < list.size) return EpRef(cs, list[idx + 1])
    val seasons = ui.episodes.keys.toList()
    val si = seasons.indexOf(cs)
    if (si > -1 && si + 1 < seasons.size) {
        val s = seasons[si + 1]
        val nl = ui.episodes[s].orEmpty()
        if (nl.isNotEmpty()) return EpRef(s, nl.first())
    }
    return null
}

fun prevEpisodeRef(ui: UiState): EpRef? {
    val cs = ui.currentSeason ?: return null
    val cur = ui.currentEpisode ?: return null
    val list = ui.episodes[cs] ?: return null
    val idx = list.indexOfFirst { it.id == cur.id }
    if (idx > 0) return EpRef(cs, list[idx - 1])
    val seasons = ui.episodes.keys.toList()
    val si = seasons.indexOf(cs)
    if (si > 0) {
        val s = seasons[si - 1]
        val pl = ui.episodes[s].orEmpty()
        if (pl.isNotEmpty()) return EpRef(s, pl.last())
    }
    return null
}

fun seriesImage(ui: UiState): String? = ui.match?.raw?.let { findImage(it) }

fun episodeThumb(ui: UiState, ep: Episode): String? {
    if (ep.raw.str("useParentImg") != "1") findImage(ep.raw)?.let { return it }
    return seriesImage(ui)
}

fun seasonPoster(ui: UiState, season: Int): String? {
    val first = ui.episodes[season]?.firstOrNull()
    return (first?.let { episodeThumb(ui, it) }) ?: seriesImage(ui)
}

private fun buildPlayer(ctx: Context): ExoPlayer {
    val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent("Mozilla/5.0 (Linux; Android) CinemanaPlayer/1.0")
    val dataSources = DefaultDataSource.Factory(ctx, http)
    return ExoPlayer.Builder(ctx)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
        .build()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val api = CinemanaApi()
    private val store = SettingsStore(app)

    /** One ExoPlayer for the whole app; it survives rotation because the Activity handles configChanges. */
    val player: ExoPlayer = buildPlayer(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _prefs = MutableStateFlow(Prefs())
    val prefs: StateFlow<Prefs> = _prefs.asStateFlow()

    private val _pb = MutableStateFlow(PlaybackUi())
    val pb: StateFlow<PlaybackUi> = _pb.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private var currentVideoId: String? = null
    private var mediaJob: Job? = null
    private var searchJob: Job? = null
    private var nextUpJob: Job? = null
    private var nextEpDismissed = false
    private var lastProgressSave = 0L
    private var speedBeforeBoost = 1f
    private var lastActiveTracks: Set<String> = emptySet()

    init {
        // We draw subtitles ourselves (multi-track, custom fonts), so keep ExoPlayer's text renderer off.
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPb()
                if (!isPlaying) saveProgress()
            }

            override fun onPlaybackStateChanged(playbackState: Int) = syncPb()
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncPb()
            override fun onPlayerError(error: PlaybackException) {
                _ui.update { it.copy(error = "This source couldn't be played.", loadingMedia = false) }
            }
        })

        viewModelScope.launch { _prefs.value = store.prefsFlow.first() }

        // Same job the browser's `timeupdate` event did: subtitles, skip buttons, next-up, progress.
        viewModelScope.launch {
            while (isActive) {
                delay(250)
                onTick()
            }
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    private fun toast(msg: String) {
        _toasts.tryEmit(msg)
    }

    /* ═══════════ prefs ═══════════ */

    fun updatePrefs(transform: (Prefs) -> Prefs) {
        val next = transform(_prefs.value)
        if (next == _prefs.value) return
        _prefs.value = next
        viewModelScope.launch { store.save(next) }
    }

    /* ═══════════ search & content loading ═══════════ */

    fun setContentType(type: String) = _ui.update { it.copy(contentType = type) }

    fun clearCache() {
        api.clearCache()
        toast("Cache cleared.")
    }

    fun search(titleIn: String, yearText: String, imdbText: String) {
        val title = titleIn.trim()
        if (title.isEmpty()) return
        val year = yearText.trim().toIntOrNull()
        val type = _ui.value.contentType
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            closePlayer()
            _ui.update { it.copy(searching = true, match = null, episodes = emptyMap(), currentSeason = null, currentEpisode = null, browseSeason = null) }
            try {
                val raw = api.searchAllPages(title, year, type)
                if (raw.isEmpty()) {
                    toast("No results found for that title.")
                    return@launch
                }
                val imdb = extractImdbId(imdbText)
                val best = raw
                    .map { c ->
                        val (conf, method) = scoreCandidate(title, year?.toString(), imdb, null, c)
                        c.copy(confidence = conf, matchMethod = method)
                    }
                    .sortedByDescending { it.confidence }
                    .first()
                if (best.confidence < AUTO_SELECT_THRESHOLD) {
                    toast("Only a low-confidence match was found — results may be inaccurate.")
                }
                loadContent(best, type)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Failed to search Cinemana.")
            } finally {
                _ui.update { it.copy(searching = false) }
            }
        }
    }

    private suspend fun loadContent(match: SearchItem, type: String) {
        nextEpDismissed = false
        _ui.update {
            it.copy(
                match = match,
                loadedType = type,
                bannerPoster = findImage(match.raw),
                episodes = emptyMap(),
                currentSeason = null,
                currentEpisode = null,
                browseSeason = null,
            )
        }
        val id = match.cinemanaId
        if (id == null) {
            toast("This result has no playable id.")
            return
        }
        if (type == "series") {
            try {
                val eps = api.fetchEpisodes(id)
                val grouped = eps.groupBy { it.season }
                    .toSortedMap()
                    .mapValues { (_, list) -> list.sortedBy { e -> e.episode ?: 0 } }
                if (grouped.isEmpty()) {
                    toast("No episodes found for this series.")
                    return
                }
                val firstSeason = grouped.keys.first()
                val firstEp = grouped[firstSeason]?.firstOrNull()
                if (firstEp == null) {
                    toast("No episodes found for this series.")
                    return
                }
                _ui.update {
                    it.copy(episodes = grouped, currentSeason = firstSeason, browseSeason = firstSeason, currentEpisode = firstEp)
                }
                loadVideoMedia(firstEp.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Could not load the episode list.")
            }
        } else {
            loadVideoMedia(id)
        }
    }

    fun browseSeason(season: Int) = _ui.update { it.copy(browseSeason = season) }

    fun switchEpisode(season: Int, ep: Episode) {
        nextEpDismissed = false
        hideNextUp()
        _ui.update { it.copy(currentSeason = season, currentEpisode = ep, browseSeason = season, panel = Panel.None) }
        loadVideoMedia(ep.id)
    }

    fun nextEpisode() = nextEpisodeRef(_ui.value)?.let { switchEpisode(it.season, it.ep) }
    fun prevEpisode() = prevEpisodeRef(_ui.value)?.let { switchEpisode(it.season, it.ep) }

    fun retry() {
        val s = _ui.value
        val id = s.currentEpisode?.id ?: s.match?.cinemanaId ?: return
        loadVideoMedia(id)
    }

    /** Port of loadVideoMedia(): sources + info in parallel, then subtitles, skip data, resume, play. */
    private fun loadVideoMedia(videoId: String) {
        mediaJob?.cancel()
        mediaJob = viewModelScope.launch {
            hideNextUp()
            currentVideoId = videoId
            player.pause()
            _ui.update {
                it.copy(
                    playerVisible = true, error = null, loadingMedia = true,
                    qualityMap = emptyMap(), subtitleMap = emptyMap(), cues = emptyMap(),
                    activeTracks = emptySet(), customFiles = emptyList(), uploadStatus = "",
                    introSegments = emptyList(), familySegments = emptyList(), activeIntro = null,
                )
            }
            try {
                val (playback, info) = coroutineScope {
                    val a = async { api.fetchPlayback(videoId) }
                    val b = async { api.fetchVideoInfo(videoId) }
                    a.await() to b.await()
                }
                val qMap = buildQualityMap(playback)
                val subMap = api.fetchSubtitleMap(videoId, info)
                val intro = api.extractIntroSkip(info)
                val family = api.fetchFamilySkip(videoId, info)
                val poster = api.extractPoster(info)

                if (qMap.isEmpty()) {
                    toast("No playable stream found for this title.")
                    _ui.update { it.copy(playerVisible = false, loadingMedia = false) }
                    return@launch
                }

                val settings = _prefs.value
                val quality = settings.quality?.takeIf { qMap.containsKey(it) } ?: pickDefaultQuality(qMap)

                _ui.update {
                    it.copy(
                        qualityMap = qMap,
                        currentQuality = quality,
                        subtitleMap = subMap,
                        introSegments = intro,
                        familySegments = family,
                        bannerPoster = if (it.loadedType != "series" && poster != null) poster else it.bannerPoster,
                    )
                }

                // Default subtitle tracks: remembered choice, else Arabic when available.
                val saved = settings.activeTracks
                val tracks: Set<String> = when {
                    saved == null -> if (subMap.containsKey("ar")) setOf("ar") else emptySet()
                    else -> {
                        val f = saved.filter { subMap.containsKey(it) }.toSet()
                        when {
                            f.isNotEmpty() -> f
                            saved.isEmpty() -> emptySet()
                            subMap.containsKey("ar") -> setOf("ar")
                            else -> emptySet()
                        }
                    }
                }
                for (t in tracks) ensureCues(t)
                _ui.update { it.copy(activeTracks = tracks) }

                // Resume, unless the episode was basically finished.
                val progress = store.loadProgress(videoId)
                val resumeMs = if (progress != null && progress.time > 5 && progress.duration > 0 &&
                    progress.time < progress.duration - 15
                ) (progress.time * 1000).toLong() else 0L

                player.setMediaItem(MediaItem.fromUri(qMap.getValue(quality ?: qMap.keys.first())), resumeMs)
                player.setPlaybackSpeed(settings.playbackRate)
                player.prepare()
                player.playWhenReady = true
                if (resumeMs > 0) toast("Resumed from ${fmtTime(resumeMs / 1000.0)}")
                _ui.update { it.copy(loadingMedia = false) }
                syncPb()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loadingMedia = false, error = "Could not fetch playback data for this title.") }
            }
        }
    }

    /* ═══════════ player controls ═══════════ */

    fun togglePlay() {
        if (player.mediaItemCount == 0) return
        when {
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0); player.play()
            }
            player.playWhenReady -> player.pause()
            else -> player.play()
        }
        syncPb()
    }

    fun skipBy(ms: Long) {
        val dur = player.duration
        if (player.mediaItemCount == 0 || dur == C.TIME_UNSET) return
        player.seekTo((player.currentPosition + ms).coerceIn(0L, dur))
        syncPb()
    }

    fun seekTo(ms: Long) {
        if (player.mediaItemCount == 0) return
        player.seekTo(ms.coerceAtLeast(0L))
        syncPb()
    }

    fun skipIntro() {
        val seg = _ui.value.activeIntro ?: return
        player.seekTo(((seg.end + 0.05) * 1000).toLong())
        _ui.update { it.copy(activeIntro = null) }
    }

    fun selectQuality(q: String) {
        val url = _ui.value.qualityMap[q] ?: return
        updatePrefs { it.copy(quality = q) }
        _ui.update { it.copy(currentQuality = q) }
        if (player.currentMediaItem?.localConfiguration?.uri?.toString() == url) return
        val pos = player.currentPosition
        val wasPlaying = player.playWhenReady
        player.setMediaItem(MediaItem.fromUri(url), pos)
        player.prepare()
        player.playWhenReady = wasPlaying
    }

    fun setSpeed(rate: Float) {
        player.setPlaybackSpeed(rate)
        updatePrefs { it.copy(playbackRate = rate) }
    }

    /** Long-press = hold for 2x, release to restore (same as the HTML tap-zone behaviour). */
    fun beginBoost() {
        if (player.mediaItemCount == 0 || !player.isPlaying || _ui.value.speedBoost) return
        speedBeforeBoost = player.playbackParameters.speed
        player.setPlaybackSpeed(2f)
        _ui.update { it.copy(speedBoost = true) }
    }

    fun endBoost() {
        if (!_ui.value.speedBoost) return
        player.setPlaybackSpeed(speedBeforeBoost)
        _ui.update { it.copy(speedBoost = false) }
    }

    fun setFamilyMode(on: Boolean) = updatePrefs { it.copy(familyMode = on) }
    fun setAutoplay(on: Boolean) = updatePrefs { it.copy(autoplayNext = on) }

    /* ═══════════ window state ═══════════ */

    fun openPanel(p: Panel) = _ui.update { it.copy(panel = p) }
    fun closePanel() = _ui.update { it.copy(panel = Panel.None) }
    fun toggleFullscreen() = _ui.update { it.copy(fullscreen = !it.fullscreen) }
    fun setPip(on: Boolean) = _ui.update { it.copy(inPip = on) }

    fun onBack() {
        val s = _ui.value
        when {
            s.panel != Panel.None -> closePanel()
            s.fullscreen -> _ui.update { it.copy(fullscreen = false) }
            s.playerVisible -> closePlayer()
        }
    }

    fun closePlayer() {
        mediaJob?.cancel()
        hideNextUp()
        saveProgress()
        player.stop()
        player.clearMediaItems()
        currentVideoId = null
        _pb.value = PlaybackUi()
        _ui.update {
            it.copy(
                playerVisible = false, fullscreen = false, panel = Panel.None, loadingMedia = false,
                error = null, activeIntro = null, speedBoost = false,
            )
        }
    }

    /** Called from Activity.onStop (when not in PiP): pause like the browser tab going to the background. */
    fun onHostStopped() {
        if (player.mediaItemCount == 0) return
        player.pause()
        saveProgress()
    }

    /* ═══════════ subtitles ═══════════ */

    private suspend fun ensureCues(track: String) {
        if (_ui.value.cues.containsKey(track)) return
        val entry = _ui.value.subtitleMap[track]
        var result: List<Cue> = emptyList()
        if (entry != null) {
            for (f in entry.files) {
                try {
                    val c = api.fetchAndParseSubtitle(f.url, f.format)
                    if (c.isNotEmpty()) {
                        result = c
                        break
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // try the next available file format for this language
                }
            }
            if (result.isEmpty()) toast("Couldn't load ${LANG_LABELS[track] ?: track} subtitles.")
        }
        _ui.update { it.copy(cues = it.cues + (track to result)) }
    }

    private fun persistTracks(tracks: Set<String>) {
        updatePrefs { p -> p.copy(activeTracks = tracks.filter { !it.startsWith("custom_") }.toSet()) }
    }

    fun toggleSubtitleTrack(track: String) {
        viewModelScope.launch {
            val cur = _ui.value.activeTracks
            val next: Set<String> = when {
                track == "off" -> emptySet()
                track in cur -> cur - track
                else -> cur + track
            }
            if (track != "off" && track !in cur) ensureCues(track)
            _ui.update { it.copy(activeTracks = next) }
            persistTracks(next)
        }
    }

    /** The CC button: toggle everything off / restore the last selection (or Arabic). */
    fun toggleCC() {
        val cur = _ui.value.activeTracks
        if (cur.isNotEmpty()) {
            lastActiveTracks = cur
            toggleSubtitleTrack("off")
        } else {
            val restore: Set<String> = if (lastActiveTracks.isNotEmpty()) lastActiveTracks
            else if (_ui.value.subtitleMap.containsKey("ar")) setOf("ar") else emptySet()
            viewModelScope.launch {
                for (t in restore) ensureCues(t)
                _ui.update { it.copy(activeTracks = restore) }
                persistTracks(restore)
            }
        }
    }

    fun removeCustomTrack(id: String) {
        _ui.update {
            it.copy(
                customFiles = it.customFiles.filter { f -> f.id != id },
                cues = it.cues - id,
                activeTracks = it.activeTracks - id,
            )
        }
    }

    /** Subtitle files picked with the system file picker (.srt / .vtt). */
    fun importSubtitles(uris: List<Uri>) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            for (uri in uris) {
                val name = withContext(Dispatchers.IO) { displayName(ctx, uri) }
                try {
                    val text = withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    val cues = parseSubtitleText(text, name)
                    if (cues.isEmpty()) throw IllegalArgumentException("empty")
                    val lang = detectSubtitleLang(cues, name)
                    val id = "custom_${System.currentTimeMillis()}_${_ui.value.customFiles.size}"
                    _ui.update {
                        it.copy(
                            customFiles = it.customFiles + CustomSub(id, name, lang),
                            cues = it.cues + (id to cues),
                            activeTracks = it.activeTracks + id,
                            uploadStatus = "Loaded \"$name\" — ${cues.size} cues.",
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _ui.update { it.copy(uploadStatus = "Couldn't read \"$name\" — please choose a valid .srt or .vtt.") }
                }
            }
        }
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) return c.getString(i)
            }
        } catch (e: Exception) {
            // fall through to the path-based name
        }
        return uri.lastPathSegment ?: "subtitle"
    }

    /* ═══════════ next-up (autoplay) ═══════════ */

    private fun showNextUp() {
        if (_ui.value.nextUp != null) return
        val ref = nextEpisodeRef(_ui.value) ?: return
        _ui.update { it.copy(nextUp = ref, activeIntro = null) }
        nextUpJob?.cancel()
        if (_prefs.value.autoplayNext) {
            nextUpJob = viewModelScope.launch {
                delay(8000)
                nextEpisodeRef(_ui.value)?.let { switchEpisode(it.season, it.ep) }
            }
        }
    }

    private fun hideNextUp() {
        nextUpJob?.cancel()
        if (_ui.value.nextUp != null) _ui.update { it.copy(nextUp = null) }
    }

    fun dismissNextUp() {
        nextEpDismissed = true
        hideNextUp()
    }

    /* ═══════════ tick (what `timeupdate` did in the browser) ═══════════ */

    private fun syncPb() {
        val dur = player.duration.let { if (it == C.TIME_UNSET) 0L else it }
        _pb.value = PlaybackUi(
            playWhenReady = player.playWhenReady,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            ended = player.playbackState == Player.STATE_ENDED,
            positionMs = player.currentPosition,
            durationMs = dur,
            bufferedMs = player.bufferedPosition,
        )
    }

    private fun onTick() {
        if (player.mediaItemCount == 0) return
        syncPb()
        val durMs = player.duration
        if (durMs == C.TIME_UNSET || durMs <= 0) return
        val pos = player.currentPosition / 1000.0
        val dur = durMs / 1000.0
        val ui = _ui.value

        // Safe Watch: jump over flagged segments.
        if (_prefs.value.familyMode && ui.familySegments.isNotEmpty()) {
            val seg = ui.familySegments.firstOrNull { pos >= it.start && pos < it.end - 0.15 }
            if (seg != null) {
                player.seekTo((seg.end * 1000).toLong())
                _ui.update { it.copy(familyFlash = it.familyFlash + 1) }
                return
            }
        }

        // Skip-intro button.
        val intro = if (ui.nextUp == null) ui.introSegments.firstOrNull { pos >= it.start && pos <= it.end } else null
        if (intro != ui.activeIntro) _ui.update { it.copy(activeIntro = intro) }

        // Next-up card near the end of an episode.
        if (ui.loadedType == "series") {
            val remaining = dur - pos
            val nearEnd = minOf(8.0, dur * 0.35)
            if (remaining <= nearEnd && remaining >= 0 && !nextEpDismissed && nextEpisodeRef(ui) != null) showNextUp()
            else if (remaining > nearEnd && ui.nextUp != null) hideNextUp()
        }

        // Resume position, throttled to every ~4s.
        val now = SystemClock.elapsedRealtime()
        if (player.isPlaying && now - lastProgressSave > 4000) {
            lastProgressSave = now
            saveProgress()
        }
    }

    private fun saveProgress() {
        val id = currentVideoId ?: return
        val dur = player.duration
        if (player.mediaItemCount == 0 || dur == C.TIME_UNSET || dur <= 0) return
        val t = player.currentPosition / 1000.0
        val d = dur / 1000.0
        viewModelScope.launch { store.saveProgress(id, t, d) }
    }
}
