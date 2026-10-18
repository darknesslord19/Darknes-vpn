package com.darkness.vpn

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import com.darkness.vpn.ui.C
import com.darkness.vpn.ui.Themes
import com.darkness.vpn.ui.gecitColors
import com.darkness.vpn.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) GecitVpnService.start(this)
        else VpnState.status.value = VpnStatus.Error("VPN izni verilmedi.")
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        Themes.apply(this)
        setContent {
            val bar = C.bg.toArgb()
            SideEffect { window.statusBarColor = bar; window.navigationBarColor = bar }
            MaterialTheme(colorScheme = gecitColors()) {
                MainScreen(onConnect = ::connect, onDisconnect = { GecitVpnService.stop(this) })
            }
        }
        if (intent?.getBooleanExtra("connect", false) == true) connect()
    }

    private fun connect() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnPermission.launch(prepare) else GecitVpnService.start(this)
    }
}
