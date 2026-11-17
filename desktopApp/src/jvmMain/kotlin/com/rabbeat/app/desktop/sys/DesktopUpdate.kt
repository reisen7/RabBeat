package com.rabbeat.app.desktop.sys
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*

import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** GitHub Releases check. A failed request is not "already up to date". */
object DesktopUpdate {
    val CURRENT: String get() = System.getProperty("rabbeat.version") ?: "0.0.0-dev"

    private const val API_PATH = "/repos/reisen7/RabBeat/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/reisen7/RabBeat/releases"
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    data class Available(val tag: String, val notes: String, val pageUrl: String, val assetUrl: String?)

    sealed interface Check {
        data class Update(val release: Available) : Check
        data object Latest : Check
        data object Failed : Check
    }

    /** Direct GitHub first, then public mirrors when api.github.com is blocked or rate-limited. */
    private data class Route(val api: String, val web: String)

    private val routes = listOf(
        Route("https://api.github.com", ""),
        Route("https://gh-proxy.com/https://api.github.com", "https://gh-proxy.com/"),
        Route("https://ghproxy.net/https://api.github.com", "https://ghproxy.net/"),
    )

    suspend fun check(): Check = withContext(Dispatchers.IO) {
        var lastError = "no attempt"
        for (route in routes) {
            val result = runCatching { fetch(route) }.onFailure {
                lastError = it.message ?: it.javaClass.simpleName
            }.getOrNull()
            if (result != null) {
                DesktopLog.i("Update", "check result=$result via ${route.api}")
                return@withContext result
            }
            DesktopLog.w("Update", "check via ${route.api} failed: $lastError")
        }
        Check.Failed
    }

    private fun fetch(route: Route): Check? {
        val response = http.send(
            HttpRequest.newBuilder(URI("${route.api}$API_PATH"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "RabBeat")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        if (response.statusCode() != 200) {
            error("http ${response.statusCode()}")
        }
        val rel = json.parseToJsonElement(response.body()).jsonObject
        val tag = rel["tag_name"]?.jsonPrimitive?.content ?: return null
        if (!isNewerRelease(tag, CURRENT)) return Check.Latest
        val page = rel["html_url"]?.jsonPrimitive?.content ?: RELEASES_PAGE
        val assets = rel["assets"]?.jsonArray.orEmpty().mapNotNull {
            it.jsonObject["browser_download_url"]?.jsonPrimitive?.content
        }
        return Check.Update(
            Available(
                tag = tag,
                notes = rel["body"]?.jsonPrimitive?.content.orEmpty(),
                pageUrl = route.web + page,
                assetUrl = pickWindowsAsset(assets)?.let { route.web + it },
            ),
        )
    }

    fun browse(url: String) = runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }

    /** Packaged RabBeat.exe, whether it was installed or unzipped. */
    fun packagedExe(): File? {
        val path = ProcessHandle.current().info().command().orElse("")
        val exe = File(path)
        if (!exe.isFile || !exe.name.equals("RabBeat.exe", ignoreCase = true)) return null
        val home = exe.parentFile ?: return null
        val packaged = File(home, "app").isDirectory && File(home, "runtime").isDirectory
        return if (packaged) exe else null
    }

    /**
     * The running exe when it sits in the directory recorded by the installer.
     * A portable unzip is not this, so an update does not create a second copy.
     */
    fun installedExe(): File? {
        val exe = packagedExe() ?: return null
        val home = exe.parentFile ?: return null
        val installed = readInstallLocations().any { sameDirectory(home, File(it)) }
        return if (installed) exe else null
    }

    suspend fun downloadInstaller(
        url: String,
        onProgress: (read: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val response = http.send(
            HttpRequest.newBuilder(URI(url))
                .header("User-Agent", "RabBeat")
                .header("Accept", "application/octet-stream")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        if (response.statusCode() !in 200..299) error("http ${response.statusCode()}")
        val total = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
        val dest = File(System.getProperty("java.io.tmpdir"), "RabBeat-update-setup.exe")
        response.body().use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var readTotal = 0L
                var lastReport = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    readTotal += n
                    val now = System.currentTimeMillis()
                    if (now - lastReport >= 200) {
                        lastReport = now
                        withContext(Dispatchers.Main) { onProgress(readTotal, total) }
                    }
                }
                withContext(Dispatchers.Main) { onProgress(readTotal, if (total > 0) total else readTotal) }
            }
        }
        dest
    }

