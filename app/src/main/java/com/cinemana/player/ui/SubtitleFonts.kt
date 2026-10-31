package com.cinemana.player.ui

import androidx.compose.ui.text.font.FontFamily

data class SubFont(val id: String, val label: String, val family: FontFamily)

/*
 * The HTML loaded Cairo / Tajawal / IBM Plex Sans Arabic / Noto Naskh / Amiri and Inter / Poppins / ... from Google Fonts.
 * This project ships with the Android system families so it builds with zero binary assets
 * (Android maps Sans -> Noto Sans Arabic and Serif -> Noto Naskh Arabic for Arabic text).
 *
 * To bring the original fonts back: drop the .ttf files into app/src/main/res/font/ (e.g. cairo_regular.ttf) and add
 *   SubFont("cairo", "Cairo", FontFamily(Font(R.font.cairo_regular, FontWeight.Normal), Font(R.font.cairo_bold, FontWeight.Bold)))
 * to the lists below. Nothing else needs to change.
 */
val ARABIC_FONTS = listOf(
    SubFont("sans", "Sans (Noto Sans Arabic)", FontFamily.SansSerif),
    SubFont("serif", "Naskh (Serif)", FontFamily.Serif),
)

val LATIN_FONTS = listOf(
    SubFont("sans", "Sans", FontFamily.SansSerif),
    SubFont("serif", "Serif", FontFamily.Serif),
    SubFont("mono", "Monospace", FontFamily.Monospace),
    SubFont("cursive", "Handwriting", FontFamily.Cursive),
)

fun arabicFont(id: String): FontFamily = (ARABIC_FONTS.firstOrNull { it.id == id } ?: ARABIC_FONTS.first()).family
fun latinFont(id: String): FontFamily = (LATIN_FONTS.firstOrNull { it.id == id } ?: LATIN_FONTS.first()).family
