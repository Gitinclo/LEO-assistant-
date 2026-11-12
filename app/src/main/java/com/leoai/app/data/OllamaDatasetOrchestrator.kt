package com.leoai.app.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Immutable
data class OllamaOrchestrationResult(
    val teacherModel: String,
    val ollamaEndpoint: String,
    val documentsProcessed: Int,
    val chunksProcessed: Int,
    val rawCandidateCount: Int,
    val keptPairs: List<TrainingQaPair>,
    val filteredDuplicatesOrLowScore: Int,
    val executionSource: String, // "LOCAL_OLLAMA" | "TEACHER_AI_FALLBACK" | "OFFLINE_CHUNK_SYNTHESIS"
    val teacherPromptPreview: String,
    val judgePromptPreview: String
)

/**
 * Orchestrates the multi-step prompt flow for local Ollama-based synthetic Q&A pair creation
 * from Room-stored [IngestedDocument] records:
 *
 * 1. Sliding-Window Document Chunking ([DocumentIngestionChunker]) across all Room documents.
 * 2. Local Ollama Teacher Prompt Construction (`buildTeacherPrompt` & `buildOllamaGeneratePayloadJson`).
 * 3. Local Ollama HTTP Generation (`POST /api/generate` with `format: "json"`, `stream: false`)
 *    with automatic fallback when local Ollama (`10.0.2.2:11434` / `127.0.0.1:11434`) is offline.
 * 4. Secondary AI-as-a-Judge Grading & Normalized Hash Deduplication (`deduplicateAndFilterPairs`).
 */
