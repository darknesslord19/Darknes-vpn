package com.darkness.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import hev.htproxy.TProxyService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.concurrent.thread

sealed class VpnStatus {
    data object Off : VpnStatus()
    data class Connecting(val step: String) : VpnStatus()
    data class On(val method: Method, val since: Long) : VpnStatus()
    data class Error(val message: String) : VpnStatus()
}

object VpnState {
    val status = MutableStateFlow<VpnStatus>(VpnStatus.Off)
    val flow: StateFlow<VpnStatus> get() = status
    val isOn get() = status.value is VpnStatus.On
}

class GecitVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.darkness.vpn.START"
        const val ACTION_STOP = "com.darkness.vpn.STOP"
        const val ACTION_RELOAD = "com.darkness.vpn.RELOAD"
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) = send(ctx, ACTION_START)
        fun stop(ctx: Context) = send(ctx, ACTION_STOP)
        fun reload(ctx: Context) { if (VpnState.isOn) send(ctx, ACTION_RELOAD) }

        private fun send(ctx: Context, action: String) {
            val i = Intent(ctx, GecitVpnService::class.java).setAction(action)
            if (action == ACTION_STOP) ctx.startService(i) else ctx.startForegroundService(i)
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var byedpi: Process? = null
    private var xray: Process? = null
    @Volatile private var running = false
    @Volatile private var generation = 0
    private val lock = Any()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                generation++
                thread { synchronized(lock) { teardown() }; VpnState.status.value = VpnStatus.Off; stopSelfFully() }
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START, ACTION_RELOAD ya da sistemin "her zaman açık VPN" başlatması (action = null)
                startForeground(NOTIF_ID, notification("Bağlanıyor…"))
                val gen = ++generation
                thread(name = "vpn-start") { synchronized(lock) { if (gen == generation) connect() } }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        // Başka bir VPN açıldı ya da kullanıcı sistemden kapattı.
        thread { synchronized(lock) { teardown() }; stopSelfFully() }
    }

    override fun onDestroy() {
        synchronized(lock) { teardown() }
        super.onDestroy()
    }

    private fun stopSelfFully() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun step(s: String) {
        VpnState.status.value = VpnStatus.Connecting(s)
        Engine.log(this, "· $s")
    }

    private fun connect() {
        teardown()
        val s = Settings.load(this)
        try {
            Engine.logFile(this).writeText("")
            Engine.log(this, "=== Bağlanıyor: ${s.method} · yönlendirme ${s.routeMode} ===")

            // 1) WARP hesabı
            var warp: WarpAccount? = null
            if (s.needsWarp) {
                step("WARP hesabı hazırlanıyor")
                warp = try {
                    Warp.ensure(this)
                } catch (e: Exception) {
                    if (s.method == Method.WARP) throw e
                    Engine.log(this, "WARP hesabı alınamadı, yedek kapalı: ${e.message}")
                    null
                }
            }

            // 2) Kendi sunucum
            val custom = if (s.method == Method.CUSTOM) {
                if (s.customLink.isBlank()) throw IllegalStateException("Kendi sunucun için bir bağlantı gir.")
                ShareLink.parse(s.customLink).outbound
            } else null

            // 3) ByeDPI
            if (s.method == Method.DPI) {
                step("ByeDPI başlatılıyor")
                val p = Engine.startByeDpi(this, s.dpiArgs, XrayConfig.DPI_PORT)
                byedpi = p
                if (!Engine.waitPort(XrayConfig.DPI_PORT, 4000) { p.isAlive })
                    throw IllegalStateException("ByeDPI başlamadı. Ayarları kontrol et (Gelişmiş → Kayıtlar).")
            }

            // 4) Xray
            step("Yönlendirici başlatılıyor")
            val cfg = XrayConfig.build(s, warp, custom)
            val x = Engine.startXray(this, cfg)
            xray = x
            if (!Engine.waitPort(XrayConfig.SOCKS_PORT, 8000) { x.isAlive })
                throw IllegalStateException("Xray başlamadı. Ayrıntı için Kayıtlar'a bak.")

            // 5) TUN
            step("VPN arayüzü açılıyor")
            val b = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(8500)
                .addAddress("198.18.0.1", 32)
                .addAddress("fc00::1", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer(s.dns.ifBlank { "1.1.1.1" })
                .setBlocking(false)
            if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
            applyApps(b, s)
            b.setConfigureIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            val fd = b.establish() ?: throw IllegalStateException("VPN izni yok.")
            tun = fd

            // 6) hev-socks5-tunnel
            val hevConf = File(filesDir, "hev.yml")
            hevConf.writeText(
                """
                tunnel:
                  mtu: 8500
                  ipv4: 198.18.0.1
                  ipv6: 'fc00::1'
                socks5:
                  port: ${XrayConfig.SOCKS_PORT}
                  address: 127.0.0.1
                  udp: 'udp'
                misc:
                  task-stack-size: 81920
                  log-level: warn
                """.trimIndent() + "\n"
            )
            if (!TProxyService.TProxyStartService(hevConf.absolutePath, fd.fd))
                throw IllegalStateException("Tünel başlatılamadı.")
            running = true

            VpnState.status.value = VpnStatus.On(s.method, System.currentTimeMillis())
            Engine.log(this, "=== Bağlandı ===")
            notify(notification("Bağlı · " + methodName(s)))
            watch()
        } catch (e: Throwable) {
            Engine.log(this, "HATA: ${e.message}")
            teardown()
            VpnState.status.value = VpnStatus.Error(e.message ?: e.javaClass.simpleName)
            stopSelfFully()
        }
    }

    private fun methodName(s: Settings) = when (s.method) {
        Method.DPI -> if (s.warpFallback) "DPI aşma + WARP" else "DPI aşma"
        Method.WARP -> "WARP"
        Method.CUSTOM -> "Kendi sunucum"
    }

    private fun applyApps(b: Builder, s: Settings) {
        val me = packageName
        val installed = { p: String ->
            runCatching { packageManager.getPackageInfo(p, 0); true }.getOrDefault(false)
        }
        when {
            s.coverApps && s.appMode == AppMode.ONLY && s.apps.isNotEmpty() -> {
                s.apps.filter { it != me && installed(it) }.forEach { runCatching { b.addAllowedApplication(it) } }
            }
            s.coverApps && s.appMode == AppMode.EXCEPT -> {
                b.addDisallowedApplication(me)
                s.apps.filter { it != me && installed(it) }.forEach { runCatching { b.addDisallowedApplication(it) } }
            }
            else -> b.addDisallowedApplication(me) // Bütün uygulamalar (kendimiz hariç: ByeDPI/Xray döngüye girmesin)
        }
    }

    /** Arka plandaki süreçlerden biri ölürse bağlantıyı kapatır. */
    private fun watch() {
        val gen = generation
        thread(isDaemon = true, name = "vpn-watch") {
            while (running && gen == generation) {
                Thread.sleep(2000)
                val dead = (xray?.isAlive == false) || (byedpi?.isAlive == false)
                if (dead && running && gen == generation) {
                    Engine.log(this, "Bir bileşen beklenmedik şekilde kapandı.")
                    synchronized(lock) { teardown() }
                    VpnState.status.value = VpnStatus.Error("Bağlantı koptu. Kayıtlar'a bak.")
                    stopSelfFully()
                    break
                }
            }
        }
    }

    private fun teardown() {
        running = false
        runCatching { if (TProxyService.TProxyIsRunning()) TProxyService.TProxyStopService() }
        runCatching { tun?.close() }
        tun = null
        Engine.stop(xray); xray = null
        Engine.stop(byedpi); byedpi = null
        if (VpnState.status.value !is VpnStatus.Error) VpnState.status.value = VpnStatus.Off
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "VPN durumu", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, GecitVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Kes", stop).build())
            .build()
    }

    private fun notify(n: Notification) = getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
}
