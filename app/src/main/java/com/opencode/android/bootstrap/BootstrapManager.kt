package com.opencode.android.bootstrap

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

/**
 * Termux 引导安装管理器
 *
 * 负责检测 Termux 安装状态、复制 bootstrap 脚本到 Termux 环境、
 * 启动 Termux 并执行一键安装脚本。
 */
object BootstrapManager {

    private const val TAG = "BootstrapManager"
    private const val TERMUX_PACKAGE = "com.termux"
    private const val BOOTSTRAP_SCRIPT = "bootstrap_termux.sh"
    private const val TERMUX_SCRIPT_PATH = "/data/data/com.termux/files/home/.opencode_bootstrap.sh"

    enum class InstallStep {
        IDLE,
        CHECKING_TERMUX,
        TERMUX_NOT_INSTALLED,
        COPYING_SCRIPT,
        READY_TO_RUN,
        RUNNING,
        COMPLETED,
        FAILED,
    }

    private val _step = MutableStateFlow(InstallStep.IDLE)
    val step: StateFlow<InstallStep> = _step.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    /**
     * 检测 Termux 是否已安装
     */
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 打开 Termux 下载页面（F-Droid）
     */
    fun openTermuxDownload(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://f-droid.org/packages/com.termux/")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 复制 bootstrap 脚本到 Termux home 目录
     * 需要在 Termux 已安装后调用
     */
    fun copyBootstrapScript(context: Context): Result<Unit> {
        _step.value = InstallStep.COPYING_SCRIPT
        _message.value = "正在复制安装脚本…"

        return try {
            val scriptContent = context.assets.open(BOOTSTRAP_SCRIPT)
                .bufferedReader()
                .readText()

            val dest = File(TERMUX_SCRIPT_PATH)
            // Termux 的 files/home 权限属于 termux uid
            // 通过 Termux 的 RUN_COMMAND intent 来复制更可靠，
            // 但这里先尝试直接写入（root 设备或 proot 环境）
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { it.write(scriptContent.toByteArray()) }
            dest.setExecutable(true, false)

            Log.i(TAG, "Bootstrap script copied to $TERMUX_SCRIPT_PATH")
            _step.value = InstallStep.READY_TO_RUN
            _message.value = "脚本已就绪"
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy bootstrap script", e)
            _step.value = InstallStep.FAILED
            _message.value = "复制脚本失败: ${e.message}"
            Result.failure(e)
        }
    }

    /**
     * 启动 Termux 执行 bootstrap 脚本
     *
     * 通过 Termux:Tasker 插件或 RUN_COMMAND intent 执行。
     * Termux v0.109+ 支持 RUN_COMMAND intent。
     */
    fun runBootstrapInTermux(context: Context) {
        _step.value = InstallStep.RUNNING
        _message.value = "正在 Termux 中运行安装脚本…"

        try {
            // 方式1: Termux RUN_COMMAND intent (v0.109+)
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH",
                    "/data/data/com.termux/files/home/.opencode_bootstrap.sh")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(""))
                putExtra("com.termux.RUN_COMMAND_WORKDIR",
                    "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0") // 新 session
            }
            context.startService(intent)

            Log.i(TAG, "Termux RUN_COMMAND sent")
            _message.value = "安装脚本已在 Termux 中运行\n请切换到 Termux 查看进度"
        } catch (e: Exception) {
            // 方式2: 降级为打开 Termux + 打印指引
            Log.w(TAG, "RUN_COMMAND failed, falling back to manual", e)
            _message.value = "无法自动执行。请手动打开 Termux，运行:\nbash ~/.opencode_bootstrap.sh"

            val fallback = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            fallback?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (fallback != null) {
                context.startActivity(fallback)
            } else {
                _step.value = InstallStep.FAILED
                _message.value = "无法启动 Termux"
            }
        }
    }

    /**
     * 完整的安装引导流程
     */
    fun startInstallFlow(context: Context) {
        _step.value = InstallStep.CHECKING_TERMUX

        if (!isTermuxInstalled(context)) {
            _step.value = InstallStep.TERMUX_NOT_INSTALLED
            _message.value = "请先安装 Termux"
            return
        }

        copyBootstrapScript(context)
        runBootstrapInTermux(context)
    }

    fun reset() {
        _step.value = InstallStep.IDLE
        _message.value = ""
    }
}