package com.leoai.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.leoai.app.data.ChatMessage
import com.leoai.app.data.InputBoundsValidator
import com.leoai.app.data.local.ChatMessageCrypto

/**
 * Room Database Entity representing a single chat message in a conversation session.
 *
 * Stored in the `chat_messages` table with composite indexing on `(session_id, created_at)`
 * for fast chronological retrieval. Message `content` is encrypted at rest using
 * AES-256-GCM (`ChatMessageCrypto`) with `sessionId` bound as Associated Authenticated Data (AAD).
 */
@Immutable
@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["session_id", "created_at"]),
        Index(value = ["session_id"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "role")
    val role: String, // "user" | "assistant"
    @ColumnInfo(name = "content")
    val content: String, // AES-256-GCM encrypted payload at rest (or legacy plaintext)
    @ColumnInfo(name = "created_at")
    val createdAt: String
) {
    /**
     * Converts this persisted Room entity into a decrypted domain [ChatMessage].
     */
    fun toDomain(): ChatMessage = ChatMessage(
        id = id,
        sessionId = sessionId,
        role = InputBoundsValidator.sanitizeRole(role),
        content = ChatMessageCrypto.decryptSafely(content, associatedData = sessionId),
        createdAt = createdAt
    )

    companion object {
        /**
         * Creates a [ChatMessageEntity] from a domain [ChatMessage], applying [InputBoundsValidator]
         * guardrails and encrypting the message content at rest with AES-256-GCM bound to `sessionId`.
         */
        fun fromDomain(
            message: ChatMessage,
            encryptAtRest: Boolean = true
        ): ChatMessageEntity {
            val safeId = InputBoundsValidator.sanitizeId(message.id)
            val safeSessionId = InputBoundsValidator.sanitizeId(message.sessionId)
            val safeRole = InputBoundsValidator.sanitizeRole(message.role)
            val safeContent = InputBoundsValidator.sanitizeMessageContent(message.content)

            val storedContent = if (encryptAtRest && !ChatMessageCrypto.isEncryptedPayload(safeContent)) {
                ChatMessageCrypto.encrypt(safeContent, associatedData = safeSessionId)
            } else {
                safeContent
            }

            return ChatMessageEntity(
                id = safeId,
                sessionId = safeSessionId,
                role = safeRole,
                content = storedContent,
                createdAt = message.createdAt
            )
        }
    }
}
