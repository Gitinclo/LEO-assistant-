package com.leoai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leoai.app.data.ChatMessage
import com.leoai.app.data.ChatSession
import com.leoai.app.data.LeoRepository
import com.leoai.app.data.nowIsoString
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LeoScreen {
    data object Welcome : LeoScreen
    data class Chat(val sessionId: String, val initialPrompt: String? = null) : LeoScreen
    data object History : LeoScreen
    data object Settings : LeoScreen
}

class LeoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LeoRepository(application.applicationContext)

    private val _screenStack = MutableStateFlow<List<LeoScreen>>(listOf(LeoScreen.Welcome))
    val currentScreen: StateFlow<LeoScreen> = MutableStateFlow<LeoScreen>(LeoScreen.Welcome).also { flow ->
        viewModelScope.launch {
            _screenStack.collect { stack ->
                flow.value = stack.lastOrNull() ?: LeoScreen.Welcome
            }
        }
    }

    /**
     * Reactive StateFlow of chat sessions backed by Room Database.
     */
    val sessions: StateFlow<List<ChatSession>> = repository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _welcomeBusy = MutableStateFlow(false)
    val welcomeBusy: StateFlow<Boolean> = _welcomeBusy.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading.asStateFlow()

    private val _chatSending = MutableStateFlow(false)
    val chatSending: StateFlow<Boolean> = _chatSending.asStateFlow()

    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    private val _historyRefreshing = MutableStateFlow(false)
    val historyRefreshing: StateFlow<Boolean> = _historyRefreshing.asStateFlow()

    private val _clearingAll = MutableStateFlow(false)
    val clearingAll: StateFlow<Boolean> = _clearingAll.asStateFlow()

    private val _clearedBanner = MutableStateFlow(false)
    val clearedBanner: StateFlow<Boolean> = _clearedBanner.asStateFlow()

    private var activeSessionMessagesJob: Job? = null

    fun navigateToHistory() {
        pushScreen(LeoScreen.History)
    }

    fun navigateToSettings() {
        pushScreen(LeoScreen.Settings)
    }

    fun navigateBack(): Boolean {
        val current = _screenStack.value
        if (current.size <= 1) return false
        _screenStack.value = current.dropLast(1)
        return true
    }

    private fun pushScreen(screen: LeoScreen) {
        _screenStack.value = _screenStack.value + screen
    }

    fun startChatFromWelcome(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty() || _welcomeBusy.value) return
        viewModelScope.launch {
            _welcomeBusy.value = true
            try {
                val session = repository.createSession()
                _chatMessages.value = emptyList()
                pushScreen(LeoScreen.Chat(sessionId = session.id, initialPrompt = text))
            } finally {
                _welcomeBusy.value = false
            }
        }
    }

    fun startNewBlankChat() {
        viewModelScope.launch {
            val session = repository.createSession()
            _chatMessages.value = emptyList()
            pushScreen(LeoScreen.Chat(sessionId = session.id, initialPrompt = null))
        }
    }

    fun openExistingSession(sessionId: String) {
        _chatMessages.value = emptyList()
        pushScreen(LeoScreen.Chat(sessionId = sessionId, initialPrompt = null))
    }

    fun loadSessionMessages(sessionId: String, initialPrompt: String?) {
        activeSessionMessagesJob?.cancel()
        activeSessionMessagesJob = viewModelScope.launch {
            _chatLoading.value = true

            // Read snapshot to decide if initial prompt should be submitted
            val existing = repository.getMessagesSnapshot(sessionId)
            _chatMessages.value = existing
            _chatLoading.value = false

            if (!initialPrompt.isNullOrBlank() && existing.isEmpty()) {
                sendMessage(sessionId, initialPrompt)
            }

            // Observe Room messages reactively
            repository.getMessagesForSession(sessionId).collect { messages ->
                _chatMessages.value = messages
            }
        }
    }

    fun sendMessage(sessionId: String, rawText: String) {
        val content = rawText.trim()
        if (content.isEmpty() || _chatSending.value) return

        viewModelScope.launch {
            _chatSending.value = true
            // Optimistic message in UI
            val optimisticUserMsg = ChatMessage(
                id = "local-${System.currentTimeMillis()}",
                sessionId = sessionId,
                role = "user",
                content = content,
                createdAt = nowIsoString()
            )
            _chatMessages.value = _chatMessages.value + optimisticUserMsg

            try {
                repository.sendMessage(sessionId, content)
            } catch (_: Exception) {
                val fallbackErrorMsg = ChatMessage(
                    id = "err-${System.currentTimeMillis()}",
                    sessionId = sessionId,
                    role = "assistant",
                    content = "I couldn't reach my thoughts just now. Please try that again in a moment.",
                    createdAt = nowIsoString()
                )
                _chatMessages.value = _chatMessages.value + fallbackErrorMsg
            } finally {
                _chatSending.value = false
            }
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _historyRefreshing.value = true
            delay(300)
            _historyRefreshing.value = false
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }

    fun clearAllConversations() {
        if (_clearingAll.value) return
        viewModelScope.launch {
            _clearingAll.value = true
            try {
                repository.clearAllConversations()
                _clearedBanner.value = true
                delay(2500)
                _clearedBanner.value = false
            } finally {
                _clearingAll.value = false
            }
        }
    }
}
