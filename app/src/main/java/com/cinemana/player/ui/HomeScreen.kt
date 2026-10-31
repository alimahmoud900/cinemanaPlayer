@file:OptIn(ExperimentalComposeUiApi::class)

package com.cinemana.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cinemana.player.data.AUTO_SELECT_THRESHOLD
import com.cinemana.player.data.str

/** Centred, max-width column (the HTML's .main-container, max-width 1180px). */
private fun Modifier.page(): Modifier = this.widthIn(max = 1180.dp).fillMaxWidth()

@Composable
fun HomeContent(
    vm: AppViewModel,
    ui: UiState,
    wide: Boolean,
    showStrip: Boolean,
    modifier: Modifier = Modifier,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var imdb by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val submit: () -> Unit = {
        keyboard?.hide()
        focus.clearFocus()
        vm.search(title, year, imdb)
    }

    LazyColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item { Header(vm) }
        item {
            SearchHero(
                ui = ui,
                title = title, onTitle = { title = it },
                year = year, onYear = { year = it },
                imdb = imdb, onImdb = { imdb = it },
                onTypeChange = { vm.setContentType(it) },
                onSubmit = submit,
            )
        }
        if (ui.match != null) item { Banner(ui, wide) }
        if (showStrip && ui.loadedType == "series" && ui.episodes.isNotEmpty()) {
            item { SeriesSection(vm, ui) }
        }
    }
}

@Composable
private fun Header(vm: AppViewModel) {
    Row(
        Modifier.page().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Pal.Gold, Pal.GoldDark))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Pal.OnGold, modifier = Modifier.size(24.dp))
        }
        Text(
            "Cinemana",
            color = Pal.Ink, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Pill("Clear cache", false) { vm.clearCache() }
    }
}

@Composable
private fun SearchHero(
    ui: UiState,
    title: String, onTitle: (String) -> Unit,
    year: String, onYear: (String) -> Unit,
    imdb: String, onImdb: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        Modifier.page().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Discover & stream instantly", color = Pal.Ink, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 31.sp)
        Text("Search a movie or series — the best match is picked and played automatically.", color = Pal.Fog, fontSize = 14.sp)

        AppTextField(
            value = title, onValueChange = onTitle,
            placeholder = "Movie or series title…",
            modifier = Modifier.fillMaxWidth(),
            imeAction = ImeAction.Search, onImeAction = onSubmit,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TypeToggle(ui.contentType, onTypeChange)
            Button(
                onClick = onSubmit,
                enabled = !ui.searching && title.isNotBlank(),
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Pal.Gold, contentColor = Pal.OnGold,
                    disabledContainerColor = Pal.Gold.copy(alpha = 0.35f), disabledContentColor = Pal.OnGold.copy(alpha = 0.6f),
                ),
            ) {
                if (ui.searching) CircularProgressIndicator(Modifier.size(16.dp), color = Pal.OnGold, strokeWidth = 2.dp)
                else Text("Search", fontWeight = FontWeight.Bold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(
                value = year, onValueChange = { onYear(it.filter { c -> c.isDigit() }.take(4)) },
                placeholder = "Year (optional)", modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
            )
            AppTextField(
                value = imdb, onValueChange = onImdb,
                placeholder = "IMDb ID (optional)", modifier = Modifier.weight(1f),
                imeAction = ImeAction.Search, onImeAction = onSubmit,
            )
        }
    }
}

@Composable
private fun TypeToggle(type: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Pal.Surface)
            .border(1.dp, Pal.Hairline, CircleShape)
            .padding(3.dp)
    ) {
        listOf("movie" to "Movie", "series" to "Series").forEach { (value, label) ->
            val active = type == value
            Text(
                label,
                color = if (active) Pal.OnGold else Pal.Fog,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) Pal.Gold else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onChange(value) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun Banner(ui: UiState, wide: Boolean) {
    val m = ui.match ?: return
    val low = m.confidence < AUTO_SELECT_THRESHOLD
    val desc = (m.raw.str("ar_content") ?: m.raw.str("en_content"))
        ?.let { if (it.length > 300) it.take(300) + "…" else it } ?: "No description available."

    Row(
        Modifier.page().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(width = if (wide) 130.dp else 96.dp, height = if (wide) 190.dp else 140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Pal.Surface)
        ) {
            if (ui.bannerPoster != null) {
                AsyncImage(ui.bannerPoster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (low) "LOW-CONFIDENCE MATCH" else "✓ MATCH FOUND",
                color = if (low) Pal.Gold else Pal.Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
            Text(m.displayTitle, color = Pal.Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp)
            Text(
                "${m.year ?: "—"}  ·  ID ${m.cinemanaId ?: "—"}  ·  IMDb ${m.imdbId ?: "—"}",
                color = Pal.Fog, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(desc, color = Pal.Fog, fontSize = 13.sp, lineHeight = 19.sp, maxLines = if (wide) 6 else 5, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SeriesSection(vm: AppViewModel, ui: UiState) {
    val season = ui.browseSeason ?: ui.currentSeason ?: return
    Column(Modifier.page().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Seasons")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ui.episodes.keys.toList()) { s ->
                val selected = s == season
                Column(
                    Modifier.clickable { vm.browseSeason(s) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Pal.Surface)
                            .border(if (selected) 2.dp else 1.dp, if (selected) Pal.Gold else Pal.Hairline, RoundedCornerShape(8.dp))
                    ) {
                        val poster = seasonPoster(ui, s)
                        if (poster != null) AsyncImage(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Text("S$s", color = if (selected) Pal.Gold else Pal.Fog, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        SectionLabel("Episodes")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ui.episodes[season].orEmpty(), key = { it.id }) { ep ->
                val current = ui.currentEpisode?.id == ep.id && ui.currentSeason == season
                Column(
                    Modifier.widthIn(max = 168.dp).size(width = 168.dp, height = 150.dp).clickable { vm.switchEpisode(season, ep) },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 168.dp, height = 94.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Pal.Surface)
                            .border(if (current) 2.dp else 1.dp, if (current) Pal.Gold else Pal.Hairline, RoundedCornerShape(10.dp))
                    ) {
                        val thumb = episodeThumb(ui, ep)
                        if (thumb != null) AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Text(
                            "E${ep.episode?.toString()?.padStart(2, '0') ?: "?"}",
                            color = Pal.OnGold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Pal.Gold)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(ep.title, color = Pal.Ink, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (current) Text("Now playing", color = Pal.Gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Pal.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
