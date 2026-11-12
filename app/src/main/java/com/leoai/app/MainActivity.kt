package com.leoai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leoai.app.ui.LeoScreen
import com.leoai.app.ui.LeoViewModel
import com.leoai.app.ui.screens.ChatScreen
import com.leoai.app.ui.screens.HistoryScreen
import com.leoai.app.ui.screens.SettingsScreen
import com.leoai.app.ui.screens.TrainAiScreen
import com.leoai.app.ui.screens.WelcomeScreen
import com.leoai.app.ui.theme.LeoAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        val bgLight = 0xFFFDFBF7.toInt()
        val textDark = 0xFF2D332C.toInt()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(bgLight, textDark),
            navigationBarStyle = SystemBarStyle.light(bgLight, textDark)
        )
        setContent {
            LeoAITheme {
                LeoApp()
            }
        }
    }
}

@Composable
fun LeoApp(viewModel: LeoViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeTrainingProfile.collectAsStateWithLifecycle()
    val selectedTrainingProfile by viewModel.selectedTrainingProfile.collectAsStateWithLifecycle()
    val trainingQaPairs by viewModel.trainingQaPairs.collectAsStateWithLifecycle()
    val ingestedDocuments by viewModel.ingestedDocuments.collectAsStateWithLifecycle()
    val selectedOllamaTeacher by viewModel.selectedOllamaTeacher.collectAsStateWithLifecycle()
    val ollamaEndpoint by viewModel.ollamaEndpoint.collectAsStateWithLifecycle()
    val lastOrchestrationResult by viewModel.lastOrchestrationResult.collectAsStateWithLifecycle()
    val qloraPlan by viewModel.qloraPlan.collectAsStateWithLifecycle()
    val sideBySideEval by viewModel.sideBySideEval.collectAsStateWithLifecycle()
    val trainingBusy by viewModel.trainingBusy.collectAsStateWithLifecycle()
    val trainingStatusBanner by viewModel.trainingStatusBanner.collectAsStateWithLifecycle()
    val trainingError by viewModel.trainingError.collectAsStateWithLifecycle()
    val welcomeBusy by viewModel.welcomeBusy.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val chatLoading by viewModel.chatLoading.collectAsStateWithLifecycle()
    val chatSending by viewModel.chatSending.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val historyLoading by viewModel.historyLoading.collectAsStateWithLifecycle()
    val historyRefreshing by viewModel.historyRefreshing.collectAsStateWithLifecycle()
    val clearingAll by viewModel.clearingAll.collectAsStateWithLifecycle()
    val clearedBanner by viewModel.clearedBanner.collectAsStateWithLifecycle()

    BackHandler(enabled = currentScreen != LeoScreen.Welcome) {
        viewModel.navigateBack()
    }

    Crossfade(
        targetState = currentScreen,
        animationSpec = tween(durationMillis = 220),
        label = "leo_screen_fade"
    ) { screen ->
        when (screen) {
            is LeoScreen.Welcome -> {
                WelcomeScreen(
                    busy = welcomeBusy,
                    activeProfileName = activeProfile?.takeIf { it.isActive }?.name,
                    onStartChat = viewModel::startChatFromWelcome,
                    onOpenTrainAi = viewModel::navigateToTrainAi,
                    onOpenHistory = viewModel::navigateToHistory,
                    onOpenSettings = viewModel::navigateToSettings
                )
            }

            is LeoScreen.Chat -> {
                ChatScreen(
                    sessionId = screen.sessionId,
                    initialPrompt = screen.initialPrompt,
                    messages = chatMessages,
                    loading = chatLoading,
                    sending = chatSending,
                    errorMessage = chatError,
                    onDismissError = viewModel::clearChatError,
                    onLoadSession = viewModel::loadSessionMessages,
                    onSendMessage = viewModel::sendMessage,
                    onBack = { viewModel.navigateBack() },
                    onOpenHistory = viewModel::navigateToHistory
                )
            }

            is LeoScreen.History -> {
                HistoryScreen(
                    sessions = sessions,
                    loading = historyLoading,
                    refreshing = historyRefreshing,
                    onRefresh = viewModel::refreshHistory,
                    onSelectSession = viewModel::openExistingSession,
                    onDeleteSession = viewModel::deleteSession,
                    onStartNewChat = viewModel::startNewBlankChat,
                    onBack = { viewModel.navigateBack() }
                )
            }

            is LeoScreen.TrainAi -> {
                TrainAiScreen(
                    profile = selectedTrainingProfile,
                    qaPairs = trainingQaPairs,
                    ingestedDocuments = ingestedDocuments,
                    selectedOllamaTeacher = selectedOllamaTeacher,
                    ollamaEndpoint = ollamaEndpoint,
                    lastOrchestrationResult = lastOrchestrationResult,
                    qloraPlan = qloraPlan,
                    sideBySideEval = sideBySideEval,
                    busy = trainingBusy,
                    statusBanner = trainingStatusBanner,
                    errorMessage = trainingError,
                    onUpdateProfileFields = viewModel::updateTrainingProfileFields,
                    onToggleActiveInChat = viewModel::toggleProfileActiveInChat,
                    onIngestDocument = viewModel::ingestRawDocument,
                    onDeleteDocument = viewModel::deleteIngestedDocument,
                    onSelectOllamaTeacher = viewModel::selectOllamaTeacherModel,
                    onUpdateOllamaEndpoint = viewModel::updateOllamaEndpoint,
                    onRunOllamaOrchestrator = viewModel::runOllamaDatasetOrchestration,
                    onGenerateSyntheticDataset = viewModel::generateSyntheticDatasetWithTeacherAi,
                    onRunAiJudgeAudit = viewModel::runAiJudgeDatasetAudit,
                    onPruneLowQuality = viewModel::pruneLowQualityQaPairs,
                    onAddManualQaPair = viewModel::addManualQaPair,
                    onDeleteQaPair = viewModel::deleteQaPair,
                    onGenerateQloraPipeline = viewModel::generateQloraPipelineForHardware,
                    onRunSideBySideEval = viewModel::runSideBySideEvaluation,
                    onDismissFeedback = viewModel::clearTrainingFeedback,
                    onBack = { viewModel.navigateBack() }
                )
            }

            is LeoScreen.Settings -> {
                SettingsScreen(
                    clearing = clearingAll,
                    cleared = clearedBanner,
                    onOpenTrainAi = viewModel::navigateToTrainAi,
                    onClearAll = viewModel::clearAllConversations,
                    onBack = { viewModel.navigateBack() }
                )
            }
        }
    }
}
