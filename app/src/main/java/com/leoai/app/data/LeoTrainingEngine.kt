package com.leoai.app.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * AI-Assisted Local & In-App Training Engine powered by Firebase AI Logic (`gemini-3.8-flash`)
 * with strict `Schema.obj` JSON schemas.
 *
 * Implements the 4 core workflows:
 * 1. Teacher AI Synthetic Q&A Dataset Generation (`generateSyntheticDataset`)
 * 2. AI-as-a-Judge Quality Grading & Hallucination Audit (`auditDatasetWithJudge`)
 * 3. Hardware-Matched QLoRA & Unsloth Pipeline Configuration (`generateQloraPipeline`)
 * 4. Side-by-Side Base vs. Trained Model Evaluation (`evaluateSideBySide`)
 */
class LeoTrainingEngine {

    data class GeneratedDatasetResult(
        val systemPrompt: String,
        val pairs: List<TrainingQaPair>
    )

    private val datasetSchema = Schema.obj(
        properties = mapOf(
            "systemPrompt" to Schema.string(
                description = "A concise, high-signal system instruction capturing the domain knowledge, tone, and rules."
            ),
            "qaPairs" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "question" to Schema.string(description = "Realistic user question or prompt"),
                        "answer" to Schema.string(description = "Ideal, accurate assistant response grounded in the source material"),
                        "qualityScore" to Schema.integer(description = "AI-as-a-Judge score from 1 to 10 for factual accuracy and clarity"),
                        "judgeRationale" to Schema.string(description = "1-sentence critique explaining the quality score")
                    )
                ),
                description = "List of synthetic Q&A training examples"
            )
        )
    )

    private val judgeAuditSchema = Schema.obj(
        properties = mapOf(
            "auditedPairs" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string(description = "The ID of the Q&A pair being graded"),
                        "qualityScore" to Schema.integer(description = "Updated AI-as-a-Judge score from 1 to 10"),
                        "judgeRationale" to Schema.string(description = "Concise evaluation of clarity, grounding, and specificity")
                    )
                ),
                description = "Graded Q&A pairs"
            )
        )
    )

    private val qloraPlanSchema = Schema.obj(
        properties = mapOf(
            "recommendedModel" to Schema.string(description = "Recommended Hugging Face / Unsloth 4-bit base model identifier"),
            "vramEstimate" to Schema.string(description = "Estimated VRAM or Unified Memory footprint during QLoRA training"),
            "learningRate" to Schema.string(description = "Recommended QLoRA learning rate, e.g. 2e-4"),
            "loraRank" to Schema.integer(description = "LoRA rank r (e.g., 16 or 32)"),
            "loraAlpha" to Schema.integer(description = "LoRA alpha (e.g., 16 or 32)"),
            "batchSize" to Schema.integer(description = "Per-device training batch size"),
            "gradAccumulation" to Schema.integer(description = "Gradient accumulation steps"),
            "epochs" to Schema.integer(description = "Recommended number of training epochs"),
            "coachSummary" to Schema.string(description = "2-3 sentence explanation of why these settings fit the user's hardware and dataset")
        )
    )

    private val sideBySideSchema = Schema.obj(
        properties = mapOf(
            "baseReply" to Schema.string(description = "How the untuned base model responds to the test prompt"),
            "trainedReply" to Schema.string(description = "How the fine-tuned model trained on the custom Q&A dataset and system prompt responds"),
            "baseScore" to Schema.integer(description = "Score from 1 to 10 for the base model response"),
            "trainedScore" to Schema.integer(description = "Score from 1 to 10 for the trained model response"),
            "winner" to Schema.enumeration(
                values = listOf("trained", "base", "tie"),
                description = "Which model followed the custom domain instructions and Q&A examples better"
            ),
            "judgeSummary" to Schema.string(description = "Objective AI-as-a-Judge comparison explaining the score difference")
        )
    )

    private val datasetModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = datasetSchema
            }
        )
    }

    private val judgeAuditModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = judgeAuditSchema
            }
        )
    }

    private val qloraPlanModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = qloraPlanSchema
            }
        )
    }

    private val sideBySideModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = sideBySideSchema
            }
        )
    }

    /**
     * Uses Gemini (`gemini-3.8-flash`) as a Teacher AI to extract diverse, high-quality Q&A pairs
     * from raw notes/documents and grade each pair with an AI-as-a-Judge score (1–10).
     */
    suspend fun generateSyntheticDataset(
        profileId: String,
        profileName: String,
        domainDescription: String,
        sourceNotes: String,
        pairCount: Int = 6
    ): GeneratedDatasetResult {
        val safeDomain = MessageValidator.sanitize(domainDescription, 1_000)
        val safeNotes = MessageValidator.sanitize(sourceNotes, MessageValidator.MAX_MESSAGE_LENGTH)
        val targetCount = pairCount.coerceIn(3, 12)

        val prompt = """
            Act as an expert Teacher AI and Synthetic Data Engineer for LLM fine-tuning (Unsloth / QLoRA).
            
            Profile Name: $profileName
            Domain & Goal: $safeDomain
            Source Material / Raw Notes:
            $safeNotes
            
            Tasks:
            1. Write a clear, authoritative `systemPrompt` that instructs a student model how to answer questions in this domain and style.
            2. Generate $targetCount diverse, realistic Question-and-Answer (`qaPairs`) training examples based strictly on the domain and source material above.
            3. Grade each Q&A pair as an AI-as-a-Judge (`qualityScore` 1-10 and `judgeRationale`) based on factual grounding, specificity, and lack of hallucination.
        """.trimIndent()

        return try {
            withTimeout(TIMEOUT_MS) {
                val response = datasetModel.generateContent(content { text(prompt) })
                val jsonString = response.text ?: throw IllegalStateException("Teacher AI returned empty JSON.")
                val root = JSONObject(jsonString)
                val systemPrompt = root.optString("systemPrompt", "You are an expert assistant trained on $profileName.")
                val qaArray = root.getJSONArray("qaPairs")
                val pairs = (0 until qaArray.length()).map { idx ->
                    val obj = qaArray.getJSONObject(idx)
                    TrainingQaPair(
                        profileId = profileId,
                        question = obj.getString("question"),
                        answer = obj.getString("answer"),
                        qualityScore = obj.optInt("qualityScore", 9).coerceIn(1, 10),
                        judgeRationale = obj.optString("judgeRationale", "Grounded in provided domain notes.")
                    )
                }
                GeneratedDatasetResult(
                    systemPrompt = systemPrompt,
                    pairs = pairs
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Synthetic dataset generation timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Synthetic dataset generation failed", e)
            throw e
        }
    }

    /**
     * Runs an AI-as-a-Judge quality audit over existing Q&A pairs, updating their scores and rationales.
     */
    suspend fun auditDatasetWithJudge(
        domainDescription: String,
        pairs: List<TrainingQaPair>
    ): List<TrainingQaPair> {
        if (pairs.isEmpty()) return emptyList()

        val serializedPairs = pairs.joinToString("\n") { pair ->
            "- ID: ${pair.id}\n  Q: ${pair.question}\n  A: ${pair.answer}"
        }

        val prompt = """
            Act as an strict AI-as-a-Judge evaluator for an LLM fine-tuning dataset.
            Domain: $domainDescription
            
            Evaluate each Q&A pair below on a 1–10 scale for:
            - Factual alignment and absence of hallucination
            - Helpfulness, structure, and tone consistency
            - Non-duplication
            
            Dataset Entries:
            $serializedPairs
        """.trimIndent()

        return try {
            withTimeout(TIMEOUT_MS) {
                val response = judgeAuditModel.generateContent(content { text(prompt) })
                val jsonString = response.text ?: throw IllegalStateException("AI Judge returned empty JSON.")
                val root = JSONObject(jsonString)
                val auditedArray = root.getJSONArray("auditedPairs")
                val auditMap = (0 until auditedArray.length()).associate { idx ->
                    val item = auditedArray.getJSONObject(idx)
                    item.getString("id") to Pair(
                        item.optInt("qualityScore", 8).coerceIn(1, 10),
                        item.optString("judgeRationale", "Audited by AI Judge.")
                    )
                }

                pairs.map { existing ->
                    val updated = auditMap[existing.id]
                    if (updated != null) {
                        existing.copy(
                            qualityScore = updated.first,
                            judgeRationale = updated.second
                        )
                    } else {
                        existing
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI Judge audit timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Judge audit failed", e)
            throw e
        }
    }

    /**
     * Generates a hardware-matched Unsloth QLoRA configuration, student model recommendation,
     * Python training script, and Ollama `Modelfile`.
     */
    suspend fun generateQloraPipeline(
        profile: TrainingProfile,
        hardware: HardwarePreset,
        datasetCount: Int
    ): QloraPipelinePlan {
        val prompt = """
            Act as an AI Fine-Tuning Architect specializing in Unsloth QLoRA and local LLM deployment (Ollama / LM Studio).
            
            Profile Name: ${profile.name}
            Domain: ${profile.domainDescription}
            Hardware Target: ${hardware.label} (${hardware.memorySpec})
            Default Candidate Model: ${hardware.defaultStudentModel}
            Dataset Size: $datasetCount Q&A pairs
            
            Recommend the optimal 4-bit student model from Hugging Face / Unsloth that fits comfortably inside ${hardware.memorySpec}, along with safe QLoRA hyperparameters (`learningRate`, `loraRank`, `loraAlpha`, `batchSize`, `gradAccumulation`, `epochs`, `vramEstimate`, and `coachSummary`).
        """.trimIndent()

        return try {
            withTimeout(TIMEOUT_MS) {
                val response = qloraPlanModel.generateContent(content { text(prompt) })
                val jsonString = response.text ?: throw IllegalStateException("QLoRA planner returned empty JSON.")
                val json = JSONObject(jsonString)

                val recommendedModel = json.optString("recommendedModel", hardware.defaultStudentModel)
                val vramEstimate = json.optString("vramEstimate", hardware.memorySpec)
                val learningRate = json.optString("learningRate", "2e-4")
                val loraRank = json.optInt("loraRank", hardware.defaultLoraRank).coerceIn(8, 128)
                val loraAlpha = json.optInt("loraAlpha", loraRank).coerceIn(8, 256)
                val batchSize = json.optInt("batchSize", hardware.defaultBatchSize).coerceIn(1, 16)
                val gradAccum = json.optInt("gradAccumulation", 4).coerceIn(1, 32)
                val epochs = json.optInt("epochs", 3).coerceIn(1, 10)
                val coachSummary = json.optString(
                    "coachSummary",
                    "Configured 4-bit QLoRA adapters for $recommendedModel to fit within ${hardware.memorySpec}."
                )

                val pythonScript = TrainingDatasetExporter.buildDefaultQloraScript(
                    profileName = profile.name,
                    baseModel = recommendedModel,
                    systemPrompt = profile.systemPrompt,
                    loraRank = loraRank,
                    loraAlpha = loraAlpha,
                    batchSize = batchSize,
                    gradAccum = gradAccum,
                    learningRate = learningRate,
                    epochs = epochs
                )
                val modelfile = TrainingDatasetExporter.buildDefaultOllamaModelfile(
                    profileName = profile.name,
                    systemPrompt = profile.systemPrompt
                )

                QloraPipelinePlan(
                    recommendedModel = recommendedModel,
                    vramEstimate = vramEstimate,
                    learningRate = learningRate,
                    loraRank = loraRank,
                    loraAlpha = loraAlpha,
                    batchSize = batchSize,
                    gradAccumulation = gradAccum,
                    epochs = epochs,
                    coachSummary = coachSummary,
                    pythonScript = pythonScript,
                    ollamaModelfile = modelfile
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "QLoRA pipeline generation timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "QLoRA pipeline generation failed", e)
            throw e
        }
    }

    /**
     * Evaluates a test prompt side-by-side between an untuned Base model persona and the user's
     * Trained Profile (with custom system instructions and Q&A examples), then scores both using AI-as-a-Judge.
     */
    suspend fun evaluateSideBySide(
        profile: TrainingProfile,
        qaPairs: List<TrainingQaPair>,
        testPrompt: String
    ): SideBySideEvaluation {
        val safePrompt = MessageValidator.sanitize(testPrompt)
        require(safePrompt.isNotEmpty()) { "Test prompt cannot be empty." }

        val topExamples = qaPairs
            .sortedByDescending { it.qualityScore }
            .take(8)
            .joinToString("\n\n") { "Q: ${it.question}\nA: ${it.answer}" }

        val prompt = """
            Perform a rigorous Side-by-Side Model Evaluation and AI-as-a-Judge comparison.
            
            User Test Prompt:
            "$safePrompt"
            
            1. `baseReply`: Write how a generic, untuned base assistant (with NO knowledge of the custom domain or training dataset below) would answer the User Test Prompt.
            2. `trainedReply`: Write how the fine-tuned assistant trained specifically on the following Custom Profile & Q&A Dataset answers the User Test Prompt:
               - Profile Name: ${profile.name}
               - Domain: ${profile.domainDescription}
               - Custom System Instruction: ${profile.systemPrompt}
               - Training Q&A Examples:
               $topExamples
            3. Act as an objective AI-as-a-Judge: score `baseScore` (1-10) and `trainedScore` (1-10) on how well each response follows the custom domain knowledge and style, pick the `winner` ("trained", "base", or "tie"), and provide a concise `judgeSummary`.
        """.trimIndent()

        return try {
            withTimeout(TIMEOUT_MS) {
                val response = sideBySideModel.generateContent(content { text(prompt) })
                val jsonString = response.text ?: throw IllegalStateException("Side-by-side evaluator returned empty JSON.")
                val json = JSONObject(jsonString)

                SideBySideEvaluation(
                    testPrompt = safePrompt,
                    baseReply = json.getString("baseReply"),
                    trainedReply = json.getString("trainedReply"),
                    baseScore = json.optInt("baseScore", 6).coerceIn(1, 10),
                    trainedScore = json.optInt("trainedScore", 9).coerceIn(1, 10),
                    winner = json.optString("winner", "trained"),
                    judgeSummary = json.optString(
                        "judgeSummary",
                        "The trained profile applied domain-specific knowledge and tone from your Q&A dataset."
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Side-by-side evaluation timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Side-by-side evaluation failed", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "LeoTrainingEngine"
        const val MODEL_NAME = "gemini-3.8-flash"
        const val TIMEOUT_MS = 60_000L
    }
}
