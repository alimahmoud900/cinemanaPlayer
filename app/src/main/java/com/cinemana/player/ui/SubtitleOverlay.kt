package com.cinemana.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinemana.player.data.POSITION_STEPS
import com.cinemana.player.data.Prefs
import com.cinemana.player.data.RTL_LANGS
import com.cinemana.player.data.SIZE_STEPS
import com.cinemana.player.data.sortLanguagesArabicFirst

data class SubLine(val text: String, val rtl: Boolean)

/**
 * Port of updateSubtitle(): custom uploads on top, then API languages with Arabic last
 * so it sits on the bottom-most (most prominent) line.
 */
fun computeSubtitleLines(ui: UiState, posSec: Double): List<SubLine> {
    if (ui.activeTracks.isEmpty()) return emptyList()
    val apiLangs = sortLanguagesArabicFirst(ui.activeTracks.filter { !it.startsWith("custom_") }).reversed()
    val customIds = ui.activeTracks.filter { it.startsWith("custom_") }
    val out = ArrayList<SubLine>()
    for (track in customIds + apiLangs) {
        val cue = ui.cues[track]?.firstOrNull { posSec >= it.start && posSec <= it.end } ?: continue
        val custom = ui.customFiles.firstOrNull { it.id == track }
        val rtl = if (custom != null) custom.lang == "ar" else RTL_LANGS.contains(track)
        out.add(SubLine(cue.text, rtl))
    }
    return out
}

fun parseHexColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    Color.White
}

@Composable
fun SubtitleOverlay(lines: List<SubLine>, prefs: Prefs, playerHeight: Dp, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    // Scale relative to the player so inline (small) and fullscreen (large) both read well.
    val base = (playerHeight.value / 20f).coerceIn(11f, 24f)
    val size = (base * SIZE_STEPS[prefs.sizeIndex.coerceIn(0, SIZE_STEPS.lastIndex)].pct / 100f).sp
    val bottomPct = POSITION_STEPS[prefs.positionIndex.coerceIn(0, POSITION_STEPS.lastIndex)].pct / 100f
    val color = parseHexColor(prefs.colorHex)
    val bg = Color.Black.copy(alpha = prefs.bg / 100f)

    Column(
        modifier
            .fillMaxSize()
            .padding(bottom = playerHeight * bottomPct)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (line in lines) {
            val family = if (line.rtl) arabicFont(prefs.fontAr) else latinFont(prefs.fontLatin)
            CompositionLocalProvider(
                LocalLayoutDirection provides if (line.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Text(
                    text = line.text,
                    color = color,
                    fontSize = size,
                    lineHeight = size * 1.3f,
                    fontFamily = family,
                    fontWeight = FontWeight(prefs.weight),
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)),
                    modifier = Modifier
                        .background(bg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
