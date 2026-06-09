package com.opencode.android.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * opencode 一键安装管理器
 *
 * 直接下载 Hope2333/opencode-termux 的 ARM64 deb 包，
 * 解包提取 opencode 二进制到 files/bin/opencode。
 *
 * 整个过程纯 Java/Kotlin，无系统依赖。
 */
object OpenCodeInstaller {
    private const val TAG = "OpenCodeInstaller"

    // Hope2333/opencode-termux latest release (硬编码，后续可改为 API 动态获取)
    private const val OPENCODE_DEB_URL =
        "https://github.com/Hope2333/opencode-termux/releases/download/Push260522/opencode_1.16.2_aarch64.deb"

    enum class State { IDLE, DOWNLOADING, EXTRACTING, DONE, FAILED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _percent = MutableStateFlow(0)
    val percent: StateFlow<Int> = _percent.asStateFlow()

    fun opencodeBin(context: Context) = File(context.filesDir, "bin/opencode")

    fun isInstalled(context: Context) = opencodeBin(context).canExecute()

    suspend fun install(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val binDir = File(context.filesDir, "bin")
            binDir.mkdirs()

            // —— 1. 下载 deb (~15MB) ——
            _state.value = State.DOWNLOADING
            _message.value = "正在下载 opencode v1.16.2 (~15MB) …"
            _percent.value = 0

            val debFile = File(context.filesDir, "opencode.deb")
            download(OPENCODE_DEB_URL, debFile) { pct ->
                _percent.value = (pct * 0.8).toInt()
            }

            // —— 2. 从 deb 解压 opencode 二进制 ——
            _state.value = State.EXTRACTING
            _message.value = "正在解压 …"
            _percent.value = 80

            extractOpenCodeFromDeb(debFile, opencodeBin(context))
            debFile.delete()
            opencodeBin(context).setExecutable(true, false)

            _percent.value = 100
            _state.value = State.DONE
            _message.value = "✅ 安装完成！opencode v1.16.2"

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            _state.value = State.FAILED
            _message.value = "安装失败: ${e.message}"
            Result.failure(e)
        }
    }

    // ========== HTTP 下载 ==========

    private fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        // GitHub release 下载需要处理 redirect
        var currentUrl = url
        var redirects = 0

        while (redirects < 5) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 300000
            conn.setRequestProperty("User-Agent", "OpenCode-Android/1.0")
            conn.instanceFollowRedirects = false

            val code = conn.responseCode
            if (code == 301 || code == 302) {
                currentUrl = conn.getHeaderField("Location")
                redirects++
                conn.disconnect()
                continue
            }
            if (code != 200) throw IOException("HTTP $code: ${conn.responseMessage}")

            val total = conn.contentLengthLong
            var downloaded = 0L

            conn.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(65536)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) onProgress((downloaded * 100 / total).toInt())
                    }
                }
            }
            conn.disconnect()
            return
        }
        throw IOException("Too many redirects")
    }

    // ========== deb 解包 ==========

    /**
     * 从 deb 包中提取 opencode 二进制
     *
     * deb 格式：
     *   1. "!<arch>\n" (8 bytes)
     *   2. debian-binary (ar entry)
     *   3. control.tar.xz (ar entry)
     *   4. data.tar.xz   (ar entry) ← 我们要的
     *
     * ar 条目格式（BSD）：
     *   "#1/<length>" + 空格补齐16字节 → 后跟 length 字节数据
     *   或 SVR4/GNU：16字节文件名 + 12字节 mtime + 6字节 uid + ...
     */
    private fun extractOpenCodeFromDeb(debFile: File, destBin: File) {
        val data = debFile.readBytes()
        var pos = 0

        // 跳过 ar magic
        if (data.size < 8 || !data.copyOfRange(0, 8).decodeToString().startsWith("!<arch>")) {
            throw IOException("Not a valid deb/ar file")
        }
        pos = 8

        // 遍历 ar 条目，找 data.tar.xz
        while (pos < data.size) {
            // ar 条目头 60 字节
            if (pos + 60 > data.size) break
            val header = String(data, pos, 60, Charsets.ISO_8859_1)
            pos += 60

            // 解析文件名和大小
            val rawName = header.substring(0, 16).trim()
            val rawSize = header.substring(48, 58).trim()

            val (name, size) = when {
                rawName.startsWith("#1/") -> {
                    val nameLen = rawName.removePrefix("#1/").trim().toInt()
                    val name = data.copyOfRange(pos, pos + nameLen).decodeToString().trimEnd('\u0000')
                    pos += nameLen
                    val size = rawSize.toLong() - nameLen
                    name to size
                }
                else -> rawName to rawSize.toLong()
            }

            if (name == "data.tar.xz") {
                // 找到 data.tar.xz，搜索其中的 opencode 二进制
                val xzData = data.copyOfRange(pos, (pos + size).toInt())
                extractFromTarXz(xzData, destBin)
                return
            }

            // 跳到下一个条目（2字节对齐）
            pos += size.toInt()
            if (pos % 2 != 0) pos++
        }
        throw IOException("data.tar.xz not found in deb")
    }

    private fun extractFromTarXz(xzData: ByteArray, dest: File) {
        org.apache.commons.compress.archivers.tar.TarArchiveInputStream(
            XZCompressorInputStream(ByteArrayInputStream(xzData))
        ).use { tarIn ->
            val candidates = mutableListOf<String>()
            var entry = tarIn.nextEntry
            while (entry != null) {
                val name = entry.name.removePrefix("./").trimStart('/')
                if (!entry.isDirectory) {
                    candidates.add(name)
                    // 匹配任何名为 "opencode" 的可执行文件（不在 node_modules 中）
                    val fileName = name.substringAfterLast('/')
                    if (fileName == "opencode" && !name.contains("node_modules")) {
                        FileOutputStream(dest).use { fos ->
                            val buf = ByteArray(8192)
                            var n: Int
                            while (tarIn.read(buf).also { n = it } != -1) {
                                fos.write(buf, 0, n)
                            }
                        }
                        Log.i(TAG, "Extracted opencode ($fileName) from: $name (${dest.length()} bytes)")
                        return
                    }
                }
                entry = tarIn.nextEntry
            }
            throw IOException("opencode not found in data.tar.xz. Files found: ${candidates.take(20)}")
        }
    }
}