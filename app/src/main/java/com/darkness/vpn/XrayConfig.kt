package com.darkness.vpn

import org.json.JSONArray
import org.json.JSONObject

/**
 * Xray yapılandırmasını üretir. Akış:
 *   TUN → hev-socks5-tunnel → Xray SOCKS (127.0.0.1:10808) → kurallar →
 *     direct / dpi (ByeDPI SOCKS) / warp (WireGuard) / custom
 */
object XrayConfig {
    const val SOCKS_PORT = 10808
    const val DPI_PORT = 10801

    private val PRIVATE = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8",
        "169.254.0.0/16", "100.64.0.0/10", "fc00::/7", "fe80::/10",
    )

    fun build(
        s: Settings,
        warp: WarpAccount?,
        custom: JSONObject?,
        socksPort: Int = SOCKS_PORT,
        dpiPort: Int = DPI_PORT,
        forceOutbound: String? = null,
    ): String {
        val method = when (s.method) {
            Method.DPI -> "dpi"
            Method.WARP -> "warp"
            Method.CUSTOM -> "custom"
        }

        val outbounds = JSONArray()
        outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom")
            .put("streamSettings", JSONObject().put("sockopt", JSONObject().put("domainStrategy", "UseIP"))))
        outbounds.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        outbounds.put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
        outbounds.put(JSONObject().put("tag", "dpi").put("protocol", "socks")
            .put("settings", JSONObject().put("address", "127.0.0.1").put("port", dpiPort)))
        if (warp != null) outbounds.put(warpOutbound(warp, s.warpEndpoint))
        if (custom != null) outbounds.put(resolveAddress(JSONObject(custom.toString())))

        val rules = JSONArray()
        fun rule(vararg kv: Pair<String, Any>) = rules.put(JSONObject().apply { kv.forEach { put(it.first, it.second) } })
        fun domains(list: List<String>) = JSONArray(list.map { "domain:$it" })

        rule("inboundTag" to JSONArray(listOf("in")), "port" to "53", "outboundTag" to "dns-out")
        rule("ip" to JSONArray(PRIVATE), "outboundTag" to "direct")

        if (forceOutbound != null) {
            rule("network" to "tcp,udp", "outboundTag" to forceOutbound)
        } else {
            val hasWarp = warp != null
            if (s.method == Method.DPI && s.warpFallback && hasWarp && s.warpSites.isNotEmpty()) {
                rule("domain" to domains(s.warpSites), "outboundTag" to "warp")
            }
            if (s.method == Method.DPI) {
                // DPI aşmada bütün trafik ByeDPI'dan geçer; hangi sitenin engelli olduğunu
                // ByeDPI kendisi algılar (bkz. Settings.effectiveDpiArgs). Hız kaybı yoktur.
                if (s.blockQuic) rule("network" to "udp", "port" to "443", "outboundTag" to "block")
                rule("network" to "tcp,udp", "outboundTag" to "dpi")
            } else if (s.routeMode == RouteMode.BLOCKED) {
                if (s.blockedSites.isNotEmpty()) rule("domain" to domains(s.blockedSites), "outboundTag" to method)
                rule("network" to "tcp,udp", "outboundTag" to "direct")
            } else {
                rule("network" to "tcp,udp", "outboundTag" to method)
            }
        }

        val dnsServer = s.dns.trim().ifEmpty { "1.1.1.1" }
        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject()
                .put("servers", JSONArray(listOf("https+local://$dnsServer/dns-query", "https+local://8.8.8.8/dns-query")))
                .put("queryStrategy", "UseIPv4"))
            .put("inbounds", JSONArray().put(JSONObject()
                .put("tag", "in").put("listen", "127.0.0.1").put("port", socksPort).put("protocol", "socks")
                .put("settings", JSONObject().put("auth", "noauth").put("udp", true).put("ip", "127.0.0.1"))
                .put("sniffing", JSONObject().put("enabled", true)
                    .put("destOverride", JSONArray(listOf("http", "tls", "quic"))).put("routeOnly", true))))
            .put("outbounds", outbounds)
            .put("routing", JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
        return root.toString(2)
    }

    private fun warpOutbound(w: WarpAccount, endpoint: String): JSONObject {
        val ep = resolveEndpoint(endpoint.ifBlank { Warp.DEFAULT_ENDPOINT })
        val peer = JSONObject().put("publicKey", w.peerPublicKey).put("endpoint", ep).put("keepAlive", 25)
            .put("allowedIPs", JSONArray(listOf("0.0.0.0/0", "::/0")))
        return JSONObject().put("tag", "warp").put("protocol", "wireguard").put("settings", JSONObject()
            .put("secretKey", w.privateKey)
            .put("address", JSONArray(listOf("${w.v4}/32", "${w.v6}/128")))
            .put("peers", JSONArray().put(peer))
            .put("reserved", JSONArray(w.reserved))
            .put("mtu", 1280)
            .put("noKernelTun", true))
    }

    /** Xray, Android'de sistem DNS'ini okuyamaz; alan adlarını önceden IP'ye çeviririz. */
    private fun resolveEndpoint(ep: String): String {
        val host = ep.substringBeforeLast(':').trim('[', ']')
        val port = ep.substringAfterLast(':')
        if (Net.isIp(host)) return ep
        val ip = Net.resolve(host).firstOrNull() ?: return ep
        return "$ip:$port"
    }

    private fun resolveAddress(ob: JSONObject): JSONObject {
        val st = ob.optJSONObject("settings") ?: return ob
        st.optString("address").takeIf { it.isNotEmpty() && !Net.isIp(it) }?.let { h ->
            Net.resolve(h).firstOrNull()?.let { st.put("address", it) }
        }
        st.optJSONArray("peers")?.let { peers ->
            for (i in 0 until peers.length()) {
                val p = peers.getJSONObject(i)
                p.put("endpoint", resolveEndpoint(p.getString("endpoint")))
            }
        }
        return ob
    }
}
