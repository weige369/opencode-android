package com.opencode.android.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 内置 Node.js + opencode 一键安装管理器
 *
 * 用户只需点一个按钮，自动完成：
 *   1. 下载 Node.js ARM64 (~20MB)
 *   2. 解压到 files/node/
 *   3. npm install -g opencode
 */
object NodeRuntime {
    private const val TAG = "NodeRuntime"
    private const val NODE_VERSION = "22.12.0"
    private const val NODE_URL =
        "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-arm64.tar.xz"
    private const val NPM_REGISTRY = "https://registry.npmmirror.com"

    enum class State {
        IDLE,         // 等待开始
        DOWNLOADING,  // 下载 Node.js
        EXTRACTING,   // 解压
        INSTALLING,   // npm install opencode
        DONE,         // 完成
        FAILED,       // 失败
    }

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

    /**
     * 一键安装入口
     */
    suspend fun install(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _state.value = State.DOWNLOADING
            _message.value = "正在下载 Node.js v$NODE_VERSION …"
            _percent.value = 0

            val nodeDir = File(context.filesDir, "node")
            nodeDir.mkdirs()

            val tarFile = File(context.filesDir, "node.tar.xz")

            // 1. 下载
            downloadFile(NODE_URL, tarFile) { pct ->
                _percent.value = (pct * 0.4).toInt()
            }

            // 2. 解压 (用系统 xz + tar，Android 自带)
            _state.value = State.EXTRACTING
            _message.value = "正在解压 Node.js …"
            _percent.value = 40

            runCommand("tar", "-xJf", tarFile.absolutePath, "-C", nodeDir.absolutePath, "--strip-components=1")
            tarFile.delete()

            // 3. npm install opencode
            _state.value = State.INSTALLING
            _message.value = "正在安装 opencode (~5分钟) …"
            _percent.value = 60

            val npmBin = npmBin(context)
            val env = mapOf(
                "PATH" to "${nodeDir.absolutePath}/bin:${System.getenv("PATH")}",
                "HOME" to context.filesDir.absolutePath,
                "npm_config_registry" to NPM_REGISTRY,
                "npm_config_cache" to File(context.filesDir, ".npm-cache").absolutePath,
                "npm_config_prefix" to nodeDir.absolutePath,
            )
            runCommandWithEnv(npmBin.absolutePath, listOf("install", "-g", "@anthropic-ai/opencode"), env)

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

    private fun downloadFile(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 600000
        val total = conn.contentLengthLong
        var downloaded = 0L

        conn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) {
                        onProgress((downloaded * 100 / total).toInt())
                    }
                }
            }
        }
        conn.disconnect()
    }

    private fun runCommand(vararg cmd: String): String {
        val proc = Runtime.getRuntime().exec(cmd)
        val out = proc.inputStream.bufferedReader().readText()
        val err = proc.errorStream.bufferedReader().readText()
        proc.waitFor()
        if (proc.exitValue() != 0) throw RuntimeException("${cmd.joinToString(" ")}: $err")
        return out
    }

    private fun runCommandWithEnv(cmd: String, args: List<String>, env: Map<String, String>): String {
        val pb = ProcessBuilder(cmd, *args.toTypedArray())
        pb.environment().putAll(env)
        pb.directory(File(System.getenv("HOME") ?: "/"))
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText()
        val err = proc.errorStream.bufferedReader().readText()
        proc.waitFor()
        if (proc.exitValue() != 0) throw RuntimeException("$cmd: $err")
        return out
    }
}