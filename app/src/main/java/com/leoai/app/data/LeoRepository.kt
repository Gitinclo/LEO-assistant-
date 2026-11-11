package com.leoai.app.data

import android.content.Context
import com.leoai.app.data.local.LeoDatabase
import com.leoai.app.data.local.dao.ChatDao
import com.leoai.app.data.local.dao.TrainingDao
import com.leoai.app.data.local.entity.ChatMessageEntity
import com.leoai.app.data.local.entity.ChatSessionEntity
import com.leoai.app.data.local.entity.TrainingProfileEntity
import com.leoai.app.data.local.entity.TrainingQaPairEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository pattern abstracting Room database operations, AES-256-GCM encryption at rest,
 * real Firebase AI Logic (`gemini-3.8-flash`) chat inference, and the Train My AI Studio pipeline.
 */
class LeoRepository(
    context: Context,
    private val companionEngine: LeoCompanionEngine = LeoCompanionEngine(),
    private val trainingEngine: LeoTrainingEngine = LeoTrainingEngine(),
    private val chatDao: ChatDao = LeoDatabase.getInstance(context).chatDao(),
    private val messageDao: ChatMessageDao = LeoDatabase.getInstance(context).chatMessageDao(),
    private val trainingDao: TrainingDao = LeoDatabase.getInstance(context).trainingDao()
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
     * Reactive stream of all AES-256-GCM encrypted training profiles decrypted to domain objects.
     */
    val allTrainingProfiles: Flow<List<TrainingProfile>> = trainingDao.getAllProfiles().map { list ->
        list.map { it.toDomain() }
    }

    /**
     * Reactive stream of the currently active [TrainingProfile] (if any) applied to LEO chat.
     */
    val activeTrainingProfile: Flow<TrainingProfile?> = trainingDao.getActiveProfile().map { entity ->
        entity?.toDomain()
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

    /**
     * Reactive stream of decrypted Q&A pairs for a training profile ordered by qualityScore descending.
     */
    fun getQaPairsForProfile(profileId: String): Flow<List<TrainingQaPair>> {
        val safeId = InputBoundsValidator.sanitizeId(profileId)
        return trainingDao.getQaPairsForProfile(safeId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun ensureStarterTrainingProfile(): TrainingProfile = withContext(Dispatchers.IO) {
        val existing = trainingDao.getAllProfiles().first()
        if (existing.isNotEmpty()) {
            return@withContext existing.first().toDomain()
        }

        val hardware = HardwarePreset.RTX_16GB
        val defaultSystem = "You are LEO trained as an Executive Communication & Technical Clarity Coach. Give structured, plainspoken answers with concrete examples, zero fluff, and calm authority."
        val starterProfile = TrainingProfile(
            name = "Executive & Technical Coach",
            domainDescription = "Calm, high-signal technical architecture notes, code review habits, and executive communication guidelines.",
            sourceNotes = """
                1. When explaining complex technical systems, always lead with the 1-sentence core invariant before listing trade-offs.
                2. In code reviews or architecture docs, prefer explicit state machines and small composable functions over deep inheritance hierarchies.
                3. When replying to tense stakeholder messages, acknowledge the shared goal first, state verified facts second, and propose one concrete next step with a timestamp.
            """.trimIndent(),
            systemPrompt = defaultSystem,
            hardwareTarget = hardware.id,
            recommendedBaseModel = hardware.defaultStudentModel,
            qloraScript = TrainingDatasetExporter.buildDefaultQloraScript(
                profileName = "Executive & Technical Coach",
                baseModel = hardware.defaultStudentModel,
                systemPrompt = defaultSystem,
                loraRank = hardware.defaultLoraRank,
                loraAlpha = hardware.defaultLoraRank,
                batchSize = hardware.defaultBatchSize,
                gradAccum = 4,
                learningRate = "2e-4",
                epochs = 3
            ),
            ollamaModelfile = TrainingDatasetExporter.buildDefaultOllamaModelfile(
                profileName = "Executive & Technical Coach",
                systemPrompt = defaultSystem
            ),
            isActive = true
        )

        trainingDao.upsertProfile(TrainingProfileEntity.fromDomain(starterProfile))

        val starterPairs = listOf(
            TrainingQaPair(
                profileId = starterProfile.id,
                question = "How should I structure an architecture proposal for a skeptical team?",
                answer = "Start with the single core invariant your system must guarantee. Then present a 3-row trade-off matrix (Chosen Approach, Why It Wins, What We Give Up) so reviewers focus on engineering trade-offs rather than stylistic preferences.",
                qualityScore = 10,
                judgeRationale = "Directly reflects the 1-sentence invariant rule and concrete structure."
            ),
            TrainingQaPair(
                profileId = starterProfile.id,
                question = "A stakeholder sent an urgent message asking why a release slipped. How do I reply?",
                answer = "Acknowledge the shared launch priority first, state the two verified technical facts causing the delay without defensive language, and propose the single concrete recovery milestone with an exact time for the next update.",
                qualityScore = 9,
                judgeRationale = "Follows the 3-part stakeholder de-escalation rubric accurately."
            ),
            TrainingQaPair(
                profileId = starterProfile.id,
                question = "Should we use a deep base class hierarchy for our UI screens?",
                answer = "Avoid deep inheritance hierarchies. Prefer explicit state machines (`sealed interface UiState`) paired with small, composable functions so state transitions remain predictable and testable.",
                qualityScore = 9,
                judgeRationale = "Grounded directly in rule #2 on state machines and composition."
            )
        )
        trainingDao.insertQaPairs(starterPairs.map { TrainingQaPairEntity.fromDomain(it) })
        starterProfile
    }

    suspend fun saveTrainingProfile(profile: TrainingProfile): TrainingProfile = withContext(Dispatchers.IO) {
        val updated = profile.copy(updatedAt = nowIsoString())
        if (updated.isActive) {
            trainingDao.deactivateAllProfiles()
        }
        trainingDao.upsertProfile(TrainingProfileEntity.fromDomain(updated))
        updated
    }

    suspend fun setTrainingProfileActive(profileId: String, active: Boolean) = withContext(Dispatchers.IO) {
        val safeId = InputBoundsValidator.sanitizeId(profileId)
        if (active) {
            trainingDao.deactivateAllProfiles()
        }
        trainingDao.setProfileActive(safeId, active)
    }

    suspend fun generateSyntheticDataset(
        profile: TrainingProfile,
        pairCount: Int = 6
    ): Pair<TrainingProfile, List<TrainingQaPair>> = withContext(Dispatchers.IO) {
        val generated = trainingEngine.generateSyntheticDataset(
            profileId = profile.id,
            profileName = profile.name,
            domainDescription = profile.domainDescription,
            sourceNotes = profile.sourceNotes,
            pairCount = pairCount
        )
        val updatedProfile = profile.copy(
            systemPrompt = generated.systemPrompt,
            updatedAt = nowIsoString()
        )
        trainingDao.upsertProfile(TrainingProfileEntity.fromDomain(updatedProfile))
        trainingDao.insertQaPairs(generated.pairs.map { TrainingQaPairEntity.fromDomain(it) })
        val allPairs = trainingDao.getQaPairsForProfileSnapshot(profile.id).map { it.toDomain() }
        updatedProfile to allPairs
    }

    suspend fun auditDatasetWithJudge(profile: TrainingProfile): List<TrainingQaPair> = withContext(Dispatchers.IO) {
        val existing = trainingDao.getQaPairsForProfileSnapshot(profile.id).map { it.toDomain() }
        if (existing.isEmpty()) return@withContext emptyList()
        val audited = trainingEngine.auditDatasetWithJudge(
            domainDescription = profile.domainDescription,
            pairs = existing
        )
        trainingDao.insertQaPairs(audited.map { TrainingQaPairEntity.fromDomain(it) })
        audited
    }

    suspend fun pruneLowQualityPairs(profileId: String, minScore: Int = 8) = withContext(Dispatchers.IO) {
        val safeId = InputBoundsValidator.sanitizeId(profileId)
        trainingDao.pruneQaPairsBelowScore(safeId, minScore)
    }

    suspend fun addManualQaPair(
        profileId: String,
        question: String,
        answer: String
    ): TrainingQaPair = withContext(Dispatchers.IO) {
        val pair = TrainingQaPair(
            profileId = InputBoundsValidator.sanitizeId(profileId),
            question = MessageValidator.sanitize(question, 1_500),
            answer = MessageValidator.sanitize(answer, MessageValidator.MAX_MESSAGE_LENGTH),
            qualityScore = 9,
            judgeRationale = "User-authored gold standard Q&A example."
        )
        trainingDao.insertQaPair(TrainingQaPairEntity.fromDomain(pair))
        pair
    }

    suspend fun deleteQaPair(pairId: String) = withContext(Dispatchers.IO) {
        trainingDao.deleteQaPairById(InputBoundsValidator.sanitizeId(pairId))
    }

    suspend fun generateQloraPipeline(
        profile: TrainingProfile,
        hardware: HardwarePreset
    ): Pair<TrainingProfile, QloraPipelinePlan> = withContext(Dispatchers.IO) {
        val pairCount = trainingDao.getQaPairsForProfileSnapshot(profile.id).size
        val plan = trainingEngine.generateQloraPipeline(
            profile = profile,
            hardware = hardware,
            datasetCount = pairCount
        )
        val updatedProfile = profile.copy(
            hardwareTarget = hardware.id,
            recommendedBaseModel = plan.recommendedModel,
            qloraScript = plan.pythonScript,
            ollamaModelfile = plan.ollamaModelfile,
            updatedAt = nowIsoString()
        )
        trainingDao.upsertProfile(TrainingProfileEntity.fromDomain(updatedProfile))
        updatedProfile to plan
    }

    suspend fun evaluateSideBySide(
        profile: TrainingProfile,
        testPrompt: String
    ): SideBySideEvaluation = withContext(Dispatchers.IO) {
        val pairs = trainingDao.getQaPairsForProfileSnapshot(profile.id).map { it.toDomain() }
        trainingEngine.evaluateSideBySide(
            profile = profile,
            qaPairs = pairs,
            testPrompt = testPrompt
        )
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

    /**
     * Validates, encrypts, and stores the user's message in Room, then invokes the real Gemini API
     * (`gemini-3.8-flash`) via [LeoCompanionEngine] (automatically injecting any active
     * [TrainingProfile] and its Q&A pairs), and persists the encrypted assistant reply in Room.
     */
    suspend fun sendMessage(
        sessionId: String,
        rawContent: String,
        onStreamingUpdate: ((String) -> Unit)? = null
    ): SendMessageResponse = withContext(Dispatchers.IO) {
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
        val activeProfile = trainingDao.getActiveProfileSnapshot()?.toDomain()
        val activeQaPairs = if (activeProfile != null && activeProfile.isActive) {
            trainingDao.getQaPairsForProfileSnapshot(activeProfile.id).map { it.toDomain() }
        } else {
            emptyList()
        }

        val userMsg = MessageValidator.sanitizeChatMessage(
            ChatMessage(
                sessionId = safeSessionId,
                role = InputBoundsValidator.ROLE_USER,
                content = content
            )
        )
        messageDao.insertMessage(ChatMessageEntity.fromDomain(userMsg))

        val rawAssistantReply = if (onStreamingUpdate != null) {
            var latestText = ""
            companionEngine.generateReplyStream(
                sessionId = safeSessionId,
                userContent = content,
                priorHistory = priorMessages,
                activeProfile = activeProfile,
                activeQaPairs = activeQaPairs
            ).collect { accumulatedChunk ->
                latestText = accumulatedChunk
                onStreamingUpdate(accumulatedChunk)
            }
            latestText
        } else {
            companionEngine.generateReply(
                sessionId = safeSessionId,
                userContent = content,
                priorHistory = priorMessages,
                activeProfile = activeProfile,
                activeQaPairs = activeQaPairs
            )
        }

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
