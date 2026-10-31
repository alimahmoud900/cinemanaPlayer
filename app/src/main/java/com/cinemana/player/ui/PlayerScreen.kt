@file:OptIn(UnstableApi::class)

package com.cinemana.player.ui

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.cinemana.player.data.fmtTime
import kotlinx.coroutines.delay

/**
 * The whole video experience: PlayerView + gesture zones + controls + subtitles + skip/next-up.
 * Used both inline (16:9) and fullscreen; the ExoPlayer lives in the ViewModel so switching is seamless.
 */
@Composable
fun PlayerSurface(
    vm: AppViewModel,
    modifier: Modifier,
    fullscreen: Boolean,
    showEpisodesButton: Boolean,
    onEnterPip: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val pb by vm.pb.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val pipSupported = remember { ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }

    val pip = ui.inPip
    val playing = pb.playing
    val overlayOpen = ui.panel != Panel.None
    val isSeries = ui.loadedType == "series"
    val prevRef = prevEpisodeRef(ui)
    val nextRef = nextEpisodeRef(ui)

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var flash by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var familyToast by remember { mutableStateOf(false) }

    // Keep the screen awake while playing.
    val view = LocalView.current
    DisposableEffect(playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }

    // Controls stay up while paused / a panel is open, otherwise auto-hide after 3.5s.
    LaunchedEffect(playing, controlsVisible, interaction, overlayOpen) {
        if (!playing || overlayOpen) controlsVisible = true
        else if (controlsVisible) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(600)
            flash = null
        }
    }
    LaunchedEffect(ui.familyFlash) {
        if (ui.familyFlash > 0) {
            familyToast = true
            delay(1800)
            familyToast = false
        }
    }

    val showUi = (controlsVisible || !playing) && !pip
    val insetMod = if (fullscreen) {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    } else Modifier

    BoxWithConstraints(modifier.background(Color.Black)) {
        val playerHeight = maxHeight

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = vm.player
                }
            },
            update = { it.player = vm.player },
            onRelease = { it.player = null },
        )

        // Bottom scrim behind subtitles.
        if (ui.activeTracks.isNotEmpty() && prefs.subScrim > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = prefs.subScrim / 100f * 0.85f))
                        )
                    )
            )
        }

        if (ui.activeTracks.isNotEmpty()) {
            SubtitleOverlay(computeSubtitleLines(ui, pb.positionMs / 1000.0), prefs, playerHeight)
        }

        if (!pip) {
            // Controls scrim (top + bottom).
            AnimatedVisibility(
                visible = showUi,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.72f),
                                0.3f to Color.Transparent,
                                0.62f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.82f),
                            )
                        )
                )
            }

            // Gesture layer: tap = toggle controls, double-tap L/R = ±10s, double-tap centre = play/pause,
            // long-press = hold for 2x.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                tryAwaitRelease()
                                vm.endBoost()
                            },
                            onLongPress = { vm.beginBoost() },
                            onDoubleTap = { off ->
                                val w = size.width
                                when {
                                    off.x < w / 3f -> {
                                        vm.skipBy(-10_000)
                                        flash = -1 to System.nanoTime().toInt()
                                    }
                                    off.x > w * 2f / 3f -> {
                                        vm.skipBy(10_000)
                                        flash = 1 to System.nanoTime().toInt()
                                    }
                                    else -> vm.togglePlay()
                                }
                                interaction++
                            },
                            onTap = {
                                controlsVisible = !controlsVisible
                                interaction++
                            },
                        )
                    }
            )

            // Double-tap feedback.
            flash?.let { (side, _) ->
                Box(
                    Modifier
                        .align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 48.dp)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (side < 0) Icons.Filled.Replay10 else Icons.Filled.Forward10,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            // Centre: spinner while loading/buffering, big play button while paused.
            if (ui.error == null) {
                if (ui.loadingMedia || pb.buffering) {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center).size(44.dp),
                        color = Pal.Gold,
                        strokeWidth = 3.dp,
                    )
                } else if (showUi && !playing) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Pal.HairlineStrong, CircleShape)
                            .clickable { vm.togglePlay() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
            }

            // Badges.
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (ui.speedBoost) Badge("2x speed")
                if (familyToast) Badge("Safe Watch — skipped a segment", Icons.Filled.ContentCut)
            }

            // Error overlay.
            ui.error?.let { msg ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.86f))
                        .pointerInput(Unit) { detectTapGestures { } },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Pal.Gold, modifier = Modifier.size(28.dp))
                    Text("Playback error", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        msg, color = Pal.Fog, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                    Button(
                        onClick = { vm.retry() },
                        colors = ButtonDefaults.buttonColors(containerColor = Pal.Gold, contentColor = Pal.OnGold),
                    ) { Text("Retry") }
                }
            }

            // Skip intro.
            AnimatedVisibility(
                visible = ui.activeIntro != null && ui.nextUp == null && ui.error == null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(insetMod)
                    .padding(end = 16.dp, bottom = if (showUi) 104.dp else 24.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .border(1.dp, Pal.HairlineStrong, CircleShape)
                        .clickable { vm.skipIntro() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Skip Intro", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                }
            }

            // Next-up card.
            ui.nextUp?.let { ref ->
                NextUpCard(
                    ref = ref,
                    thumb = episodeThumb(ui, ref.ep),
                    autoplay = prefs.autoplayNext,
                    onPlay = { vm.switchEpisode(ref.season, ref.ep) },
                    onDismiss = { vm.dismissNextUp() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .then(insetMod)
                        .padding(end = 16.dp, bottom = if (showUi) 104.dp else 24.dp),
                )
            }

            // Top bar.
            AnimatedVisibility(
                visible = showUi,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(insetMod)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back") { vm.onBack() }
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(
                            ui.match?.displayTitle ?: "",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val sub = if (isSeries) {
                            ui.currentEpisode?.let { "S${ui.currentSeason ?: 0} · E${it.episode ?: "?"} — ${it.title}" } ?: ""
                        } else ui.match?.year ?: ""
                        if (sub.isNotEmpty()) {
                            Text(
                                sub, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (isSeries) {
                        IconBtn(Icons.Filled.SkipPrevious, "Previous episode", enabled = prevRef != null) { vm.prevEpisode() }
                        IconBtn(Icons.Filled.SkipNext, "Next episode", enabled = nextRef != null) { vm.nextEpisode() }
                        if (showEpisodesButton) {
                            IconBtn(Icons.Filled.FormatListBulleted, "Episodes") { vm.openPanel(Panel.Episodes) }
                        }
                    }
                }
            }

            // Bottom bar.
            AnimatedVisibility(
                visible = showUi,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(insetMod)
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 4.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime((scrubMs ?: pb.positionMs) / 1000.0), color = Color.White, fontSize = 12.sp)
                        SeekBar(
                            positionMs = pb.positionMs,
                            durationMs = pb.durationMs,
                            bufferedMs = pb.bufferedMs,
                            onSeek = { vm.seekTo(it); interaction++ },
                            onScrub = { scrubMs = it },
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        )
                        Text(fmtTime(pb.durationMs / 1000.0), color = Color.White, fontSize = 12.sp)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconBtn(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play") {
                            vm.togglePlay(); interaction++
                        }
                        IconBtn(Icons.Filled.Replay10, "Back 10 seconds") { vm.skipBy(-10_000); interaction++ }
                        IconBtn(Icons.Filled.Forward10, "Forward 10 seconds") { vm.skipBy(10_000); interaction++ }
                        Spacer(Modifier.weight(1f))
                        IconBtn(
                            if (ui.activeTracks.isNotEmpty()) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                            "Subtitles",
                            tint = if (ui.activeTracks.isNotEmpty()) Pal.Gold else Color.White,
                            enabled = ui.subtitleMap.isNotEmpty() || ui.customFiles.isNotEmpty() || ui.activeTracks.isNotEmpty(),
                        ) { vm.toggleCC(); interaction++ }
                        IconBtn(Icons.Filled.Settings, "Settings") { vm.openPanel(Panel.Settings) }
                        if (pipSupported) IconBtn(Icons.Filled.PictureInPictureAlt, "Picture in picture") { onEnterPip() }
                        IconBtn(
                            if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            if (fullscreen) "Exit fullscreen" else "Fullscreen",
                        ) { vm.toggleFullscreen() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = Pal.Gold, modifier = Modifier.padding(end = 6.dp).size(14.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit,
    onScrub: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val frac = when {
        dragging -> dragFraction
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val buf = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier
            .height(32.dp)
            .pointerInput(durationMs) {
                detectTapGestures { off ->
                    if (durationMs > 0) onSeek((off.x / size.width * durationMs).toLong().coerceIn(0L, durationMs))
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        dragging = true
                        dragFraction = (off.x / size.width).coerceIn(0f, 1f)
                        onScrub((dragFraction * durationMs).toLong())
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek((dragFraction * durationMs).toLong())
                        onScrub(null)
                    },
                    onDragCancel = {
                        dragging = false
                        onScrub(null)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrub((dragFraction * durationMs).toLong())
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = 4.dp.toPx()
            val cy = size.height / 2f
            val w = size.width
            val corner = CornerRadius(h / 2f)
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(0f, cy - h / 2f), Size(w, h), corner)
            drawRoundRect(Color.White.copy(alpha = 0.40f), Offset(0f, cy - h / 2f), Size(w * buf, h), corner)
            drawRoundRect(Pal.Gold, Offset(0f, cy - h / 2f), Size(w * frac, h), corner)
            drawCircle(Pal.Gold, radius = (if (dragging) 8.dp else 6.dp).toPx(), center = Offset(w * frac, cy))
        }
    }
}

@Composable
private fun NextUpCard(
    ref: EpRef,
    thumb: String?,
    autoplay: Boolean,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prog = remember(ref.ep.id, autoplay) { Animatable(0f) }
    LaunchedEffect(ref.ep.id, autoplay) {
        prog.snapTo(0f)
        if (autoplay) prog.animateTo(1f, tween(8000, easing = LinearEasing))
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .widthIn(max = 300.dp)
            .clip(shape)
            .background(Color(0xF0101013))
            .border(1.dp, Pal.HairlineStrong, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 88.dp, height = 50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Pal.Elevated)
        ) {
            if (thumb != null) {
                AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text("NEXT EPISODE", color = Pal.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                "E${ref.ep.episode ?: "?"} — ${ref.ep.title}",
                color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = 4.dp).clickable { onPlay() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(20.dp)) {
                    val stroke = 2.5.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(
                        Pal.Gold, -90f, 360f * (1f - prog.value), false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Text(
                    "Play now", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        IconBtn(Icons.Filled.Close, "Dismiss", modifier = Modifier.size(32.dp), tint = Pal.Fog) { onDismiss() }
    }
}
