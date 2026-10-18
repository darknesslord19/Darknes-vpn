package com.darkness.vpn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val bg = Color(0xFF0E0E10)
    val card = Color(0xFF18181B)
    val inner = Color(0xFF1F1F23)
    val border = Color(0xFF2C2C31)
    val text = Color(0xFFF2F2F3)
    val sub = Color(0xFFA2A2AA)
    val accent = Color(0xFFE6C48E)
    val onAccent = Color(0xFF1A1408)
    val green = Color(0xFF4CC38A)
    val greenBg = Color(0xFF1C3328)
    val orange = Color(0xFFF08A2E)
    val orangeBg = Color(0xFF3A2618)
    val red = Color(0xFFE5675F)
}

val GecitColors = darkColorScheme(
    primary = C.accent, onPrimary = C.onAccent, background = C.bg, surface = C.card,
    onSurface = C.text, onBackground = C.text, surfaceVariant = C.inner, outline = C.border,
)

@Composable
fun Section(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Text(title, color = C.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = C.sub, fontSize = 14.sp)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
fun Badge(text: String, fg: Color = C.green, bg: Color = C.greenBg) {
    Text(
        text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun Radio(selected: Boolean) {
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .border(2.dp, if (selected) C.accent else C.border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(C.accent))
    }
}

@Composable
fun RadioRow(title: String, desc: String?, selected: Boolean, badge: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.padding(top = 2.dp, start = 4.dp, end = 14.dp)) { Radio(selected) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (badge != null) { Spacer(Modifier.width(8.dp)); Badge(badge) }
            }
            if (desc != null) Text(desc, color = C.sub, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
fun SwitchRow(title: String, desc: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (desc != null) Text(desc, color = C.sub, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = C.accent, checkedThumbColor = C.bg,
                uncheckedTrackColor = C.inner, uncheckedThumbColor = C.sub, uncheckedBorderColor = C.border,
            )
        )
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) C.onAccent else C.text,
        fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) C.accent else C.inner)
            .border(1.dp, if (selected) C.accent else C.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.onAccent,
            disabledContainerColor = C.inner, disabledContentColor = C.sub)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun OutlineButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, C.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = C.text)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Divider() = HorizontalDivider(color = C.border, thickness = 1.dp)

@Composable
fun gecitFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.accent, unfocusedBorderColor = C.border, cursorColor = C.accent,
    focusedTextColor = C.text, unfocusedTextColor = C.text, focusedLabelColor = C.accent, unfocusedLabelColor = C.sub,
)
