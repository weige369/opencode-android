package com.opencode.android.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 内置 Node.js + opencode 一键安装管理器
 *
 * 用户只需点一个按钮，自动完成：
 *   1. 下载 Node.js ARM64 tar.xz (~20MB)
 *   2. 用纯 Java (Apache Commons Compress) 解压到 files/node/
 *   3. npm install -g opencode
 */
object NodeRuntime {
    private const val TAG = "NodeRuntime"
    private const val NODE_VERSION = "22.12.0"
    private const val NODE_URL =
        "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-arm64.tar.xz"
    private const val NPM_REGISTRY = "https://registry.npmmirror.com"

    enum class State { IDLE, DOWNLOADING, EXTRACTING, INSTALLING, DONE, FAILED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _percent = MutableStateFlow(0)
    val percent: StateFlow<Int> = _percent.asStateFlow()

    fun nodeBin(context: Context) = File(context.filesDir, "node/bin/node")
    fun npmBin(context: Context)  = File(context.filesDir, "node/bin/npm")
    fun opencodeBin(context: Context) = File(context.filesDir, "node/bin/opencode")

    fun isNodeReady(context: Context) = nodeBin(context).canExecute()
    fun isOpenCodeReady(context: Context) = opencodeBin(context).canExecute()

    suspend fun install(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val nodeDir = File(context.filesDir, "node")
            nodeDir.mkdirs()

            // —— 1. 下载 ——
            _state.value = State.DOWNLOADING
            _message.value = "正在下载 Node.js v$NODE_VERSION (~20MB) …"
            _percent.value = 0

            val tarFile = File(context.filesDir, "node.tar.xz")
            download(NODE_URL, tarFile) { pct -> _percent.value = (pct * 0.35).toInt() }

            // —— 2. 解压 (纯 Java, 不依赖系统 tar/xz) ——
            _state.value = State.EXTRACTING
            _message.value = "正在解压 …"
            _percent.value = 35

            extractTarXz(tarFile, nodeDir) { pct ->
                _percent.value = 35 + (pct * 0.25).toInt()
            }
            tarFile.delete()

            // 确保 node/npm 可执行
            nodeBin(context).setExecutable(true, false)
            npmBin(context).setExecutable(true, false)

            // —— 3. npm install opencode ——
            _state.value = State.INSTALLING
            _message.value = "正在安装 opencode (约2-5分钟) …"
            _percent.value = 60

            val binDir = File(nodeDir, "bin")
            val result = runCommand(
                npmBin(context).absolutePath,
                listOf("install", "-g", "@anthropic-ai/opencode"),
                mapOf(
                    "HOME" to context.filesDir.absolutePath,
                    "npm_config_registry" to NPM_REGISTRY,
                    "npm_config_cache" to File(context.filesDir, ".npm-cache").absolutePath,
                    "npm_config_prefix" to nodeDir.absolutePath,
                ),
                binDir,
            )
            Log.i(TAG, "npm install result: $result")

            _percent.value = 100
            _state.value = State.DONE
            _message.value = "✅ 安装完成！"

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            _state.value = State.FAILED
            _message.value = "安装失败: ${e.message}"
            Result.failure(e)
        }
    }

    // =========== 纯 Java 下载/解压 ===========

    private fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 600000
        conn.setRequestProperty("User-Agent", "OpenCode-Android")
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
    }

    private fun extractTarXz(tarXzFile: File, destDir: File, onProgress: (Int) -> Unit) {
        val totalSize = tarXzFile.length()
        var extracted = 0L

        XZCompressorInputStream(tarXzFile.inputStream().buffered()).use { xzIn ->
            TarArchiveInputStream(xzIn).use { tarIn ->
                var entry = tarIn.nextEntry
                while (entry != null) {
                    val name = entry.name.removePrefix("./")
                    // 去掉顶层目录名（如 node-v22.12.0-linux-arm64/）
                    val stripped = name.substringAfter("/")
                    if (stripped.isBlank()) {
                        entry = tarIn.nextEntry
                        continue
                    }

                    val outFile = File(destDir, stripped)

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            tarIn.copyTo(fos)
                        }
                        // 保留可执行权限
                        if (entry.mode and 0b001_000_000 != 0 || entry.mode and 0b000_001_001 != 0) {
                            outFile.setExecutable(true, false)
                        }
                    }

                    extracted = tarIn.bytesRead
                    onProgress((extracted * 100 / totalSize).toInt())
                    entry = tarIn.nextEntry
                }
            }
        }
    }

    private fun runCommand(
        cmd: String,
        args: List<String>,
        env: Map<String, String>,
        workDir: File,
    ): String {
        val pb = ProcessBuilder(cmd, *args.toTypedArray())
        pb.environment().putAll(env)
        pb.directory(workDir)
        pb.redirectErrorStream(true)

        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText()
        val exitCode = proc.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("$cmd ${args.joinToString(" ")} (exit=$exitCode): $output")
        }
        return output
    }
}