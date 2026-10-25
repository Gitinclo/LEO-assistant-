package com.leoai.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leoai.app.R
import com.leoai.app.data.ChatSession
import com.leoai.app.data.groupSessionsByDate
import com.leoai.app.ui.theme.LeoColors
import com.leoai.app.ui.theme.LeoRadius

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessions: List<ChatSession>,
    loading: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onStartNewChat: () -> Unit,
    onBack: () -> Unit
) {
    val groups = remember(sessions) { groupSessionsByDate(sessions) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LeoColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("history-screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("history-back-button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = LeoColors.TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = stringResource(R.string.history_title),
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                color = LeoColors.TextPrimary
            )

            IconButton(
                onClick = onStartNewChat,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("new-chat-button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.cd_new_chat),
                    tint = LeoColors.Primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = LeoColors.Border)

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LeoColors.Primary)
            }
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (groups.isEmpty()) {
                        item(key = "empty-history") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.history_empty_title),
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 22.sp,
                                    color = LeoColors.TextPrimary
                                )
                                Text(
                                    text = stringResource(R.string.history_empty_hint),
                                    fontSize = 14.sp,
                                    color = LeoColors.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .padding(top = 16.dp)
                                        .clip(RoundedCornerShape(LeoRadius.Pill))
                                        .background(LeoColors.Primary)
                                        .clickable(onClick = onStartNewChat)
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                        .testTag("empty-new-chat-button"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = null,
                                        tint = LeoColors.PrimaryFg,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.history_empty_cta),
                                        color = LeoColors.PrimaryFg,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(groups, key = { it.label }) { group ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 600.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = group.label,
                                    fontSize = 11.sp,
                                    letterSpacing = 2.sp,
                                    color = LeoColors.TextMuted,
                                    modifier = Modifier.padding(start = 4.dp)
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.data.forEach { session ->
                                        SessionRowCard(
                                            session = session,
                                            onClick = { onSelectSession(session.id) },
                                            onDelete = { onDeleteSession(session.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRowCard(
    session: ChatSession,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(LeoRadius.Md),
        color = if (isPressed) LeoColors.SurfaceElevated else LeoColors.Surface,
        border = BorderStroke(1.dp, LeoColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session-row-${session.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title.ifBlank { "Untitled" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = LeoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (session.preview.isNotBlank()) {
                    Text(
                        text = session.preview,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = LeoColors.TextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete-session-${session.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_session),
                    tint = LeoColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
