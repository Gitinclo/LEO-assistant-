package com.leoai.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leoai.app.ui.theme.LeoColors
import com.leoai.app.ui.theme.LeoRadius

enum class ComplianceDocTab(
    val label: String,
    val fileName: String,
    val summaryTitle: String,
    val content: String
) {
    PRIVACY(
        label = "PRIVACY",
        fileName = "PRIVACY.md",
        summaryTitle = "Privacy Policy, Data Retention & GDPR/CCPA",
        content = """
            • Local Room Persistence: Conversations are stored on-device in leo_ai_room.db using ChatMessageEntity and ChatMessageDao.
            • AES-256-GCM Encryption at Rest: Every message and session preview is encrypted with a 256-bit AndroidKeyStore key, a unique 12-byte random IV/nonce, a 128-bit authentication tag, and Associated Authenticated Data (AAD) bound to the sessionId.
            • Data Retention & Instant Wipe: Local transcripts remain solely on your device until you delete a session or tap "Clear all conversations" in Settings.
            • Right to be Forgotten (GDPR / CCPA): Request full account or server-side erasure at creatergame31@gmail.com (processed within 30 days).
            • Third-Party Disclosures: Covers PCI-DSS payment processors, optional ad network identifiers (GAID opt-out), and cookie controls.
        """.trimIndent()
    ),
    TERMS(
        label = "TERMS",
        fileName = "TERMS.md",
        summaryTitle = "Terms of Service & User Responsibilities",
        content = """
            • User Responsibilities: Users are responsible for safeguarding credentials, device access, and evaluating AI-generated conversational suggestions. Do not enter sensitive PII (SSNs, banking PINs, medical records) into prompts.
            • Account Termination ("Ban Hammer"): Reserves the right to suspend or terminate access for Terms violations, abuse, or security risks.
            • Subscriptions & Refund Policy: Details auto-renewal rules, Google Play Store cancellation steps, and 14-day dispute resolution via creatergame31@gmail.com.
            • Third-Party Ads & AI Disclaimers: External links and third-party advertisements are provided "AS IS" without liability. LEO AI provides reflective and informational responses, not medical, legal, or financial advice.
        """.trimIndent()
    ),
    SECURITY(
        label = "SECURITY",
        fileName = "SECURITY.md",
        summaryTitle = "AES-256-GCM Architecture & Vulnerability Policy",
        content = """
            • Authenticated Encryption (AEAD): Uses AES/GCM/NoPadding (256-bit key) with a fresh 96-bit (12-byte) SecureRandom nonce per write and 128-bit authentication tag.
            • AAD Context Binding: Binds each encrypted message to its conversation sessionId so ciphertext cannot be transplanted across sessions.
            • Zero Exposed Keys: Operates out of the box with zero client-side API keys or hardcoded secrets.
            • Private Vulnerability Reporting: Report security issues privately to creatergame31@gmail.com (48-hour acknowledgment SLA).
        """.trimIndent()
    ),
    GUARDRAILS(
        label = "GUARDRAILS",
        fileName = "CONTRIBUTING.md",
        summaryTitle = "Vibe Coding Guardrails & Input Bounds Validation",
        content = """
            • InputBoundsValidator: Enforces strict character caps (messages <= 4,000 chars, titles <= 80 chars, previews <= 140 chars) and role validation (user | assistant) before Room DAO writes.
            • Parameterized SQL Only: ChatMessageDao uses compile-time KSP-verified Room queries with zero string concatenation.
            • Coroutine Safety: Rethrows CancellationException cleanly and keeps all database I/O off the main UI thread.
            • Automated CI Pipeline (.github/workflows/build.yml): Enforces Kotlin compilation, Android Lint (0 errors), dependency auditing, code formatting, and JVM unit tests on every push.
        """.trimIndent()
    ),
    README(
        label = "README",
        fileName = "README.md",
        summaryTitle = "LEO AI Architecture & Feature Overview",
        content = """
            • Calm Companion Experience: Time-of-day dynamic greetings, 5 curated starter prompts, multi-turn chat with animated typing indicator, and chronological history (TODAY, YESTERDAY, EARLIER).
            • Clean MVVM + Room Architecture: Unidirectional data flow from ChatMessageDao & ChatDao -> LeoRepository -> LeoViewModel -> Jetpack Compose UI.
            • Vibe Coding Disclosure: Built with director-led human architecture, AI-assisted synthesis, and automated anti-regression tests.
        """.trimIndent()
    ),
    LICENSE(
        label = "LICENSE",
        fileName = "LICENSE",
        summaryTitle = "MIT Open-Source Software License",
        content = """
            • MIT License (Copyright © 2026 LEO AI Contributors).
            • Grants permission to use, copy, modify, merge, publish, and distribute subject to preserving the copyright and license notice.
            • Provided "AS IS", without warranty of any kind, express or implied.
        """.trimIndent()
    ),
    CODE_OF_CONDUCT(
        label = "CODE OF CONDUCT",
        fileName = "CODE_OF_CONDUCT.md",
        summaryTitle = "Contributor Covenant v2.1 Community Standards",
        content = """
            • Pledges a welcoming, respectful, harassment-free environment for all contributors and users.
            • Prohibits harassment, trolling, publishing private information, or intentionally introducing insecure code.
            • Enforcement reports are handled confidentially at creatergame31@gmail.com.
        """.trimIndent()
    ),
    CHANGELOG(
        label = "CHANGELOG",
        fileName = "CHANGELOG.md",
        summaryTitle = "Release History & Semantic Versioning",
        content = """
            • v1.1.0: Added AES-256-GCM at-rest encryption (ChatMessageCrypto) with AAD binding, InputBoundsValidator guardrails, expanded ChatMessageDao, and In-App Compliance & Legal Docs Hub.
            • v1.0.1: Added 4-stage CI Guardrails workflow (.github/workflows/build.yml), API 24 UTC date compatibility, and monochrome adaptive icon support.
            • v1.0.0: Initial release of LEO AI with Welcome, Chat, History, and Settings screens backed by Room Database.
        """.trimIndent()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComplianceDocsDialog(
    onDismiss: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(ComplianceDocTab.PRIVACY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LeoColors.Bg,
        titleContentColor = LeoColors.TextPrimary,
        textContentColor = LeoColors.TextPrimary,
        shape = RoundedCornerShape(LeoRadius.Lg),
        modifier = Modifier.testTag("compliance-docs-dialog"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Legal, Security & Compliance Hub",
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LeoColors.TextPrimary
                )
                Text(
                    text = "AES-256-GCM Encrypted • GDPR / CCPA • Vibe Guardrails",
                    fontSize = 12.sp,
                    color = LeoColors.Secondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ComplianceDocTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = LeoColors.Surface,
                                labelColor = LeoColors.TextSecondary,
                                selectedContainerColor = LeoColors.Primary.copy(alpha = 0.15f),
                                selectedLabelColor = LeoColors.Primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = LeoColors.Border,
                                selectedBorderColor = LeoColors.Primary
                            ),
                            modifier = Modifier.testTag("compliance-tab-${tab.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(LeoRadius.Md),
                    color = LeoColors.Surface,
                    border = BorderStroke(1.dp, LeoColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = selectedTab.summaryTitle,
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LeoColors.TextPrimary
                        )
                        Text(
                            text = "Repository File: /${selectedTab.fileName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = LeoColors.Primary
                        )
                        Text(
                            text = selectedTab.content,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = LeoColors.TextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("compliance-close-button")
            ) {
                Text(
                    text = "Close",
                    color = LeoColors.Primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}
