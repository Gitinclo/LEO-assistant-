package com.leoai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leoai.app.R
import com.leoai.app.data.ChatMessage
import com.leoai.app.data.MessageValidator
import com.leoai.app.ui.components.LeoAvatar
import com.leoai.app.ui.components.TypingDotsIndicator
import com.leoai.app.ui.theme.LeoColors
import com.leoai.app.ui.theme.LeoRadius

@Composable
fun ChatScreen(
    sessionId: String,
    initialPrompt: String?,
    messages: List<ChatMessage>,
    loading: Boolean,
    sending: Boolean,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    onLoadSession: (String, String?) -> Unit,
    onSendMessage: (String, String) -> Unit,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit
) {
    var input by rememberSaveable(sessionId) { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(sessionId) {
        onLoadSession(sessionId, initialPrompt)
    }

    LaunchedEffect(messages.size, sending) {
        val totalItems = messages.size + if (sending) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val sendCurrentInput = {
        val validation = MessageValidator.validate(input)
        if (validation.isValid && !sending) {
            input = ""
            onSendMessage(sessionId, validation.sanitizedContent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LeoColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("chat-screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LeoColors.Bg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat-back-button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = LeoColors.TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LeoAvatar(size = 36.dp)
                Column {
                    Text(
                        text = stringResource(R.string.brand_name),
                        fontFamily = FontFamily.Serif,
                        fontSize = 18.sp,
                        color = LeoColors.TextPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (sending) {
                            stringResource(R.string.chat_status_thinking)
                        } else {
                            stringResource(R.string.chat_status_ready)
                        },
                        fontSize = 11.sp,
                        color = LeoColors.TextMuted,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat-history-button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = stringResource(R.string.cd_open_history),
                    tint = LeoColors.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = LeoColors.Border)

        if (!errorMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LeoColors.SurfaceElevated)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("chat-error-banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = errorMessage,
                    color = LeoColors.Danger,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )
                Text(
                    text = "Dismiss",
                    color = LeoColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onDismissError)
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .testTag("dismiss-error-button")
                )
            }
            HorizontalDivider(thickness = 1.dp, color = LeoColors.Border)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LeoColors.Primary)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (messages.isEmpty() && !sending) {
                        item(key = "empty-chat") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                LeoAvatar(size = 72.dp, alpha = 0.85f)
                                Text(
                                    text = stringResource(R.string.chat_empty_prompt),
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 20.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = LeoColors.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }

                    items(messages, key = { it.id }) { message ->
                        ChatMessageRow(message = message)
                    }

                    val hasStreamingAssistantBubble = messages.lastOrNull()?.id?.startsWith("streaming-") == true
                    if (sending && !hasStreamingAssistantBubble) {
                        item(key = "typing-indicator-item") {
                            TypingDotsIndicator()
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = LeoColors.Border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LeoColors.Bg)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("chat-input-bar"),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 140.dp)
                    .clip(RoundedCornerShape(LeoRadius.Lg))
                    .background(LeoColors.Surface)
                    .border(1.dp, LeoColors.Border, RoundedCornerShape(LeoRadius.Lg))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (input.isEmpty()) {
                    Text(
                        text = stringResource(R.string.placeholder_message_leo),
                        fontSize = 16.sp,
                        color = LeoColors.TextMuted
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = MessageValidator.clampLiveInput(it) },
                    enabled = !sending,
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = LeoColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(LeoColors.Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat-input-field")
                )
            }

            val sendEnabled = MessageValidator.isValidMessage(input) && !sending
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .alpha(if (sendEnabled) 1f else 0.5f)
                    .clip(CircleShape)
                    .background(LeoColors.Primary)
                    .clickable(enabled = sendEnabled, onClick = sendCurrentInput)
                    .testTag("send-message-button"),
                contentAlignment = Alignment.Center
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        color = LeoColors.PrimaryFg,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.ArrowUpward,
                        contentDescription = stringResource(R.string.cd_send_message),
                        tint = LeoColors.PrimaryFg,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageRow(message: ChatMessage) {
    val isUser = message.role == "user"

    val bubbleShape = if (isUser) {
        RoundedCornerShape(
            topStart = LeoRadius.Lg,
            topEnd = 4.dp,
            bottomEnd = LeoRadius.Lg,
            bottomStart = LeoRadius.Lg
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = LeoRadius.Lg,
            bottomEnd = LeoRadius.Lg,
            bottomStart = LeoRadius.Lg
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message-${message.role}"),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            LeoAvatar(
                size = 28.dp,
                modifier = Modifier.padding(end = 10.dp, bottom = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(if (isUser) 0.85f else 0.82f)
                .clip(bubbleShape)
                .background(if (isUser) LeoColors.Primary else LeoColors.Surface)
                .then(
                    if (!isUser) {
                        Modifier.border(1.dp, LeoColors.Border, bubbleShape)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SelectionContainer {
                Text(
                    text = formatMarkdownText(message.content),
                    color = if (isUser) LeoColors.PrimaryFg else LeoColors.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

private fun formatMarkdownText(raw: String) = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        if (raw.startsWith("**", i)) {
            val end = raw.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(raw.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        if (raw.startsWith("*", i) && !raw.startsWith("**", i)) {
            val end = raw.indexOf("*", i + 1)
            if (end != -1 && end > i + 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(raw.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        append(raw[i])
        i++
    }
}
