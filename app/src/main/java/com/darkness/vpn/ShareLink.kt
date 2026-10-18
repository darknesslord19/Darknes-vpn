package com.darkness.vpn

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/**
 * "Kendi sunucum" için paylaşım bağlantısını (vless/vmess/trojan/ss) ya da
 * WireGuard .conf metnini Xray outbound JSON'una çevirir.
 */
object ShareLink {
    class Parsed(val outbound: JSONObject, val summary: String, val host: String)

    fun parse(text: String, tag: String = "custom"): Parsed {
        val t = text.trim()
        return when {
            t.startsWith("vless://", true) -> vless(t, tag)
            t.startsWith("vmess://", true) -> vmess(t, tag)
            t.startsWith("trojan://", true) -> trojan(t, tag)
            t.startsWith("ss://", true) -> shadowsocks(t, tag)
            t.contains("[Interface]", true) -> wireguard(t, tag)
            else -> throw IllegalArgumentException("Tanınmayan biçim. vless://, vmess://, trojan://, ss:// bağlantısı ya da WireGuard ayarı yapıştır.")
        }
    }

    private fun dec(s: String?) = s?.let { URLDecoder.decode(it, "UTF-8") }

    private fun b64(s: String): String {
        val clean = s.trim().replace('-', '+').replace('_', '/')
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        return String(Base64.decode(padded, Base64.DEFAULT))
    }

    /** Ortak taşıma (ws/grpc/xhttp/tcp) ve güvenlik (tls/reality) ayarları. */
    private fun stream(q: (String) -> String?, host: String): JSONObject {
        val net = (q("type") ?: "tcp").let { if (it == "raw") "tcp" else it }
        val sec = q("security") ?: "none"
        val s = JSONObject().put("network", net).put("security", sec)
        val sni = q("sni") ?: q("host") ?: host
        when (sec) {
            "tls" -> s.put("tlsSettings", JSONObject().apply {
                put("serverName", sni)
                q("fp")?.let { put("fingerprint", it) }
                q("alpn")?.let { put("alpn", JSONArray(it.split(','))) }
                if (q("allowInsecure") == "1") put("allowInsecure", true)
            })
            "reality" -> s.put("realitySettings", JSONObject().apply {
                put("serverName", sni)
                put("fingerprint", q("fp") ?: "chrome")
                put("publicKey", q("pbk") ?: "")
                put("shortId", q("sid") ?: "")
                q("spx")?.let { put("spiderX", it) }
            })
        }
        when (net) {
            "ws" -> s.put("wsSettings", JSONObject().put("path", q("path") ?: "/").put("host", q("host") ?: sni))
            "grpc" -> s.put("grpcSettings", JSONObject().put("serviceName", q("serviceName") ?: q("path") ?: ""))
            "xhttp", "splithttp" -> s.put("xhttpSettings", JSONObject().apply {
                put("path", q("path") ?: "/"); put("host", q("host") ?: sni); q("mode")?.let { put("mode", it) }
            }).put("network", "xhttp")
            "httpupgrade" -> s.put("httpupgradeSettings", JSONObject().put("path", q("path") ?: "/").put("host", q("host") ?: sni))
        }
        return s
    }

    private fun vless(link: String, tag: String): Parsed {
        val u = Uri.parse(link)
        val host = u.host ?: error("Sunucu adresi yok")
        val q = { k: String -> u.getQueryParameter(k)?.takeIf { it.isNotEmpty() } }
        val settings = JSONObject().put("address", host).put("port", u.port)
            .put("id", dec(u.userInfo)).put("encryption", q("encryption") ?: "none")
        q("flow")?.let { settings.put("flow", it) }
        val ob = JSONObject().put("tag", tag).put("protocol", "vless").put("settings", settings)
            .put("streamSettings", stream(q, host))
        return Parsed(ob, "VLESS · $host:${u.port}" + (u.fragment?.let { " · $it" } ?: ""), host)
    }

