package com.cinemana.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons

@Composable
fun Pill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val bg = if (active) Pal.Gold else Pal.Void
    val fg = if (active) Pal.OnGold else Pal.Fog
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (active) Pal.Gold else Pal.Hairline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        trailing?.invoke()
    }
}

@Composable
fun IconBtn(
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(44.dp)) {
        Icon(icon, contentDescription = desc, tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    required: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(placeholder, color = Pal.Muted, fontSize = 14.sp) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onSearch = { onImeAction() }, onDone = { onImeAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Pal.Ink,
            unfocusedTextColor = Pal.Ink,
            cursorColor = Pal.Gold,
            focusedBorderColor = Pal.Gold,
            unfocusedBorderColor = Pal.Hairline,
            focusedContainerColor = Pal.Surface,
            unfocusedContainerColor = Pal.Surface,
        ),
    )
}

/** Small gold check used by selection rows. */
@Composable
fun CheckMark() {
    Icon(Icons.Filled.Check, contentDescription = null, tint = Pal.Gold, modifier = Modifier.size(20.dp))
}
