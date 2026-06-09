package com.opencode.android.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * OpenCode Server REST API 数据模型
 * 基于 coulious/opencode-android 的 ApiModels，适配 opencode-android 项目
 */

@Serializable
data class Session(
    val id: String = "",
    val slug: String = "",
    @SerialName("projectID")
    val projectID: String = "",
    val directory: String = "",
    @SerialName("parentID")
    val parentID: String? = null,
    val title: String = "",
    val version: String = "",
    val time: SessionTime = SessionTime(),
    val share: ShareInfo? = null,
    val summary: SessionSummary? = null,
)

@Serializable
data class SessionTime(
    val created: Long = 0,
    val updated: Long = 0,
)

@Serializable
data class ShareInfo(
    val url: String = "",
)

@Serializable
data class SessionSummary(
    val additions: Int = 0,
    val deletions: Int = 0,
    val files: Int = 0,
)

@Serializable
data class Message(
    val info: MessageInfo? = null,
    val parts: List<Part> = emptyList(),
)

@Serializable
data class MessageInfo(
    val id: String = "",
    val role: String = "",
    @SerialName("sessionID")
    val sessionID: String = "",
    val time: MessageTime? = null,
    val error: MessageError? = null,
    val agent: String? = null,
    @SerialName("parentID")
    val parentID: String? = null,
    @SerialName("modelID")
    val modelID: String? = null,
    @SerialName("providerID")
    val providerID: String? = null,
    val cost: Double? = null,
)

@Serializable
data class MessageTime(
    val created: Long = 0,
    val completed: Long? = null,
)

@Serializable
data class MessageError(
    val name: String = "",
    val data: ErrorData? = null,
)

@Serializable
data class ErrorData(
    val message: String = "",
)

@Serializable
data class Part(
    val id: String = "",
    @SerialName("sessionID")
    val sessionID: String = "",
    @SerialName("messageID")
    val messageID: String = "",
    val type: String = "",
    val text: String? = null,
    val tool: String? = null,
    val state: ToolState? = null,
    val callID: String? = null,
    val time: PartTime? = null,
)

@Serializable
data class PartTime(
    val start: Long = 0,
    val end: Long? = null,
)

@Serializable
data class ToolState(
    val status: String = "",
    val input: JsonElement? = null,
    val output: String? = null,
    val title: String? = null,
    val error: String? = null,
)

@Serializable
data class Command(
    val name: String = "",
    val description: String? = null,
    val template: String = "",
    val agent: String? = null,
    val model: String? = null,
    @SerialName("subtask")
    val subtask: Boolean? = null,
)

@Serializable
data class Provider(
    val id: String = "",
    val name: String = "",
    val source: String = "",
    val env: List<String> = emptyList(),
    val models: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class CreateSessionRequest(
    val title: String? = null,
    @SerialName("parentID")
    val parentID: String? = null,
)

@Serializable
data class PromptRequest(
    @SerialName("messageID")
    val messageID: String? = null,
    val parts: List<PromptPart>,
    val agent: String? = null,
    val model: ModelReference? = null,
)

@Serializable
data class PromptPart(
    @EncodeDefault
    val type: String = "text",
    val text: String,
)

@Serializable
data class ModelReference(
    @SerialName("providerID")
    val providerID: String = "",
    @SerialName("modelID")
    val modelID: String = "",
)

@Serializable
data class CommandRequest(
    val command: String,
    @EncodeDefault
    val arguments: String = "",
    @SerialName("messageID")
    val messageID: String? = null,
)

@Serializable
data class HealthResponse(
    val healthy: Boolean = false,
    val version: String = "",
)

@Serializable
data class SessionStatus(
    val type: String = "idle",
)

@Serializable
data class Todo(
    val id: String = "",
    val content: String = "",
    val status: String = "",
    val priority: String = "",
)

@Serializable
data class ConfigInfo(
    val providers: List<Provider> = emptyList(),
)
