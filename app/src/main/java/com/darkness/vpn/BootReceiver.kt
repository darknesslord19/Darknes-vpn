package com.darkness.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

/** Telefon açılınca (ayar açıksa ve VPN izni önceden verildiyse) bağlanır. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!Settings.load(ctx).connectOnBoot) return
        if (VpnService.prepare(ctx) != null) return
        runCatching { GecitVpnService.start(ctx) }
    }
}
