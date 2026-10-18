package com.darkness.vpn.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class NavItem(val label: String, val icon: ImageVector)

/**
 * Alt gezinme çubuğu: seçili sekme çubuğun üstüne taşan yuvarlak bir düğmede durur,
 * çubukta onun için yumuşak bir oyuk açılır ve sekme değişince oyuk kayarak gider.
 */
@Composable
fun NotchedBottomBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    val barHeight = 68.dp
    val circle = 58.dp
    val rise = 26.dp // düğmenin çubuğun üstüne taşan kısmı

    val slot = if (items.isEmpty()) 0f else widthPx / items.size.toFloat()
    val targetX = slot * selected + slot / 2f
    val cx by animateFloatAsState(targetX, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "notch")

    Box(
        modifier
            .fillMaxWidth()
            .height(barHeight + rise)
            .onSizeChanged { widthPx = it.width }
    ) {
        val barColor = C.nav
        val edge = C.border
        // Oyuklu çubuk
        Canvas(Modifier.fillMaxSize()) {
            if (widthPx == 0) return@Canvas
            val top = rise.toPx()
            val bottom = size.height
            val r = 26.dp.toPx() // köşe yarıçapı
            val cr = circle.toPx() / 2f
            val gap = 7.dp.toPx()
            val depth = cr + gap - (rise.toPx() - cr) // oyuğun dibi, düğmenin altını sarar
            val half = cr + gap + 26.dp.toPx()
            val p = Path().apply {
                moveTo(0f, top + r)
                quadraticBezierTo(0f, top, r, top)
                lineTo(cx - half, top)
                cubicTo(cx - half * 0.55f, top, cx - (cr + gap), top + depth, cx, top + depth)
                cubicTo(cx + (cr + gap), top + depth, cx + half * 0.55f, top, cx + half, top)
                lineTo(size.width - r, top)
                quadraticBezierTo(size.width, top, size.width, top + r)
                lineTo(size.width, bottom - r)
                quadraticBezierTo(size.width, bottom, size.width - r, bottom)
                lineTo(r, bottom)
                quadraticBezierTo(0f, bottom, 0f, bottom - r)
                close()
            }
            drawPath(p, barColor)
            drawPath(p, edge, style = Stroke(1.dp.toPx()))
        }

        // Seçili olmayan sekmeler
        Row(Modifier.fillMaxWidth().height(barHeight).align(Alignment.BottomCenter)) {
            items.forEachIndexed { i, item ->
                val alpha by animateFloatAsState(if (i == selected) 0f else 1f, label = "a$i")
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(item.icon, item.label, tint = C.sub.copy(alpha = alpha), modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(item.label, color = C.sub.copy(alpha = alpha), fontSize = 11.sp)
                }
            }
        }

        // Seçili sekmenin yükselen düğmesi
        if (widthPx > 0) {
            val circlePx = with(density) { circle.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((cx - circlePx / 2f).roundToInt(), 0) }
                    .size(circle)
                    .shadow(14.dp, CircleShape, ambientColor = C.accent, spotColor = C.accent)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(C.accent, C.accent2)))
                    .clickable { onSelect(selected) },
                contentAlignment = Alignment.Center
            ) {
                Icon(items[selected].icon, items[selected].label, tint = C.onAccent, modifier = Modifier.size(28.dp))
            }
        }
    }
}
