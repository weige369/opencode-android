package com.opencode.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.opencode.android.R
import com.opencode.android.engine.OpenCodeManager
import com.opencode.android.engine.OpenCodeManager.RuntimeState
import com.opencode.android.ui.MainActivity
import kotlinx.coroutines.*

/**
 * OpenCode 运行时前台服务
 *
 * 在 Android 前台运行 OpenCode Server 进程，确保不会被系统杀死。
 * 监听 OpenCodeManager 状态变化并更新通知。
 *
 * 借鉴 codex-android 的 CodexRuntimeService 架构，
 * 将通信方式从 stdio pipe 改为 HTTP REST（OpenCode Server 模式）。
 */
class OpenCodeRuntimeService : Service() {

    companion object {
        private const val TAG = "OpenCodeRuntimeSvc"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "opencode_runtime_channel"
        private const val CHANNEL_NAME = "OpenCode Runtime"

        const val ACTION_START = "com.opencode.android.action.START_RUNTIME"
        const val ACTION_STOP = "com.opencode.android.action.STOP_RUNTIME"
        const val EXTRA_PORT = "runtime_port"
        const val EXTRA_PASSWORD = "runtime_password"
        const val EXTRA_WORK_DIR = "runtime_work_dir"

        /**
         * 启动运行时服务
         */
        fun start(context: Context, port: Int = 4096, password: String? = null, workDir: String? = null) {
            val intent = Intent(context, OpenCodeRuntimeService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_PASSWORD, password)
                putExtra(EXTRA_WORK_DIR, workDir)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止运行时服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, OpenCodeRuntimeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, 4096)
                val password = intent.getStringExtra(EXTRA_PASSWORD)
                val workDir = intent.getStringExtra(EXTRA_WORK_DIR)

                Log.i(TAG, "Starting OpenCode runtime on port $port")
                startForeground(NOTIFICATION_ID, buildNotification("Starting OpenCode..."))
                startRuntime(port, password, workDir)
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stopping OpenCode runtime")
                stopRuntime()
            }
            else -> {
                // 如果服务被系统重启，尝试恢复
                Log.w(TAG, "Unknown action: $action")
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        stateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 不自动停止，保持后台运行
        Log.d(TAG, "Task removed, keeping service alive")
    }

    // ================ 内部方法 ================

    private fun startRuntime(port: Int, password: String?, workDir: String?) {
        serviceScope.launch {
            try {
                val result = OpenCodeManager.start(this@OpenCodeRuntimeService, port, password, workDir)

                result.onFailure { error ->
                    Log.e(TAG, "Failed to start runtime", error)
                    updateNotification("Error: ${error.message ?: "Unknown error"}")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Runtime start exception", e)
                updateNotification("Fatal: ${e.message}")
                stopSelf()
            }
        }

        // 监听状态变化以更新通知
        stateJob = serviceScope.launch {
            OpenCodeManager.state.collect { state ->
                val notification = when (state) {
                    RuntimeState.STOPPED -> buildNotification("Service stopped")
                    RuntimeState.STARTING -> buildNotification("Starting OpenCode Server...")
                    RuntimeState.RUNNING -> {
                        val version = OpenCodeManager.serverVersion.value
                        if (version != null) {
                            buildNotification("Running (v$version) on port ${OpenCodeManager.port.value}")
                        } else {
                            buildNotification("Running on port ${OpenCodeManager.port.value}")
                        }
                    }
                    RuntimeState.STOPPING -> buildNotification("Stopping...")
                    RuntimeState.ERROR -> buildNotification("Runtime error")
                }
                updateNotification(notification)
            }
        }
    }

    private fun stopRuntime() {
        serviceScope.launch {
            try {
                OpenCodeManager.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping runtime", e)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenCode")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows OpenCode Server runtime status"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}