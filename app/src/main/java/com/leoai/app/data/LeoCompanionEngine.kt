package com.leoai.app.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

/**
 * LEO AI Companion Engine backed by Firebase AI Logic (`Firebase.ai`) and the real Gemini API
 * (`gemini-3.8-flash`).
 *
 * Supports live customization from the user's active [TrainingProfile] and [TrainingQaPair]
 * dataset so LEO immediately applies custom domain instructions and few-shot training examples
 * during chat conversations.
 */
class LeoCompanionEngine(
    private val customResponder: (suspend (userContent: String, priorHistory: List<ChatMessage>) -> String)? = null
) {

    private val baseModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(SYSTEM_INSTRUCTION)
            }
        )
    }

    private fun modelForProfile(
        activeProfile: TrainingProfile?,
        activeQaPairs: List<TrainingQaPair>
    ) = if (activeProfile == null || !activeProfile.isActive) {
        baseModel
    } else {
        val customInstruction = buildTrainedSystemInstruction(activeProfile, activeQaPairs)
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content {
                text(customInstruction)
            }
        )
    }

    /**
     * Generates a complete reply from the Gemini model (`gemini-3.8-flash`) using multi-turn
     * conversation history, optional active training profile adaptation, and a 60-second timeout window.
     */
    suspend fun generateReply(
        sessionId: String,
        userContent: String,
        priorHistory: List<ChatMessage>,
        activeProfile: TrainingProfile? = null,
        activeQaPairs: List<TrainingQaPair> = emptyList()
    ): String {
        val cleanPrompt = MessageValidator.sanitize(userContent)
        require(cleanPrompt.isNotEmpty()) { "Prompt cannot be empty." }

        customResponder?.let { responder ->
            return withTimeout(INFERENCE_TIMEOUT_MS) {
                responder(cleanPrompt, priorHistory)
            }
        }

        return try {
            withTimeout(INFERENCE_TIMEOUT_MS) {
                val targetModel = modelForProfile(activeProfile, activeQaPairs)
                val chat = targetModel.startChat(
                    history = buildChatHistory(priorHistory, activeProfile, activeQaPairs)
                )
                val response = chat.sendMessage(cleanPrompt)
                val replyText = response.text?.trim().orEmpty()
                if (replyText.isEmpty()) {
                    throw IllegalStateException("Gemini returned an empty response.")
                }
                replyText
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini generation timed out for session $sessionId", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed for session $sessionId", e)
            throw e
        }
    }

    /**
     * Streams incremental text chunks from the Gemini model (`gemini-3.8-flash`) for multi-turn chat,
     * applying any active [TrainingProfile] and emitting accumulated partial responses in real time.
     */
    fun generateReplyStream(
        sessionId: String,
        userContent: String,
        priorHistory: List<ChatMessage>,
        activeProfile: TrainingProfile? = null,
        activeQaPairs: List<TrainingQaPair> = emptyList()
    ): Flow<String> = flow {
        val cleanPrompt = MessageValidator.sanitize(userContent)
        require(cleanPrompt.isNotEmpty()) { "Prompt cannot be empty." }

        customResponder?.let { responder ->
            val full = withTimeout(INFERENCE_TIMEOUT_MS) {
                responder(cleanPrompt, priorHistory)
            }
            emit(full)
            return@flow
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(INFERENCE_TIMEOUT_MS) {
                val targetModel = modelForProfile(activeProfile, activeQaPairs)
                val chat = targetModel.startChat(
                    history = buildChatHistory(priorHistory, activeProfile, activeQaPairs)
                )
                chat.sendMessageStream(cleanPrompt).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini returned an empty streaming response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini streaming timed out for session $sessionId", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI streaming failed for session $sessionId", e)
            throw e
        }
    }

    internal fun buildTrainedSystemInstruction(
        profile: TrainingProfile,
        qaPairs: List<TrainingQaPair>
    ): String {
        val topPairs = qaPairs
            .filter { it.qualityScore >= 7 }
            .sortedByDescending { it.qualityScore }
            .take(10)

        val examplesBlock = if (topPairs.isNotEmpty()) {
            buildString {
                appendLine("\nReference Q&A Training Examples (follow these facts and style closely):")
                topPairs.forEachIndexed { index, pair ->
                    appendLine("${index + 1}. User: ${pair.question}")
                    appendLine("   LEO: ${pair.answer}")
                }
            }
        } else {
            ""
        }

        return """
            $SYSTEM_INSTRUCTION
            
            --- ACTIVE CUSTOM TRAINING PROFILE: ${profile.name} ---
            Domain Focus: ${profile.domainDescription}
            Custom Instructions: ${profile.systemPrompt}
            $examplesBlock
        """.trimIndent()
    }

    private fun buildChatHistory(
        priorHistory: List<ChatMessage>,
        activeProfile: TrainingProfile?,
        activeQaPairs: List<TrainingQaPair>
    ): List<Content> {
        val fewShotTurns = if (activeProfile != null && activeProfile.isActive) {
            activeQaPairs
                .filter { it.qualityScore >= 8 }
                .sortedByDescending { it.qualityScore }
                .take(4)
                .flatMap { pair ->
                    listOf(
                        content("user") { text(pair.question) },
                        content("model") { text(pair.answer) }
                    )
                }
        } else {
            emptyList()
        }

        val conversationTurns = priorHistory
            .takeLast(MAX_HISTORY_TURNS)
            .mapNotNull { message ->
                val text = message.content.trim()
                if (text.isEmpty()) return@mapNotNull null
                val geminiRole = if (message.role == InputBoundsValidator.ROLE_ASSISTANT) {
                    "model"
                } else {
                    "user"
                }
                content(geminiRole) {
                    text(text)
                }
            }

        return fewShotTurns + conversationTurns
    }

    companion object {
        private const val TAG = "LeoCompanionEngine"
        const val MODEL_NAME = "gemini-3.8-flash"
        const val INFERENCE_TIMEOUT_MS = 60_000L
        private const val MAX_HISTORY_TURNS = 24

        private val SYSTEM_INSTRUCTION = """
            You are LEO, a warm, perceptive, and grounded AI companion.
            Answer the user's actual question or message directly, accurately, and thoughtfully.
            Never repeat canned templates or generic filler. Adapt your tone and depth naturally to what the user writes—whether they ask a technical question, want creative brainstorming, need help drafting a message, or type something brief or playful.
            Use clear formatting (concise paragraphs or bullet points when helpful) and keep your voice calm, genuine, and human-scale.
        """.trimIndent()
    }
}
