@file:OptIn(ExperimentalLayoutApi::class)

package com.cinemana.player.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinemana.player.data.LANG_LABELS
import com.cinemana.player.data.POSITION_STEPS
import com.cinemana.player.data.Prefs
import com.cinemana.player.data.QUALITY_DISPLAY_ORDER
import com.cinemana.player.data.RTL_LANGS
import com.cinemana.player.data.SIZE_STEPS
import com.cinemana.player.data.SPEED_STEPS
import com.cinemana.player.data.qualityLabel
import com.cinemana.player.data.sortLanguagesArabicFirst
import com.cinemana.player.data.speedLabel
import kotlin.math.roundToInt

private enum class SetView { Main, Quality, Speed, Subtitles }

private val SWATCHES = listOf("#ffffff", "#f5e6b8", "#ffe066", "#57cdbf", "#7ee787", "#ff7b72", "#8ab4f8", "#f9a8d4")

@Composable
fun SettingsPanel(vm: AppViewModel, ui: UiState, prefs: Prefs, onClose: () -> Unit) {
    var view by remember { mutableStateOf(SetView.Main) }
    val title = when (view) {
        SetView.Main -> "Settings"
        SetView.Quality -> "Quality"
        SetView.Speed -> "Playback speed"
        SetView.Subtitles -> "Subtitles"
    }
    val back: (() -> Unit)? = if (view == SetView.Main) null else ({ view = SetView.Main })

    Column(Modifier.fillMaxSize()) {
        PanelHeader(title, back, onClose)
        HorizontalDivider(color = Pal.Hairline)
        when (view) {
            SetView.Main -> Column(Modifier.verticalScroll(rememberScrollState())) {
                NavRow("Quality", qualityLabel(ui.currentQuality)) { view = SetView.Quality }
                NavRow("Playback speed", speedLabel(prefs.playbackRate)) { view = SetView.Speed }
                NavRow("Subtitles", subtitleSummary(ui)) { view = SetView.Subtitles }
                if (ui.familySegments.isNotEmpty()) {
                    SwitchRow("Safe Watch", "Automatically skip flagged scenes", prefs.familyMode) { vm.setFamilyMode(it) }
                }
            }
            SetView.Quality -> Column(Modifier.verticalScroll(rememberScrollState())) {
                val available = QUALITY_DISPLAY_ORDER.filter { ui.qualityMap.containsKey(it) }
                if (available.isEmpty()) Text("No qualities available.", color = Pal.Fog, modifier = Modifier.padding(16.dp))
                available.forEach { q ->
                    ChoiceRow(qualityLabel(q), q == ui.currentQuality) { vm.selectQuality(q) }
                }
            }
            SetView.Speed -> Column(Modifier.verticalScroll(rememberScrollState())) {
                SPEED_STEPS.forEach { (rate, label) ->
                    ChoiceRow(label, rate == prefs.playbackRate) { vm.setSpeed(rate) }
                }
            }
            SetView.Subtitles -> SubtitlesView(vm, ui, prefs)
        }
    }
}

private fun subtitleSummary(ui: UiState): String {
    if (ui.activeTracks.isEmpty()) return "Off"
    val names = ui.activeTracks.map { t ->
        ui.customFiles.firstOrNull { it.id == t }?.let { "Custom" } ?: (LANG_LABELS[t] ?: t)
    }
    return names.joinToString(", ")
}

