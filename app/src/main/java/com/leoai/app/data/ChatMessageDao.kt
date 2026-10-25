package com.leoai.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.leoai.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for chat messages stored in the local Room database.
 */
@Dao
interface ChatMessageDao {

    /**
     * Inserts a new message into the chat_messages table.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    /**
     * Alias for insertMessage to support standard insert naming.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    /**
     * Deletes all chat message history from the database.
     */
    @Query("DELETE FROM chat_messages")
    suspend fun deleteMessageHistory()

    /**
     * Deletes chat message history for a specific session.
     */
    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    suspend fun deleteMessageHistoryBySession(sessionId: String)

    /**
     * Retrieves all chat history ordered by timestamp (created_at) ascending as a reactive Flow.
     */
    @Query("SELECT * FROM chat_messages ORDER BY created_at ASC")
    fun getAllChatHistory(): Flow<List<ChatMessageEntity>>

    /**
     * Retrieves all chat history ordered by timestamp as a reactive Flow.
     */
    @Query("SELECT * FROM chat_messages ORDER BY created_at ASC")
    fun getChatHistoryOrderedByTimestamp(): Flow<List<ChatMessageEntity>>

    /**
     * Retrieves a one-time snapshot of all chat history ordered by timestamp.
     */
    @Query("SELECT * FROM chat_messages ORDER BY created_at ASC")
    suspend fun getAllChatHistorySnapshot(): List<ChatMessageEntity>

    /**
     * Retrieves chat history for a specific session ordered by timestamp.
     */
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    fun getChatHistoryForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    /**
     * Retrieves a one-time snapshot of chat history for a specific session ordered by timestamp.
     */
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun getChatHistoryForSessionSnapshot(sessionId: String): List<ChatMessageEntity>
}