    /**
     * Starts a helper that waits until this process exits, then runs the
     * installer with no directory page and opens the same RabBeat.exe again.
     */
    fun launchInstalledUpdate(installer: File, relaunch: File) {
        val script = File(System.getProperty("java.io.tmpdir"), "rabbeat-update.ps1")
        val text = silentInstallScript(
            pid = ProcessHandle.current().pid(),
            installer = installer.absolutePath,
            relaunch = relaunch.absolutePath,
        )
        script.outputStream().use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            out.write(text.toByteArray(Charsets.UTF_8))
        }
        ProcessBuilder(
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-WindowStyle", "Hidden",
            "-File", script.absolutePath,
        ).apply {
            redirectOutput(ProcessBuilder.Redirect.DISCARD)
            redirectError(ProcessBuilder.Redirect.DISCARD)
        }.start()
    }
}

/** True when [tag] (like "v0.0.5") is a higher version than [current]. */
internal fun isNewerRelease(tag: String, current: String): Boolean {
    fun parts(v: String) = v.removePrefix("v").removePrefix("V")
        .substringBefore('+')
        .split('.', '-')
        .mapNotNull { it.toIntOrNull() }
    val remote = parts(tag)
    val local = parts(current)
    if (remote.isEmpty()) return false
    for (i in 0 until maxOf(remote.size, local.size)) {
        val x = remote.getOrElse(i) { 0 }
        val y = local.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

internal fun silentInstallScript(pid: Long, installer: String, relaunch: String): String {
    fun quote(path: String) = path.replace("'", "''")
    return $$"""
        $ErrorActionPreference = 'SilentlyContinue'
        while (Get-Process -Id $$pid -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 1 }
        Start-Process -FilePath '$${quote(installer)}' -ArgumentList '/quiet','/norestart' -Wait
        Start-Process -FilePath '$${quote(relaunch)}'
        Remove-Item -LiteralPath $PSCommandPath -Force
    """.trimIndent()
}

internal fun parseInstallLocations(regOutput: String): List<String> =
    Regex("""(?m)^\s*InstallLocation\s+REG_SZ\s+(.+?)\s*$""")
        .findAll(regOutput)
        .map { it.groupValues[1].trim().trimEnd('\\', '/') }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

private fun readInstallLocations(): List<String> = runCatching {
    val proc = ProcessBuilder(
        "reg", "query",
        "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
        "/f", "RabBeat",
        "/s",
    ).redirectErrorStream(true).start()
    val text = proc.inputStream.bufferedReader(Charset.defaultCharset()).readText()
    if (!proc.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) proc.destroyForcibly()
    parseInstallLocations(text)
}.getOrDefault(emptyList())

private fun sameDirectory(a: File, b: File): Boolean = runCatching {
    a.canonicalFile == b.canonicalFile
}.getOrDefault(false)

internal fun pickWindowsAsset(urls: List<String>): String? =
    urls.firstOrNull { it.endsWith("-windows-setup.exe", ignoreCase = true) }
        ?: urls.firstOrNull { it.endsWith("-windows-portable.zip", ignoreCase = true) }
        ?: urls.firstOrNull {
            val name = it.substringAfterLast('/').lowercase()
            (name.endsWith(".exe") || name.endsWith(".msi") || name.endsWith(".zip")) &&
                !name.endsWith(".sha256")
        }
