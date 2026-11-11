package com.leoai.app.data

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class HardwarePreset(
    val id: String,
    val label: String,
    val memorySpec: String,
    val defaultStudentModel: String,
    val defaultLoraRank: Int,
    val defaultBatchSize: Int
) {
    RTX_8GB(
        id = "RTX_8GB",
        label = "NVIDIA GPU · 8 GB VRAM",
        memorySpec = "8 GB VRAM (e.g., RTX 3070 / 4060)",
        defaultStudentModel = "unsloth/Llama-3.2-3B-Instruct-bnb-4bit",
        defaultLoraRank = 16,
        defaultBatchSize = 2
    ),
    RTX_16GB(
        id = "RTX_16GB",
        label = "NVIDIA GPU · 12–16 GB VRAM",
        memorySpec = "12–16 GB VRAM (e.g., RTX 4070 Ti / 4080)",
        defaultStudentModel = "unsloth/Qwen2.5-7B-Instruct-bnb-4bit",
        defaultLoraRank = 16,
        defaultBatchSize = 4
    ),
    RTX_24GB_PLUS(
        id = "RTX_24GB_PLUS",
        label = "NVIDIA GPU · 24 GB+ VRAM",
        memorySpec = "24 GB+ VRAM (e.g., RTX 3090 / 4090 / A100)",
        defaultStudentModel = "unsloth/Qwen2.5-14B-Instruct-bnb-4bit",
        defaultLoraRank = 32,
        defaultBatchSize = 4
    ),
    MAC_M_SERIES_16GB(
        id = "MAC_M_SERIES_16GB",
        label = "Apple Silicon · 16 GB Unified",
        memorySpec = "16 GB Unified Memory (M1 / M2 / M3 / M4)",
        defaultStudentModel = "unsloth/Llama-3.2-3B-Instruct-bnb-4bit",
        defaultLoraRank = 16,
        defaultBatchSize = 2
    ),
    MAC_M_SERIES_32GB_PLUS(
        id = "MAC_M_SERIES_32GB_PLUS",
        label = "Apple Silicon · 32 GB+ Unified",
        memorySpec = "32 GB+ Unified Memory (M2/M3/M4 Pro & Max)",
        defaultStudentModel = "unsloth/Qwen2.5-7B-Instruct-bnb-4bit",
        defaultLoraRank = 32,
        defaultBatchSize = 4
    );

    companion object {
        fun fromId(id: String): HardwarePreset =
            entries.firstOrNull { it.id == id } ?: RTX_16GB
    }
}

@Immutable
data class TrainingProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val domainDescription: String,
    val sourceNotes: String,
    val systemPrompt: String,
    val hardwareTarget: String = HardwarePreset.RTX_16GB.id,
    val recommendedBaseModel: String = HardwarePreset.RTX_16GB.defaultStudentModel,
    val qloraScript: String = "",
    val ollamaModelfile: String = "",
    val isActive: Boolean = true,
    val updatedAt: String = nowIsoString()
)

@Immutable
data class TrainingQaPair(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val question: String,
    val answer: String,
    val qualityScore: Int = 9,
    val judgeRationale: String = "Clear, grounded domain Q&A pair.",
    val createdAt: String = nowIsoString()
)

@Immutable
data class QloraPipelinePlan(
    val recommendedModel: String,
    val vramEstimate: String,
    val learningRate: String,
    val loraRank: Int,
    val loraAlpha: Int,
    val batchSize: Int,
    val gradAccumulation: Int,
    val epochs: Int,
    val coachSummary: String,
    val pythonScript: String,
    val ollamaModelfile: String
)

@Immutable
data class SideBySideEvaluation(
    val testPrompt: String,
    val baseReply: String,
    val trainedReply: String,
    val baseScore: Int,
    val trainedScore: Int,
    val winner: String, // "trained" | "base" | "tie"
    val judgeSummary: String
)

object TrainingDatasetExporter {

