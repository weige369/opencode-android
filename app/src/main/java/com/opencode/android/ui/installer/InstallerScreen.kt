package com.opencode.android.ui.installer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.android.engine.NodeRuntime
import kotlinx.coroutines.launch

/**
 * 一键安装界面
 *
 * 用户只需点一个按钮，自动下载 Node.js + 安装 opencode。
 * 整个过程在后台运行，显示实时进度。
 */
@Composable
fun InstallerScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val state by NodeRuntime.state.collectAsStateWithLifecycle()
    val message by NodeRuntime.message.collectAsStateWithLifecycle()
    val percent by NodeRuntime.percent.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val alreadyInstalled = remember { NodeRuntime.isOpenCodeReady(context) }

    // 如果已安装，直接完成
    LaunchedEffect(alreadyInstalled) {
        if (alreadyInstalled) onComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 图标
        Icon(
            Icons.Filled.Downloading,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))

        Text(
            "环境一键安装",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "首次使用需要安装 Node.js 和 OpenCode 运行环境。\n全程自动，无需手动操作。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        Spacer(Modifier.height(32.dp))

        // 进度条
        when (state) {
            NodeRuntime.State.IDLE -> {
                Button(
                    onClick = {
                        scope.launch { NodeRuntime.install(context) }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("一键安装", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(12.dp))

                TextButton(onClick = onSkip) {
                    Text("跳过，先体验 UI")
                }
            }

            NodeRuntime.State.DOWNLOADING,
            NodeRuntime.State.EXTRACTING,
            NodeRuntime.State.INSTALLING -> {
                // 进度信息
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        LinearProgressIndicator(
                            progress = { (percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "$percent%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            NodeRuntime.State.DONE -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                    ),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            "成功",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "安装完成！",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("开始使用", style = MaterialTheme.typography.titleMedium)
                }
            }

            NodeRuntime.State.FAILED -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "⚠️ 安装失败",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch { NodeRuntime.install(context) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("重试")
                }
            }
        }
    }
}