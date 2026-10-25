package com.leoai.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.leoai.app.data.ChatMessage

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["session_id", "created_at"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    val role: String, // "user" | "assistant"
    val content: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String
) {
    fun toDomain(): ChatMessage = ChatMessage(
        id = id,
        sessionId = sessionId,
        role = role,
        content = content,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(message: ChatMessage): ChatMessageEntity = ChatMessageEntity(
            id = message.id,
            sessionId = message.sessionId,
            role = message.role,
            content = message.content,
            createdAt = message.createdAt
        )
    }
}
