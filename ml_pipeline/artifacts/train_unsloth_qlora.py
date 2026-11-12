#!/usr/bin/env python3
"""
Auto-Generated Unsloth 4-Bit QLoRA Training Script
Detected Hardware: x86_64 (12.0 GB)
Selected Student Model: unsloth/Qwen2.5-7B-Instruct-bnb-4bit
Dataset Schema: chatml (4 verified rows)
"""

import torch
from datasets import load_dataset
from trl import SFTTrainer
from transformers import TrainingArguments
from unsloth import FastLanguageModel, is_bfloat16_supported

MAX_SEQ_LENGTH = 2048
MODEL_NAME = "unsloth/Qwen2.5-7B-Instruct-bnb-4bit"
DATASET_PATH = "ml_pipeline/artifacts/cleaned_training_dataset.jsonl"
OUTPUT_DIR = "ml_pipeline/artifacts/leo_student_qlora"

def main():
    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name=MODEL_NAME,
        max_seq_length=MAX_SEQ_LENGTH,
        dtype=None,
        load_in_4bit=True,
    )

    model = FastLanguageModel.get_peft_model(
        model,
        r=16,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        lora_alpha=16,
        lora_dropout=0,
        bias="none",
        use_gradient_checkpointing="unsloth",
        random_state=3407,
    )

    dataset = load_dataset("json", data_files=DATASET_PATH, split="train")

    def format_prompts(batch):
        texts = []
        if "chatml" == "chatml":
            for msgs in batch["messages"]:
                texts.append(
                    tokenizer.apply_chat_template(msgs, tokenize=False, add_generation_prompt=False)
                )
        else:
            for q, a in zip(batch["messages[role=user].content"], batch["messages[role=assistant].content"]):
                msgs = [
                    {"role": "user", "content": str(q)},
                    {"role": "assistant", "content": str(a)},
                ]
                texts.append(
                    tokenizer.apply_chat_template(msgs, tokenize=False, add_generation_prompt=False)
                )
        return {"text": texts}

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
            per_device_train_batch_size=2,
            gradient_accumulation_steps=4,
            warmup_steps=5,
            num_train_epochs=3,
            learning_rate=2e-4,
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
    model.save_pretrained(f"{OUTPUT_DIR}_safetensors")
    tokenizer.save_pretrained(f"{OUTPUT_DIR}_safetensors")

    # Export 4-bit quantized GGUF for Ollama / LM Studio
    if "gguf" == "gguf":
        model.save_pretrained_gguf(
            f"{OUTPUT_DIR}_gguf",
            tokenizer,
            quantization_method="q4_k_m",
        )

if __name__ == "__main__":
    main()
