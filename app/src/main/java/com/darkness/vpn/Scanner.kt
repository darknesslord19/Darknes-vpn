package com.darkness.vpn

import android.content.Context
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** "Otomatik bul" ve "Siteleri tara" işlemleri. Ana iş parçacığında çağırma. */
object Scanner {
    private const val TEST_PORT = 10860

    /** Sonuç: true = açıldı, false = engelli; DNS kaydı olmayan siteler sonuçta yer almaz. */
    private fun parallel(sites: List<String>, port: Int?, timeout: Int = 6000): Map<String, Boolean> {
        if (sites.isEmpty()) return emptyMap()
        val pool = Executors.newFixedThreadPool(minOf(sites.size, 6))
        try {
            val futures = sites.associateWith { s -> pool.submit(Callable { Net.siteWorks(s, port, timeout) }) }
            return futures.mapValues { runCatching { it.value.get() }.getOrDefault(false) }
                .filterValues { it != null }.mapValues { it.value!! }
        } finally {
            pool.shutdownNow()
        }
    }

    data class StrategyResult(val preset: Preset?, val score: Int, val total: Int, val message: String)

    fun findStrategy(ctx: Context, sites: List<String>, progress: (String) -> Unit): StrategyResult {
        progress("Engelli siteler belirleniyor…")
        val direct = parallel(sites, null)
        val blocked = direct.filterValues { !it }.keys.toList().take(4)
        if (blocked.isEmpty())
            return StrategyResult(null, 0, 0, "Listedeki siteler zaten doğrudan açılıyor; engel bulunamadı.")

        var best: Preset? = null
        var bestScore = -1
        val cands = Presets.candidates(ctx)
        for ((i, c) in cands.withIndex()) {
            progress("Deneniyor ${i + 1}/${cands.size}: ${c.title}")
            val p = Engine.startByeDpi(ctx, c.args, TEST_PORT, "test")
            try {
                if (!Engine.waitPort(TEST_PORT, 3000) { p.isAlive }) continue
                val score = parallel(blocked, TEST_PORT, 5000).count { it.value }
                Engine.log(ctx, "[test] ${c.title}: $score/${blocked.size}")
                if (score > bestScore) { best = c; bestScore = score }
                if (score == blocked.size) break
            } finally {
                Engine.stop(p)
            }
        }
        val b = best
        return if (b == null || bestScore <= 0)
            StrategyResult(null, 0, blocked.size, "Hiçbir yöntem engeli aşamadı. WARP yedeği açık kalsın.")
        else StrategyResult(b, bestScore, blocked.size, "${b.title} seçildi · ${bestScore}/${blocked.size} engelli site açıldı")
    }

    data class SiteReport(val direct: Set<String>, val viaDpi: Set<String>, val needWarp: Set<String>)

    fun scanSites(ctx: Context, s: Settings, progress: (String) -> Unit): SiteReport {
        progress("Siteler doğrudan deneniyor…")
        val direct = parallel(s.blockedSites, null)
        val blocked = direct.filterValues { !it }.keys.toList()
        if (blocked.isEmpty() || s.method != Method.DPI) {
            return SiteReport(direct.filterValues { it }.keys, emptySet(), blocked.toSet())
        }
        progress("Engelli ${blocked.size} site ByeDPI ile deneniyor…")
        val p = Engine.startByeDpi(ctx, s.dpiArgs, TEST_PORT, "test")
        try {
            if (!Engine.waitPort(TEST_PORT, 3000) { p.isAlive })
                return SiteReport(direct.filterValues { it }.keys, emptySet(), blocked.toSet())
            val viaDpi = parallel(blocked, TEST_PORT)
            return SiteReport(
                direct.filterValues { it }.keys,
                viaDpi.filterValues { it }.keys,
                viaDpi.filterValues { !it }.keys,
            )
        } finally {
            Engine.stop(p)
        }
    }
}
