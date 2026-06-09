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
import com.opencode.android.engine.NodeRuntime
import com.opencode.android.engine.OpenCodeManager
import com.opencode.android.service.OpenCodeRuntimeService
import com.opencode.android.ui.chat.ChatScreen
import com.opencode.android.ui.installer.InstallerScreen
import com.opencode.android.ui.theme.OpenCodeTheme
import com.opencode.android.util.PreferencesManager

/**
 * 主 Activity
 * 
 * 首次启动 → 安装引导页 → 安装完成 → 聊天页
 * 后续启动 → 直接进入聊天页
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRuntime()
        else Toast.makeText(this, "需通知权限以保持后台服务运行", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OpenCodeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // 判断是否需要安装
                    val needsSetup = remember {
                        !NodeRuntime.isOpenCodeReady(this@MainActivity) &&
                        !OpenCodeManager.isBinaryAvailable
                    }

                    var screen by remember { mutableStateOf(if (needsSetup) "installer" else "chat") }

                    when (screen) {
                        "installer" -> InstallerScreen(
                            onComplete = {
                                screen = "chat"
                            },
                            onSkip = {
                                screen = "chat"
                            },
                        )
                        else -> ChatScreen(
                            onNavigateToSettings = { /* TODO */ },
                            onNavigateToSessions = { /* TODO */ },
                            onNavigateToBootstrap = { screen = "installer" },
                        )
                    }
                }
            }
        }

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
        OpenCodeRuntimeService.start(this, port, password, workDir)
    }
}