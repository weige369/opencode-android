package com.opencode.android.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.android.data.model.Message
import com.opencode.android.data.model.Part
import com.opencode.android.engine.OpenCodeManager.RuntimeState

/**
 * 主聊天界面
 *
 * 借鉴 coulious/opencode-android 的 ChatScreen 设计：
 * - 工具调用折叠卡片
 * - Markdown 渲染（简化版）
 * - 消息分页加载
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

    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    // 自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // 首次加载
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
                        // 状态指示器
                        val (statusText, statusColor) = when (runtimeState) {
                            RuntimeState.RUNNING -> {
                                val v = serverVersion
                                if (v != null) "● Running (v$v)" to Color(0xFF4CAF50)
                                else "● Running" to Color(0xFF4CAF50)
                            }
                            RuntimeState.STARTING -> "◌ Starting..." to Color(0xFFFFC107)
                            RuntimeState.STOPPING -> "◌ Stopping..." to Color(0xFFFFC107)
                            RuntimeState.ERROR -> "✕ Error" to Color(0xFFFF5252)
                            RuntimeState.STOPPED -> "○ Stopped" to Color(0xFF9E9E9E)
                        }
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            color = statusColor,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSessions) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "Sessions")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            // 输入栏
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
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
                        placeholder = { Text("Ask OpenCode anything...") },
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
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.error,
                            )
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
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = "Send",
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // 错误提示
            error?.let { err ->
                Snackbar(
                    modifier = Modifier.padding(8.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    },
                ) {
                    Text(err)
                }
            }

            if (currentSession == null && runtimeState == RuntimeState.RUNNING) {
                // 无会话状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.AddCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Create a new session to start",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.createNewSession() }) {
                            Text("New Session")
                        }
                    }
                }
            } else if (runtimeState != RuntimeState.RUNNING) {
                // 服务未运行
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            when (runtimeState) {
                                RuntimeState.STARTING -> "Starting OpenCode Server..."
                                RuntimeState.STOPPED -> "Server stopped"
                                RuntimeState.ERROR -> "Server error - check settings"
                                else -> "Waiting for server..."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
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

/**
 * 单条消息卡片
 */
@Composable
fun MessageCard(message: Message) {
    val role = message.info?.role ?: "user"
    val isUser = role == "user"
    val hasError = message.info?.error != null

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // Role label
        Text(
            text = when {
                hasError -> "Error"
                isUser -> "You"
                else -> message.info?.agent?.ifBlank { "OpenCode" } ?: "OpenCode"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hasError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )

        // 消息 parts
        message.parts.forEach { part ->
            PartCard(part = part, isUser = isUser)
        }

        // 错误信息
        if (hasError) {
            Text(
                text = message.info?.error?.data?.message ?: "Unknown error",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * 单个 Part 渲染
 */
@Composable
fun PartCard(part: Part, isUser: Boolean) {
    when (part.type) {
        "text" -> {
            val text = part.text ?: return
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
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
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .padding(vertical = 2.dp),
                onClick = { isExpanded.value = !isExpanded.value },
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 工具状态图标
                        val (icon, tint) = when (state.status) {
                            "running" -> Icons.Filled.PlayArrow to Color(0xFF2196F3)
                            "completed" -> Icons.Filled.CheckCircle to Color(0xFF4CAF50)
                            "error" -> Icons.Filled.Error to Color(0xFFFF5252)
                            else -> Icons.Filled.HourglassEmpty to Color(0xFF9E9E9E)
                        }
                        Icon(
                            icon,
                            contentDescription = state.status,
                            modifier = Modifier.size(16.dp),
                            tint = tint,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.title ?: part.tool ?: "Tool",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            if (isExpanded.value) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "Toggle",
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    // 展开时显示详情
                    AnimatedVisibility(visible = isExpanded.value) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            if (state.input != null) {
                                Text(
                                    text = "Input:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                                Text(
                                    text = state.input.toString(),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            if (!state.output.isNullOrBlank()) {
                                Text(
                                    text = "Output:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                                Text(
                                    text = state.output,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            if (!state.error.isNullOrBlank()) {
                                Text(
                                    text = state.error,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        else -> {
            // 未知 part type，忽略或显示原始内容
        }
    }
}