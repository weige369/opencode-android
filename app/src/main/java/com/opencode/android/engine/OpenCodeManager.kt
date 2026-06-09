package com.opencode.android.engine

import android.content.Context
import android.util.Log
import com.opencode.android.data.model.HealthResponse
import com.opencode.android.util.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * OpenCode 引擎管理器
 *
 * 负责管理 OpenCode Server 进程的生命周期：启动、健康检查、停止。
 * 通过 proot/Linux 环境执行 `opencode serve --port {port}`。
 *
 * 借鉴 codex-android 的 CodexManager 架构，适配 OpenCode HTTP Server 模式。
 */
object OpenCodeManager {

    private const val TAG = "OpenCodeManager"
    private const val DEFAULT_PORT = 4096
    private const val HEALTH_CHECK_INTERVAL_MS = 2000L
    private const val HEALTH_CHECK_MAX_RETRIES = 15

    /**
     * 运行时状态
     */
    enum class RuntimeState {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING,
        ERROR
    }

    private val lock = Any()

    private val _state = MutableStateFlow(RuntimeState.STOPPED)
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private val _port = MutableStateFlow(DEFAULT_PORT)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _serverVersion = MutableStateFlow<String?>(null)
    val serverVersion: StateFlow<String?> = _serverVersion.asStateFlow()

    /**
     * 是否检测到 opencode 二进制文件
     * 同步检查，可在 UI 线程调用
     */
    val isBinaryAvailable: Boolean by lazy { findOpenCodeBinary() != null }

    @Volatile
    private var serverProcess: Process? = null
    private var healthCheckJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var isShuttingDown = false

    /**
     * 启动 OpenCode Server
     *
     * @param context Android Context
     * @param port 端口号（默认 4096）
     * @param password 可选的密码认证
     * @param workDir 工作目录
     */
    suspend fun start(
        context: Context,
        port: Int = DEFAULT_PORT,
        password: String? = null,
        workDir: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (_state.value == RuntimeState.RUNNING || _state.value == RuntimeState.STARTING) {
                Log.w(TAG, "OpenCode Server already running or starting")
                return@withContext Result.success(Unit)
            }

            _state.value = RuntimeState.STARTING
            _port.value = port
            isShuttingDown = false

            try {
                val binaryPath = findOpenCodeBinary()
                if (binaryPath == null) {
                    _state.value = RuntimeState.ERROR
                    return@withContext Result.failure(IllegalStateException(
                        "OpenCode binary not found. Please install opencode first.\n" +
                        "Recommended: Termux + Hope2333/opencode-termux deb package"
                    ))
                }

                Log.i(TAG, "Starting OpenCode Server: $binaryPath serve --port $port")

                val processBuilder = ProcessBuilder().apply {
                    val env = environment().apply {
                        put("PATH", System.getenv("PATH") ?: "/usr/bin:/bin")
                        put("HOME", System.getenv("HOME") ?: "/root")
                        if (!password.isNullOrBlank()) {
                            put("OPENCODE_SERVER_PASSWORD", password)
                        }
                    }

                    command(
                        binaryPath,
                        "serve",
                        "--port", port.toString(),
                        "--cors", "http://localhost"
                    )

                    if (!workDir.isNullOrBlank()) {
                        directory(java.io.File(workDir))
                    }

                    redirectErrorStream(true)
                }

                serverProcess = processBuilder.start()
                startHealthCheck(port)

                Log.i(TAG, "OpenCode Server process started, PID: ${getPid()}")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start OpenCode Server", e)
                _state.value = RuntimeState.ERROR
                Result.failure(e)
            }
        }
    }

    /**
     * 停止 OpenCode Server
     */
    suspend fun stop() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (_state.value == RuntimeState.STOPPED) {
                return@withContext
            }

            _state.value = RuntimeState.STOPPING
            isShuttingDown = true

            try {
                healthCheckJob?.cancel()
                healthCheckJob = null

                val process = serverProcess
                if (process != null && process.isAlive) {
                    process.destroy()
                    val exited = try {
                        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (_: Exception) { false }

                    if (!exited) {
                        Log.w(TAG, "OpenCode Server did not exit gracefully, force killing")
                        process.destroyForcibly()
                    }
                }

                serverProcess = null
                _state.value = RuntimeState.STOPPED
                _serverVersion.value = null
                Log.i(TAG, "OpenCode Server stopped")

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping OpenCode Server", e)
                serverProcess = null
                _state.value = RuntimeState.STOPPED
            }
        }
    }

    /**
     * 获取进程 PID
     */
    fun getPid(): Long {
        return try {
            val process = serverProcess ?: return -1
            val pidField = Process::class.java.getDeclaredField("pid")
            pidField.isAccessible = true
            pidField.getLong(process)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get PID", e)
            -1
        }
    }

    /**
     * 获取服务器 base URL
     */
    fun getBaseUrl(): String = "http://127.0.0.1:${_port.value}"

    // ================ 内部方法 ================

    /**
     * 查找 opencode 二进制路径
     */
    private fun findOpenCodeBinary(): String? {
        val candidates = listOf(
            // OpenCodeInstaller 安装的二进制 (最高优先级)
            "/data/data/com.opencode.android/files/bin/opencode",
            // Termux
            "/data/data/com.termux/files/usr/bin/opencode",
            "/data/data/com.termux/files/usr/glibc/bin/opencode",
            // 系统路径
            "/usr/bin/opencode",
            "/usr/local/bin/opencode",
            System.getenv("HOME")?.let { "$it/.local/bin/opencode" },
        )

        for (path in candidates) {
            if (path != null && java.io.File(path).exists()) {
                Log.d(TAG, "Found OpenCode binary: $path")
                return path
            }
        }

        // 尝试 which
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("/usr/bin/which", "opencode"))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val result = reader.readLine()?.trim()
            proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            reader.close()
            if (!result.isNullOrBlank() && java.io.File(result).exists()) result else null
        } catch (e: Exception) {
            Log.w(TAG, "which opencode failed", e)
            null
        }
    }

    /**
     * 健康检查轮询
     */
    private fun startHealthCheck(port: Int) {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            var retries = 0
            while (isActive && retries < HEALTH_CHECK_MAX_RETRIES) {
                if (isShuttingDown) break

                delay(HEALTH_CHECK_INTERVAL_MS)

                try {
                    val request = okhttp3.Request.Builder()
                        .url("http://127.0.0.1:$port/global/health")
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        response.close()

                        _state.value = RuntimeState.RUNNING
                        Log.i(TAG, "OpenCode Server healthy on port $port")

                        // 尝试解析版本号
                        try {
                            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                            val health = json.decodeFromString<HealthResponse>(body)
                            _serverVersion.value = health.version
                        } catch (_: Exception) {
                            Log.w(TAG, "Failed to parse health response: $body")
                        }
                        return@launch
                    }
                    response.close()

                } catch (_: java.net.ConnectException) {
                    Log.d(TAG, "Health check #$retries: server not ready yet")
                } catch (e: Exception) {
                    Log.w(TAG, "Health check #$retries error: ${e.message}")
                }

                retries++
            }

            if (!isShuttingDown && _state.value != RuntimeState.RUNNING) {
                Log.e(TAG, "OpenCode Server failed to become healthy after $retries retries")
                _state.value = RuntimeState.ERROR
            }
        }
    }
}