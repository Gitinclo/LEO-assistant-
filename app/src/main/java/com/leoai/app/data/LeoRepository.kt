package com.leoai.app.data

import android.content.Context
import com.leoai.app.data.local.LeoDatabase
import com.leoai.app.data.local.dao.ChatDao
import com.leoai.app.data.local.entity.ChatMessageEntity
import com.leoai.app.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository pattern abstracting Room database operations and AES-256-GCM encryption from UI/ViewModel.
 * Adheres strictly to the room-database-integration skill guidelines:
 * - Returns Flow<T> from queries for reactive Compose integration
 * - Marks insert/update/delete as suspend functions
 * - Sanitizes inputs via [InputBoundsValidator] prior to persistence
 * - Never blocks the main thread
 */
class LeoRepository(
    context: Context,
    private val companionEngine: LeoCompanionEngine = LeoCompanionEngine(),
    private val chatDao: ChatDao = LeoDatabase.getInstance(context).chatDao(),
    private val messageDao: ChatMessageDao = LeoDatabase.getInstance(context).chatMessageDao()
) {

    /**
     * Reactive stream of all chat sessions, automatically decrypting previews and emitting whenever
     * the Room table updates.
     */
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions().map { entities ->
        entities.map { it.toDomain() }
    }

    /**
     * Reactive stream of all chat messages across all sessions ordered by timestamp ascending.
     */
    val allMessages: Flow<List<ChatMessage>> = messageDao.getAllChatHistory().map { entities ->
        entities.map { it.toDomain() }
    }

    /**
     * Reactive stream of decrypted messages for a given session using [ChatMessageDao].
     */
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        val safeSessionId = InputBoundsValidator.sanitizeId(sessionId)
        return messageDao.getChatHistoryForSession(safeSessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun createSession(title: String? = null): ChatSession = withContext(Dispatchers.IO) {
        val safeTitle = InputBoundsValidator.sanitizeSessionTitle(
            title?.takeIf { it.isNotBlank() } ?: InputBoundsValidator.DEFAULT_SESSION_TITLE
        )
        val session = ChatSession(title = safeTitle)
        chatDao.insertOrUpdateSession(ChatSessionEntity.fromDomain(session))
        session
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        val safeSessionId = InputBoundsValidator.sanitizeId(sessionId)
        messageDao.deleteMessageHistoryBySession(safeSessionId)
        chatDao.deleteSessionById(safeSessionId)
    }

    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        messageDao.deleteMessageHistory()
        chatDao.clearAllSessions()
    }

    suspend fun getMessagesSnapshot(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val safeSessionId = InputBoundsValidator.sanitizeId(sessionId)
        messageDao.getChatHistoryForSessionSnapshot(safeSessionId).map { it.toDomain() }
    }

    suspend fun sendMessage(sessionId: String, rawContent: String): SendMessageResponse =
        withContext(Dispatchers.IO) {
            val safeSessionId = InputBoundsValidator.sanitizeId(sessionId)
            val validation = MessageValidator.validate(
                rawInput = rawContent,
                maxLength = MessageValidator.MAX_MESSAGE_LENGTH,
                truncateIfExceeded = true
            )
            require(validation.isValid) {
                validation.errorMessage ?: "Message content is invalid"
            }
            val content = validation.sanitizedContent

            val existingSession = chatDao.getSessionById(safeSessionId)?.toDomain()
                ?: ChatSession(id = safeSessionId, title = InputBoundsValidator.DEFAULT_SESSION_TITLE)

            val priorMessages = messageDao.getChatHistoryForSessionSnapshot(safeSessionId).map { it.toDomain() }

            val userMsg = MessageValidator.sanitizeChatMessage(
                ChatMessage(
                    sessionId = safeSessionId,
                    role = InputBoundsValidator.ROLE_USER,
                    content = content
                )
            )
            messageDao.insertMessage(ChatMessageEntity.fromDomain(userMsg))

            val rawAssistantReply = companionEngine.generateReply(
                sessionId = safeSessionId,
                userContent = content,
                priorHistory = priorMessages
            )
            val assistantText = MessageValidator.sanitize(rawAssistantReply)

            val assistantMsg = MessageValidator.sanitizeChatMessage(
                ChatMessage(
                    sessionId = safeSessionId,
                    role = InputBoundsValidator.ROLE_ASSISTANT,
                    content = assistantText
                )
            )
            messageDao.insertMessage(ChatMessageEntity.fromDomain(assistantMsg))

            val currentTitle = InputBoundsValidator.sanitizeSessionTitle(existingSession.title)
            val updatedTitle = if (currentTitle == InputBoundsValidator.DEFAULT_SESSION_TITLE) {
                InputBoundsValidator.deriveTitleFromPrompt(content)
            } else {
                currentTitle
            }
            val updatedPreview = InputBoundsValidator.sanitizeSessionPreview(assistantText)

            val updatedSession = existingSession.copy(
                title = updatedTitle,
                updatedAt = nowIsoString(),
                preview = updatedPreview
            )
            chatDao.insertOrUpdateSession(ChatSessionEntity.fromDomain(updatedSession))

            SendMessageResponse(
                userMessage = userMsg,
                assistantMessage = assistantMsg,
                session = updatedSession
            )
        }
}
