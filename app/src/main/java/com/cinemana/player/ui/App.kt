package com.cinemana.player.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinemana.player.data.Prefs

/**
 * Adaptive shell.
 *  - Phone (compact): pinned 16:9 player on top, scrolling search/banner/episodes below; episodes drawer on demand.
 *  - Tablet / unfolded (>= 840dp wide): two panes — search + banner on the left; player + permanent episode list on the right.
 *  - Fullscreen / PiP: the player takes the whole window.
 */
@Composable
fun CinemanaApp(vm: AppViewModel, onEnterPip: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        vm.toasts.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
    }
    BackHandler(enabled = ui.panel != Panel.None || ui.fullscreen || ui.playerVisible) { vm.onBack() }

    // The UI is English / left-to-right; Arabic subtitle lines set their own direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Pal.Void)) {
            val wide = maxWidth >= 840.dp
            val shown = ui.playerVisible
            val full = shown && (ui.fullscreen || ui.inPip)
            val twoPane = wide && shown && !full

            when {
                full -> PlayerSurface(vm, Modifier.fillMaxSize(), fullscreen = true, showEpisodesButton = true, onEnterPip = onEnterPip)

                twoPane -> Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                    HomeContent(vm, ui, wide = true, showStrip = false, modifier = Modifier.weight(0.42f).fillMaxHeight())
                    Column(Modifier.weight(0.58f).fillMaxHeight()) {
                        PlayerSurface(
                            vm, Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            fullscreen = false, showEpisodesButton = false, onEnterPip = onEnterPip,
                        )
                        if (ui.loadedType == "series") {
                            EpisodesContent(vm, ui, prefs, onClose = null, modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }

                else -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (shown) {
                        PlayerSurface(
                            vm, Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            fullscreen = false, showEpisodesButton = true, onEnterPip = onEnterPip,
                        )
                    }
                    HomeContent(vm, ui, wide = wide, showStrip = true, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }

            SidePanels(vm, ui, prefs, episodesAllowed = !twoPane)
        }
    }
}

@Composable
private fun BoxScope.SidePanels(vm: AppViewModel, ui: UiState, prefs: Prefs, episodesAllowed: Boolean) {
    // Remember the last real panel so it doesn't blank out while sliding away.
    var lastPanel by remember { mutableStateOf(Panel.Settings) }
    if (ui.panel != Panel.None) lastPanel = ui.panel

    val open = !ui.inPip && ui.playerVisible &&
        (ui.panel == Panel.Settings || (ui.panel == Panel.Episodes && episodesAllowed))

    AnimatedVisibility(visible = open, modifier = Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.closePanel() }
        )
    }
    AnimatedVisibility(
        visible = open,
        modifier = Modifier.align(Alignment.CenterEnd),
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
    ) {
        Surface(
            color = Pal.Elevated,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.92f)
                .widthIn(max = 380.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Box(Modifier.safeDrawingPadding()) {
                when (lastPanel) {
                    Panel.Episodes -> EpisodesContent(vm, ui, prefs, onClose = { vm.closePanel() }, modifier = Modifier.fillMaxSize())
                    else -> SettingsPanel(vm, ui, prefs) { vm.closePanel() }
                }
            }
        }
    }
}
