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
import com.leoai.app.ui.screens.WelcomeScreen
import com.leoai.app.ui.theme.LeoAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.parseColor("#FDFBF7"),
                android.graphics.Color.parseColor("#2D332C")
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.parseColor("#FDFBF7"),
                android.graphics.Color.parseColor("#2D332C")
            )
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
    val welcomeBusy by viewModel.welcomeBusy.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val chatLoading by viewModel.chatLoading.collectAsStateWithLifecycle()
    val chatSending by viewModel.chatSending.collectAsStateWithLifecycle()
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
                    onStartChat = viewModel::startChatFromWelcome,
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

            is LeoScreen.Settings -> {
                SettingsScreen(
                    clearing = clearingAll,
                    cleared = clearedBanner,
                    onClearAll = viewModel::clearAllConversations,
                    onBack = { viewModel.navigateBack() }
                )
            }
        }
    }
}
