package com.darkness.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Hızlı ayarlar paneli kutucuğu: tek dokunuşla bağlan / kes. */
class QuickTile : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        job = CoroutineScope(Dispatchers.Main).launch { VpnState.flow.collect { refresh(it) } }
    }

    override fun onStopListening() { job?.cancel() }

    private fun refresh(st: VpnStatus) {
        val t = qsTile ?: return
        t.state = when (st) {
            is VpnStatus.On, is VpnStatus.Connecting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = when (st) {
            is VpnStatus.On -> "Bağlı"
            is VpnStatus.Connecting -> "Bağlanıyor"
            else -> "Kapalı"
        }
        t.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val st = VpnState.status.value
        if (st is VpnStatus.On || st is VpnStatus.Connecting) {
            GecitVpnService.stop(this); return
        }
        if (VpnService.prepare(this) == null) {
            GecitVpnService.start(this)
        } else {
            val i = Intent(this, MainActivity::class.java).putExtra("connect", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION") startActivityAndCollapse(i)
            }
        }
    }
}
