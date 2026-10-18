package com.darkness.vpn

import android.content.Context
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/** ciadpi (ByeDPI) ve xray çalıştırılabilir dosyalarını yönetir. */
object Engine {
    private fun bin(ctx: Context, name: String) = File(ctx.applicationInfo.nativeLibraryDir, name)

    fun logFile(ctx: Context) = File(ctx.filesDir, "gecit.log")

    @Synchronized
    fun log(ctx: Context, line: String) {
        val f = logFile(ctx)
        if (f.length() > 256 * 1024) f.writeText(f.readLines().takeLast(400).joinToString("\n") + "\n")
        f.appendText(line.trimEnd() + "\n")
    }

    fun readLog(ctx: Context, lines: Int = 300): String =
        logFile(ctx).takeIf { it.exists() }?.readLines()?.takeLast(lines)?.joinToString("\n") ?: "(kayıt yok)"

    /** ByeDPI argümanlarını kabaca böler (tırnak desteğiyle). */
    fun splitArgs(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        for (c in s) {
            when {
                quote != null && c == quote -> quote = null
                quote == null && (c == '"' || c == '\'') -> quote = c
                quote == null && c.isWhitespace() -> if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    fun startByeDpi(ctx: Context, args: String, port: Int, tag: String = "byedpi"): Process {
        val cmd = listOf(bin(ctx, "libciadpi.so").absolutePath, "-i", "127.0.0.1", "-p", port.toString()) + splitArgs(args)
        log(ctx, "[$tag] başlat: " + cmd.drop(1).joinToString(" "))
        return launch(ctx, cmd, tag, emptyMap())
    }

    fun startXray(ctx: Context, configJson: String, name: String = "xray"): Process {
        val cfg = File(ctx.filesDir, "$name.json").apply { writeText(configJson) }
        val cmd = listOf(bin(ctx, "libxray.so").absolutePath, "run", "-c", cfg.absolutePath)
        log(ctx, "[$name] başlat")
        return launch(ctx, cmd, name, mapOf("XRAY_LOCATION_ASSET" to ctx.filesDir.absolutePath))
    }

    private fun launch(ctx: Context, cmd: List<String>, tag: String, env: Map<String, String>): Process {
        val pb = ProcessBuilder(cmd).redirectErrorStream(true).directory(ctx.filesDir)
        pb.environment().putAll(env)
        val p = pb.start()
        thread(isDaemon = true, name = "log-$tag") {
            runCatching {
                p.inputStream.bufferedReader().forEachLine { log(ctx, "[$tag] $it") }
            }
        }
        return p
    }

    /** Yerel bir portun dinlemeye başlamasını bekler. */
    fun waitPort(port: Int, timeoutMs: Long = 5000, alive: () -> Boolean = { true }): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (!alive()) return false
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300); return true }
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        return false
    }

    fun stop(p: Process?) {
        if (p == null) return
        runCatching {
            p.destroy()
            if (!waitFor(p, 1500)) p.destroyForcibly()
        }
    }

    private fun waitFor(p: Process, ms: Long): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (!p.isAlive) return true
            Thread.sleep(50)
        }
        return !p.isAlive
    }

    fun version(ctx: Context): String = runCatching {
        val p = ProcessBuilder(bin(ctx, "libxray.so").absolutePath, "version").redirectErrorStream(true).start()
        val first = p.inputStream.bufferedReader().readLine() ?: ""
        p.destroy()
        first.split(' ').take(2).joinToString(" ")
    }.getOrDefault("Xray")
}
