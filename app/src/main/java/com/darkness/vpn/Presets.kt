package com.darkness.vpn

/** ByeDPI hazır ayarları. Argümanlar ciadpi komut satırı biçimindedir. */
data class Preset(val id: String, val title: String, val args: String)

object Presets {
    const val DEFAULT_ID = "auto"
    const val CUSTOM_ID = "custom"

    // "Otomatik": birkaç yöntemi sırayla dener (-A ile engel algılanınca bir sonraki gruba geçer).
    private val MIXED = "-o1 -r-5+se -a1 -At,r,s -d1 -s1+s -a1 -At,r,s -f-1 -t8 -s1+s -a1 -At,r,s -q1 -r1+s -a1"

    // Çalışan CloudStream sürümündeki hazır ayarlar; her biri gerekirse (-A) kendi içinde başka yönteme geçer.
    val list = listOf(
        Preset("auto", "Otomatik", MIXED),
        Preset("tt", "Türk Telekom", "-s1+sm -r1+s -a1 -At,r,s -o1 -r-5+se -a1 -At,r,s -f-1 -t8 -a1"),
        Preset("turkcell", "Turkcell / Superonline", "-f-1 -t8 -s1+s -a1 -At,r,s -d1 -s1+s -a1 -At,r,s -o1 -r-5+se -a1"),
        Preset("vodafone", "Vodafone", "-d1 -s1+s -a1 -At,r,s -f-1 -t8 -a1 -At,r,s -o1 -r-5+se -a1"),
        Preset("turknet", "TurkNet", "-s1+h -d3+s -a1 -At,r,s -o1 -r-5+se -a1 -At,r,s -f-1 -t8 -a1"),
        Preset("mobile", "Mobil veri", "-f-1 -t6 -d1 -a1 -At,r,s -o1 -r-5+se -a1 -At,r,s -s1+sm -a1"),
    )

    /** "Otomatik bul" adayları: hazır ayarlar + assets/dpi_strategies.list. */
    fun candidates(ctx: android.content.Context): List<Preset> = list + runCatching {
        ctx.assets.open("dpi_strategies.list").bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapIndexed { i, a -> Preset("s$i", "Yöntem ${i + 1}", a) }
    }.getOrDefault(emptyList())

    fun byId(id: String): Preset = list.firstOrNull { it.id == id } ?: list.first()
}
