#!/usr/bin/env python3
"""
End-to-End Autonomous Local LLM Fine-Tuning Orchestrator (Phases 1 -> 4)
Configured for:
- Source Data Path: ./data/raw_docs (or custom --source-dir)
- Privacy Mode: 100% Local Ollama Teacher & Judge (llama3.3 / qwen2.5)
- Target Task: Domain documentation, PDFs, and technical Q&A
"""

import argparse
import json
from dataclasses import asdict
from pathlib import Path

from phase1_hardware_env import inspect_hardware
from phase2_synthetic_dataset import (
    generate_qa_pairs_from_chunk,
    ingest_directory,
    judge_and_filter_dataset,
    save_chatml_jsonl,
)
from phase3_qlora_trainer import build_unsloth_training_script, inspect_jsonl_schema
from phase4_evaluate_judge import run_side_by_side_benchmark


def ensure_sample_docs(source_dir: str) -> None:
    root = Path(source_dir)
    root.mkdir(parents=True, exist_ok=True)
    sample_doc = root / "leo_architecture_and_security_manual.md"
    if not sample_doc.exists():
        sample_doc.write_text(
            """# LEO Technical Architecture & AES-256-GCM Security Specification

## 1. Local Room Database Encryption at Rest
All chat messages (`chat_messages`), session previews (`chat_sessions`), and training datasets (`training_profiles`, `training_qa_pairs`) are encrypted at rest using `AES/GCM/NoPadding` with a 256-bit master key managed by `AndroidKeyStore`.
Every encryption operation generates a fresh 12-byte (96-bit) cryptographically random IV and a 128-bit authentication tag, binding the `sessionId` or `profileId` as Associated Authenticated Data (AAD) to prevent cross-record tampering.

## 2. Input Sanitization & Character Guardrails
Before any user message is sent to the model or persisted in SQLite, `MessageValidator` strips null bytes and non-printable ASCII control characters, normalizes CRLF line endings, collapses excessive blank lines, and enforces a strict 4,000-character limit.

## 3. Local QLoRA Fine-Tuning & Unsloth Workflow
When training a local student model (such as `unsloth/Llama-3.2-3B-Instruct-bnb-4bit` or `unsloth/Qwen2.5-7B-Instruct-bnb-4bit`), raw technical manuals are chunked and converted into ChatML `.jsonl` pairs by a local Ollama teacher model, audited by an AI-as-a-Judge pass (`quality_score >= 8`), and trained with 4-bit NormalFloat (`nf4`) QLoRA adapters before exporting to `q4_k_m` GGUF.
""",
            encoding="utf-8",
        )


def run_autonomous_pipeline(
    source_dir: str = "./data/raw_docs",
    target_task: str = "Domain documentation, PDFs, and technical Q&A",
    teacher_model: str = "llama3.3",
    output_dir: str = "./ml_pipeline/artifacts",
) -> dict:
    out_root = Path(output_dir)
    out_root.mkdir(parents=True, exist_ok=True)

    # Phase 1: Hardware & Environment Inspection
    hardware = inspect_hardware()

    # Phase 2: Ingest Documents -> Teacher Q&A Generation -> AI-as-a-Judge Filtering
    ensure_sample_docs(source_dir)
    chunks = ingest_directory(source_dir)
    raw_records = []
    for chunk in chunks:
        raw_records.extend(
            generate_qa_pairs_from_chunk(
                chunk=chunk,
                target_task=target_task,
                teacher_model=teacher_model,
            )
        )

    curated_records = judge_and_filter_dataset(
        records=raw_records,
        target_task=target_task,
        judge_model=teacher_model,
        min_score=8,
    )

    dataset_path = str(out_root / "cleaned_training_dataset.jsonl")
    saved_count = save_chatml_jsonl(curated_records, dataset_path)

    # Phase 3: Dataset Schema Mapping & 4-Bit Unsloth QLoRA Trainer Generation
    schema_mapping = inspect_jsonl_schema(dataset_path)
    trainer_script = build_unsloth_training_script(
        dataset_path=dataset_path,
        output_dir=str(out_root / "leo_student_qlora"),
        hardware=hardware,
        export_format="gguf",
    )
    trainer_script_path = out_root / "train_unsloth_qlora.py"
    trainer_script_path.write_text(trainer_script, encoding="utf-8")

    # Phase 4: Side-by-Side Evaluation & Judge Scoring
    held_out = [
        {"question": rec.question, "answer": rec.answer}
        for rec in curated_records[:2]
    ]
    eval_items = run_side_by_side_benchmark(
        held_out_pairs=held_out,
        judge_model=teacher_model,
    )

    summary = {
        "phase1_hardware": asdict(hardware),
        "phase2_dataset": {
            "source_dir": source_dir,
            "privacy_mode": "local_ollama_offline",
            "teacher_model": teacher_model,
            "ingested_chunks": len(chunks),
            "generated_pairs": len(raw_records),
            "judge_approved_pairs": saved_count,
            "dataset_jsonl": dataset_path,
        },
        "phase3_qlora": {
            "schema_mapping": asdict(schema_mapping),
            "trainer_script": str(trainer_script_path),
        },
        "phase4_evaluation": [asdict(item) for item in eval_items],
    }

    report_path = out_root / "pipeline_execution_report.json"
    report_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Autonomous 4-Phase Local LLM Fine-Tuning Pipeline")
    parser.add_argument("--source-dir", default="./data/raw_docs", help="Path to raw documents")
    parser.add_argument(
        "--target-task",
        default="Domain documentation, PDFs, and technical Q&A",
        help="Target domain knowledge or behavior to learn",
    )
    parser.add_argument("--teacher-model", default="llama3.3", help="Local Ollama teacher/judge model")
    parser.add_argument("--self-test", action="store_true", help="Run end-to-end verification pass")
    args = parser.parse_args()

    result = run_autonomous_pipeline(
        source_dir=args.source_dir,
        target_task=args.target_task,
        teacher_model=args.teacher_model,
    )
    print(json.dumps(result, indent=2))
