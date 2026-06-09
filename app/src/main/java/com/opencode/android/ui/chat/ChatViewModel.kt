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
import kotlinx.coroutines.delay
import android.util.Log

/**
 * 聊天界面 ViewModel
 *
 * 管理会话、消息发送、AI 回复轮询等。
 * 当 opencode 二进制未安装时自动进入 Mock 模式，允许体验 UI。
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
    }

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

    /** 当 opencode 未安装时，Mock 模式为 true */
    private val _isMockMode = MutableStateFlow(!OpenCodeManager.isBinaryAvailable)
    val isMockMode: StateFlow<Boolean> = _isMockMode.asStateFlow()

    private var apiClient: OpenCodeApiClient? = null
    private var mockIdCounter = 0

    val runtimeState = OpenCodeManager.state
    val serverVersion = OpenCodeManager.serverVersion

    init {
        if (_isMockMode.value) {
            Log.i(TAG, "opencode 二进制未找到，启用 Mock 模式")
            initMockData()
        }
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
        if (_isMockMode.value) return  // mock 模式跳过网络请求
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
        if (_isMockMode.value) {
            sendMockMessage(text)
            return
        }
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

    // ============== Mock 模式 ==============

    private fun initMockData() {
        val mockSession = Session(
            id = "mock-1",
            title = "Mock 会话 (离线模式)",
            time = SessionTime(created = System.currentTimeMillis() / 1000),
        )
        _sessions.value = listOf(mockSession)
        _currentSession.value = mockSession
        _messages.value = listOf(
            Message(
                info = MessageInfo(id = "msg-welcome", role = "assistant", agent = "OpenCode"),
                parts = listOf(Part(
                    id = "p1", type = "text",
                    text = "👋 欢迎使用 OpenCode Android！\n\n" +
                        "当前为 **Mock 离线模式**，因为未检测到 opencode 二进制文件。\n\n" +
                        "你可以在此模式下体验 UI 交互，但 AI 功能需要安装 opencode 后才能使用。\n\n" +
                        "📦 **安装步骤：**\n" +
                        "1. 安装 [Termux](https://f-droid.org/packages/com.termux/)\n" +
                        "2. 在 Termux 中安装 opencode-termux deb 包\n" +
                        "3. 返回本应用，点击「设置 → 启动服务」"
                )),
            ),
        )
    }

    fun sendMockMessage(text: String) {
        if (!_isMockMode.value) return
        val session = _currentSession.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val userMsg = Message(
                info = MessageInfo(
                    id = "mock-u-${++mockIdCounter}",
                    role = "user",
                    sessionID = session.id,
                ),
                parts = listOf(Part(id = "mp-u-$mockIdCounter", type = "text", text = text)),
            )
            _messages.value = _messages.value + userMsg

            delay(800) // 模拟网络延迟

            val reply = Message(
                info = MessageInfo(
                    id = "mock-a-${++mockIdCounter}",
                    role = "assistant",
                    sessionID = session.id,
                    agent = "OpenCode (Mock)",
                ),
                parts = listOf(Part(
                    id = "mp-a-$mockIdCounter", type = "text",
                    text = "这是 Mock 回复。你说：\n> $text\n\n" +
                        "⚠️ 要使用真正的 AI 功能，请安装 opencode。\n" +
                        "前往 **设置** 页面查看安装指引。"
                )),
            )
            _messages.value = _messages.value + reply
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        apiClient?.close()
    }
}