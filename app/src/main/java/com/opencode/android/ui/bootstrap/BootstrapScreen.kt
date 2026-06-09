package com.opencode.android.ui.bootstrap

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.android.bootstrap.BootstrapManager
import com.opencode.android.bootstrap.BootstrapManager.InstallStep

/**
 * Termux + opencode 一键安装引导界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootstrapScreen(
    onBack: () -> Unit,
    onInstallComplete: () -> Unit = {},
) {
    val context = LocalContext.current
    val step by BootstrapManager.step.collectAsStateWithLifecycle()
    val message by BootstrapManager.message.collectAsStateWithLifecycle()
    val termuxInstalled = remember { BootstrapManager.isTermuxInstalled(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("环境安装") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 标题
            Icon(
                Icons.Filled.Terminal,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "一键安装开发环境",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "通过 Termux 自动安装 Node.js、Git、Python 和 opencode",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 步骤指示器
            InstallStepCard(
                checked = termuxInstalled,
                active = step == InstallStep.CHECKING_TERMUX,
                number = "1",
                title = "安装 Termux",
                description = if (termuxInstalled) "已安装 ✅"
                else "从 F-Droid 安装 Termux 终端模拟器",
            )
            InstallStepCard(
                checked = step == InstallStep.READY_TO_RUN || step == InstallStep.RUNNING,
                active = step == InstallStep.COPYING_SCRIPT,
                number = "2",
                title = "复制安装脚本",
                description = "将一键安装脚本写入 Termux 环境",
            )
            InstallStepCard(
                checked = step == InstallStep.COMPLETED,
                active = step == InstallStep.RUNNING,
                number = "3",
                title = "运行安装脚本",
                description = "在 Termux 中自动安装 Node.js + opencode 等工具",
            )
            InstallStepCard(
                checked = false,
                active = step == InstallStep.COMPLETED,
                number = "4",
                title = "返回应用启动服务",
                description = "安装完成后返回 OpenCode App，启动 AI 服务",
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 状态消息
            if (message.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    ),
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 操作按钮
            when (step) {
                InstallStep.IDLE -> {
                    if (!termuxInstalled) {
                        Button(
                            onClick = { BootstrapManager.openTermuxDownload(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("安装 Termux (F-Droid)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                // 用户可能已手动安装，刷新检测
                                if (BootstrapManager.isTermuxInstalled(context)) {
                                    BootstrapManager.startInstallFlow(context)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("已手动安装？点此继续")
                        }
                    } else {
                        Button(
                            onClick = { BootstrapManager.startInstallFlow(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始一键安装")
                        }
                    }
                }
                InstallStep.TERMUX_NOT_INSTALLED -> {
                    Button(
                        onClick = { BootstrapManager.openTermuxDownload(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("前往 F-Droid 下载 Termux")
                    }
                }
                InstallStep.READY_TO_RUN -> {
                    Button(
                        onClick = { BootstrapManager.runBootstrapInTermux(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Terminal, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("在 Termux 中运行安装")
                    }
                }
                InstallStep.RUNNING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "安装进行中… 请切换到 Termux 查看进度",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                InstallStep.COMPLETED -> {
                    Button(
                        onClick = onInstallComplete,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("✅ 安装完成，返回应用")
                    }
                }
                InstallStep.FAILED -> {
                    OutlinedButton(
                        onClick = { BootstrapManager.reset() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("重试")
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun InstallStepCard(
    checked: Boolean,
    active: Boolean,
    number: String,
    title: String,
    description: String,
) {
    val borderColor = when {
        checked -> Color(0xFF4CAF50)
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val bgColor = when {
        checked -> Color(0xFF4CAF50).copy(alpha = 0.05f)
        active -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = borderColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (checked) {
                        Icon(Icons.Filled.Check, contentDescription = null,
                            tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                    } else {
                        Text(number, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                    color = if (checked || active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text(description, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            if (active && !checked) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}