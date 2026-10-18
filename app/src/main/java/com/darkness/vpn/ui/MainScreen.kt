package com.darkness.vpn.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings as SysSettings
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkness.vpn.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf(Settings.load(ctx)) }
    val status by VpnState.flow.collectAsState()

    /** Ayarı değiştirir, kaydeder; bağlıysa yeniden bağlanır. */
    fun update(reload: Boolean = true, f: (Settings) -> Settings) {
        val n = f(s)
        if (n == s) return
        s = n
        n.save(ctx)
        if (reload) GecitVpnService.reload(ctx)
    }

    var dialog by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var dpiMessage by remember { mutableStateOf<String?>(null) }
    var siteMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (s.autoConnectOnOpen && status !is VpnStatus.On && status !is VpnStatus.Connecting) onConnect()
    }

    Column(
        Modifier.fillMaxSize().background(C.bg).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Başlık
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Darkness VPN", color = C.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val scopeLabel = if (s.appMode == AppMode.ALL || (s.appMode == AppMode.ONLY && s.apps.isEmpty())) "Tüm telefon"
            else if (s.appMode == AppMode.ONLY) "${s.apps.size} uygulama" else "Seçtiklerim hariç"
            Text(scopeLabel, color = C.sub, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(C.card).padding(horizontal = 14.dp, vertical = 8.dp))
        }

        // Güç düğmesi
        PowerCard(status, s) { if (status is VpnStatus.On || status is VpnStatus.Connecting) onDisconnect() else onConnect() }

        // Bağlantı türü
        Section("Bağlantı türü", "Bağlanmak için kullanılacak yol.") {
            MethodCard(
                letter = "D", letterFg = C.green, letterBg = C.greenBg,
                title = "DPI aşma", tag = "YENİ",
                desc = "Sunucusuz · ByeDPI · engeli paketleri parçalayarak aşar, hız kaybı yok, IP değişmez",
                selected = s.method == Method.DPI, recommended = true,
                modifier = Modifier.fillMaxWidth()
            ) { update { it.copy(method = Method.DPI) } }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.height(IntrinsicSize.Max)) {
                MethodCard("W", C.orange, C.orangeBg, "WARP", null,
                    "Ücretsiz · Cloudflare · engelleri aşar, IP Türkiye kalır",
                    s.method == Method.WARP, false, Modifier.weight(1f).fillMaxHeight()
                ) { update { it.copy(method = Method.WARP) } }
                Spacer(Modifier.width(12.dp))
                MethodCard("S", C.accent, C.inner, "Kendi sunucum", null,
                    "WireGuard ya da VLESS / VMess / Trojan / SS",
                    s.method == Method.CUSTOM, false, Modifier.weight(1f).fillMaxHeight()
                ) { update { it.copy(method = Method.CUSTOM) } }
            }
        }

        // Yönteme özel ayarlar
        when (s.method) {
            Method.DPI -> Section("DPI aşma ayarı",
                "Sağlayıcını seç ya da \"Otomatik bul\" de: yöntemler engelli sitelerde tek tek denenir, açan kaydedilir.") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Presets.list.forEach { p -> Chip(p.title, s.presetId == p.id) { update { it.copy(presetId = p.id) } } }
                    Chip("Gelişmiş", s.presetId == Presets.CUSTOM_ID) { dialog = "advanced" }
                }
                Spacer(Modifier.height(12.dp))
                val name = if (s.presetId == Presets.CUSTOM_ID) "Gelişmiş" else Presets.byId(s.presetId).title
                Text("Kullanılan: $name · ${s.dpiArgs}", color = C.sub, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(if (busy == "dpi") "Aranıyor…" else "Otomatik bul", enabled = busy == null) {
                    busy = "dpi"; dpiMessage = "Başlıyor…"
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            Scanner.findStrategy(ctx, s.blockedSites) { m -> scope.launch { dpiMessage = m } }
                        }
                        r.preset?.let { p ->
                            if (Presets.list.any { it.id == p.id }) update { it.copy(presetId = p.id) }
                            else update { it.copy(presetId = Presets.CUSTOM_ID, customArgs = p.args) }
                        }
                        dpiMessage = r.message
                        busy = null
                    }
                }
                dpiMessage?.let { Spacer(Modifier.height(8.dp)); Text(it, color = C.accent, fontSize = 13.sp) }
                Spacer(Modifier.height(12.dp)); Divider()
                SwitchRow("DPI işe yaramazsa WARP'a geç", "DPI ile açılmayan siteler WARP üzerinden geçer", s.warpFallback) { v ->
                    update { it.copy(warpFallback = v) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("Doğrudan", C.text)
                    Arrow(); Pill("DPI aşma", C.green)
                    if (s.warpFallback) { Arrow(); Pill("WARP", C.orange) }
                }
                if (s.warpFallback && s.warpSites.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("WARP'tan geçen: " + s.warpSites.joinToString(", "), color = C.sub, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                OutlineButton("Gelişmiş ayarlar") { dialog = "advanced" }
            }
            Method.WARP -> Section("WARP", "Cloudflare'in ücretsiz WARP ağı. İlk bağlantıda hesap otomatik açılır.") {
                val acc = remember(status) { Warp.account(ctx) }
                Text(if (acc != null) "Hesap hazır · ${acc.v4}" else "Hesap henüz yok, bağlanınca açılacak.",
                    color = C.sub, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlineButton("Gelişmiş ayarlar") { dialog = "advanced" }
            }
            Method.CUSTOM -> Section("Kendi sunucum", "Bağlantıyı (vless://, vmess://, trojan://, ss://) ya da WireGuard .conf içeriğini yapıştır.") {
                var link by remember { mutableStateOf(s.customLink) }
                val parsed = remember(link) { runCatching { ShareLink.parse(link) } }
                OutlinedTextField(
                    value = link, onValueChange = { link = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    colors = gecitFieldColors(), shape = RoundedCornerShape(14.dp),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    placeholder = { Text("vless://…") }
                )
                Spacer(Modifier.height(8.dp))
                if (link.isNotBlank()) Text(
                    parsed.fold({ "✓ " + it.summary }, { "✗ " + (it.message ?: "Geçersiz") }),
                    color = if (parsed.isSuccess) C.green else C.red, fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Kaydet", enabled = link != s.customLink && (link.isBlank() || parsed.isSuccess)) {
                    update { it.copy(customLink = link.trim()) }
                }
            }
        }

        // Trafik yönlendirme
        Section("Hangi trafik tünelden geçsin?") {
            RadioRow("Sadece engelli siteler",
                "Listedeki siteler tünelden geçer, gerisi doğrudan. En hızlı ve en az veri.",
                s.routeMode == RouteMode.BLOCKED, badge = "ÖNERİLEN") { update { it.copy(routeMode = RouteMode.BLOCKED) } }
            Divider()
            RadioRow("Her şey", "Bütün trafik seçili yoldan geçer.", s.routeMode == RouteMode.ALL) {
                update { it.copy(routeMode = RouteMode.ALL) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${s.blockedSites.size} site engelli listesinde · ", color = C.sub, fontSize = 14.sp)
                Text("düzenle", color = C.accent, fontSize = 14.sp, modifier = Modifier.clickable { dialog = "sites" })
                Text(" · ", color = C.sub, fontSize = 14.sp)
                Text(if (busy == "scan") "taranıyor…" else "tara", color = C.accent, fontSize = 14.sp,
                    modifier = Modifier.clickable(enabled = busy == null) {
                        busy = "scan"; siteMessage = "Başlıyor…"
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                Scanner.scanSites(ctx, s) { m -> scope.launch { siteMessage = m } }
                            }
                            if (s.method == Method.DPI) update { it.copy(warpSites = r.needWarp.sorted()) }
                            siteMessage = "Doğrudan açılan: ${r.direct.size} · DPI ile açılan: ${r.viaDpi.size} · " +
                                "açılmayan: ${r.needWarp.size}" + if (r.needWarp.isNotEmpty() && s.method == Method.DPI && s.warpFallback)
                                " (WARP'a yönlendirildi)" else ""
                            busy = null
                        }
                    })
            }
            siteMessage?.let { Spacer(Modifier.height(6.dp)); Text(it, color = C.accent, fontSize = 13.sp) }
        }

        // Uygulamalar
        Section("Telefonun tamamı", "VPN açılınca hangi uygulamalar tünelden geçsin?") {
            RadioRow("Bütün uygulamalar", "Telefondaki her şey.", s.appMode == AppMode.ALL) { update { it.copy(appMode = AppMode.ALL) } }
            Divider()
            RadioRow("Sadece seçtiklerim", "Yalnızca seçtiğin uygulamalar geçer.", s.appMode == AppMode.ONLY) {
                update { it.copy(appMode = AppMode.ONLY) }
            }
            Divider()
            RadioRow("Seçtiklerim hariç", "Bankalar gibi seçtiklerin VPN'siz kalır.", s.appMode == AppMode.EXCEPT) {
                update { it.copy(appMode = AppMode.EXCEPT) }
            }
            if (s.appMode != AppMode.ALL) {
                Spacer(Modifier.height(10.dp))
                OutlineButton("Uygulama seç · ${s.apps.size} seçili") { dialog = "apps" }
                if (s.appMode == AppMode.ONLY && s.apps.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Hiç uygulama seçilmedi; seçene kadar bütün uygulamalar geçer.", color = C.orange, fontSize = 13.sp)
                }
            }
        }

        // Genel ayarlar
        Section("Ayarlar") {
            SwitchRow("Açılışta otomatik bağlan", "Uygulama açılınca VPN de açılır", s.autoConnectOnOpen) { v ->
                update(reload = false) { it.copy(autoConnectOnOpen = v) }
            }
            Divider()
            SwitchRow("Telefon açılınca bağlan", "Yeniden başlatmadan sonra VPN kendiliğinden açılır", s.connectOnBoot) { v ->
                update(reload = false) { it.copy(connectOnBoot = v) }
            }
            Divider()
            Row(Modifier.fillMaxWidth().clickable {
                ctx.startActivity(Intent(SysSettings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.padding(vertical = 12.dp)) {
                Column {
                    Text("Her zaman açık VPN", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Sistem ayarlarından Darkness VPN'i \"her zaman açık\" yap; koparsa internet kesilsin istersen \"VPN olmadan bağlantıları engelle\"yi de aç.",
                        color = C.sub, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }

        // Bağlantı testi
        ConnectionTest(status)

        Text("ByeDPI · Xray · hev-socks5-tunnel", color = C.sub, fontSize = 13.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }

    when (dialog) {
        "apps" -> AppPickerDialog(s.apps, { dialog = null }) { sel -> update { it.copy(apps = sel) }; dialog = null }
        "sites" -> SiteListDialog(
            "Engelli siteler", "Her satıra bir alan adı yaz (ör. discord.com). Alt alan adları da kapsanır.",
            s.blockedSites, { dialog = null }
        ) { text ->
            val list = text.lines().mapNotNull { Settings.normalizeDomain(it) }.distinct()
            update { it.copy(blockedSites = list, warpSites = it.warpSites.filter { w -> w in list }) }
            dialog = null
        }
        "advanced" -> AdvancedDialog(
            AdvancedValues(s.dpiArgs, s.dns, s.warpEndpoint, s.blockQuic),
            onDismiss = { dialog = null },
            onSave = { v ->
                update {
                    val argsChanged = v.args.trim() != it.dpiArgs
                    it.copy(
                        presetId = if (argsChanged) Presets.CUSTOM_ID else it.presetId,
                        customArgs = if (argsChanged) v.args.trim() else it.customArgs,
                        dns = v.dns.ifBlank { "1.1.1.1" }, warpEndpoint = v.warpEndpoint.ifBlank { Warp.DEFAULT_ENDPOINT },
                        blockQuic = v.blockQuic,
                    )
                }
                dialog = null
            },
            onResetWarp = {
                Warp.clear(ctx)
                Toast.makeText(ctx, "WARP hesabı silindi; bir sonraki bağlantıda yenisi açılır.", Toast.LENGTH_LONG).show()
                GecitVpnService.reload(ctx)
            },
            onShowLog = { dialog = "log" },
        )
        "log" -> LogDialog { dialog = null }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(text, color = color, fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(C.inner).padding(horizontal = 12.dp, vertical = 8.dp))
}

@Composable
private fun Arrow() = Text("  →  ", color = C.sub)

@Composable
private fun MethodCard(
    letter: String, letterFg: Color, letterBg: Color, title: String, tag: String?, desc: String,
    selected: Boolean, recommended: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(C.inner)
            .border(if (selected) 2.dp else 1.dp, if (selected) C.accent else C.border, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(letterBg), contentAlignment = Alignment.Center) {
                Text(letter, color = letterFg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            if (recommended) { Badge("ÖNERİLEN"); Spacer(Modifier.width(8.dp)) }
            Radio(selected)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = C.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            if (tag != null) { Spacer(Modifier.width(8.dp)); Badge(tag, fg = C.bg, bg = C.green) }
        }
        Spacer(Modifier.height(4.dp))
        Text(desc, color = C.sub, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun PowerCard(status: VpnStatus, s: Settings, onClick: () -> Unit) {
    val on = status is VpnStatus.On
    val connecting = status is VpnStatus.Connecting
    val ring = when {
        on -> C.green
        connecting -> C.accent
        status is VpnStatus.Error -> C.red
        else -> C.border
    }
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a"
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.card)
            .border(1.dp, C.border, RoundedCornerShape(28.dp)).padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(180.dp).clip(CircleShape).background(if (on) C.greenBg else C.inner)
                .border(3.dp, if (connecting) ring.copy(alpha = pulse) else ring, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val iconColor = if (on) C.green else if (connecting) C.accent else C.sub
            Canvas(Modifier.size(64.dp)) {
                val w = size.width
                val stroke = w * 0.11f
                drawArc(iconColor, -60f, 300f, false, topLeft = Offset(stroke, stroke + w * 0.06f),
                    size = androidx.compose.ui.geometry.Size(w - 2 * stroke, w - 2 * stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(iconColor, Offset(w / 2, stroke / 2), Offset(w / 2, w * 0.45f), stroke, StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(20.dp))
        val title = when (status) {
            is VpnStatus.On -> "Bağlı"
            is VpnStatus.Connecting -> "Bağlanıyor…"
            is VpnStatus.Error -> "Bağlanamadı"
            VpnStatus.Off -> "Kapalı"
        }
        Text(title, color = C.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        val via = when (s.method) {
            Method.DPI -> if (s.warpFallback) "DPI aşma + WARP" else "DPI aşma"
            Method.WARP -> "WARP"
            Method.CUSTOM -> "Kendi sunucum"
        }
        val sub = when (status) {
            is VpnStatus.On -> "$via · dokun, kes"
            is VpnStatus.Connecting -> status.step
            is VpnStatus.Error -> status.message
            VpnStatus.Off -> "Dokun, bağlan · $via"
        }
        Text(sub, color = if (status is VpnStatus.Error) C.red else C.sub, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ConnectionTest(status: VpnStatus) {
    val scope = rememberCoroutineScope()
    var direct by remember { mutableStateOf<String?>(null) }
    var directSub by remember { mutableStateOf("VPN'siz") }
    var vpn by remember { mutableStateOf<String?>(null) }
    var vpnSub by remember { mutableStateOf("VPN'li") }
    var running by remember { mutableStateOf(false) }

    Section("Bağlantı testi") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IpBox(direct ?: "—", directSub, Modifier.weight(1f))
            Text("  →  ", color = C.sub, fontSize = 18.sp)
            IpBox(vpn ?: "—", vpnSub, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        OutlineButton(if (running) "Test ediliyor…" else "Test et", enabled = !running) {
            running = true
            scope.launch {
                val d = withContext(Dispatchers.IO) { Net.ipInfo(null) }
                direct = d?.ip ?: "bağlantı yok"; directSub = "VPN'siz · ${d?.country ?: "?"}"
                if (status is VpnStatus.On) {
                    val v = withContext(Dispatchers.IO) { Net.ipInfo(XrayConfig.SOCKS_PORT) }
                    vpn = v?.ip ?: "açılmadı"
                    vpnSub = if (v != null) "${v.country} · ✓ çalışıyor" else "✗ tünel yanıt vermedi"
                } else {
                    vpn = "kapalı"; vpnSub = "Önce bağlan"
                }
                running = false
            }
        }
    }
}

@Composable
private fun IpBox(main: String, sub: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(C.inner).padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(main, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(sub, color = C.sub, fontSize = 12.sp, maxLines = 1)
    }
}
