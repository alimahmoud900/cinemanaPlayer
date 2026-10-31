package com.cinemana.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cinemana.player.data.Prefs

/**
 * Episode browser. On phones it is shown in the side drawer (opened from the player's list button);
 * on tablets it sits permanently under the player. Same composable, different host.
 */
@Composable
fun EpisodesContent(
    vm: AppViewModel,
    ui: UiState,
    prefs: Prefs,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val season = ui.browseSeason ?: ui.currentSeason ?: ui.episodes.keys.firstOrNull()
    val list = if (season != null) ui.episodes[season].orEmpty() else emptyList()
    var menu by remember { mutableStateOf(false) }

    Column(modifier) {
        if (onClose != null) PanelHeader("Episodes", null, onClose)
        else Text(
            "Episodes", color = Pal.Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
        )

        // Season selector.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(8.dp)).background(Pal.Surface)
            ) {
                val poster = if (season != null) seasonPoster(ui, season) else null
                if (poster != null) AsyncImage(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.padding(start = 12.dp)) {
                Column(Modifier.clickable { menu = true }) {
                    Text("SEASON", color = Pal.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (season != null) "Season $season" else "—",
                            color = Pal.Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        )
                        Icon(Icons.Filled.ArrowDropDown, null, tint = Pal.Fog)
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    ui.episodes.keys.forEach { s ->
                        DropdownMenuItem(
                            text = { Text("Season $s") },
                            onClick = {
                                vm.browseSeason(s)
                                menu = false
                            },
                        )
                    }
                }
            }
        }

        SwitchRow("Autoplay next episode", null, prefs.autoplayNext) { vm.setAutoplay(it) }
        HorizontalDivider(color = Pal.Hairline)

        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { ep ->
                val current = ui.currentEpisode?.id == ep.id && ui.currentSeason == season
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (current) Pal.Gold.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { if (season != null) vm.switchEpisode(season, ep) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 96.dp, height = 54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Pal.Surface)
                            .border(1.dp, if (current) Pal.Gold else Pal.Hairline, RoundedCornerShape(8.dp))
                    ) {
                        val thumb = episodeThumb(ui, ep)
                        if (thumb != null) AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "E${ep.episode?.toString()?.padStart(2, '0') ?: "?"}",
                            color = if (current) Pal.Gold else Pal.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(ep.title, color = Pal.Ink, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (current) Text("Now playing", color = Pal.Gold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
