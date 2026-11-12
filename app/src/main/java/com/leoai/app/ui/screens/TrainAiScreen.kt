package com.leoai.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leoai.app.R
import com.leoai.app.data.HardwarePreset
import com.leoai.app.data.MessageValidator
import com.leoai.app.data.QloraPipelinePlan
import com.leoai.app.data.SideBySideEvaluation
import com.leoai.app.data.TrainingDatasetExporter
import com.leoai.app.data.TrainingProfile
import com.leoai.app.data.TrainingQaPair
import com.leoai.app.ui.theme.LeoColors
import com.leoai.app.ui.theme.LeoRadius

@Composable
fun TrainAiScreen(
    profile: TrainingProfile?,
    qaPairs: List<TrainingQaPair>,
    qloraPlan: QloraPipelinePlan?,
    sideBySideEval: SideBySideEvaluation?,
    busy: Boolean,
    statusBanner: String?,
    errorMessage: String?,
    onUpdateProfileFields: (String, String, String, String, String) -> Unit,
    onToggleActiveInChat: (Boolean) -> Unit,
    onGenerateSyntheticDataset: (String, String, String) -> Unit,
    onRunAiJudgeAudit: () -> Unit,
    onPruneLowQuality: (Int) -> Unit,
    onAddManualQaPair: (String, String) -> Unit,
    onDeleteQaPair: (String) -> Unit,
    onGenerateQloraPipeline: (HardwarePreset) -> Unit,
    onRunSideBySideEval: (String) -> Unit,
    onDismissFeedback: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedStage by rememberSaveable { mutableIntStateOf(0) }

    var nameInput by rememberSaveable(profile?.id) {
        mutableStateOf(profile?.name ?: "Executive & Technical Coach")
    }
    var domainInput by rememberSaveable(profile?.id) {
        mutableStateOf(profile?.domainDescription ?: "")
    }
    var notesInput by rememberSaveable(profile?.id) {
        mutableStateOf(profile?.sourceNotes ?: "")
    }
    var systemPromptInput by rememberSaveable(profile?.id) {
        mutableStateOf(profile?.systemPrompt ?: "")
    }
    var hardwareId by rememberSaveable(profile?.id) {
        mutableStateOf(profile?.hardwareTarget ?: HardwarePreset.RTX_16GB.id)
    }

    var manualQuestion by rememberSaveable { mutableStateOf("") }
    var manualAnswer by rememberSaveable { mutableStateOf("") }
    var evalPromptInput by rememberSaveable {
        mutableStateOf("How should I structure an architecture proposal for a skeptical team?")
    }
    var localCopiedNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile?.systemPrompt) {
        if (!profile?.systemPrompt.isNullOrBlank()) {
            systemPromptInput = profile?.systemPrompt.orEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LeoColors.Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("train-ai-screen")
    ) {
        // Header Bar
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
                    .testTag("train-ai-back-button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = LeoColors.TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Train My AI Studio",
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    color = LeoColors.TextPrimary
                )
                Text(
                    text = "TEACHER AI · QLoRA · JUDGE",
                    fontSize = 10.sp,
                    letterSpacing = 1.8.sp,
                    color = LeoColors.TextMuted
                )
            }

            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(
                        color = LeoColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = LeoColors.Border)

        // Active In-App Brain Toggle Bar
        Surface(
            color = LeoColors.SurfaceElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Verified,
                        contentDescription = null,
                        tint = if (profile?.isActive == true) LeoColors.Primary else LeoColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (profile?.isActive == true) {
                                "Active in LEO Chat: ${profile.name}"
                            } else {
                                "Apply Trained Profile to LEO Chat"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LeoColors.TextPrimary
                        )
                        Text(
                            text = "Uses your Q&A dataset (${qaPairs.size} pairs) + custom instructions in live chat",
                            fontSize = 11.sp,
                            color = LeoColors.TextSecondary
                        )
                    }
                }
                Switch(
                    checked = profile?.isActive == true,
                    onCheckedChange = onToggleActiveInChat,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = LeoColors.PrimaryFg,
                        checkedTrackColor = LeoColors.Primary
                    ),
                    modifier = Modifier.testTag("toggle-active-profile-switch")
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = LeoColors.Border)

        // Stage Switcher Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LeoColors.Surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StageTabButton(
                label = "1. Dataset & Judge",
                selected = selectedStage == 0,
                tag = "tab-dataset-judge",
                modifier = Modifier.weight(1f),
                onClick = { selectedStage = 0 }
            )
            StageTabButton(
                label = "2. Unsloth QLoRA",
                selected = selectedStage == 1,
                tag = "tab-qlora-hardware",
                modifier = Modifier.weight(1f),
                onClick = { selectedStage = 1 }
            )
            StageTabButton(
                label = "3. Side-by-Side Eval",
                selected = selectedStage == 2,
                tag = "tab-side-by-side",
                modifier = Modifier.weight(1f),
                onClick = { selectedStage = 2 }
            )
        }
        HorizontalDivider(thickness = 1.dp, color = LeoColors.Border)

        // Feedback Banners
        val activeNotice = localCopiedNotice ?: statusBanner
        if (!errorMessage.isNullOrBlank() || !activeNotice.isNullOrBlank()) {
            val isError = !errorMessage.isNullOrBlank()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isError) Color(0xFFFBEAEA) else LeoColors.SurfaceElevated)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag("training-feedback-banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = errorMessage ?: activeNotice.orEmpty(),
                    fontSize = 13.sp,
                    color = if (isError) LeoColors.Danger else LeoColors.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )
                Text(
                    text = "Dismiss",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LeoColors.TextSecondary,
                    modifier = Modifier
                        .clickable {
                            localCopiedNotice = null
                            onDismissFeedback()
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            HorizontalDivider(thickness = 1.dp, color = LeoColors.Border)
        }

        // Main Scrollable Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                when (selectedStage) {
                    0 -> DatasetAndJudgeStage(
                        nameInput = nameInput,
                        domainInput = domainInput,
                        notesInput = notesInput,
                        systemPromptInput = systemPromptInput,
                        manualQuestion = manualQuestion,
                        manualAnswer = manualAnswer,
                        qaPairs = qaPairs,
                        busy = busy,
                        onNameChange = { nameInput = MessageValidator.clampLiveInput(it, 80) },
                        onDomainChange = { domainInput = MessageValidator.clampLiveInput(it, 1_000) },
                        onNotesChange = { notesInput = MessageValidator.clampLiveInput(it) },
                        onSystemPromptChange = { systemPromptInput = MessageValidator.clampLiveInput(it, 2_000) },
                        onManualQuestionChange = { manualQuestion = MessageValidator.clampLiveInput(it, 1_500) },
                        onManualAnswerChange = { manualAnswer = MessageValidator.clampLiveInput(it) },
                        onSaveProfile = {
                            onUpdateProfileFields(
                                nameInput,
                                domainInput,
                                notesInput,
                                systemPromptInput,
                                hardwareId
                            )
                        },
                        onGenerateDataset = {
                            onGenerateSyntheticDataset(nameInput, domainInput, notesInput)
                        },
                        onRunAiJudge = onRunAiJudgeAudit,
                        onPruneLowQuality = { onPruneLowQuality(8) },
                        onAddManualPair = {
                            if (manualQuestion.isNotBlank() && manualAnswer.isNotBlank()) {
                                onAddManualQaPair(manualQuestion, manualAnswer)
                                manualQuestion = ""
                                manualAnswer = ""
                            }
                        },
                        onDeletePair = onDeleteQaPair,
                        onCopyChatMlJsonl = {
                            if (profile != null) {
                                val jsonl = TrainingDatasetExporter.exportToChatMlJsonl(profile, qaPairs)
                                copyToClipboard(context, "leo_training_dataset.jsonl", jsonl)
                                localCopiedNotice =
                                    "Copied ${qaPairs.size} Q&A pairs in Unsloth ChatML .jsonl format to clipboard."
                            }
                        },
                        onCopyAlpacaJson = {
                            if (profile != null) {
                                val alpaca = TrainingDatasetExporter.exportToAlpacaJson(profile, qaPairs)
                                copyToClipboard(context, "leo_alpaca_dataset.json", alpaca)
                                localCopiedNotice =
                                    "Copied ${qaPairs.size} Q&A pairs in Alpaca .json format to clipboard."
                            }
                        }
                    )

                    1 -> QloraHardwareStage(
                        profile = profile,
                        qaPairCount = qaPairs.size,
                        selectedHardware = HardwarePreset.fromId(hardwareId),
                        qloraPlan = qloraPlan,
                        busy = busy,
                        onSelectHardware = { preset ->
                            hardwareId = preset.id
                            onUpdateProfileFields(
                                nameInput,
                                domainInput,
                                notesInput,
                                systemPromptInput,
                                preset.id
                            )
                        },
                        onGenerateQloraPipeline = { preset ->
                            onGenerateQloraPipeline(preset)
                        },
                        onCopyScript = { script ->
                            copyToClipboard(context, "train_qlora_unsloth.py", script)
                            localCopiedNotice = "Copied Unsloth QLoRA Python training script to clipboard."
                        },
                        onCopyModelfile = { modelfile ->
                            copyToClipboard(context, "Modelfile", modelfile)
                            localCopiedNotice = "Copied Ollama Modelfile to clipboard."
                        }
                    )

                    2 -> SideBySideEvalStage(
                        profileName = nameInput,
                        qaPairCount = qaPairs.size,
                        evalPromptInput = evalPromptInput,
                        evaluation = sideBySideEval,
                        busy = busy,
                        onEvalPromptChange = { evalPromptInput = MessageValidator.clampLiveInput(it) },
                        onRunEvaluation = { onRunSideBySideEval(evalPromptInput) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StageTabButton(
    label: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(LeoRadius.Sm),
        color = if (selected) LeoColors.Primary else LeoColors.Bg,
        border = BorderStroke(1.dp, if (selected) LeoColors.Primary else LeoColors.Border),
        modifier = modifier.testTag(tag)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) LeoColors.PrimaryFg else LeoColors.TextPrimary
            )
        }
    }
}