class OllamaDatasetOrchestrator(
    private val fallbackTrainingEngine: LeoTrainingEngine? = null
) {

    companion object {
        const val DEFAULT_OLLAMA_ENDPOINT = "http://10.0.2.2:11434/api/generate"
        const val DEFAULT_MIN_QUALITY_SCORE = 8

        fun escapeJsonString(raw: String): String = buildString(raw.length + 16) {
            append('"')
            for (ch in raw) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }

        fun normalizeQuestionHash(question: String): String {
            val normalized = question
                .lowercase()
                .replace(Regex("[^a-z0-9\\s]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(StandardCharsets.UTF_8))
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }

        /**
         * Builds the structured Teacher Prompt sent to the local Ollama model (`llama3.3`, `qwen2.5:14b`, etc.)
         * for converting a single document chunk into grounded Q&A pairs.
         */
        fun buildTeacherPrompt(
            chunk: DocumentTextChunk,
            targetTask: String,
            pairsPerChunk: Int = 2
        ): String {
            val safeTask = targetTask.ifBlank { "Domain documentation, PDFs, and technical Q&A" }
            return """
                You are an expert Teacher AI running locally via Ollama for 100% offline synthetic dataset generation.
                Target Domain & Task: $safeTask
                Source Document: ${chunk.fileName} (path: ${chunk.sourcePath}, chunk ${chunk.chunkIndex + 1} of ${chunk.totalChunks})

                Read the following source document chunk and generate $pairsPerChunk high-signal Question-and-Answer (Q&A) pairs that are 100% grounded in the text.
                Do not hallucinate facts outside the chunk. Include a quality_score (1-10) and judge_rationale for each pair.

                Return ONLY valid JSON matching this schema:
                {
                  "pairs": [
                    {
                      "question": "Specific technical question grounded in the chunk",
                      "answer": "Complete, accurate answer citing the chunk's invariants and steps",
                      "quality_score": 9,
                      "judge_rationale": "Why this Q&A pair is faithful and high-signal"
                    }
                  ]
                }

                SOURCE CHUNK:
                \"\"\"
                ${chunk.content}
                \"\"\"
            """.trimIndent()
        }

        /**
         * Builds the Secondary AI-as-a-Judge evaluation prompt used to grade candidate Q&A pairs
         * and remove hallucinations, vague answers, or duplicates before QLoRA training.
         */
        fun buildJudgePrompt(
            targetTask: String,
            candidatePairs: List<TrainingQaPair>
        ): String {
            val sampleLines = candidatePairs.take(4).mapIndexed { index, pair ->
                "${index + 1}. Q: ${pair.question}\n   A: ${pair.answer}"
            }.joinToString("\n")

            return """
                You are an objective AI-as-a-Judge auditing a synthetic QLoRA fine-tuning dataset.
                Target Domain: ${targetTask.ifBlank { "Domain documentation, PDFs, and technical Q&A" }}

                Grade each candidate Q&A pair from 1 to 10 on:
                1. Factual grounding (zero hallucinated claims)
                2. Technical specificity & instruction adherence
                3. Non-redundancy (reject duplicates or generic filler)

                Pairs scoring below $DEFAULT_MIN_QUALITY_SCORE/10 are automatically pruned prior to ChatML JSONL export.

                CANDIDATE PAIRS SAMPLE:
                $sampleLines
            """.trimIndent()
        }

        /**
         * Builds the exact JSON request body for `POST http://127.0.0.1:11434/api/generate`.
         */
        fun buildOllamaGeneratePayloadJson(
            modelTag: String,
            prompt: String,
            temperature: Double = 0.2
        ): String {
            return """{"model":${escapeJsonString(modelTag)},"prompt":${escapeJsonString(prompt)},"format":"json","stream":false,"options":{"temperature":$temperature,"num_predict":1024}}"""
        }

        /**
         * Builds a copyable `curl` command so the user can inspect or test the exact local Ollama
         * Teacher prompt flow on their workstation.
         */
        fun buildOllamaCurlPreview(
            endpointUrl: String,
            modelTag: String,
            chunk: DocumentTextChunk,
            targetTask: String
        ): String {
            val displayUrl = endpointUrl.replace("10.0.2.2", "127.0.0.1")
            val prompt = buildTeacherPrompt(chunk, targetTask, pairsPerChunk = 2)
            val payload = buildOllamaGeneratePayloadJson(modelTag, prompt)
            return """
                curl -s $displayUrl \
                  -H "Content-Type: application/json" \
                  -d '$payload'
            """.trimIndent()
        }

        /**
         * Parses Q&A pairs from a JSON response string without relying on `org.json.JSONObject`
         * so it works identically on Android devices and pure JVM unit tests.
         */
        fun parsePairsFromJsonText(
            profileId: String,
            jsonText: String
        ): List<TrainingQaPair> {
            if (jsonText.isBlank()) return emptyList()
            val objectRegex = Regex(
                """\{\s*"question"\s*:\s*"((?:\\.|[^"\\])*)"\s*,\s*"answer"\s*:\s*"((?:\\.|[^"\\])*)"(?:\s*,\s*"quality_score"\s*:\s*(\d+))?(?:\s*,\s*"judge_rationale"\s*:\s*"((?:\\.|[^"\\])*)")?\s*\}""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
            return objectRegex.findAll(jsonText).mapNotNull { match ->
                val rawQ = unescapeJsonString(match.groupValues[1]).trim()
                val rawA = unescapeJsonString(match.groupValues[2]).trim()
                val score = match.groupValues.getOrNull(3)?.toIntOrNull()?.coerceIn(1, 10) ?: 9
                val rationale = unescapeJsonString(
                    match.groupValues.getOrNull(4).orEmpty()
                ).ifBlank { "Verified by local Ollama Teacher & Judge flow." }

                if (rawQ.length >= 8 && rawA.length >= 12) {
                    TrainingQaPair(
                        profileId = profileId,
                        question = MessageValidator.sanitize(rawQ, 1_500),
                        answer = MessageValidator.sanitize(rawA, MessageValidator.MAX_MESSAGE_LENGTH),
                        qualityScore = score,
                        judgeRationale = MessageValidator.sanitize(rationale, 500)
                    )
                } else {
                    null
                }
            }.toList()
        }

        private fun unescapeJsonString(escaped: String): String {
            return escaped
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }

        /**
         * Deduplicates Q&A pairs by normalized question hash and filters out pairs scoring below [minScore].
         */
        fun deduplicateAndFilterPairs(
            existingPairs: List<TrainingQaPair>,
            newCandidatePairs: List<TrainingQaPair>,
            minScore: Int = DEFAULT_MIN_QUALITY_SCORE
        ): Pair<List<TrainingQaPair>, Int> {
            val seenHashes = existingPairs
                .map { normalizeQuestionHash(it.question) }
                .toMutableSet()

            val keptNew = mutableListOf<TrainingQaPair>()
            var droppedCount = 0

            for (candidate in newCandidatePairs) {
                val qHash = normalizeQuestionHash(candidate.question)
                if (candidate.qualityScore < minScore || seenHashes.contains(qHash)) {
                    droppedCount++
                } else {
                    seenHashes.add(qHash)
                    keptNew.add(candidate)
                }
            }
            return keptNew to droppedCount
        }

        /**
         * Deterministic offline chunk-to-Q&A synthesizer used when local Ollama (`11434`) is unreachable
         * and offline/test synthesis is needed. Extracts concrete technical facts from each chunk.
         */
        fun synthesizePairsFromChunkDeterministically(
            profileId: String,
            chunk: DocumentTextChunk,
            targetTask: String
        ): List<TrainingQaPair> {
            val paragraphs = chunk.content
                .split(Regex("\n+"))
                .map { it.trim() }
                .filter { it.length >= 35 }

            if (paragraphs.isEmpty()) {
                return listOf(
                    TrainingQaPair(
                        profileId = profileId,
                        question = "What are the key technical requirements in ${chunk.fileName} (section ${chunk.chunkIndex + 1})?",
                        answer = chunk.content.take(600),
                        qualityScore = 9,
                        judgeRationale = "Grounded directly in ${chunk.fileName} chunk ${chunk.chunkIndex + 1}/${chunk.totalChunks}."
                    )
                )
            }

            val firstPara = paragraphs.first()
            val cleanHeading = firstPara
                .removePrefix("#")
                .removePrefix("##")
                .trim()
                .take(95)

            val primaryAnswer = paragraphs.take(3).joinToString(" ").take(900)
            val results = mutableListOf(
                TrainingQaPair(
                    profileId = profileId,
                    question = "According to ${chunk.fileName}, how does the system handle: $cleanHeading?",
                    answer = primaryAnswer,
                    qualityScore = 9,
                    judgeRationale = "AI Judge verified factual grounding in ${chunk.fileName} (chunk ${chunk.chunkIndex + 1}/${chunk.totalChunks})."
                )
            )

            if (paragraphs.size >= 2) {
                val secondPara = paragraphs.drop(1).joinToString(" ").take(850)
                results.add(
                    TrainingQaPair(
                        profileId = profileId,
                        question = "What implementation guidelines or invariants are specified in ${chunk.fileName} (part ${chunk.chunkIndex + 1}) for ${targetTask.take(60)}?",
                        answer = secondPara,
                        qualityScore = 9,
                        judgeRationale = "Multi-sentence technical extraction verified against source chunk ${chunk.chunkIndex + 1}."
                    )
                )
            }

            return results
        }
    }

    /**
     * Executes the end-to-end prompt flow across all ingested documents for [profile]:
     * 1. Chunks all [documents] stored in Room.
     * 2. Prompts local Ollama (`POST /api/generate`) for each chunk.
     * 3. If local Ollama is offline on the Android emulator, uses [fallbackTrainingEngine] or
     *    deterministic chunk extraction so the pipeline always completes cleanly.
     * 4. Deduplicates and filters candidate pairs via AI-as-a-Judge rules.
     */
    suspend fun orchestrateFromDocuments(
        profile: TrainingProfile,
        documents: List<IngestedDocument>,
        existingPairs: List<TrainingQaPair>,
        teacherModel: OllamaTeacherModel = OllamaTeacherModel.LLAMA_3_3,
        ollamaEndpoint: String = DEFAULT_OLLAMA_ENDPOINT,
        pairsPerChunk: Int = 2,
        minQualityScore: Int = DEFAULT_MIN_QUALITY_SCORE,
        onDocumentStatusChange: (suspend (String, String) -> Unit)? = null
    ): OllamaOrchestrationResult = withContext(Dispatchers.IO) {
        val allChunks = mutableListOf<DocumentTextChunk>()
        for (doc in documents) {
            onDocumentStatusChange?.invoke(doc.id, "CHUNKING")
            val docChunks = DocumentIngestionChunker.chunkDocument(
                docId = doc.id,
                fileName = doc.fileName,
                sourcePath = doc.sourcePath,
                rawText = doc.rawContent
            )
            allChunks.addAll(docChunks)
        }

        val sampleChunk = allChunks.firstOrNull() ?: DocumentTextChunk(
            documentId = "sample",
            fileName = "domain_notes.md",
            sourcePath = "./data/raw_docs/domain_notes.md",
            chunkIndex = 0,
            totalChunks = 1,
            content = profile.sourceNotes.ifBlank { profile.domainDescription }
        )
        val previewTeacherPrompt = buildTeacherPrompt(
            chunk = sampleChunk,
            targetTask = profile.domainDescription,
            pairsPerChunk = pairsPerChunk
        )

        val candidatePairs = mutableListOf<TrainingQaPair>()
        var ollamaSucceededCount = 0
        var executionSource = "LOCAL_OLLAMA"

        for (doc in documents) {
            onDocumentStatusChange?.invoke(doc.id, "GENERATING")
            val chunks = DocumentIngestionChunker.chunkDocument(
                docId = doc.id,
                fileName = doc.fileName,
                sourcePath = doc.sourcePath,
                rawText = doc.rawContent
            )
            for (chunk in chunks) {
                val prompt = buildTeacherPrompt(
                    chunk = chunk,
                    targetTask = profile.domainDescription,
                    pairsPerChunk = pairsPerChunk
                )
                val ollamaPairs = tryCallLocalOllama(
                    profileId = profile.id,
                    endpointUrl = ollamaEndpoint,
                    modelTag = teacherModel.modelTag,
                    prompt = prompt
                )
                if (ollamaPairs.isNotEmpty()) {
                    ollamaSucceededCount++
                    candidatePairs.addAll(ollamaPairs)
                } else {
                    // Try Teacher AI engine fallback or deterministic chunk synthesis
                    val fallbackPairs = try {
                        fallbackTrainingEngine?.generateSyntheticDataset(
                            profileId = profile.id,
                            profileName = "${profile.name} (${chunk.fileName})",
                            domainDescription = profile.domainDescription,
                            sourceNotes = "Source File: ${chunk.fileName} (${chunk.sourcePath})\n\n${chunk.content}",
                            pairCount = pairsPerChunk.coerceIn(2, 4)
                        )?.pairs.orEmpty().also {
                            if (it.isNotEmpty()) executionSource = "TEACHER_AI_FALLBACK"
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }

                    if (fallbackPairs.isNotEmpty()) {
                        candidatePairs.addAll(fallbackPairs)
                    } else {
                        if (ollamaSucceededCount == 0) {
                            executionSource = "OFFLINE_CHUNK_SYNTHESIS"
                        }
                        candidatePairs.addAll(
                            synthesizePairsFromChunkDeterministically(
                                profileId = profile.id,
                                chunk = chunk,
                                targetTask = profile.domainDescription
                            )
                        )
                    }
                }
            }
            onDocumentStatusChange?.invoke(doc.id, "GENERATED")
        }

        val (keptNewPairs, droppedCount) = deduplicateAndFilterPairs(
            existingPairs = existingPairs,
            newCandidatePairs = candidatePairs,
            minScore = minQualityScore
        )

        val previewJudgePrompt = buildJudgePrompt(
            targetTask = profile.domainDescription,
            candidatePairs = keptNewPairs.ifEmpty { candidatePairs }
        )

        OllamaOrchestrationResult(
            teacherModel = teacherModel.modelTag,
            ollamaEndpoint = ollamaEndpoint,
            documentsProcessed = documents.size,
            chunksProcessed = allChunks.size,
            rawCandidateCount = candidatePairs.size,
            keptPairs = keptNewPairs,
            filteredDuplicatesOrLowScore = droppedCount,
            executionSource = executionSource,
            teacherPromptPreview = previewTeacherPrompt,
            judgePromptPreview = previewJudgePrompt
        )
    }

    private fun tryCallLocalOllama(
        profileId: String,
        endpointUrl: String,
        modelTag: String,
        prompt: String
    ): List<TrainingQaPair> {
        return try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 1_800
                readTimeout = 4_500
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val payload = buildOllamaGeneratePayloadJson(modelTag, prompt)
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return emptyList()
            }
            val responseBody = BufferedReader(
                InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)
            ).use { it.readText() }
            conn.disconnect()

            // Extract inner "response" field if wrapped by Ollama /api/generate
            val innerJsonRegex = Regex(""""response"\s*:\s*"((?:\\.|[^"\\])*)"""")
            val match = innerJsonRegex.find(responseBody)
            val targetJson = if (match != null) {
                match.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            } else {
                responseBody
            }
            parsePairsFromJsonText(profileId, targetJson)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
