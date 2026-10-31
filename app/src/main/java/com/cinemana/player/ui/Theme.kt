package com.cinemana.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colour tokens from the HTML's :root block. */
object Pal {
    val Void = Color(0xFF050506)
    val Surface = Color(0xFF101013)
    val Elevated = Color(0xFF17171B)
    val Ink = Color(0xFFF3F1EA)
    val Fog = Color(0xFF9B9689)
    val Muted = Color(0xFF6F6A5D)
    val Hairline = Color(0x17FFFFFF)
    val HairlineStrong = Color(0x2EFFFFFF)
    val Gold = Color(0xFFE2AB45)
    val GoldDark = Color(0xFFA97417)
    val OnGold = Color(0xFF1C1204)
    val Teal = Color(0xFF57CDBF)
}

@Composable
fun CinemanaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Gold,
            onPrimary = Pal.OnGold,
            secondary = Pal.Teal,
            background = Pal.Void,
            onBackground = Pal.Ink,
            surface = Pal.Surface,
            onSurface = Pal.Ink,
            surfaceVariant = Pal.Elevated,
            onSurfaceVariant = Pal.Fog,
            outline = Pal.Hairline,
        ),
        content = content,
    )
}