@Composable
private fun DatasetAndJudgeStage(
    nameInput: String,
    domainInput: String,
    notesInput: String,
    systemPromptInput: String,
    manualQuestion: String,
    manualAnswer: String,
    qaPairs: List<TrainingQaPair>,
    busy: Boolean,
    onNameChange: (String) -> Unit,
    onDomainChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onManualQuestionChange: (String) -> Unit,
    onManualAnswerChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onGenerateDataset: () -> Unit,
    onRunAiJudge: () -> Unit,
    onPruneLowQuality: () -> Unit,
    onAddManualPair: () -> Unit,
    onDeletePair: (String) -> Unit,
    onCopyChatMlJsonl: () -> Unit,
    onCopyAlpacaJson: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(LeoRadius.Md),
        color = LeoColors.Surface,
        border = BorderStroke(1.dp, LeoColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "STEP 1 · TEACHER AI SYNTHETIC DATA ENGINE",
                fontSize = 11.sp,
                letterSpacing = 1.8.sp,
                color = LeoColors.TextMuted
            )
            Text(
                text = "Feed your raw notes, documentation, or style rules to the Teacher AI (Gemini 3.8 Flash). It automatically extracts high-signal Q&A pairs, grades each pair with AI-as-a-Judge, and encrypts your dataset in Room.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = LeoColors.TextSecondary
            )

            LabeledInputField(
                label = "TRAINING PROFILE NAME",
                value = nameInput,
                onValueChange = onNameChange,
                placeholder = "e.g., Executive & Technical Coach",
                singleLine = true,
                testTag = "train-profile-name-input"
            )

            LabeledInputField(
                label = "WHAT SHOULD THE MODEL LEARN? (DOMAIN / GOAL)",
                value = domainInput,
                onValueChange = onDomainChange,
                placeholder = "e.g., Coding style, customer support playbook, or PDF manual facts",
                minHeight = 64.dp,
                testTag = "train-domain-input"
            )

            LabeledInputField(
                label = "SOURCE MATERIAL / RAW NOTES / SAMPLE DOCS",
                value = notesInput,
                onValueChange = onNotesChange,
                placeholder = "Paste raw notes, policies, code conventions, or sample Q&A points here…",
                minHeight = 110.dp,
                testTag = "train-source-notes-input"
            )

            LabeledInputField(
                label = "CUSTOM SYSTEM INSTRUCTION (AUTO-REFINED BY TEACHER AI)",
                value = systemPromptInput,
                onValueChange = onSystemPromptChange,
                placeholder = "System prompt guiding LEO's behavior…",
                minHeight = 76.dp,
                testTag = "train-system-prompt-input"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onGenerateDataset,
                    enabled = !busy && notesInput.isNotBlank(),
                    shape = RoundedCornerShape(LeoRadius.Sm),
                    color = LeoColors.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("generate-dataset-button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = LeoColors.PrimaryFg,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (busy) "Generating Q&A…" else "Generate Q&A Dataset",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LeoColors.PrimaryFg,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Surface(
                    onClick = onSaveProfile,
                    enabled = !busy,
                    shape = RoundedCornerShape(LeoRadius.Sm),
                    color = LeoColors.SurfaceElevated,
                    border = BorderStroke(1.dp, LeoColors.Border),
                    modifier = Modifier.testTag("save-profile-button")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = LeoColors.TextPrimary
                        )
                    }
                }
            }
        }
    }

    // AI-as-a-Judge & Export Controls
    Surface(
        shape = RoundedCornerShape(LeoRadius.Md),
        color = LeoColors.Surface,
        border = BorderStroke(1.dp, LeoColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI-AS-A-JUDGE CURATION & JSONL EXPORT",
                    fontSize = 11.sp,
                    letterSpacing = 1.8.sp,
                    color = LeoColors.TextMuted
                )
                val avgScore = if (qaPairs.isNotEmpty()) {
                    String.format(java.util.Locale.US, "%.1f/10", qaPairs.map { it.qualityScore }.average())
                } else {
                    "—"
                }
                Text(
                    text = "${qaPairs.size} pairs · Avg $avgScore",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LeoColors.Primary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionChipButton(
                    icon = Icons.Outlined.CheckCircle,
                    label = "Run AI Judge Audit",
                    enabled = !busy && qaPairs.isNotEmpty(),
                    testTag = "run-ai-judge-button",
                    onClick = onRunAiJudge
                )
                ActionChipButton(
                    icon = Icons.Outlined.FilterAlt,
                    label = "Prune < 8/10",
                    enabled = !busy && qaPairs.isNotEmpty(),
                    testTag = "prune-low-quality-button",
                    onClick = onPruneLowQuality
                )
                ActionChipButton(
                    icon = Icons.Outlined.ContentCopy,
                    label = "Copy ChatML .jsonl",
                    enabled = qaPairs.isNotEmpty(),
                    testTag = "export-jsonl-button",
                    onClick = onCopyChatMlJsonl
                )
                ActionChipButton(
                    icon = Icons.Outlined.ContentCopy,
                    label = "Copy Alpaca .json",
                    enabled = qaPairs.isNotEmpty(),
                    testTag = "export-alpaca-button",
                    onClick = onCopyAlpacaJson
                )
            }
        }
    }

    // Manual Q&A Pair Adder
    Surface(
        shape = RoundedCornerShape(LeoRadius.Md),
        color = LeoColors.Surface,
        border = BorderStroke(1.dp, LeoColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ADD CUSTOM Q&A PAIR",
                fontSize = 11.sp,
                letterSpacing = 1.8.sp,
                color = LeoColors.TextMuted
            )
            LabeledInputField(
                label = "USER QUESTION / PROMPT",
                value = manualQuestion,
                onValueChange = onManualQuestionChange,
                placeholder = "Enter a sample question a user would ask…",
                singleLine = false,
                minHeight = 52.dp,
                testTag = "manual-qa-question-input"
            )
            LabeledInputField(
                label = "IDEAL ASSISTANT ANSWER",
                value = manualAnswer,
                onValueChange = onManualAnswerChange,
                placeholder = "Enter the ideal response you want LEO to learn…",
                singleLine = false,
                minHeight = 68.dp,
                testTag = "manual-qa-answer-input"
            )
            Surface(
                onClick = onAddManualPair,
                enabled = manualQuestion.isNotBlank() && manualAnswer.isNotBlank(),
                shape = RoundedCornerShape(LeoRadius.Sm),
                color = LeoColors.SurfaceElevated,
                border = BorderStroke(1.dp, LeoColors.Border),
                modifier = Modifier.testTag("add-manual-qa-button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = LeoColors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Add Q&A Pair to Dataset",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LeoColors.TextPrimary
                    )
                }
            }
        }
    }

    // Q&A Pair Cards
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        qaPairs.forEachIndexed { index, pair ->
            QaPairCard(
                pair = pair,
                index = index,
                onDelete = { onDeletePair(pair.id) }
            )
        }
    }
}