    private fun trojan(link: String, tag: String): Parsed {
        val u = Uri.parse(link)
        val host = u.host ?: error("Sunucu adresi yok")
        val q = { k: String -> u.getQueryParameter(k)?.takeIf { it.isNotEmpty() } }
        val qs = { k: String -> if (k == "security") (q(k) ?: "tls") else q(k) }
        val ob = JSONObject().put("tag", tag).put("protocol", "trojan")
            .put("settings", JSONObject().put("address", host).put("port", u.port).put("password", dec(u.userInfo)))
            .put("streamSettings", stream(qs, host))
        return Parsed(ob, "Trojan · $host:${u.port}" + (u.fragment?.let { " · $it" } ?: ""), host)
    }

    private fun vmess(link: String, tag: String): Parsed {
        val j = JSONObject(b64(link.substring(8)))
        val host = j.getString("add")
        val port = j.optString("port").toInt()
        val q = { k: String ->
            when (k) {
                "type" -> j.optString("net", "tcp")
                "security" -> j.optString("tls").ifEmpty { "none" }
                "sni" -> j.optString("sni")
                "host" -> j.optString("host")
                "path" -> j.optString("path")
                "fp" -> j.optString("fp")
                "alpn" -> j.optString("alpn")
                "serviceName" -> j.optString("path")
                else -> ""
            }.takeIf { it.isNotEmpty() }
        }
        val settings = JSONObject().put("address", host).put("port", port)
            .put("id", j.getString("id")).put("security", j.optString("scy", "auto").ifEmpty { "auto" })
        val ob = JSONObject().put("tag", tag).put("protocol", "vmess").put("settings", settings)
            .put("streamSettings", stream(q, host))
        return Parsed(ob, "VMess · $host:$port · ${j.optString("ps")}", host)
    }

    private fun shadowsocks(link: String, tag: String): Parsed {
        // ss://base64(method:pass)@host:port#name  ya da  ss://base64(method:pass@host:port)#name
        val noFrag = link.substring(5).substringBefore('#')
        val name = dec(link.substringAfter('#', ""))
        val (userPart, hostPart) = if (noFrag.contains('@')) {
            noFrag.substringBeforeLast('@') to noFrag.substringAfterLast('@').substringBefore('?').substringBefore('/')
        } else {
            val d = b64(noFrag)
            d.substringBeforeLast('@') to d.substringAfterLast('@')
        }
        val creds = if (userPart.contains(':')) dec(userPart)!! else b64(dec(userPart)!!)
        val method = creds.substringBefore(':')
        val pass = creds.substringAfter(':')
        val host = hostPart.substringBeforeLast(':').trim('[', ']')
        val port = hostPart.substringAfterLast(':').toInt()
        val ob = JSONObject().put("tag", tag).put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("address", host).put("port", port).put("method", method).put("password", pass))
        return Parsed(ob, "Shadowsocks · $host:$port" + (name?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""), host)
    }

    private fun wireguard(conf: String, tag: String): Parsed {
        val kv = mutableMapOf<String, String>()
        conf.lines().forEach { l ->
            val line = l.substringBefore('#').trim()
            if (line.contains('=')) {
                val k = line.substringBefore('=').trim().lowercase()
                val v = line.substringAfter('=').trim()
                kv[k] = if (kv.containsKey(k) && k == "address") kv[k] + "," + v else v
            }
        }
        val endpoint = kv["endpoint"] ?: error("Endpoint yok")
        val host = endpoint.substringBeforeLast(':').trim('[', ']')
        val peer = JSONObject().put("publicKey", kv["publickey"] ?: error("PublicKey yok"))
            .put("endpoint", endpoint)
            .put("allowedIPs", JSONArray(listOf("0.0.0.0/0", "::/0")))
            .put("keepAlive", 25)
        kv["presharedkey"]?.let { peer.put("preSharedKey", it) }
        val settings = JSONObject()
            .put("secretKey", kv["privatekey"] ?: error("PrivateKey yok"))
            .put("address", JSONArray(kv["address"]!!.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
            .put("peers", JSONArray().put(peer))
            .put("mtu", kv["mtu"]?.toIntOrNull() ?: 1280)
            .put("noKernelTun", true)
        val ob = JSONObject().put("tag", tag).put("protocol", "wireguard").put("settings", settings)
        return Parsed(ob, "WireGuard · $endpoint", host)
    }
}
