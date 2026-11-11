package com.leoai.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leoai.app.R
import com.leoai.app.data.LeoMediaAssets
import com.leoai.app.data.MessageValidator
import com.leoai.app.data.greetingForCurrentHour
import com.leoai.app.ui.components.BotanicalBackground
import com.leoai.app.ui.components.LeoAvatar
import com.leoai.app.ui.theme.LeoColors
import com.leoai.app.ui.theme.LeoRadius

@Composable
fun WelcomeScreen(
    busy: Boolean,
    activeProfileName: String? = null,
    onStartChat: (String) -> Unit,
    onOpenTrainAi: () -> Unit = {},
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    val greeting = remember { greetingForCurrentHour() }

    val submitInput = {
        val validation = MessageValidator.validate(input)
        if (validation.isValid && !busy) {
            val toSend = validation.sanitizedContent
            input = ""
            onStartChat(toSend)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LeoColors.Bg)
            .testTag("welcome-screen")
    ) {
        BotanicalBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LeoAvatar(size = 32.dp)
                    Text(
                        text = stringResource(R.string.brand_name),
                        fontFamily = FontFamily.Serif,
                        fontSize = 22.sp,
                        color = LeoColors.TextPrimary,
                        letterSpacing = 1.5.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenTrainAi,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open-train-ai-button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = stringResource(R.string.cd_open_train_ai),
                            tint = LeoColors.Primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenHistory,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open-history-button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = stringResource(R.string.cd_open_history),
                            tint = LeoColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open-settings-button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.cd_open_settings),
                            tint = LeoColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp)
                    .padding(top = 32.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                ) {
                    Text(
                        text = stringResource(R.string.welcome_eyebrow),
                        fontSize = 12.sp,
                        letterSpacing = 2.5.sp,
                        color = LeoColors.TextMuted,
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .testTag("welcome-eyebrow")
                    )

                    Text(
                        text = greeting,
                        fontFamily = FontFamily.Serif,
                        fontSize = 38.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Medium,
                        color = LeoColors.TextPrimary,
                        modifier = Modifier.testTag("welcome-heading")
                    )

                    Text(
                        text = stringResource(R.string.welcome_subheading),
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                        lineHeight = 34.sp,
                        fontStyle = FontStyle.Italic,
                        color = LeoColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )

                    Surface(
                        onClick = onOpenTrainAi,
                        shape = RoundedCornerShape(LeoRadius.Md),
                        color = LeoColors.SurfaceElevated,
                        border = BorderStroke(1.dp, LeoColors.Primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 28.dp)
                            .testTag("welcome-train-ai-card")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = LeoColors.Primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Train My AI · Dataset & QLoRA Studio",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LeoColors.TextPrimary
                                )
                                Text(
                                    text = if (!activeProfileName.isNullOrBlank()) {
                                        "Active profile: $activeProfileName · Generate Q&A, Unsloth scripts & AI Judge"
                                    } else {
                                        "Generate synthetic Q&A data, Unsloth QLoRA code & AI-as-a-Judge scores"
                                    },
                                    fontSize = 12.sp,
                                    color = LeoColors.TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = LeoColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.section_try_starter),
                        fontSize = 11.sp,
                        letterSpacing = 2.5.sp,
                        color = LeoColors.TextMuted,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LeoMediaAssets.SUGGESTIONS.forEachIndexed { index, suggestion ->
                            StarterPromptChip(
                                text = suggestion,
                                index = index,
                                enabled = !busy,
                                onClick = { onStartChat(suggestion) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = LeoColors.Border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LeoColors.Bg)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .testTag("welcome-input-bar"),
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
                            text = stringResource(R.string.placeholder_ask_leo),
                            fontSize = 16.sp,
                            color = LeoColors.TextMuted
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = MessageValidator.clampLiveInput(it) },
                        enabled = !busy,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            color = LeoColors.TextPrimary
                        ),
                        cursorBrush = SolidColor(LeoColors.Primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitInput() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("welcome-input-field")
                    )
                }

                val sendEnabled = MessageValidator.isValidMessage(input) && !busy
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(if (sendEnabled) 1f else 0.5f)
                        .clip(CircleShape)
                        .background(LeoColors.Primary)
                        .clickable(enabled = sendEnabled, onClick = submitInput)
                        .testTag("welcome-send-button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (busy) {
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
}

@Composable
private fun StarterPromptChip(
    text: String,
    index: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(LeoRadius.Md),
        color = if (isPressed) LeoColors.SurfaceElevated else LeoColors.Surface,
        border = BorderStroke(1.dp, LeoColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("suggestion-chip-$index")
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = LeoColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}