@Composable
private fun QaPairCard(
    pair: TrainingQaPair,
    index: Int,
    onDelete: () -> Unit
) {
    val scoreColor = when {
        pair.qualityScore >= 8 -> LeoColors.Primary
        pair.qualityScore >= 5 -> Color(0xFF8A6D3B)
        else -> LeoColors.Danger
    }

    Surface(
        shape = RoundedCornerShape(LeoRadius.Md),
        color = LeoColors.Surface,
        border = BorderStroke(1.dp, LeoColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("qa-pair-card-$index")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(scoreColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "AI Judge: ${pair.qualityScore}/10",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scoreColor
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete Q&A pair",
                        tint = LeoColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = "Q: ${pair.question}",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LeoColors.TextPrimary
            )
            Text(
                text = "A: ${pair.answer}",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = LeoColors.TextSecondary
            )
            if (pair.judgeRationale.isNotBlank()) {
                Text(
                    text = "Judge critique: ${pair.judgeRationale}",
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = LeoColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun QloraHardwareStage(
    profile: TrainingProfile?,
    qaPairCount: Int,
    selectedHardware: HardwarePreset,
    qloraPlan: QloraPipelinePlan?,
    busy: Boolean,
    onSelectHardware: (HardwarePreset) -> Unit,
    onGenerateQloraPipeline: (HardwarePreset) -> Unit,
    onCopyScript: (String) -> Unit,
    onCopyModelfile: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(LeoRadius.Md),
        color = LeoColors.Surface,
        border = BorderStroke(1.dp, LeoColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "STEP 2 · HARDWARE-MATCHED UNSLOTH QLoRA CONFIGURATOR",
                fontSize = 11.sp,
                letterSpacing = 1.8.sp,
                color = LeoColors.TextMuted
            )
            Text(
                text = "Select your PC GPU VRAM or Mac Unified Memory. Gemini inspects your hardware and dataset ($qaPairCount Q&A pairs) to choose the right 4-bit student model, LoRA rank, batch size, and Python training script.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = LeoColors.TextSecondary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HardwarePreset.entries.forEach { preset ->
                    val isSelected = preset.id == selectedHardware.id
                    Surface(
                        onClick = { onSelectHardware(preset) },
                        shape = RoundedCornerShape(LeoRadius.Sm),
                        color = if (isSelected) LeoColors.SurfaceElevated else LeoColors.Bg,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) LeoColors.Primary else LeoColors.Border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hardware-preset-${preset.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Memory,
                                    contentDescription = null,
                                    tint = if (isSelected) LeoColors.Primary else LeoColors.TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = preset.label,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LeoColors.TextPrimary
                                    )
                                    Text(
                                        text = "Student model: ${preset.defaultStudentModel}",
                                        fontSize = 12.sp,
                                        color = LeoColors.TextSecondary
                                    )
                                }
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(LeoColors.Primary)
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                onClick = { onGenerateQloraPipeline(selectedHardware) },
                enabled = !busy,
                shape = RoundedCornerShape(LeoRadius.Sm),
                color = LeoColors.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generate-qlora-button")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                        tint = LeoColors.PrimaryFg,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (busy) {
                            "Configuring QLoRA Pipeline with AI…"
                        } else {
                            "Configure QLoRA & Generate Unsloth Script"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LeoColors.PrimaryFg,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }

    val activeScript = qloraPlan?.pythonScript ?: profile?.qloraScript.orEmpty()
    val activeModelfile = qloraPlan?.ollamaModelfile ?: profile?.ollamaModelfile.orEmpty()
    val recommendedModel = qloraPlan?.recommendedModel
        ?: profile?.recommendedBaseModel
        ?: selectedHardware.defaultStudentModel

    Surface(
        shape = RoundedCornerShape(LeoRadius.Md),
        color = LeoColors.Surface,
        border = BorderStroke(1.dp, LeoColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "RECOMMENDED STUDENT MODEL & HYPERPARAMETERS",
                fontSize = 11.sp,
                letterSpacing = 1.8.sp,
                color = LeoColors.TextMuted
            )
            Text(
                text = recommendedModel,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LeoColors.Primary
            )
            if (qloraPlan != null) {
                Text(
                    text = qloraPlan.coachSummary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = LeoColors.TextSecondary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HyperparamPill("LoRA r=${qloraPlan.loraRank}")
                    HyperparamPill("alpha=${qloraPlan.loraAlpha}")
                    HyperparamPill("LR=${qloraPlan.learningRate}")
                    HyperparamPill("Batch=${qloraPlan.batchSize}")
                    HyperparamPill("Epochs=${qloraPlan.epochs}")
                }
            }

            if (activeScript.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "train_qlora_unsloth.py",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LeoColors.TextPrimary
                    )
                    ActionChipButton(
                        icon = Icons.Outlined.ContentCopy,
                        label = "Copy Python Script",
                        enabled = true,
                        testTag = "copy-qlora-script-button",
                        onClick = { onCopyScript(activeScript) }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LeoRadius.Sm))
                        .background(LeoColors.SurfaceElevated)
                        .border(1.dp, LeoColors.Border, RoundedCornerShape(LeoRadius.Sm))
                        .padding(14.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = activeScript,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = LeoColors.TextPrimary
                        )
                    }
                }
            }

            if (activeModelfile.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ollama Modelfile",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LeoColors.TextPrimary
                    )
                    ActionChipButton(
                        icon = Icons.Outlined.ContentCopy,
                        label = "Copy Modelfile",
                        enabled = true,
                        testTag = "copy-modelfile-button",
                        onClick = { onCopyModelfile(activeModelfile) }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LeoRadius.Sm))
                        .background(LeoColors.SurfaceElevated)
                        .border(1.dp, LeoColors.Border, RoundedCornerShape(LeoRadius.Sm))
                        .padding(14.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = activeModelfile,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = LeoColors.TextPrimary
                        )
                    }
                }
            }

            val autonomousCliScript = TrainingDatasetExporter.buildAutonomous4PhasePipelineScript(
                sourceDataPath = "./data/raw_docs",
                targetTask = profile?.domainDescription?.ifBlank {
                    "Domain documentation, PDFs, and technical Q&A"
                } ?: "Domain documentation, PDFs, and technical Q&A",
                localTeacherModel = "llama3.3",
                studentBaseModel = recommendedModel
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "4-Phase Local Ollama Orchestrator (ml_pipeline/)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LeoColors.TextPrimary
                )
                ActionChipButton(
                    icon = Icons.Outlined.ContentCopy,
                    label = "Copy 4-Phase Pipeline",
                    enabled = true,
                    testTag = "copy-4phase-pipeline-button",
                    onClick = { onCopyScript(autonomousCliScript) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LeoRadius.Sm))
                    .background(LeoColors.SurfaceElevated)
                    .border(1.dp, LeoColors.Border, RoundedCornerShape(LeoRadius.Sm))
                    .padding(14.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = autonomousCliScript,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = LeoColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SideBySideEvalStage(
    profileName: String,
    qaPairCount: Int,
    evalPromptInput: String,
    evaluation: SideBySideEvaluation?,
    busy: Boolean,
    onEvalPromptChange: (String) -> Unit,
    onRunEvaluation: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(LeoRadius.Md),
        color = LeoColors.Surface,
        border = BorderStroke(1.dp, LeoColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "STEP 3 · SIDE-BY-SIDE BASE VS. TRAINED EVALUATOR",
                fontSize = 11.sp,
                letterSpacing = 1.8.sp,
                color = LeoColors.TextMuted
            )
            Text(
                text = "Run a test prompt against both the untuned Base model and your '$profileName' profile ($qaPairCount Q&A pairs). Gemini acts as an objective AI-as-a-Judge to score which response followed your training instructions better.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = LeoColors.TextSecondary
            )

            LabeledInputField(
                label = "TEST PROMPT FOR SIDE-BY-SIDE BENCHMARK",
                value = evalPromptInput,
                onValueChange = onEvalPromptChange,
                placeholder = "Ask a question from your training domain…",
                minHeight = 76.dp,
                testTag = "eval-test-prompt-input"
            )

            Surface(
                onClick = onRunEvaluation,
                enabled = !busy && evalPromptInput.isNotBlank(),
                shape = RoundedCornerShape(LeoRadius.Sm),
                color = LeoColors.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("run-side-by-side-button")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CompareArrows,
                        contentDescription = null,
                        tint = LeoColors.PrimaryFg,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (busy) {
                            "Running Side-by-Side Judge Evaluation…"
                        } else {
                            "Compare Base vs. Trained with AI Judge"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LeoColors.PrimaryFg,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }

    if (evaluation != null) {
        Surface(
            shape = RoundedCornerShape(LeoRadius.Md),
            color = LeoColors.SurfaceElevated,
            border = BorderStroke(1.dp, LeoColors.Primary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("side-by-side-scorecard")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI-AS-A-JUDGE VERDICT",
                        fontSize = 11.sp,
                        letterSpacing = 1.8.sp,
                        color = LeoColors.Primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Trained ${evaluation.trainedScore}/10 · Base ${evaluation.baseScore}/10",
                        fontFamily = FontFamily.Serif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LeoColors.Primary
                    )
                }
                Text(
                    text = evaluation.judgeSummary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = LeoColors.TextPrimary
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(LeoRadius.Md),
            color = LeoColors.Surface,
            border = BorderStroke(1.5.dp, LeoColors.Primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "TRAINED MODEL RESPONSE (${evaluation.trainedScore}/10)",
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LeoColors.Primary
                )
                SelectionContainer {
                    Text(
                        text = evaluation.trainedReply,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = LeoColors.TextPrimary
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(LeoRadius.Md),
            color = LeoColors.Surface,
            border = BorderStroke(1.dp, LeoColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "UNTUNED BASE MODEL RESPONSE (${evaluation.baseScore}/10)",
                    fontSize = 11.sp,
                    letterSpacing = 1.5.sp,
                    color = LeoColors.TextMuted
                )
                SelectionContainer {
                    Text(
                        text = evaluation.baseReply,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = LeoColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 46.dp,
    testTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = LeoColors.TextMuted
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(RoundedCornerShape(LeoRadius.Sm))
                .background(LeoColors.Bg)
                .border(1.dp, LeoColors.Border, RoundedCornerShape(LeoRadius.Sm))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 14.sp,
                    color = LeoColors.TextMuted
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = LeoColors.TextPrimary
                ),
                cursorBrush = SolidColor(LeoColors.Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag)
            )
        }
    }
}

@Composable
private fun ActionChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(LeoRadius.Sm),
        color = LeoColors.SurfaceElevated,
        border = BorderStroke(1.dp, LeoColors.Border),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LeoColors.Primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = LeoColors.TextPrimary
            )
        }
    }
}

@Composable
private fun HyperparamPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LeoColors.SurfaceElevated)
            .border(1.dp, LeoColors.Border, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = LeoColors.TextPrimary
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
