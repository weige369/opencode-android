package com.opencode.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.opencode.android.service.OpenCodeRuntimeService
import com.opencode.android.ui.bootstrap.BootstrapScreen
import com.opencode.android.ui.chat.ChatScreen
import com.opencode.android.ui.theme.OpenCodeTheme
import com.opencode.android.util.PreferencesManager

/**
 * 主 Activity
 *
 * 启动时自动启动 OpenCode 运行时服务，管理聊天/安装导航。
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRuntime()
        } else {
            Toast.makeText(
                this,
                "需通知权限以保持后台服务运行",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OpenCodeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var currentScreen by remember { mutableStateOf("chat") }

                    when (currentScreen) {
                        "bootstrap" -> BootstrapScreen(
                            onBack = { currentScreen = "chat" },
                            onInstallComplete = { currentScreen = "chat" },
                        )
                        else -> ChatScreen(
                            onNavigateToSettings = {
                                // TODO: 导航到设置页面
                            },
                            onNavigateToSessions = {
                                // TODO: 导航到会话列表
                            },
                            onNavigateToBootstrap = {
                                currentScreen = "bootstrap"
                            },
                        )
                    }
                }
            }
        }

        // 默认启动运行时服务
        requestNotificationPermissionAndStart()
    }

    private fun requestNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startRuntime()
    }

    private fun startRuntime() {
        val port = PreferencesManager.getServerPort(this)
        val password = PreferencesManager.getServerPassword(this)
        val workDir = PreferencesManager.getDefaultWorkDir(this)

        OpenCodeRuntimeService.start(
            context = this,
            port = port,
            password = password,
            workDir = workDir,
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note: 不在这里停止服务，保持后台运行
    }
}