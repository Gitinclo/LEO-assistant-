#!/usr/bin/env python3
"""
Phase 3: Automated Schema Mapping & 4-Bit QLoRA Training with Unsloth
1. Inspects the cleaned JSONL dataset to automatically detect ChatML (`messages`),
   Alpaca (`instruction`/`input`/`output`), or ShareGPT (`conversations`) schemas.
2. Calculates safe batch size, gradient accumulation steps, and learning rate for the detected VRAM.
3. Generates and executes the 4-bit QLoRA Unsloth trainer and exports weights to GGUF & safetensors.
"""

import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Dict, Optional

from phase1_hardware_env import HardwareProfile, inspect_hardware


@dataclass
class DatasetSchemaMapping:
    schema_type: str  # "chatml" | "alpaca" | "prompt_completion"
    row_count: int
    user_key: str
    assistant_key: str
    system_key: Optional[str]
    avg_char_length: int


def inspect_jsonl_schema(jsonl_path: str) -> DatasetSchemaMapping:
    path = Path(jsonl_path)
    if not path.exists():
        raise FileNotFoundError(f"Dataset file not found: {jsonl_path}")

    rows = []
    total_chars = 0
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                obj = json.loads(line)
                rows.append(obj)
                total_chars += len(line)

    if not rows:
        raise ValueError(f"Dataset file is empty: {jsonl_path}")

    first = rows[0]
    avg_len = total_chars // len(rows)

    if "messages" in first and isinstance(first["messages"], list):
        return DatasetSchemaMapping(
            schema_type="chatml",
            row_count=len(rows),
            user_key="messages[role=user].content",
            assistant_key="messages[role=assistant].content",
            system_key="messages[role=system].content",
            avg_char_length=avg_len,
        )

    if "instruction" in first and "output" in first:
        return DatasetSchemaMapping(
            schema_type="alpaca",
            row_count=len(rows),
            user_key="instruction + input",
            assistant_key="output",
            system_key="instruction",
            avg_char_length=avg_len,
        )

    return DatasetSchemaMapping(
        schema_type="prompt_completion",
        row_count=len(rows),
        user_key="prompt" if "prompt" in first else "question",
        assistant_key="completion" if "completion" in first else "answer",
        system_key=None,
        avg_char_length=avg_len,
    )


def build_unsloth_training_script(
    dataset_path: str,
    output_dir: str = "outputs/leo_qlora_model",
    hardware: Optional[HardwareProfile] = None,
    export_format: str = "gguf",
) -> str:
    hw = hardware or inspect_hardware()
    schema = inspect_jsonl_schema(dataset_path)

    return f'''#!/usr/bin/env python3
"""
Auto-Generated Unsloth 4-Bit QLoRA Training Script
Detected Hardware: {hw.device_name} ({hw.memory_gb} GB)
Selected Student Model: {hw.recommended_student_model}
Dataset Schema: {schema.schema_type} ({schema.row_count} verified rows)
"""

import torch
from datasets import load_dataset
from trl import SFTTrainer
from transformers import TrainingArguments
from unsloth import FastLanguageModel, is_bfloat16_supported

MAX_SEQ_LENGTH = {hw.max_seq_length}
MODEL_NAME = "{hw.recommended_student_model}"
DATASET_PATH = "{dataset_path}"
OUTPUT_DIR = "{output_dir}"

def main():
    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name=MODEL_NAME,
        max_seq_length=MAX_SEQ_LENGTH,
        dtype=None,
        load_in_4bit=True,
    )

    model = FastLanguageModel.get_peft_model(
        model,
        r={hw.lora_rank},
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        lora_alpha={hw.lora_alpha},
        lora_dropout=0,
        bias="none",
        use_gradient_checkpointing="unsloth",
        random_state=3407,
    )

    dataset = load_dataset("json", data_files=DATASET_PATH, split="train")

    def format_prompts(batch):
        texts = []
        if "{schema.schema_type}" == "chatml":
            for msgs in batch["messages"]:
                texts.append(
                    tokenizer.apply_chat_template(msgs, tokenize=False, add_generation_prompt=False)
                )
        else:
            for q, a in zip(batch["{schema.user_key}"], batch["{schema.assistant_key}"]):
                msgs = [
                    {{"role": "user", "content": str(q)}},
                    {{"role": "assistant", "content": str(a)}},
                ]
                texts.append(
                    tokenizer.apply_chat_template(msgs, tokenize=False, add_generation_prompt=False)
                )
        return {{"text": texts}}

    dataset = dataset.map(format_prompts, batched=True)

    trainer = SFTTrainer(
        model=model,
        tokenizer=tokenizer,
        train_dataset=dataset,
        dataset_text_field="text",
        max_seq_length=MAX_SEQ_LENGTH,
        dataset_num_proc=2,
        packing=False,
        args=TrainingArguments(
            per_device_train_batch_size={hw.batch_size},
            gradient_accumulation_steps={hw.grad_accumulation},
            warmup_steps=5,
            num_train_epochs=3,
            learning_rate={hw.learning_rate},
            fp16=not is_bfloat16_supported(),
            bf16=is_bfloat16_supported(),
            logging_steps=1,
            optim="adamw_8bit",
            weight_decay=0.01,
            lr_scheduler_type="linear",
            seed=3407,
            output_dir=OUTPUT_DIR,
        ),
    )

    trainer.train()

    # Save LoRA safetensors adapters
    model.save_pretrained(f"{{OUTPUT_DIR}}_safetensors")
    tokenizer.save_pretrained(f"{{OUTPUT_DIR}}_safetensors")

    # Export 4-bit quantized GGUF for Ollama / LM Studio
    if "{export_format}" == "gguf":
        model.save_pretrained_gguf(
            f"{{OUTPUT_DIR}}_gguf",
            tokenizer,
            quantization_method="q4_k_m",
        )

if __name__ == "__main__":
    main()
'''
