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
 * Repository pattern abstracting Room database operations from UI/ViewModel.
 * Adheres strictly to the room-database-integration skill guidelines:
 * - Returns Flow<T> from queries for reactive Compose integration
 * - Marks insert/update/delete as suspend functions
 * - Never blocks the main thread
 */
class LeoRepository(
    context: Context,
    private val companionEngine: LeoCompanionEngine = LeoCompanionEngine(),
    private val chatDao: ChatDao = LeoDatabase.getInstance(context).chatDao(),
    private val messageDao: ChatMessageDao = LeoDatabase.getInstance(context).chatMessageDao()
) {

    /**
     * Reactive stream of all chat sessions, automatically emitting whenever the Room table updates.
     */
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions().map { entities ->
        entities.map { it.toDomain() }
    }

    /**
     * Reactive stream of messages for a given session using ChatMessageDao.
     */
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return messageDao.getChatHistoryForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun createSession(title: String? = null): ChatSession = withContext(Dispatchers.IO) {
        val session = ChatSession(
            title = title?.takeIf { it.isNotBlank() } ?: "New conversation"
        )
        chatDao.insertOrUpdateSession(ChatSessionEntity.fromDomain(session))
        session
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatDao.deleteSessionWithMessages(sessionId)
    }

    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        messageDao.deleteMessageHistory()
        chatDao.clearAllSessions()
    }

    suspend fun getMessagesSnapshot(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        messageDao.getChatHistoryForSessionSnapshot(sessionId).map { it.toDomain() }
    }

    suspend fun sendMessage(sessionId: String, rawContent: String): SendMessageResponse =
        withContext(Dispatchers.IO) {
            val content = rawContent.trim()
            require(content.isNotEmpty()) { "Message content is empty" }

            val existingSession = chatDao.getSessionById(sessionId)?.toDomain()
                ?: ChatSession(id = sessionId, title = "New conversation")

            val priorMessages = messageDao.getChatHistoryForSessionSnapshot(sessionId).map { it.toDomain() }

            val userMsg = ChatMessage(
                sessionId = sessionId,
                role = "user",
                content = content
            )
            messageDao.insertMessage(ChatMessageEntity.fromDomain(userMsg))

            val assistantText = companionEngine.generateReply(
                sessionId = sessionId,
                userContent = content,
                priorHistory = priorMessages
            )

            val assistantMsg = ChatMessage(
                sessionId = sessionId,
                role = "assistant",
                content = assistantText
            )
            messageDao.insertMessage(ChatMessageEntity.fromDomain(assistantMsg))

            val currentTitle = existingSession.title.ifBlank { "New conversation" }
            val updatedTitle = if (currentTitle == "New conversation") {
                if (content.length > 48) content.take(48) + "…" else content
            } else {
                currentTitle
            }
            val updatedPreview = assistantText
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .take(120)

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
