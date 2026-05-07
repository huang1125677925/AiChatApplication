package com.example.aichatapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichatapplication.model.ConversationMessage
import com.example.aichatapplication.model.ConversationSummary
import com.example.aichatapplication.model.Role
import com.example.aichatapplication.model.ToolCard
import com.example.aichatapplication.model.UiMessage
import android.util.Log
import com.example.aichatapplication.network.ChatNetworkClient
import com.example.aichatapplication.utils.SessionManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

/**
 * 聊天页面的 ViewModel
 * 负责管理聊天数据流和网络请求状态。
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val networkClient = ChatNetworkClient()
    private val sessionManager = SessionManager(application)
    private val toolArgsPrettyGson = GsonBuilder().setPrettyPrinting().create()

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isChatting = MutableStateFlow(false)
    val isChatting: StateFlow<Boolean> = _isChatting.asStateFlow()

    private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversations: StateFlow<List<ConversationSummary>> = _conversations.asStateFlow()

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    private val _loadingHistory = MutableStateFlow(false)
    val loadingHistory: StateFlow<Boolean> = _loadingHistory.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var streamingJob: Job? = null
    private var pollingJob: Job? = null
    private var wasStreamingOnPause = false

    init {
        bootstrap()
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun createConversation() {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) {
            _message.value = "登录态已失效，请重新登录"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                networkClient.createConversation(token = token)
            }.onSuccess { created ->
                _conversations.value = listOf(created) + _conversations.value.filterNot { it.id == created.id }
                _currentConversationId.value = created.id
                _messages.value = emptyList()
            }.onFailure {
                _message.value = it.message ?: "创建会话失败"
            }
        }
    }

    fun selectConversation(conversationId: Long) {
        if (_currentConversationId.value == conversationId && _messages.value.isNotEmpty()) {
            return
        }
        pollingJob?.cancel()
        pollingJob = null
        _currentConversationId.value = conversationId
        loadConversationMessages(conversationId)
    }

    fun refreshConversations() {
        bootstrap()
    }

    fun onAppPaused() {
        wasStreamingOnPause = streamingJob?.isActive == true
        cancelStreaming()
    }

    fun onAppResumed() {
        val conversationId = _currentConversationId.value ?: return

        loadConversationMessages(conversationId)

        if (wasStreamingOnPause) {
            wasStreamingOnPause = false
            startPollingUpdates(conversationId)
        }
    }

    private fun cancelStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        pollingJob?.cancel()
        pollingJob = null
        _isChatting.value = false
    }

    private fun startPollingUpdates(conversationId: Long) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            val token = sessionManager.getToken() ?: return@launch
            var lastContentSnapshot = ""
            var stableRounds = 0

            repeat(40) {
                delay(3000)
                val history = runCatching {
                    networkClient.listMessages(token = token, conversationId = conversationId)
                }.getOrNull() ?: return@launch

                val snapshot = history.lastOrNull()?.let { "${it.id}-${it.content.length}" } ?: ""
                if (snapshot == lastContentSnapshot) {
                    stableRounds++
                    if (stableRounds >= 2) return@launch
                } else {
                    stableRounds = 0
                    _messages.value = history.map { it.toUiMessage() }
                }
                lastContentSnapshot = snapshot
            }
        }
    }

    fun deleteConversation(conversationId: Long) {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) {
            _message.value = "登录态已失效，请重新登录"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                networkClient.deleteConversation(token = token, conversationId = conversationId)
            }.onSuccess {
                val updatedConversations = _conversations.value.filterNot { it.id == conversationId }
                _conversations.value = updatedConversations
                if (_currentConversationId.value == conversationId) {
                    val nextConversationId = updatedConversations.firstOrNull()?.id
                    _currentConversationId.value = nextConversationId
                    if (nextConversationId != null) {
                        loadConversationMessages(nextConversationId)
                    } else {
                        _messages.value = emptyList()
                    }
                }
            }.onFailure {
                _message.value = it.message ?: "删除会话失败"
            }
        }
    }

    fun sendMessage() {
        // #region agent log
        Log.d("DBG_ca7fbf", """{"h":"H6","loc":"ChatViewModel:sendMessage","msg":"guard_check","data":{"isChatting":${_isChatting.value},"inputEmpty":${_inputText.value.trim().isEmpty()}}}""")
        // #endregion
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isChatting.value) return
        pollingJob?.cancel()
        pollingJob = null
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) {
            _message.value = "登录态已失效，请重新登录"
            return
        }

        val userMessage = UiMessage(role = Role.USER, content = text)
        val currentList = _messages.value.toMutableList()
        currentList.add(userMessage)

        val assistantMessageId = java.util.UUID.randomUUID().toString()
        val assistantMessage = UiMessage(
            id = assistantMessageId,
            role = Role.ASSISTANT,
            content = "思考中...",
            isLoading = true,
            isStreaming = true
        )
        currentList.add(assistantMessage)

        _messages.value = currentList
        _inputText.value = ""
        _isChatting.value = true
        // #region agent log
        Log.d("DBG_ca7fbf", """{"h":"H1","loc":"ChatViewModel:sendMessage","msg":"start","data":{"thread":"${Thread.currentThread().name}","convId":${_currentConversationId.value}}}""")
        // #endregion

        streamingJob = viewModelScope.launch(Dispatchers.IO) {
            // #region agent log
            Log.d("DBG_ca7fbf", """{"h":"H1","loc":"ChatViewModel:sendMessage:launch","msg":"inside_launch","data":{"thread":"${Thread.currentThread().name}"}}""")
            // #endregion
            val conversationId = ensureConversationForSend(token)
            if (conversationId == null) {
                updateAssistantMessage(assistantMessageId) {
                    it.copy(content = "创建会话失败", isLoading = false, isStreaming = false)
                }
                _isChatting.value = false
                return@launch
            }
            _currentConversationId.value = conversationId
            networkClient.streamConversation(
                token = token,
                conversationId = conversationId,
                content = text
            )
                .catch { e ->
                    // #region agent log
                    Log.d("DBG_ca7fbf", """{"h":"ALL","loc":"ChatViewModel:sendMessage:catch","msg":"flow_error","data":{"error":"${e.message}","errorClass":"${e.javaClass.name}"}}""")
                    // #endregion
                    updateAssistantMessage(assistantMessageId) {
                        val errorContent = if (it.isLoading) "网络请求失败: ${e.message}" else "${it.content}\n\n[网络请求失败: ${e.message}]"
                        it.copy(content = errorContent, isLoading = false, isStreaming = false, isError = true)
                    }
                    _isChatting.value = false
                }
                .onCompletion { cause ->
                    // #region agent log
                    Log.d("DBG_ca7fbf", """{"h":"H6","loc":"ChatViewModel:sendMessage:onCompletion","msg":"flow_completed","data":{"cause":"${cause?.message}","causeClass":"${cause?.javaClass?.name}"}}""")
                    // #endregion
                    _isChatting.value = false
                    streamingJob = null
                }
                .collect { sseResponse ->
                    when (sseResponse.type) {
                        "status" -> {
                            val statusContent = sseResponse.content ?: "正在执行..."
                            updateAssistantMessage(assistantMessageId) {
                                if (it.isLoading) {
                                    it.copy(content = statusContent, isLoading = true, isStreaming = true, isError = false)
                                } else {
                                    it
                                }
                            }
                        }
                        "tool_card" -> {
                            val toolName = sseResponse.toolName ?: "Unknown Tool"
                            val result = sseResponse.result ?: ""
                            val argsStr = formatToolArgsJson(sseResponse.args)
                            val toolCard = ToolCard(toolName = toolName, args = argsStr, result = result)
                            updateAssistantMessage(assistantMessageId) { msg ->
                                val cards = msg.toolCards.toMutableList().apply { add(toolCard) }
                                msg.copy(toolCards = cards)
                            }
                        }
                        "text" -> {
                            val delta = sseResponse.content ?: ""
                            updateAssistantMessage(assistantMessageId) { msg ->
                                // isLoading=true 说明还没收到过真正的文字片段（当前显示的是"思考中..."或 status 提示），直接覆盖
                                val newContent = if (msg.isLoading) delta else msg.content + delta
                                msg.copy(content = newContent, isLoading = false, isStreaming = true, isError = false)
                            }
                        }
                        "error" -> {
                            val errorContent = sseResponse.content ?: "未知错误"
                            // #region agent log
                            Log.d("DBG_ca7fbf", """{"h":"H5","loc":"ChatViewModel:sendMessage:collect","msg":"sse_error_type","data":{"content":"${errorContent.take(200).replace("\"", "'")}"}}""")
                            // #endregion
                            updateAssistantMessage(assistantMessageId) {
                                val oldContent = if (it.isLoading) "" else it.content
                                it.copy(content = "$oldContent\n[错误]: $errorContent", isLoading = false, isStreaming = false, isError = true)
                            }
                        }
                        "done" -> {
                            // #region agent log
                            Log.d("DBG_ca7fbf", """{"h":"H3","loc":"ChatViewModel:sendMessage:collect","msg":"sse_done"}""")
                            // #endregion
                            updateAssistantMessage(assistantMessageId) {
                                it.copy(isLoading = false, isStreaming = false)
                            }
                            _isChatting.value = false
                            refreshConversationData(conversationId)
                        }
                    }
                }
        }
    }

    fun retryMessage(messageId: String) {
        val list = _messages.value
        val index = list.indexOfFirst { it.id == messageId }
        if (index == -1 || _isChatting.value) return
        pollingJob?.cancel()
        pollingJob = null

        // 查找对应的用户输入消息（假设错误消息上方是用户发送的消息）
        // 如果找不到合适的用户消息，可以选择重新发送当前输入框内容（但最好是基于上一条）
        var userText = ""
        for (i in index - 1 downTo 0) {
            if (list[i].role == Role.USER) {
                userText = list[i].content
                break
            }
        }
        
        if (userText.isEmpty()) return

        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) {
            _message.value = "登录态已失效，请重新登录"
            return
        }

        // 更新当前错误消息状态为加载中
        updateAssistantMessage(messageId) {
            it.copy(content = "重新思考中...", isLoading = true, isStreaming = true, isError = false)
        }

        _isChatting.value = true

        streamingJob = viewModelScope.launch(Dispatchers.IO) {
            val conversationId = ensureConversationForSend(token)
            if (conversationId == null) {
                updateAssistantMessage(messageId) {
                    it.copy(content = "创建会话失败", isLoading = false, isStreaming = false, isError = true)
                }
                _isChatting.value = false
                return@launch
            }
            _currentConversationId.value = conversationId
            networkClient.streamConversation(
                token = token,
                conversationId = conversationId,
                content = userText
            )
                .catch { e ->
                    updateAssistantMessage(messageId) {
                        val errorContent = if (it.isLoading) "网络请求失败: ${e.message}" else "${it.content}\n\n[网络请求失败: ${e.message}]"
                        it.copy(content = errorContent, isLoading = false, isStreaming = false, isError = true)
                    }
                    _isChatting.value = false
                }
                .onCompletion {
                    _isChatting.value = false
                    streamingJob = null
                }
                .collect { sseResponse ->
                    when (sseResponse.type) {
                        "status" -> {
                            val statusContent = sseResponse.content ?: "正在执行..."
                            updateAssistantMessage(messageId) {
                                if (it.isLoading) {
                                    it.copy(content = statusContent, isLoading = true, isStreaming = true, isError = false)
                                } else {
                                    it
                                }
                            }
                        }
                        "tool_card" -> {
                            val toolName = sseResponse.toolName ?: "Unknown Tool"
                            val result = sseResponse.result ?: ""
                            val argsStr = formatToolArgsJson(sseResponse.args)
                            val toolCard = ToolCard(toolName = toolName, args = argsStr, result = result)
                            updateAssistantMessage(messageId) { msg ->
                                val cards = msg.toolCards.toMutableList().apply { add(toolCard) }
                                msg.copy(toolCards = cards)
                            }
                        }
                        "text" -> {
                            val delta = sseResponse.content ?: ""
                            updateAssistantMessage(messageId) { msg ->
                                val newContent = if (msg.isLoading) delta else msg.content + delta
                                msg.copy(content = newContent, isLoading = false, isStreaming = true, isError = false)
                            }
                        }
                        "error" -> {
                            val errorContent = sseResponse.content ?: "未知错误"
                            updateAssistantMessage(messageId) {
                                val oldContent = if (it.isLoading) "" else it.content
                                it.copy(content = "$oldContent\n[错误]: $errorContent", isLoading = false, isStreaming = false, isError = true)
                            }
                        }
                        "done" -> {
                            updateAssistantMessage(messageId) {
                                it.copy(isLoading = false, isStreaming = false, isError = false)
                            }
                            _isChatting.value = false
                            refreshConversationData(conversationId)
                        }
                    }
                }
        }
    }

    private fun bootstrap() {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) {
            _conversations.value = emptyList()
            _messages.value = emptyList()
            _currentConversationId.value = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _loadingHistory.value = true
            runCatching {
                networkClient.listConversations(token = token)
            }.onSuccess { conversations ->
                _conversations.value = conversations
                val targetId = _currentConversationId.value ?: conversations.firstOrNull()?.id
                _currentConversationId.value = targetId
                if (targetId != null) {
                    loadConversationMessages(targetId)
                } else {
                    _messages.value = emptyList()
                }
            }.onFailure {
                _message.value = it.message ?: "加载会话失败"
            }
            _loadingHistory.value = false
        }
    }

    private fun loadConversationMessages(conversationId: Long) {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) {
            _messages.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _loadingHistory.value = true
            runCatching {
                networkClient.listMessages(
                    token = token,
                    conversationId = conversationId
                )
            }.onSuccess { history ->
                _messages.value = history.map { it.toUiMessage() }
            }.onFailure {
                _message.value = it.message ?: "加载消息失败"
            }
            _loadingHistory.value = false
        }
    }

    private fun refreshConversationData(conversationId: Long) {
        val token = sessionManager.getToken()
        if (token.isNullOrBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                networkClient.listConversations(token = token)
            }.onSuccess { conversations ->
                _conversations.value = conversations
            }
            runCatching {
                networkClient.listMessages(token = token, conversationId = conversationId)
            }.onSuccess { history ->
                _messages.value = history.map { it.toUiMessage() }
            }
        }
    }

    private fun ensureConversationForSend(token: String): Long? {
        // #region agent log
        Log.d("DBG_ca7fbf", """{"h":"H1","loc":"ChatViewModel:ensureConversationForSend","msg":"entry","data":{"thread":"${Thread.currentThread().name}","currentId":${_currentConversationId.value}}}""")
        // #endregion
        _currentConversationId.value?.let { return it }
        // #region agent log
        Log.d("DBG_ca7fbf", """{"h":"H1","loc":"ChatViewModel:ensureConversationForSend","msg":"will_create_conversation","data":{"thread":"${Thread.currentThread().name}"}}""")
        // #endregion
        return runCatching {
            networkClient.createConversation(token = token)
        }.onSuccess { created ->
            _conversations.value = listOf(created) + _conversations.value.filterNot { it.id == created.id }
            _currentConversationId.value = created.id
        }.onFailure {
            // #region agent log
            Log.d("DBG_ca7fbf", """{"h":"H1","loc":"ChatViewModel:ensureConversationForSend","msg":"create_failed","data":{"error":"${it.message}","errorClass":"${it.javaClass.name}"}}""")
            // #endregion
            _message.value = it.message ?: "创建会话失败"
        }.getOrNull()?.id
    }

    private fun updateAssistantMessage(id: String, update: (UiMessage) -> UiMessage) {
        val list = _messages.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            list[index] = update(list[index])
            _messages.value = list
        }
    }

    private fun ConversationMessage.toUiMessage(): UiMessage {
        val mappedRole = when (role.lowercase()) {
            Role.USER.value -> Role.USER
            Role.TOOL.value -> Role.TOOL
            else -> Role.ASSISTANT
        }
        return UiMessage(
            id = id.toString(),
            role = mappedRole,
            content = content,
            isLoading = false,
            isStreaming = false,
            toolCards = parseToolCards(toolData)
        )
    }

    private fun parseToolCards(toolData: com.google.gson.JsonElement?): List<ToolCard> {
        if (toolData == null || toolData.isJsonNull) return emptyList()
        return try {
            when {
                toolData.isJsonArray -> {
                    toolData.asJsonArray.mapNotNull { element ->
                        if (!element.isJsonObject) return@mapNotNull null
                        val obj = element.asJsonObject
                        val toolName = obj.get("tool_name")?.takeIf { !it.isJsonNull }?.asString
                            ?: return@mapNotNull null
                        val result = obj.get("result")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val argsStr = obj.get("args")?.takeIf { !it.isJsonNull }
                            ?.let { formatToolArgsJson(it) }
                        ToolCard(toolName = toolName, args = argsStr, result = result)
                    }
                }
                toolData.isJsonObject -> {
                    val obj = toolData.asJsonObject
                    val toolName = obj.get("tool_name")?.takeIf { !it.isJsonNull }?.asString
                        ?: return emptyList()
                    val result = obj.get("result")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val argsStr = obj.get("args")?.takeIf { !it.isJsonNull }
                        ?.let { formatToolArgsJson(it) }
                    listOf(ToolCard(toolName = toolName, args = argsStr, result = result))
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.w("ChatViewModel", "Failed to parse tool_data", e)
            emptyList()
        }
    }

    private fun formatToolArgsJson(args: JsonElement?): String? {
        if (args == null || args.isJsonNull) return null
        return toolArgsPrettyGson.toJson(args)
    }
}
