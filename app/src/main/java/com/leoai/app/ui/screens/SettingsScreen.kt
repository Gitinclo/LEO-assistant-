package com.leoai.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leoai.app.R
import com.leoai.app.ui.components.ComplianceDocsDialog
import com.leoai.app.ui.components.LeoAvatar
import com.leoai.app.ui.theme.LeoColors
import com.leoai.app.ui.theme.LeoRadius

@Composable
fun SettingsScreen(
    clearing: Boolean,
    cleared: Boolean,
    onClearAll: () -> Unit,
    onBack: () -> Unit
) {
    var showComplianceDialog by rememberSaveable { mutableStateOf(false) }

    if (showComplianceDialog) {
        ComplianceDocsDialog(
            onDismiss = { showComplianceDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LeoColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("settings-screen")
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
                    .testTag("settings-back-button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = LeoColors.TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = stringResource(R.string.settings_title),
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                color = LeoColors.TextPrimary
            )

            Box(modifier = Modifier.size(48.dp))
        }
        HorizontalDivider(thickness = 1.dp, color = LeoColors.Border)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LeoAvatar(
                        size = 86.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.brand_name),
                        fontFamily = FontFamily.Serif,
                        fontSize = 28.sp,
                        color = LeoColors.TextPrimary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = stringResource(R.string.settings_brand_tag),
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = LeoColors.TextSecondary
                    )
                }

                Text(
                    text = stringResource(R.string.settings_section_about),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = LeoColors.TextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Surface(
                    shape = RoundedCornerShape(LeoRadius.Md),
                    color = LeoColors.Surface,
                    border = BorderStroke(1.dp, LeoColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.settings_about_body),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = LeoColors.TextPrimary,
                        modifier = Modifier.padding(20.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.settings_section_compliance),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = LeoColors.TextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Surface(
                    shape = RoundedCornerShape(LeoRadius.Md),
                    color = LeoColors.Surface,
                    border = BorderStroke(1.dp, LeoColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LeoRadius.Md))
                        .clickable { showComplianceDialog = true }
                        .testTag("open-compliance-docs-button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Gavel,
                            contentDescription = null,
                            tint = LeoColors.Primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_open_compliance_docs),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = LeoColors.TextPrimary
                            )
                            Text(
                                text = stringResource(R.string.settings_open_compliance_subtitle),
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
                    text = stringResource(R.string.settings_section_data),
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = LeoColors.TextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Surface(
                    shape = RoundedCornerShape(LeoRadius.Md),
                    color = LeoColors.Surface,
                    border = BorderStroke(1.dp, LeoColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("encryption-status-card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = LeoColors.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_encryption_status),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = LeoColors.TextPrimary
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !clearing, onClick = onClearAll)
                            .padding(vertical = 16.dp)
                            .testTag("clear-conversations-button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (clearing) {
                            CircularProgressIndicator(
                                color = LeoColors.Danger,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = LeoColors.Danger,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = if (cleared) {
                                stringResource(R.string.settings_cleared)
                            } else {
                                stringResource(R.string.settings_clear_conversations)
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = LeoColors.Danger
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = LeoColors.Border)
                }

                Spacer(modifier = Modifier.size(8.dp))

                Text(
                    text = stringResource(R.string.settings_footer),
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = LeoColors.TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
