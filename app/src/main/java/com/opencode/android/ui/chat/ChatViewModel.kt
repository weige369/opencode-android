package com.opencode.android.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.android.bridge.OpenCodeApiClient
import com.opencode.android.data.model.*
import com.opencode.android.engine.OpenCodeManager
import com.opencode.android.util.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

/**
 * 聊天界面 ViewModel
 *
 * 管理会话、消息发送、AI 回复轮询等。
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _todos = MutableStateFlow<List<Todo>>(emptyList())
    val todos: StateFlow<List<Todo>> = _todos.asStateFlow()

    private val _providers = MutableStateFlow<List<Provider>>(emptyList())
    val providers: StateFlow<List<Provider>> = _providers.asStateFlow()

    private var apiClient: OpenCodeApiClient? = null

    val runtimeState = OpenCodeManager.state
    val serverVersion = OpenCodeManager.serverVersion

    init {
        // 自动连接已运行的 server
        ensureClient()
    }

    private fun ensureClient(): OpenCodeApiClient {
        val existing = apiClient
        if (existing != null) return existing

        val port = PreferencesManager.getServerPort(context)
        val password = PreferencesManager.getServerPassword(context)
        val client = OpenCodeApiClient(
            baseUrl = "http://127.0.0.1:$port",
            password = password,
        )
        apiClient = client
        return client
    }

    // ============== 会话管理 ==============

    fun loadSessions() {
        viewModelScope.launch {
            val client = ensureClient()
            client.listSessions()
                .onSuccess { sessions ->
                    _sessions.value = sessions
                    _error.value = null

                    // 恢复上次会话
                    val lastId = PreferencesManager.getLastSessionId(context)
                    if (lastId != null && _currentSession.value == null) {
                        sessions.find { it.id == lastId }?.let { selectSession(it) }
                    }
                }
                .onFailure { e ->
                    _error.value = "加载会话失败: ${e.message}"
                }
        }
    }

    fun createNewSession(title: String? = null) {
        viewModelScope.launch {
            val client = ensureClient()
            client.createSession(CreateSessionRequest(title = title))
                .onSuccess { session ->
                    _sessions.value = _sessions.value + session
                    selectSession(session)
                }
                .onFailure { e ->
                    _error.value = "创建会话失败: ${e.message}"
                }
        }
    }

    fun selectSession(session: Session) {
        _currentSession.value = session
        PreferencesManager.setLastSessionId(context, session.id)
        loadMessages(session.id)
        loadTodos(session.id)
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val client = ensureClient()
            client.deleteSession(sessionId)
                .onSuccess {
                    _sessions.value = _sessions.value.filter { it.id != sessionId }
                    if (_currentSession.value?.id == sessionId) {
                        _currentSession.value = null
                        _messages.value = emptyList()
                    }
                }
                .onFailure { e ->
                    _error.value = "删除会话失败: ${e.message}"
                }
        }
    }

    // ============== 消息 ==============

    private fun loadMessages(sessionId: String) {
        viewModelScope.launch {
            val client = ensureClient()
            client.getMessages(sessionId)
                .onSuccess { msgs -> _messages.value = msgs }
                .onFailure { e -> _error.value = "加载消息失败: ${e.message}" }
        }
    }

    fun sendMessage(text: String, agent: String? = null) {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val request = PromptRequest(
                parts = listOf(PromptPart(text = text)),
                agent = agent,
            )

            val client = ensureClient()
            client.sendMessage(session.id, request)
                .onSuccess { message ->
                    // 将新消息追加到列表
                    _messages.value = _messages.value + message
                    loadMessages(session.id) // 刷新获取完整响应
                }
                .onFailure { e ->
                    _error.value = "发送消息失败: ${e.message}"
                }

            _isLoading.value = false
        }
    }

    fun abortCurrentMessage() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            val client = ensureClient()
            client.abortMessage(session.id)
            _isLoading.value = false
        }
    }

    // ============== Todo ==============

    private fun loadTodos(sessionId: String) {
        viewModelScope.launch {
            val client = ensureClient()
            client.getTodos(sessionId)
                .onSuccess { todos -> _todos.value = todos }
                .onFailure { e -> Log.e("ChatViewModel", "加载 Todo 失败", e) }
        }
    }

    // ============== Providers ==============

    fun loadProviders() {
        viewModelScope.launch {
            val client = ensureClient()
            client.getProviders()
                .onSuccess { providers -> _providers.value = providers }
                .onFailure { e -> Log.e("ChatViewModel", "加载 Provider 列表失败", e) }
        }
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        apiClient?.close()
    }
}