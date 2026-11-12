# LEO Technical Architecture & AES-256-GCM Security Specification

## 1. Local Room Database Encryption at Rest
All chat messages (`chat_messages`), session previews (`chat_sessions`), and training datasets (`training_profiles`, `training_qa_pairs`) are encrypted at rest using `AES/GCM/NoPadding` with a 256-bit master key managed by `AndroidKeyStore`.
Every encryption operation generates a fresh 12-byte (96-bit) cryptographically random IV and a 128-bit authentication tag, binding the `sessionId` or `profileId` as Associated Authenticated Data (AAD) to prevent cross-record tampering.

## 2. Input Sanitization & Character Guardrails
Before any user message is sent to the model or persisted in SQLite, `MessageValidator` strips null bytes and non-printable ASCII control characters, normalizes CRLF line endings, collapses excessive blank lines, and enforces a strict 4,000-character limit.

## 3. Local QLoRA Fine-Tuning & Unsloth Workflow
When training a local student model (such as `unsloth/Llama-3.2-3B-Instruct-bnb-4bit` or `unsloth/Qwen2.5-7B-Instruct-bnb-4bit`), raw technical manuals are chunked and converted into ChatML `.jsonl` pairs by a local Ollama teacher model, audited by an AI-as-a-Judge pass (`quality_score >= 8`), and trained with 4-bit NormalFloat (`nf4`) QLoRA adapters before exporting to `q4_k_m` GGUF.
