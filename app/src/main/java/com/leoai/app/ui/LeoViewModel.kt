package com.leoai.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leoai.app.data.ChatMessage
import com.leoai.app.data.ChatSession
import com.leoai.app.data.HardwarePreset
import com.leoai.app.data.IngestedDocument
import com.leoai.app.data.InputBoundsValidator
import com.leoai.app.data.LeoRepository
import com.leoai.app.data.MessageValidator
import com.leoai.app.data.OllamaDatasetOrchestrator
import com.leoai.app.data.OllamaOrchestrationResult
import com.leoai.app.data.OllamaTeacherModel
import com.leoai.app.data.QloraPipelinePlan
import com.leoai.app.data.SideBySideEvaluation
import com.leoai.app.data.TrainingProfile
import com.leoai.app.data.TrainingQaPair
import com.leoai.app.data.nowIsoString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "LeoViewModel"

sealed interface LeoScreen {
    data object Welcome : LeoScreen
    data class Chat(val sessionId: String, val initialPrompt: String? = null) : LeoScreen
    data object History : LeoScreen
    data object TrainAi : LeoScreen
    data object Settings : LeoScreen
}

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Streaming(val text: String) : AiUiState
    data class Success(val text: String) : AiUiState
    data class Error(val message: String) : AiUiState
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

    /**
     * Reactive StateFlow of the currently active [TrainingProfile] (if any).
     */
    val activeTrainingProfile: StateFlow<TrainingProfile?> = repository.activeTrainingProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _selectedTrainingProfile = MutableStateFlow<TrainingProfile?>(null)
    val selectedTrainingProfile: StateFlow<TrainingProfile?> = _selectedTrainingProfile.asStateFlow()

    private val _trainingQaPairs = MutableStateFlow<List<TrainingQaPair>>(emptyList())
    val trainingQaPairs: StateFlow<List<TrainingQaPair>> = _trainingQaPairs.asStateFlow()

    private val _ingestedDocuments = MutableStateFlow<List<IngestedDocument>>(emptyList())
    val ingestedDocuments: StateFlow<List<IngestedDocument>> = _ingestedDocuments.asStateFlow()

    private val _selectedOllamaTeacher = MutableStateFlow(OllamaTeacherModel.LLAMA_3_3)
    val selectedOllamaTeacher: StateFlow<OllamaTeacherModel> = _selectedOllamaTeacher.asStateFlow()

    private val _ollamaEndpoint = MutableStateFlow(OllamaDatasetOrchestrator.DEFAULT_OLLAMA_ENDPOINT)
    val ollamaEndpoint: StateFlow<String> = _ollamaEndpoint.asStateFlow()

    private val _lastOrchestrationResult = MutableStateFlow<OllamaOrchestrationResult?>(null)
    val lastOrchestrationResult: StateFlow<OllamaOrchestrationResult?> = _lastOrchestrationResult.asStateFlow()

    private val _qloraPlan = MutableStateFlow<QloraPipelinePlan?>(null)
    val qloraPlan: StateFlow<QloraPipelinePlan?> = _qloraPlan.asStateFlow()

    private val _sideBySideEval = MutableStateFlow<SideBySideEvaluation?>(null)
    val sideBySideEval: StateFlow<SideBySideEvaluation?> = _sideBySideEval.asStateFlow()

    private val _trainingBusy = MutableStateFlow(false)
    val trainingBusy: StateFlow<Boolean> = _trainingBusy.asStateFlow()

    private val _trainingStatusBanner = MutableStateFlow<String?>(null)
    val trainingStatusBanner: StateFlow<String?> = _trainingStatusBanner.asStateFlow()

    private val _trainingError = MutableStateFlow<String?>(null)
    val trainingError: StateFlow<String?> = _trainingError.asStateFlow()

    private val _welcomeBusy = MutableStateFlow(false)
    val welcomeBusy: StateFlow<Boolean> = _welcomeBusy.asStateFlow()

    private val _roomMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _streamingAssistantMessage = MutableStateFlow<ChatMessage?>(null)

    /**
     * Combines persisted Room messages with any active streaming assistant response so the user
     * sees live tokens from Gemini (`gemini-3.8-flash`) as they arrive.
     */
    val chatMessages: StateFlow<List<ChatMessage>> = combine(
        _roomMessages,
        _streamingAssistantMessage
    ) { persisted, streaming ->
        if (streaming != null) persisted + streaming else persisted
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val _aiUiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiUiState: StateFlow<AiUiState> = _aiUiState.asStateFlow()

    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading.asStateFlow()

    private val _chatSending = MutableStateFlow(false)
    val chatSending: StateFlow<Boolean> = _chatSending.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    private val _historyRefreshing = MutableStateFlow(false)
    val historyRefreshing: StateFlow<Boolean> = _historyRefreshing.asStateFlow()

    private val _clearingAll = MutableStateFlow(false)
    val clearingAll: StateFlow<Boolean> = _clearingAll.asStateFlow()

    private val _clearedBanner = MutableStateFlow(false)
    val clearedBanner: StateFlow<Boolean> = _clearedBanner.asStateFlow()

    private var activeSessionMessagesJob: Job? = null
    private var activeProfilePairsJob: Job? = null
    private var activeProfileDocsJob: Job? = null

    init {
        viewModelScope.launch {
            val starter = repository.ensureStarterTrainingProfile()
            _selectedTrainingProfile.value = starter
            observeQaPairsForProfile(starter.id)
            observeIngestedDocumentsForProfile(starter.id)
        }
    }

    private fun observeQaPairsForProfile(profileId: String) {
        activeProfilePairsJob?.cancel()
        activeProfilePairsJob = viewModelScope.launch {
            repository.getQaPairsForProfile(profileId).collect { pairs ->
                _trainingQaPairs.value = pairs
            }
        }
    }

    private fun observeIngestedDocumentsForProfile(profileId: String) {
        activeProfileDocsJob?.cancel()
        activeProfileDocsJob = viewModelScope.launch {
            repository.getIngestedDocumentsForProfile(profileId).collect { docs ->
                _ingestedDocuments.value = docs
            }
        }
    }

    fun navigateToHistory() {
        pushScreen(LeoScreen.History)
    }

    fun navigateToTrainAi() {
        _trainingError.value = null
        pushScreen(LeoScreen.TrainAi)
    }

    fun navigateToSettings() {
        pushScreen(LeoScreen.Settings)
    }

    fun navigateBack(): Boolean {
        val current = _screenStack.value
        if (current.size <= 1) return false
        _chatError.value = null
        _trainingError.value = null
        _streamingAssistantMessage.value = null
        _screenStack.value = current.dropLast(1)
        return true
    }

    private fun pushScreen(screen: LeoScreen) {
        _screenStack.value = _screenStack.value + screen
    }

    fun clearChatError() {
        _chatError.value = null
        if (_aiUiState.value is AiUiState.Error) {
            _aiUiState.value = AiUiState.Idle
        }
    }

    fun clearTrainingFeedback() {
        _trainingError.value = null
        _trainingStatusBanner.value = null
    }

    fun updateTrainingProfileFields(
        name: String,
        domainDescription: String,
        sourceNotes: String,
        systemPrompt: String,
        hardwareTarget: String
    ) {
        val current = _selectedTrainingProfile.value ?: return
        val updated = current.copy(
            name = name,
            domainDescription = domainDescription,
            sourceNotes = sourceNotes,
            systemPrompt = systemPrompt,
            hardwareTarget = hardwareTarget
        )
        _selectedTrainingProfile.value = updated
        viewModelScope.launch {
            repository.saveTrainingProfile(updated)
            _trainingStatusBanner.value = "Saved encrypted profile to Room database."
        }
    }

    fun toggleProfileActiveInChat(active: Boolean) {
        val current = _selectedTrainingProfile.value ?: return
        val updated = current.copy(isActive = active)
        _selectedTrainingProfile.value = updated
        viewModelScope.launch {
            repository.setTrainingProfileActive(current.id, active)
            _trainingStatusBanner.value = if (active) {
                "Active in LEO Chat: LEO now uses '${updated.name}' and its Q&A dataset."
            } else {
                "Switched LEO back to default base companion mode."
            }
        }
    }

    fun generateSyntheticDatasetWithTeacherAi(
        name: String,
        domainDescription: String,
        sourceNotes: String
    ) {
        val current = _selectedTrainingProfile.value ?: return
        if (_trainingBusy.value) return
        val draft = current.copy(
            name = name.ifBlank { current.name },
            domainDescription = domainDescription.ifBlank { current.domainDescription },
            sourceNotes = sourceNotes.ifBlank { current.sourceNotes }
        )

        viewModelScope.launch {
            _trainingBusy.value = true
            _trainingError.value = null
            _trainingStatusBanner.value = null
            try {
                val (updatedProfile, pairs) = repository.generateSyntheticDataset(draft, pairCount = 6)
                _selectedTrainingProfile.value = updatedProfile
                _trainingStatusBanner.value =
                    "Teacher AI generated and graded ${pairs.size} Q&A pairs (encrypted in Room)."
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Teacher AI dataset generation timed out", e)
                _trainingError.value = "Teacher AI timed out after 60 seconds. Please try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Teacher AI dataset generation failed", e)
                _trainingError.value = e.localizedMessage ?: "Failed to generate synthetic Q&A dataset."
            } finally {
                _trainingBusy.value = false
            }
        }
    }

    fun runAiJudgeDatasetAudit() {
        val current = _selectedTrainingProfile.value ?: return
        if (_trainingBusy.value) return

        viewModelScope.launch {
            _trainingBusy.value = true
            _trainingError.value = null
            _trainingStatusBanner.value = null
            try {
                val audited = repository.auditDatasetWithJudge(current)
                val avg = if (audited.isNotEmpty()) {
                    String.format(java.util.Locale.US, "%.1f", audited.map { it.qualityScore }.average())
                } else {
                    "0.0"
                }
                _trainingStatusBanner.value =
                    "AI-as-a-Judge audited ${audited.size} pairs · Average quality score: $avg/10"
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI Judge audit timed out", e)
                _trainingError.value = "AI Judge audit timed out after 60 seconds."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Judge audit failed", e)
                _trainingError.value = e.localizedMessage ?: "AI Judge audit failed."
            } finally {
                _trainingBusy.value = false
            }
        }
    }

    fun pruneLowQualityQaPairs(minScore: Int = 8) {
        val current = _selectedTrainingProfile.value ?: return
        viewModelScope.launch {
            repository.pruneLowQualityPairs(current.id, minScore)
            _trainingStatusBanner.value = "Filtered out Q&A pairs below $minScore/10."
        }
    }

    fun addManualQaPair(question: String, answer: String) {
        val current = _selectedTrainingProfile.value ?: return
        val qValid = MessageValidator.validate(question, maxLength = 1_500)
        val aValid = MessageValidator.validate(answer, maxLength = MessageValidator.MAX_MESSAGE_LENGTH)
        if (!qValid.isValid || !aValid.isValid) return

        viewModelScope.launch {
            repository.addManualQaPair(current.id, qValid.sanitizedContent, aValid.sanitizedContent)
            _trainingStatusBanner.value = "Added custom Q&A pair to encrypted Room dataset."
        }
    }

    fun deleteQaPair(pairId: String) {
        viewModelScope.launch {
            repository.deleteQaPair(pairId)
        }
    }

    fun selectOllamaTeacherModel(model: OllamaTeacherModel) {
        _selectedOllamaTeacher.value = model
    }

    fun updateOllamaEndpoint(endpoint: String) {
        _ollamaEndpoint.value = endpoint
    }

    fun ingestRawDocument(
        fileName: String,
        sourcePath: String,
        rawContent: String
    ) {
        val current = _selectedTrainingProfile.value ?: return
        val contentValidation = MessageValidator.validate(rawContent, maxLength = 32_000, truncateIfExceeded = true)
        if (!contentValidation.isValid) {
            _trainingError.value = contentValidation.errorMessage ?: "Raw document text cannot be empty."
            return
        }

        viewModelScope.launch {
            _trainingError.value = null
            try {
                val doc = repository.ingestRawDocument(
                    profileId = current.id,
                    fileName = fileName,
                    sourcePath = sourcePath,
                    rawContent = contentValidation.sanitizedContent
                )
                _trainingStatusBanner.value =
                    "Ingested '${doc.fileName}' into Room (${doc.chunkCount} chunks · ${doc.charCount} chars · AES-256-GCM encrypted)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Document ingestion failed", e)
                _trainingError.value = e.localizedMessage ?: "Failed to ingest document into Room."
            }
        }
    }

    fun deleteIngestedDocument(documentId: String) {
        viewModelScope.launch {
            repository.deleteIngestedDocument(documentId)
            _trainingStatusBanner.value = "Removed document from Room vault."
        }
    }

    fun runOllamaDatasetOrchestration() {
        val current = _selectedTrainingProfile.value ?: return
        if (_trainingBusy.value) return

        viewModelScope.launch {
            _trainingBusy.value = true
            _trainingError.value = null
            _trainingStatusBanner.value = null
            try {
                val result = repository.orchestrateOllamaDatasetGeneration(
                    profile = current,
                    teacherModel = _selectedOllamaTeacher.value,
                    ollamaEndpoint = _ollamaEndpoint.value
                )
                _lastOrchestrationResult.value = result
                _trainingStatusBanner.value =
                    "Ollama Prompt Flow (${result.teacherModel}): Processed ${result.documentsProcessed} docs (${result.chunksProcessed} chunks) → Added ${result.keptPairs.size} verified Q&A pairs to Room."
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Ollama dataset orchestration timed out", e)
                _trainingError.value = "Ollama prompt flow timed out after 60 seconds."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Ollama dataset orchestration failed", e)
                _trainingError.value = e.localizedMessage ?: "Ollama dataset orchestration failed."
            } finally {
                _trainingBusy.value = false
            }
        }
    }

    fun generateQloraPipelineForHardware(hardware: HardwarePreset) {
        val current = _selectedTrainingProfile.value ?: return
        if (_trainingBusy.value) return

        viewModelScope.launch {
            _trainingBusy.value = true
            _trainingError.value = null
            _trainingStatusBanner.value = null
            try {
                val (updatedProfile, plan) = repository.generateQloraPipeline(current, hardware)
                _selectedTrainingProfile.value = updatedProfile
                _qloraPlan.value = plan
                _trainingStatusBanner.value =
                    "Configured Unsloth 4-bit QLoRA pipeline for ${plan.recommendedModel}."
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "QLoRA pipeline generation timed out", e)
                _trainingError.value = "QLoRA configuration timed out after 60 seconds."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "QLoRA pipeline generation failed", e)
                _trainingError.value = e.localizedMessage ?: "Failed to generate QLoRA pipeline."
            } finally {
                _trainingBusy.value = false
            }
        }
    }

    fun runSideBySideEvaluation(testPrompt: String) {
        val current = _selectedTrainingProfile.value ?: return
        val validation = MessageValidator.validate(testPrompt)
        if (!validation.isValid || _trainingBusy.value) return

        viewModelScope.launch {
            _trainingBusy.value = true
            _trainingError.value = null
            _trainingStatusBanner.value = null
            try {
                val result = repository.evaluateSideBySide(current, validation.sanitizedContent)
                _sideBySideEval.value = result
                _trainingStatusBanner.value =
                    "AI Judge Score: Trained ${result.trainedScore}/10 vs Base ${result.baseScore}/10"
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Side-by-side evaluation timed out", e)
                _trainingError.value = "Side-by-side evaluation timed out after 60 seconds."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Side-by-side evaluation failed", e)
                _trainingError.value = e.localizedMessage ?: "Side-by-side evaluation failed."
            } finally {
                _trainingBusy.value = false
            }
        }
    }

    fun startChatFromWelcome(prompt: String) {
        val validation = MessageValidator.validate(prompt)
        if (!validation.isValid || _welcomeBusy.value) return
        val text = validation.sanitizedContent
        viewModelScope.launch {
            _welcomeBusy.value = true
            try {
                val session = repository.createSession()
                _roomMessages.value = emptyList()
                _streamingAssistantMessage.value = null
                _chatError.value = null
                pushScreen(LeoScreen.Chat(sessionId = session.id, initialPrompt = text))
            } finally {
                _welcomeBusy.value = false
            }
        }
    }

    fun startNewBlankChat() {
        viewModelScope.launch {
            val session = repository.createSession()
            _roomMessages.value = emptyList()
            _streamingAssistantMessage.value = null
            _chatError.value = null
            pushScreen(LeoScreen.Chat(sessionId = session.id, initialPrompt = null))
        }
    }

    fun openExistingSession(sessionId: String) {
        _roomMessages.value = emptyList()
        _streamingAssistantMessage.value = null
        _chatError.value = null
        pushScreen(LeoScreen.Chat(sessionId = sessionId, initialPrompt = null))
    }

    fun loadSessionMessages(sessionId: String, initialPrompt: String?) {
        activeSessionMessagesJob?.cancel()
        activeSessionMessagesJob = viewModelScope.launch {
            _chatLoading.value = true
            _chatError.value = null

            // Read snapshot to decide if initial prompt should be submitted
            val existing = repository.getMessagesSnapshot(sessionId)
            _roomMessages.value = existing
            _chatLoading.value = false

            if (!initialPrompt.isNullOrBlank() && existing.isEmpty()) {
                sendMessage(sessionId, initialPrompt)
            }

            // Observe Room messages reactively
            repository.getMessagesForSession(sessionId).collect { messages ->
                _roomMessages.value = messages
            }
        }
    }

    fun sendMessage(sessionId: String, rawText: String) {
        val validation = MessageValidator.validate(rawText)
        if (!validation.isValid || _chatSending.value) return
        val content = validation.sanitizedContent

        viewModelScope.launch {
            _chatSending.value = true
            _chatError.value = null
            _aiUiState.value = AiUiState.Loading

            val streamingMsgId = "streaming-${System.currentTimeMillis()}"

            try {
                val response = repository.sendMessage(
                    sessionId = sessionId,
                    rawContent = content,
                    onStreamingUpdate = { partialText ->
                        _aiUiState.value = AiUiState.Streaming(partialText)
                        _streamingAssistantMessage.value = ChatMessage(
                            id = streamingMsgId,
                            sessionId = sessionId,
                            role = InputBoundsValidator.ROLE_ASSISTANT,
                            content = partialText,
                            createdAt = nowIsoString()
                        )
                    }
                )
                _streamingAssistantMessage.value = null
                _aiUiState.value = AiUiState.Success(response.assistantMessage.content)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini generation timed out", e)
                _streamingAssistantMessage.value = null
                val timeoutMsg = "Generation timed out after 60 seconds. Please try again."
                _aiUiState.value = AiUiState.Error(timeoutMsg)
                _chatError.value = timeoutMsg
            } catch (e: CancellationException) {
                // ALWAYS rethrow CancellationException to preserve cooperative coroutine cancellation
                _streamingAssistantMessage.value = null
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI generation failed", e)
                _streamingAssistantMessage.value = null
                val errMsg = e.localizedMessage ?: "Failed to generate a response from Gemini."
                _aiUiState.value = AiUiState.Error(errMsg)
                _chatError.value = errMsg
            } finally {
                _streamingAssistantMessage.value = null
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
