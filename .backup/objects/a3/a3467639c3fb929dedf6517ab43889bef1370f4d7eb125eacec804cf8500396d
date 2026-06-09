package com.opencode.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opencode.android.service.OpenCodeRuntimeService
import com.opencode.android.util.PreferencesManager

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var port by remember { mutableStateOf(PreferencesManager.getServerPort(context).toString()) }
    var password by remember { mutableStateOf(PreferencesManager.getServerPassword(context) ?: "") }
    var workDir by remember { mutableStateOf(PreferencesManager.getDefaultWorkDir(context) ?: "/sdcard") }
    var autoStart by remember { mutableStateOf(PreferencesManager.isAutoStart(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Server Configuration
            Text("Server Configuration", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() } },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = workDir,
                onValueChange = { workDir = it },
                label = { Text("Working Directory") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Auto-start
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Auto-start server", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = autoStart,
                    onCheckedChange = { autoStart = it },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            Button(
                onClick = {
                    val portInt = port.toIntOrNull() ?: 4096
                    PreferencesManager.setServerPort(context, portInt)
                    PreferencesManager.setServerPassword(context, password.ifBlank { null })
                    PreferencesManager.setDefaultWorkDir(context, workDir.ifBlank { "/sdcard" })
                    PreferencesManager.setAutoStart(context, autoStart)

                    // Restart service with new config
                    OpenCodeRuntimeService.stop(context)
                    OpenCodeRuntimeService.start(
                        context = context,
                        port = portInt,
                        password = password.ifBlank { null },
                        workDir = workDir.ifBlank { "/sdcard" },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save & Restart")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Service Controls
            Text("Service Controls", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val portInt = port.toIntOrNull() ?: 4096
                        OpenCodeRuntimeService.start(
                            context = context,
                            port = portInt,
                            password = password.ifBlank { null },
                            workDir = workDir.ifBlank { "/sdcard" },
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Start Server")
                }
                OutlinedButton(
                    onClick = { OpenCodeRuntimeService.stop(context) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Stop Server")
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // About
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "OpenCode Android v0.1.0\n\n" +
                "AI-powered coding assistant for Android.\n" +
                "Built on OpenCode Server (anomalyco/opencode).\n\n" +
                "Architecture inspired by codex-android.\n" +
                "API models from coulious/opencode-android.\n" +
                "Runtime integration from Hope2333/opencode-termux.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}