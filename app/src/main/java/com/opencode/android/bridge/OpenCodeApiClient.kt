package com.opencode.android.bridge

import com.opencode.android.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * OpenCode Server REST API 客户端
 *
 * 直接对接 OpenCode Server (`opencode serve`) 的 REST API：
 * - 会话管理：GET/POST /session
 * - 消息发送：POST /session/{id}/message
 * - 消息分页：GET /session/{id}/message?cursor=...
 * - 异步 Prompt：POST /session/{id}/prompt_async
 * - 命令执行：POST /session/{id}/command
 * - 健康检查：GET /global/health
 * - Todo 追踪：GET /session/{id}/todo
 * - 提供商列表：GET /provider
 *
 * 借鉴 coulious/opencode-android 的 Ktor 客户端实现。
 */
class OpenCodeApiClient(
    private val baseUrl: String = "http://127.0.0.1:4096",
    private val password: String? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    // ============== 健康检查 ==============

    suspend fun healthCheck(): Result<HealthResponse> = runCatching {
        client.get("$baseUrl/global/health").body<HealthResponse>()
    }

    // ============== 会话管理 ==============

    suspend fun listSessions(): Result<List<Session>> = runCatching {
        client.get("$baseUrl/session").body<List<Session>>()
    }

    suspend fun createSession(request: CreateSessionRequest): Result<Session> = runCatching {
        client.post("$baseUrl/session") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<Session>()
    }

    suspend fun getSession(sessionId: String): Result<Session> = runCatching {
        client.get("$baseUrl/session/$sessionId").body<Session>()
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        client.delete("$baseUrl/session/$sessionId")
        Unit
    }

    // ============== 消息 ==============

    suspend fun sendMessage(
        sessionId: String,
        request: PromptRequest
    ): Result<Message> = runCatching {
        client.post("$baseUrl/session/$sessionId/message") {
            contentType(ContentType.Application.Json)
            setBody(request)
            // 传递密码认证（如需要）
            password?.let { header("Authorization", "Bearer $it") }
        }.body<Message>()
    }

    suspend fun getMessages(
        sessionId: String,
        cursor: String? = null,
        limit: Int = 50
    ): Result<List<Message>> = runCatching {
        client.get("$baseUrl/session/$sessionId/message") {
            cursor?.let { parameter("cursor", it) }
            parameter("limit", limit)
        }.body<List<Message>>()
    }

    suspend fun abortMessage(sessionId: String): Result<Unit> = runCatching {
        client.post("$baseUrl/session/$sessionId/abort")
        Unit
    }

    // ============== 命令 ==============

    suspend fun runCommand(
        sessionId: String,
        request: CommandRequest
    ): Result<Message> = runCatching {
        client.post("$baseUrl/session/$sessionId/command") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<Message>()
    }

    // ============== Todo 追踪 ==============

    suspend fun getTodos(sessionId: String): Result<List<Todo>> = runCatching {
        client.get("$baseUrl/session/$sessionId/todo").body<List<Todo>>()
    }

    // ============== 提供商 & 配置 ==============

    suspend fun getProviders(): Result<List<Provider>> = runCatching {
        client.get("$baseUrl/provider").body<List<Provider>>()
    }

    // ============== 分享 ==============

    suspend fun getShareInfo(sessionId: String): Result<ShareInfo> = runCatching {
        client.get("$baseUrl/session/$sessionId/share").body<ShareInfo>()
    }

    /**
     * 清理资源
     */
    fun close() {
        client.close()
    }
}