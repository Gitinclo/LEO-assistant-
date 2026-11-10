package com.leoai.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.leoai.app.data.ChatSession
import com.leoai.app.data.InputBoundsValidator
import com.leoai.app.data.local.ChatMessageCrypto

/**
 * Room Database Entity representing a conversation session in the `chat_sessions` table.
 * Session `preview` snippets are encrypted at rest with AES-256-GCM bound to the session `id`.
 */
@Immutable
@Entity(
    tableName = "chat_sessions",
    indices = [
        Index(value = ["updated_at"])
    ]
)
data class ChatSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String,
    @ColumnInfo(name = "preview")
    val preview: String
) {
    fun toDomain(): ChatSession = ChatSession(
        id = id,
        title = InputBoundsValidator.sanitizeSessionTitle(title),
        createdAt = createdAt,
        updatedAt = updatedAt,
        preview = if (preview.isEmpty()) {
            ""
        } else {
            ChatMessageCrypto.decryptSafely(preview, associatedData = id)
        }
    )

    companion object {
        fun fromDomain(
            session: ChatSession,
            encryptAtRest: Boolean = true
        ): ChatSessionEntity {
            val safeId = InputBoundsValidator.sanitizeId(session.id)
            val safeTitle = InputBoundsValidator.sanitizeSessionTitle(session.title)
            val safePreview = InputBoundsValidator.sanitizeSessionPreview(session.preview)

            val storedPreview = when {
                safePreview.isEmpty() -> ""
                encryptAtRest && !ChatMessageCrypto.isEncryptedPayload(safePreview) ->
                    ChatMessageCrypto.encrypt(safePreview, associatedData = safeId)
                else -> safePreview
            }

            return ChatSessionEntity(
                id = safeId,
                title = safeTitle,
                createdAt = session.createdAt,
                updatedAt = session.updatedAt,
                preview = storedPreview
            )
        }
    }
}
