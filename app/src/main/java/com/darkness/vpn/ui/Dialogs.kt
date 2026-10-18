package com.darkness.vpn.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.darkness.vpn.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppItem(val pkg: String, val label: String, val system: Boolean)

@Composable
private fun FullDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(C.bg).systemBarsPadding().imePadding().padding(16.dp),
            content = content
        )
    }
}

@Composable
fun AppPickerDialog(initial: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var selected by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val launchable = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName }.toSet()
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != ctx.packageName }
                .map { AppItem(it.packageName, it.loadLabel(pm).toString(), it.packageName !in launchable) }
                .sortedWith(compareBy({ it.pkg !in initial }, { it.label.lowercase() }))
        }
    }

    FullDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Uygulama seç", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${selected.size} seçili", color = C.accent)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Ara") }, modifier = Modifier.fillMaxWidth(), colors = gecitFieldColors(),
            shape = RoundedCornerShape(14.dp)
        )
        SwitchRow("Sistem uygulamalarını göster", null, showSystem) { showSystem = it }
        val list = apps
        if (list == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.accent) }
        } else {
            val q = query.trim().lowercase()
            val shown = list.filter { (showSystem || !it.system || it.pkg in selected) &&
                (q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q)) }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.pkg }) { app ->
                    val checked = app.pkg in selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { selected = if (checked) selected - app.pkg else selected + app.pkg }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app.pkg)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, color = C.text, fontSize = 15.sp)
                            Text(app.pkg, color = C.sub, fontSize = 11.sp)
                        }
                        Checkbox(checked, { selected = if (checked) selected - app.pkg else selected + app.pkg },
                            colors = CheckboxDefaults.colors(checkedColor = C.accent, checkmarkColor = C.bg))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Box(Modifier.weight(1f)) { OutlineButton("Vazgeç", onClick = onDismiss) }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) { PrimaryButton("Kaydet") { onSave(selected) } }
        }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val pm = LocalContext.current.packageManager
    var img by remember(pkg) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pkg) {
        img = withContext(Dispatchers.IO) {
            runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    val i = img
    if (i != null) Image(i, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
    else Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(C.inner))
}

/** Satır satır alan adı listesi düzenleyici. */
@Composable
fun SiteListDialog(title: String, hint: String, initial: List<String>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial.joinToString("\n")) }
    FullDialog(onDismiss) {
        Text(title, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(hint, color = C.sub, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth().weight(1f),
            colors = gecitFieldColors(), shape = RoundedCornerShape(14.dp),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Box(Modifier.weight(1f)) { OutlineButton("Vazgeç", onClick = onDismiss) }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) { PrimaryButton("Kaydet") { onSave(text) } }
        }
    }
}

data class AdvancedValues(
    val args: String, val dns: String, val warpEndpoint: String, val blockQuic: Boolean,
)

@Composable
fun AdvancedDialog(
    initial: AdvancedValues,
    onDismiss: () -> Unit,
    onSave: (AdvancedValues) -> Unit,
    onResetWarp: () -> Unit,
    onShowLog: () -> Unit,
) {
    var v by remember { mutableStateOf(initial) }
    FullDialog(onDismiss) {
        Text("Gelişmiş ayarlar", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("ByeDPI argümanları", color = C.text, fontWeight = FontWeight.Medium)
            Text("Değiştirirsen ayar \"Gelişmiş\" olarak kaydedilir. Örnek: -d1 -s1+s -r1+s -a1", color = C.sub, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = v.args, onValueChange = { v = v.copy(args = it) }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                colors = gecitFieldColors(), shape = RoundedCornerShape(14.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = v.dns, onValueChange = { v = v.copy(dns = it.trim()) }, singleLine = true,
                label = { Text("Şifreli DNS sunucusu (DoH, IP)") }, modifier = Modifier.fillMaxWidth(),
                colors = gecitFieldColors(), shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = v.warpEndpoint, onValueChange = { v = v.copy(warpEndpoint = it.trim()) }, singleLine = true,
                label = { Text("WARP uç noktası (IP:port)") }, modifier = Modifier.fillMaxWidth(),
                colors = gecitFieldColors(), shape = RoundedCornerShape(14.dp)
            )
            Text("Bağlanmazsa dene: 162.159.193.10:2408, 162.159.192.1:500, 188.114.97.1:4500", color = C.sub, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            SwitchRow("QUIC'i kapat", "DPI aşmada tarayıcılar TCP'ye geçer; engelli siteler daha iyi açılır", v.blockQuic) {
                v = v.copy(blockQuic = it)
            }
            Spacer(Modifier.height(8.dp))
            OutlineButton("WARP hesabını yenile", onClick = onResetWarp)
            Spacer(Modifier.height(10.dp))
            OutlineButton("Kayıtları göster", onClick = onShowLog)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Box(Modifier.weight(1f)) { OutlineButton("Vazgeç", onClick = onDismiss) }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) { PrimaryButton("Kaydet") { onSave(v) } }
        }
    }
}

@Composable
fun LogDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(Engine.readLog(ctx)) }
    FullDialog(onDismiss) {
        Text("Kayıtlar", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).padding(12.dp)) {
            SelectionContainer {
                Text(
                    text, color = C.sub, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Box(Modifier.weight(1f)) { OutlineButton("Yenile") { text = Engine.readLog(ctx) } }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) { PrimaryButton("Kapat", onClick = onDismiss) }
        }
    }
}
