package com.darkness.vpn.ui

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color

/** Monet tarzı tema: tek bir ana renkten tonlanan yüzeyler ve vurgu. */
data class AppTheme(val id: String, val name: String, val hue: Float, val sat: Float, val dynamic: Boolean = false)

object Themes {
    val list = listOf(
        AppTheme("monet", "Duvar kağıdı", 38f, 0.6f, dynamic = true),
        AppTheme("gold", "Altın", 38f, 0.6f),
        AppTheme("olive", "Zeytin", 85f, 0.38f),
        AppTheme("ocean", "Okyanus", 200f, 0.62f),
        AppTheme("lavender", "Lavanta", 265f, 0.55f),
        AppTheme("rose", "Gül", 340f, 0.6f),
        AppTheme("coral", "Mercan", 12f, 0.72f),
        AppTheme("mint", "Nane", 160f, 0.45f),
        AppTheme("night", "Gece mavisi", 225f, 0.55f),
        AppTheme("graphite", "Grafit", 220f, 0.06f),
    )

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("gecit", Context.MODE_PRIVATE)

    fun currentId(ctx: Context): String = prefs(ctx).getString("theme", "gold") ?: "gold"

    fun select(ctx: Context, id: String) {
        prefs(ctx).edit().putString("theme", id).apply()
        apply(ctx)
    }

    fun dynamicSupported() = Build.VERSION.SDK_INT >= 31

    private fun hsl(h: Float, s: Float, l: Float) = Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

    /** Tema seçicideki küçük önizleme renkleri: (vurgu, yüzey). */
    fun preview(ctx: Context, t: AppTheme): Pair<Color, Color> {
        if (t.dynamic && dynamicSupported()) {
            val d = dynamicDarkColorScheme(ctx)
            return d.primary to d.surfaceContainerHigh
        }
        return hsl(t.hue, t.sat, 0.76f) to hsl(t.hue, t.sat * 0.2f, 0.14f)
    }

    fun apply(ctx: Context) {
        val t = list.firstOrNull { it.id == currentId(ctx) } ?: list[1]
        if (t.dynamic && dynamicSupported()) {
            val d = dynamicDarkColorScheme(ctx)
            C.bg = d.background
            C.card = d.surfaceContainer
            C.inner = d.surfaceContainerHigh
            C.border = d.outlineVariant
            C.nav = d.surfaceContainerHighest
            C.text = d.onSurface
            C.sub = d.onSurfaceVariant
            C.accent = d.primary
            C.onAccent = d.onPrimary
            C.accent2 = d.tertiary
            return
        }
        val h = t.hue
        val s = t.sat
        C.bg = hsl(h, s * 0.18f, 0.055f)
        C.card = hsl(h, s * 0.16f, 0.095f)
        C.inner = hsl(h, s * 0.15f, 0.135f)
        C.border = hsl(h, s * 0.12f, 0.21f)
        C.nav = hsl(h, s * 0.18f, 0.15f)
        C.text = hsl(h, s * 0.12f, 0.95f)
        C.sub = hsl(h, s * 0.10f, 0.68f)
        C.accent = hsl(h, s, 0.76f)
        C.onAccent = hsl(h, s * 0.6f, 0.11f)
        C.accent2 = hsl(h + 25f, s, 0.58f)
    }
}