    private fun escapeJson(raw: String): String = buildString(raw.length + 16) {
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

    /**
     * Exports Q&A pairs in standard OpenAI / Unsloth ChatML `.jsonl` format (one JSON object per line).
     */
    fun exportToChatMlJsonl(
        profile: TrainingProfile,
        pairs: List<TrainingQaPair>
    ): String {
        if (pairs.isEmpty()) return ""
        val sysPrompt = profile.systemPrompt.ifBlank {
            "You are a helpful domain assistant trained on ${profile.name}."
        }
        return pairs.joinToString("\n") { pair ->
            val sysMsg = """{"role":"system","content":${escapeJson(sysPrompt)}}"""
            val userMsg = """{"role":"user","content":${escapeJson(pair.question)}}"""
            val asstMsg = """{"role":"assistant","content":${escapeJson(pair.answer)}}"""
            """{"messages":[$sysMsg,$userMsg,$asstMsg],"quality_score":${pair.qualityScore}}"""
        }
    }

    /**
     * Exports Q&A pairs in standard Alpaca `.json` array format (`instruction`, `input`, `output`).
     */
    fun exportToAlpacaJson(
        profile: TrainingProfile,
        pairs: List<TrainingQaPair>
    ): String {
        val sysPrompt = profile.systemPrompt.ifBlank {
            "Answer accurately according to ${profile.name} guidelines."
        }
        val entries = pairs.joinToString(",\n") { pair ->
            """  {
    "instruction": ${escapeJson(sysPrompt)},
    "input": ${escapeJson(pair.question)},
    "output": ${escapeJson(pair.answer)},
    "judge_score": ${pair.qualityScore},
    "judge_rationale": ${escapeJson(pair.judgeRationale)}
  }"""
        }
        return "[\n$entries\n]"
    }

    /**
     * Builds a ready-to-run Unsloth QLoRA Python script tailored to the hardware preset and dataset.
     */
    fun buildDefaultQloraScript(
        profileName: String,
        baseModel: String,
        systemPrompt: String,
        loraRank: Int,
        loraAlpha: Int,
        batchSize: Int,
        gradAccum: Int,
        learningRate: String,
        epochs: Int
    ): String {
        val safeSystem = systemPrompt.replace("\"", "\\\"").replace("\n", " ")
        return """
            # Unsloth QLoRA Local Fine-Tuning Pipeline — $profileName
            # Install: pip install "unsloth[colab-new] @ git+https://github.com/unslothai/unsloth.git"
            #          pip install --no-deps xformers "trl<0.9.0" peft accelerate bitsandbytes
            
            import torch
            from datasets import load_dataset
            from trl import SFTTrainer
            from transformers import TrainingArguments
            from unsloth import FastLanguageModel, is_bfloat16_supported
            
            max_seq_length = 2048
            model, tokenizer = FastLanguageModel.from_pretrained(
                model_name = "$baseModel",
                max_seq_length = max_seq_length,
                dtype = None,
                load_in_4bit = True,
            )
            
            # Configure 4-bit QLoRA adapters
            model = FastLanguageModel.get_peft_model(
                model,
                r = $loraRank,
                target_modules = ["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
                lora_alpha = $loraAlpha,
                lora_dropout = 0,
                bias = "none",
                use_gradient_checkpointing = "unsloth",
                random_state = 3407,
            )
            
            # Load AI-generated ChatML JSONL dataset exported from LEO Train My AI Studio
            dataset = load_dataset("json", data_files = "leo_training_dataset.jsonl", split = "train")
            
            def format_chat_template(batch):
                texts = [
                    tokenizer.apply_chat_template(msgs, tokenize = False, add_generation_prompt = False)
                    for msgs in batch["messages"]
                ]
                return { "text": texts }
            
            dataset = dataset.map(format_chat_template, batched = True)
            
            trainer = SFTTrainer(
                model = model,
                tokenizer = tokenizer,
                train_dataset = dataset,
                dataset_text_field = "text",
                max_seq_length = max_seq_length,
                dataset_num_proc = 2,
                packing = False,
                args = TrainingArguments(
                    per_device_train_batch_size = $batchSize,
                    gradient_accumulation_steps = $gradAccum,
                    warmup_steps = 5,
                    num_train_epochs = $epochs,
                    learning_rate = $learningRate,
                    fp16 = not is_bfloat16_supported(),
                    bf16 = is_bfloat16_supported(),
                    logging_steps = 1,
                    optim = "adamw_8bit",
                    weight_decay = 0.01,
                    lr_scheduler_type = "linear",
                    seed = 3407,
                    output_dir = "outputs",
                ),
            )
            
            trainer.train()
            
            # Export 4-bit quantized GGUF for Ollama / LM Studio
            model.save_pretrained_gguf("leo_custom_model", tokenizer, quantization_method = "q4_k_m")
            print("Saved GGUF for Ollama! System prompt: $safeSystem")
        """.trimIndent()
    }

    fun buildDefaultOllamaModelfile(
        profileName: String,
        systemPrompt: String
    ): String {
        return """
            # Ollama Modelfile for $profileName
            # Run: ollama create leo-custom -f Modelfile && ollama run leo-custom
            FROM ./leo_custom_model-unsloth.Q4_K_M.gguf
            PARAMETER temperature 0.4
            PARAMETER top_p 0.9
            SYSTEM ""${'"'}
            $systemPrompt
            ""${'"'}
        """.trimIndent()
    }
}
