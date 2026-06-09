package com.opencode.android.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.android.data.model.Message
import com.opencode.android.data.model.Part
import com.opencode.android.engine.OpenCodeManager.RuntimeState
import com.opencode.android.service.OpenCodeRuntimeService

/**
 * 主聊天界面 — 中文版
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSessions: () -> Unit = {},
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val serverVersion by viewModel.serverVersion.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadSessions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentSession?.title?.ifBlank { "OpenCode" } ?: "OpenCode",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        val (statusText, statusColor) = when (runtimeState) {
                            RuntimeState.RUNNING -> {
                                val v = serverVersion
                                if (v != null) "● 运行中 (v$v)" to Color(0xFF4CAF50)
                                else "● 运行中" to Color(0xFF4CAF50)
                            }
                            RuntimeState.STARTING -> "◌ 启动中…" to Color(0xFFFFC107)
                            RuntimeState.STOPPING -> "◌ 停止中…" to Color(0xFFFFC107)
                            RuntimeState.ERROR -> "✕ 错误" to Color(0xFFFF5252)
                            RuntimeState.STOPPED -> "○ 已停止" to Color(0xFF9E9E9E)
                        }
                        Text(text = statusText, fontSize = 11.sp, color = statusColor)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSessions) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "会话列表")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("向 OpenCode 提问…") },
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        enabled = currentSession != null && runtimeState == RuntimeState.RUNNING,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isLoading) {
                        IconButton(
                            onClick = { viewModel.abortCurrentMessage() },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "停止", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText.trim())
                                    inputText = ""
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            enabled = inputText.isNotBlank() && runtimeState == RuntimeState.RUNNING,
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "发送")
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            error?.let { err ->
                Snackbar(
                    modifier = Modifier.padding(8.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("关闭")
                        }
                    },
                ) { Text(err) }
            }

            if (currentSession == null && runtimeState == RuntimeState.RUNNING) {
                // 无会话
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.AddCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("创建新会话以开始", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.createNewSession() }) {
                            Text("新建会话")
                        }
                    }
                }
            } else if (runtimeState != RuntimeState.RUNNING) {
                // 服务未运行 — 显示启动按钮而非无限转圈
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            when (runtimeState) {
                                RuntimeState.STARTING -> "正在启动 OpenCode 服务…"
                                RuntimeState.STOPPED -> "服务未运行"
                                RuntimeState.ERROR -> "服务异常 — 请检查设置"
                                else -> "等待服务…"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (runtimeState == RuntimeState.STOPPED || runtimeState == RuntimeState.ERROR) {
                            Button(
                                onClick = {
                                    OpenCodeRuntimeService.start(context)
                                },
                            ) {
                                Text("启动 OpenCode 服务")
                            }
                        }
                        if (runtimeState == RuntimeState.STARTING) {
                            Spacer(modifier = Modifier.height(12.dp))
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            } else {
                // 消息列表
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.info?.id ?: it.hashCode().toString() }) { message ->
                        MessageCard(message = message)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageCard(message: Message) {
    val role = message.info?.role ?: "user"
    val isUser = role == "user"
    val hasError = message.info?.error != null

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = when {
                hasError -> "错误"
                isUser -> "你"
                else -> message.info?.agent?.ifBlank { "OpenCode" } ?: "OpenCode"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hasError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        message.parts.forEach { part -> PartCard(part = part, isUser = isUser) }
        if (hasError) {
            Text(
                text = message.info?.error?.data?.message ?: "未知错误",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun PartCard(part: Part, isUser: Boolean) {
    when (part.type) {
        "text" -> {
            val text = part.text ?: return
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp,
                ),
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        "tool" -> {
            val state = part.state ?: return
            val isExpanded = remember { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.widthIn(max = 320.dp).padding(vertical = 2.dp),
                onClick = { isExpanded.value = !isExpanded.value },
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, tint) = when (state.status) {
                            "running" -> Icons.Filled.PlayArrow to Color(0xFF2196F3)
                            "completed" -> Icons.Filled.CheckCircle to Color(0xFF4CAF50)
                            "error" -> Icons.Filled.Error to Color(0xFFFF5252)
                            else -> Icons.Filled.HourglassEmpty to Color(0xFF9E9E9E)
                        }
                        Icon(icon, state.status, modifier = Modifier.size(16.dp), tint = tint)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.title ?: part.tool ?: "工具",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            if (isExpanded.value) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "展开/收起",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    AnimatedVisibility(visible = isExpanded.value) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            if (state.input != null) {
                                Text("输入：", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                Text(state.input.toString(), fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp))
                            }
                            if (!state.output.isNullOrBlank()) {
                                Text("输出：", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                Text(state.output, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp))
                            }
                            if (!state.error.isNullOrBlank()) {
                                Text(state.error, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}