@Composable
private fun SubtitlesView(vm: AppViewModel, ui: UiState, prefs: Prefs) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importSubtitles(uris)
    }
    val langs = sortLanguagesArabicFirst(ui.subtitleMap.keys)

    // Which font pickers matter depends on which scripts are currently on screen.
    val rtlFlags = ui.activeTracks.map { t ->
        val c = ui.customFiles.firstOrNull { it.id == t }
        if (c != null) c.lang == "ar" else RTL_LANGS.contains(t)
    }
    val needArabic = rtlFlags.any { it }
    val needLatin = rtlFlags.any { !it }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Group("Tracks") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Off", ui.activeTracks.isEmpty()) { vm.toggleSubtitleTrack("off") }
                langs.forEach { lang ->
                    val label = LANG_LABELS[lang] ?: ui.subtitleMap[lang]?.label ?: lang
                    Pill(label, ui.activeTracks.contains(lang)) { vm.toggleSubtitleTrack(lang) }
                }
                ui.customFiles.forEach { f ->
                    val name = if (f.name.length > 18) f.name.take(17) + "…" else f.name
                    Pill(
                        text = name,
                        active = ui.activeTracks.contains(f.id),
                        trailing = {
                            Icon(
                                Icons.Filled.Close, "Remove",
                                tint = if (ui.activeTracks.contains(f.id)) Pal.OnGold else Pal.Fog,
                                modifier = Modifier.padding(start = 6.dp).size(14.dp).clickable { vm.removeCustomTrack(f.id) },
                            )
                        },
                    ) { vm.toggleSubtitleTrack(f.id) }
                }
            }
            Pill("+ Upload .srt / .vtt", false, Modifier.padding(top = 10.dp)) { picker.launch(arrayOf("*/*")) }
            if (ui.uploadStatus.isNotEmpty()) {
                Text(ui.uploadStatus, color = Pal.Fog, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        if (needArabic) {
            Group("Arabic font") {
                FontDropdown(ARABIC_FONTS, prefs.fontAr) { id -> vm.updatePrefs { it.copy(fontAr = id) } }
            }
        }
        if (needLatin) {
            Group("Latin font") {
                FontDropdown(LATIN_FONTS, prefs.fontLatin) { id -> vm.updatePrefs { it.copy(fontLatin = id) } }
            }
        }

        Group("Size — ${SIZE_STEPS[prefs.sizeIndex.coerceIn(0, SIZE_STEPS.lastIndex)].label}") {
            AppSlider(prefs.sizeIndex.toFloat(), 0f..SIZE_STEPS.lastIndex.toFloat(), SIZE_STEPS.size - 2) { v ->
                vm.updatePrefs { it.copy(sizeIndex = v.roundToInt()) }
            }
        }
        Group("Position — ${POSITION_STEPS[prefs.positionIndex.coerceIn(0, POSITION_STEPS.lastIndex)].label}") {
            AppSlider(prefs.positionIndex.toFloat(), 0f..POSITION_STEPS.lastIndex.toFloat(), POSITION_STEPS.size - 2) { v ->
                vm.updatePrefs { it.copy(positionIndex = v.roundToInt()) }
            }
        }
        Group("Weight") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(400 to "Light", 600 to "Medium", 900 to "Bold").forEach { (w, label) ->
                    Pill(label, prefs.weight == w) { vm.updatePrefs { it.copy(weight = w) } }
                }
            }
        }
        Group("Colour") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SWATCHES.forEach { hex ->
                    val selected = prefs.colorHex.equals(hex, ignoreCase = true)
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(hex))
                            .border(if (selected) 2.dp else 1.dp, if (selected) Pal.Gold else Pal.HairlineStrong, CircleShape)
                            .clickable { vm.updatePrefs { it.copy(colorHex = hex) } }
                    )
                }
            }
        }
        Group("Text background — ${prefs.bg}%") {
            AppSlider(prefs.bg.toFloat(), 0f..100f, 19) { v -> vm.updatePrefs { it.copy(bg = v.roundToInt()) } }
        }
        Group("Bottom shade — ${prefs.subScrim}%") {
            AppSlider(prefs.subScrim.toFloat(), 0f..100f, 19) { v -> vm.updatePrefs { it.copy(subScrim = v.roundToInt()) } }
        }
    }
}

/* ───────── reusable pieces ───────── */

@Composable
fun PanelHeader(title: String, onBack: (() -> Unit)?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
        Text(
            title,
            modifier = Modifier.weight(1f).padding(start = if (onBack == null) 12.dp else 4.dp),
            color = Pal.Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp,
        )
        IconBtn(Icons.Filled.Close, "Close", tint = Pal.Fog, onClick = onClose)
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), color = Pal.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Column(Modifier.padding(top = 10.dp)) { content() }
    }
}

@Composable
private fun NavRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Pal.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Pal.Fog, fontSize = 14.sp)
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = Pal.Muted)
    }
}

@Composable
fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) Pal.Gold else Pal.Ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) CheckMark()
    }
}

@Composable
fun SwitchRow(label: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Pal.Ink, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = Pal.Fog, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Pal.OnGold,
                checkedTrackColor = Pal.Gold,
                uncheckedThumbColor = Pal.Fog,
                uncheckedTrackColor = Pal.Surface,
                uncheckedBorderColor = Pal.HairlineStrong,
            ),
        )
    }
}

@Composable
private fun AppSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Pal.Gold,
            activeTrackColor = Pal.Gold,
            inactiveTrackColor = Pal.HairlineStrong,
            activeTickColor = Pal.OnGold,
            inactiveTickColor = Pal.Muted,
        ),
    )
}

@Composable
private fun FontDropdown(fonts: List<SubFont>, selectedId: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = fonts.firstOrNull { it.id == selectedId } ?: fonts.first()
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(Pal.Void)
                .border(1.dp, Pal.Hairline, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.label, color = Pal.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f), fontFamily = selected.family)
            Icon(Icons.Filled.ArrowDropDown, null, tint = Pal.Fog)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fonts.forEach { f ->
                DropdownMenuItem(
                    text = { Text(f.label, fontFamily = f.family) },
                    onClick = {
                        onSelect(f.id)
                        open = false
                    },
                )
            }
        }
    }
}
