# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.1] - 2026-10-24

### Added
- **Automated CI Guardrails**:
  - Configured comprehensive GitHub Actions workflow (`.github/workflows/build.yml`) incorporating the four essential vibe coding guardrails: Linter/Static Analysis, Deprecation/Vulnerability auditing, Code Formatting check, and JVM Regression Tests.
- **Vibe Coding Process Guidelines**:
  - Documented the Architect-First Rule, Periodic Refactor Session checkpoints, and Rubber Duck Technique in `CONTRIBUTING.md` and `README.md`.

### Changed
- Removed `.env.example` environment variable entries so the application runs immediately with zero secret/API key prompts and unrestricted preview sharing.
- Refactored date helpers in `Models.kt` to use thread-safe UTC `SimpleDateFormat` and `Calendar` for seamless minSdk 24 compatibility and zero NewApi lint warnings.
- Cleaned up layout width modifiers in `ChatScreen.kt` and status bar color definitions in `MainActivity.kt`.

---

## [1.0.0] - 2026-10-24

### Added
- **Welcome Screen**:
  - Time-of-day dynamic greeting (*"Good morning."*, *"Good afternoon."*, *"Late night thoughts?"*).
  - 5 curated starter prompt chips to immediately spark reflection or writing.
  - Subtle organic botanical background illustration and ambient texture.
  - Bottom input bar routing directly to newly created chat sessions.
- **Chat Screen**:
  - Full multi-turn conversation UI with optimistic message rendering.
  - Animated 3-dot typing indicator (*"thinking…"*, *"here when you need"*).
  - Automatic session titling from initial user query and preview generation.
  - On-device LEO companion engine providing calm, structured responses without external API keys.
- **History Screen**:
  - Chronological conversation grouping (**TODAY**, **YESTERDAY**, **EARLIER**).
  - One-tap conversation resume and individual session deletion.
  - Pull-to-refresh and empty-state action button.
- **Settings Screen**:
  - Brand overview card with high-resolution avatar.
  - About section articulating the companion philosophy.
  - One-click "Clear all conversations" with transient feedback.
- **Persistence & Architecture**:
  - Local **Room Database** (`leo_ai_room.db`) with KSP code generation, DAOs, and reactive `Flow` queries for offline-first session and message storage.
  - Unidirectional data flow with Jetpack Compose, ViewModel, and StateFlow.
- **CI / CD**:
  - Automated GitHub Actions workflow (`.github/workflows/build.yml`) for building and publishing debug APKs.
