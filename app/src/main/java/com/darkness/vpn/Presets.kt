package com.darkness.vpn

/** ByeDPI hazır ayarları. Argümanlar ciadpi komut satırı biçimindedir. */
data class Preset(val id: String, val title: String, val args: String)

object Presets {
    const val DEFAULT_ID = "auto"
    const val CUSTOM_ID = "custom"

    // "Otomatik": birkaç yöntemi sırayla dener (-A ile engel algılanınca bir sonraki gruba geçer).
    private val MIXED = "-o1 -r-5+se -a1 -At,r,s -d1 -s1+s -a1 -At,r,s -f-1 -t8 -s1+s -a1 -At,r,s -q1 -r1+s -a1"

    val list = listOf(
        Preset("auto", "Otomatik", MIXED),
        Preset("tt", "Türk Telekom", "-o1 -d1 -a1 -At,r,s -s1 -d1 -s5+s -s10+s -s15+s -s20+s -r1+s -a1"),
        Preset("turkcell", "Turkcell / Superonline", "-s1 -q1 -a1 -At,r,s -d1 -s1+s -r1+s -a1"),
        Preset("vodafone", "Vodafone", "-d1 -s1+s -r1+s -a1 -At,r,s -f-1 -t6 -s1+s -a1"),
        Preset("turknet", "TurkNet", "-s1 -d1 -r1+s -a1 -At,r,s -o1 -s1+s -a1"),
        Preset("mobile", "Mobil veri", "-f-1 -t5 -s1+s -a1 -At,r,s -d1 -s1+s -a1 -At,r,s -o1 -r1+s -a1"),
    )

    /** "Otomatik bul" sırasında denenecek ek adaylar (hazır ayarlara ek olarak). */
    val candidates: List<Preset> = list + listOf(
        Preset("c1", "Bölme + kayıt", "-s1 -r1+s"),
        Preset("c2", "Ters sıra", "-d1"),
        Preset("c3", "Ters sıra SNI", "-d1+s -s3+s"),
        Preset("c4", "OOB", "-o1 -s1+s"),
        Preset("c5", "Sahte paket TTL 5", "-f-1 -t5 -s1+s"),
        Preset("c6", "Sahte paket TTL 8", "-f-1 -t8 -r1+s"),
        Preset("c7", "TLS kayıt bölme", "-r1+s -r5+s"),
        Preset("c8", "Çoklu bölme", "-d1 -s0+s -d3+s -s6+s -d9+s -s12+s"),
        Preset("c9", "Disoob", "-q1 -r1+s"),
        Preset("c10", "Sahte SNI", "-f-1 -t6 -n www.google.com -s1+s"),
    )

    fun byId(id: String): Preset = list.firstOrNull { it.id == id } ?: list.first()
}
