package com.darkness.vpn

import android.content.Context
import android.content.SharedPreferences

enum class Method { DPI, WARP, CUSTOM }
enum class RouteMode { BLOCKED, ALL }
enum class AppMode { ALL, ONLY, EXCEPT }

/** Uygulamanın bütün ayarları. Değişmez; değiştirmek için copy() + Settings.save(). */
data class Settings(
    val method: Method = Method.DPI,
    val presetId: String = Presets.DEFAULT_ID,
    val customArgs: String = "",
    val warpFallback: Boolean = true,
    val routeMode: RouteMode = RouteMode.ALL,
    val blockedSites: List<String> = DEFAULT_BLOCKED,
    val warpSites: List<String> = emptyList(),
    val coverApps: Boolean = true,
    val appMode: AppMode = AppMode.ALL,
    val apps: Set<String> = emptySet(),
    val autoConnectOnOpen: Boolean = false,
    val connectOnBoot: Boolean = false,
    val blockQuic: Boolean = true,
    val warpEndpoint: String = Warp.DEFAULT_ENDPOINT,
    val customLink: String = "",
    val dns: String = "1.1.1.1",
) {
    /** Kullanılacak ByeDPI argümanları. */
    val dpiArgs: String
        get() = if (presetId == Presets.CUSTOM_ID) customArgs.ifBlank { Presets.byId(Presets.DEFAULT_ID).args }
        else Presets.byId(presetId).args

    /** WARP gerçekten gerekli mi (yöntem WARP ya da DPI + yedek)? */
    val needsWarp: Boolean get() = method == Method.WARP || (method == Method.DPI && warpFallback)

    fun save(ctx: Context) = save(prefs(ctx), this)

    companion object {
        val DEFAULT_BLOCKED = listOf(
            "discord.com", "discord.gg", "discordapp.com", "discordapp.net", "discord.media",
            "roblox.com", "wattpad.com", "pastebin.com", "4shared.com", "wikileaks.org", "bitly.com",
        )

        private fun prefs(ctx: Context): SharedPreferences =
            ctx.applicationContext.getSharedPreferences("gecit", Context.MODE_PRIVATE)

        private const val VERSION = 3

        fun load(ctx: Context): Settings {
            val p = prefs(ctx)
            if (p.getInt("version", 0) < VERSION) {
                // v2: varsayılan yönlendirme "Her şey" oldu (DPI aşmada hız kaybı yok, daha güvenilir).
                p.edit().putInt("version", VERSION).remove("routeMode").remove("blocked").remove("preset").apply()
            }
            val d = Settings()
            fun list(key: String, def: List<String>) =
                p.getString(key, null)?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: def
            return Settings(
                method = enumOr(p.getString("method", null), d.method),
                presetId = p.getString("preset", d.presetId)!!,
                customArgs = p.getString("customArgs", d.customArgs)!!,
                warpFallback = p.getBoolean("warpFallback", d.warpFallback),
                routeMode = enumOr(p.getString("routeMode", null), d.routeMode),
                blockedSites = list("blocked", d.blockedSites),
                warpSites = list("warpSites", d.warpSites),
                coverApps = p.getBoolean("coverApps", d.coverApps),
                appMode = enumOr(p.getString("appMode", null), d.appMode),
                apps = p.getStringSet("apps", d.apps)!!.toSet(),
                autoConnectOnOpen = p.getBoolean("autoOpen", d.autoConnectOnOpen),
                connectOnBoot = p.getBoolean("boot", d.connectOnBoot),
                blockQuic = p.getBoolean("blockQuic", d.blockQuic),
                warpEndpoint = p.getString("warpEndpoint", d.warpEndpoint)!!,
                customLink = p.getString("customLink", d.customLink)!!,
                dns = p.getString("dns", d.dns)!!,
            )
        }

        private fun save(p: SharedPreferences, s: Settings) {
            p.edit()
                .putString("method", s.method.name)
                .putString("preset", s.presetId)
                .putString("customArgs", s.customArgs)
                .putBoolean("warpFallback", s.warpFallback)
                .putString("routeMode", s.routeMode.name)
                .putString("blocked", s.blockedSites.joinToString("\n"))
                .putString("warpSites", s.warpSites.joinToString("\n"))
                .putBoolean("coverApps", s.coverApps)
                .putString("appMode", s.appMode.name)
                .putStringSet("apps", s.apps)
                .putBoolean("autoOpen", s.autoConnectOnOpen)
                .putBoolean("boot", s.connectOnBoot)
                .putBoolean("blockQuic", s.blockQuic)
                .putString("warpEndpoint", s.warpEndpoint)
                .putString("customLink", s.customLink)
                .putString("dns", s.dns)
                .apply()
        }

        private inline fun <reified T : Enum<T>> enumOr(v: String?, def: T): T =
            v?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: def

        /** Kullanıcının yazdığı alan adını sadeleştirir: https://www.x.com/a → x.com */
        fun normalizeDomain(raw: String): String? {
            var s = raw.trim().lowercase()
            s = s.substringAfter("://").substringBefore('/').substringBefore(':')
            s = s.removePrefix("www.").trim('.')
            return if (s.contains('.') && s.all { it.isLetterOrDigit() || it == '.' || it == '-' }) s else null
        }
    }
}
