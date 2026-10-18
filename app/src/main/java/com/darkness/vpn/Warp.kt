package com.darkness.vpn

import android.content.Context
import android.util.Base64
import org.bouncycastle.math.ec.rfc7748.X25519
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Cloudflare WARP için ücretsiz hesap açar ve WireGuard bilgisini saklar. */
data class WarpAccount(
    val privateKey: String,
    val peerPublicKey: String,
    val v4: String,
    val v6: String,
    val reserved: List<Int>,
    val id: String,
) {
    fun toJson() = JSONObject().apply {
        put("privateKey", privateKey); put("peerPublicKey", peerPublicKey)
        put("v4", v4); put("v6", v6); put("reserved", JSONArray(reserved)); put("id", id)
    }.toString()

    companion object {
        fun fromJson(s: String) = JSONObject(s).let { j ->
            val r = j.getJSONArray("reserved")
            WarpAccount(
                j.getString("privateKey"), j.getString("peerPublicKey"), j.getString("v4"), j.getString("v6"),
                (0 until r.length()).map { r.getInt(it) }, j.optString("id"),
            )
        }
    }
}

object Warp {
    const val DEFAULT_ENDPOINT = "162.159.192.1:2408"
    const val DEFAULT_PEER_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
    private const val API = "https://api.cloudflareclient.com/v0a2158/reg"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("warp", Context.MODE_PRIVATE)

    fun account(ctx: Context): WarpAccount? =
        prefs(ctx).getString("account", null)?.let { runCatching { WarpAccount.fromJson(it) }.getOrNull() }

    fun clear(ctx: Context) = prefs(ctx).edit().remove("account").apply()

    /** Hesap yoksa yeni hesap açar. Ağ isteği yapar; ana iş parçacığında çağırma. */
    fun ensure(ctx: Context): WarpAccount = account(ctx) ?: register(ctx)

    fun register(ctx: Context): WarpAccount {
        val priv = ByteArray(X25519.SCALAR_SIZE)
        X25519.generatePrivateKey(SecureRandom(), priv)
        val pub = ByteArray(X25519.POINT_SIZE)
        X25519.generatePublicKey(priv, 0, pub, 0)
        val b64 = { b: ByteArray -> Base64.encodeToString(b, Base64.NO_WRAP) }

        val tos = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val body = JSONObject().apply {
            put("key", b64(pub)); put("install_id", ""); put("fcm_token", "")
            put("tos", tos); put("model", "PC"); put("serial_number", ""); put("locale", "tr_TR")
        }.toString()

        val conn = URL(API).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000; conn.readTimeout = 15000
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("User-Agent", "okhttp/3.12.1")
        conn.setRequestProperty("CF-Client-Version", "a-6.30-3596")
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) throw IllegalStateException("WARP kaydı başarısız ($code): ${text.take(200)}")

        val j = JSONObject(text)
        val cfg = j.getJSONObject("config")
        val peer = cfg.getJSONArray("peers").getJSONObject(0)
        val addr = cfg.getJSONObject("interface").getJSONObject("addresses")
        val clientId = Base64.decode(cfg.optString("client_id", "AAAA"), Base64.DEFAULT)
        val reserved = clientId.take(3).map { it.toInt() and 0xff }.let { if (it.size == 3) it else listOf(0, 0, 0) }

        val acc = WarpAccount(
            privateKey = b64(priv),
            peerPublicKey = peer.optString("public_key", DEFAULT_PEER_KEY),
            v4 = addr.getString("v4"),
            v6 = addr.getString("v6"),
            reserved = reserved,
            id = j.optString("id"),
        )
        prefs(ctx).edit().putString("account", acc.toJson()).apply()
        return acc
    }
}
