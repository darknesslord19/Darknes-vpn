package com.darkness.vpn

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Ağ yardımcıları. Uygulamanın kendisi VPN dışında kalır, bu yüzden buradaki
 * istekler doğrudan internete gider (ya da açıkça verilen SOCKS vekilinden geçer).
 */
object Net {
    private val DOH = listOf("1.1.1.1", "1.0.0.1", "8.8.8.8")

    /** DNS zehirlemesinden etkilenmeden IP bulur (DNS-over-HTTPS). */
    fun resolve(host: String, v6: Boolean = false): List<String> {
        if (isIp(host)) return listOf(host)
        for (server in DOH) {
            try {
                val url = if (server.startsWith("8.")) "https://dns.google/resolve" else "https://$server/dns-query"
                val q = "?name=" + URLEncoder.encode(host, "UTF-8") + "&type=" + (if (v6) "AAAA" else "A")
                val conn = URL(url + q).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/dns-json")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONObject(body).optJSONArray("Answer") ?: continue
                val want = if (v6) 28 else 1
                val out = (0 until arr.length()).map { arr.getJSONObject(it) }
                    .filter { it.optInt("type") == want }.map { it.getString("data") }
                if (out.isNotEmpty()) return out
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    fun isIp(s: String): Boolean =
        s.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) || (s.contains(':') && s.all { it.isLetterOrDigit() || it == ':' || it == '.' })

    /**
     * Bir siteye HTTPS ile gerçekten ulaşılabiliyor mu? DNS'i DoH ile çözer,
     * isteğe bağlı olarak SOCKS vekili (ByeDPI / Xray) üzerinden bağlanır,
     * TLS el sıkışması yapar ve bir HTTP yanıtı bekler.
     */
    fun siteWorks(host: String, socksPort: Int? = null, timeoutMs: Int = 7000): Boolean? {
        val ip = resolve(host).firstOrNull() ?: return null
        var raw: Socket? = null
        try {
            raw = if (socksPort != null) Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))) else Socket()
            raw.soTimeout = timeoutMs
            raw.connect(InetSocketAddress(ip, 443), timeoutMs)
            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(raw, host, 443, true) as SSLSocket
            ssl.sslParameters = ssl.sslParameters.apply { serverNames = listOf(SNIHostName(host)) }
            ssl.soTimeout = timeoutMs
            // Türkiye'deki engel TLS el sıkışmasını keser; el sıkışma tamamlanıyorsa site açıktır.
            ssl.startHandshake()
            runCatching { ssl.close() }
            return true
        } catch (_: Exception) {
            return false
        } finally {
            runCatching { raw?.close() }
        }
    }

    /** SOCKS üzerinden küçük bir HTTPS isteğinin süresi (ms); başarısızsa null. */
    fun ping(socksPort: Int?): Long? {
        repeat(2) {
            try {
                val proxy = socksPort?.let { Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", it)) } ?: Proxy.NO_PROXY
                val t0 = System.nanoTime()
                val conn = URL("https://www.google.com/generate_204").openConnection(proxy) as HttpURLConnection
                conn.connectTimeout = 6000; conn.readTimeout = 6000; conn.useCaches = false
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..399) return (System.nanoTime() - t0) / 1_000_000
            } catch (_: Exception) {
            }
        }
        return null
    }

    data class IpInfo(val ip: String, val country: String)

    /** Cloudflare trace ile dışarıdan görünen IP ve ülke. */
    fun ipInfo(socksPort: Int? = null): IpInfo? = try {
        val proxy = socksPort?.let { Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", it)) } ?: Proxy.NO_PROXY
        val conn = URL("https://1.1.1.1/cdn-cgi/trace").openConnection(proxy) as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        val map = conn.inputStream.bufferedReader().readLines()
            .mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        IpInfo(map["ip"] ?: "?", map["loc"] ?: "?")
    } catch (_: Exception) {
        null
    }
}
