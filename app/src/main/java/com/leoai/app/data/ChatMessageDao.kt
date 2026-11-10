package com.leoai.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.leoai.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for storing and retrieving chat conversation history locally.
 *
 * Adheres to the Room Persistence Integration guidelines:
 * - Returns reactive [Flow] streams for continuous Jetpack Compose observation.
 * - Marks all write/mutation and one-shot queries as `suspend` functions for main-thread safety.
 * - Uses parameterized SQL queries exclusively to prevent injection.
 */
@Dao
interface ChatMessageDao {

    /**
     * Inserts or replaces a single chat message in the `chat_messages` table.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    /**
     * Standard alias for [insertMessage].
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    /**
     * Inserts or replaces a batch of chat messages atomically.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    /**
     * Retrieves a single chat message by its unique [id], or `null` if not found.
     */
    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): ChatMessageEntity?

    /**
     * Deletes a single chat message by its unique [id].
     */
    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    /**
     * Deletes all chat message history across all sessions from the local database.
     */
    @Query("DELETE FROM chat_messages")
    suspend fun deleteMessageHistory()

    /**
     * Deletes all chat message history belonging to a specific [sessionId].
     */
    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    suspend fun deleteMessageHistoryBySession(sessionId: String)

    /**
     * Retrieves all chat history across all sessions ordered by timestamp (`created_at`) ascending
     * as a reactive [Flow].
     */
    @Query("SELECT * FROM chat_messages ORDER BY created_at ASC")
    fun getAllChatHistory(): Flow<List<ChatMessageEntity>>

    /**
     * Retrieves all chat history ordered by timestamp ascending as a reactive [Flow].
     */
    @Query("SELECT * FROM chat_messages ORDER BY created_at ASC")
    fun getChatHistoryOrderedByTimestamp(): Flow<List<ChatMessageEntity>>

    /**
     * Retrieves a one-time snapshot of all chat history ordered by timestamp ascending.
     */
    @Query("SELECT * FROM chat_messages ORDER BY created_at ASC")
    suspend fun getAllChatHistorySnapshot(): List<ChatMessageEntity>

    /**
     * Retrieves chat history for a specific [sessionId] ordered by timestamp (`created_at`) ascending
     * as a reactive [Flow].
     */
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    fun getChatHistoryForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    /**
     * Retrieves a one-time snapshot of chat history for a specific [sessionId] ordered by timestamp ascending.
     */
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY created_at ASC")
    suspend fun getChatHistoryForSessionSnapshot(sessionId: String): List<ChatMessageEntity>

    /**
     * Returns the total count of stored messages for a specific [sessionId].
     */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE session_id = :sessionId")
    suspend fun getMessageCountForSession(sessionId: String): Int
}
