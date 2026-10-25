# Contributing to LEO AI

Thank you for your interest in contributing to **LEO AI**! We welcome contributions that help preserve and enhance our calm, thoughtful AI companion experience.

---

## Code of Conduct

All contributors are expected to uphold our [Code of Conduct](CODE_OF_CONDUCT.md). Please be respectful and considerate in all interactions.

---

## Development Setup

### Prerequisites

- **Java Development Kit (JDK)**: JDK 21 (Temurin or OpenJDK recommended)
- **Android SDK**: API Level 36 (Android 16), build-tools 36.0.0
- **Build System**: Gradle 9.3.1 (or compatible)
- **IDE**: Android Studio Ladybug / Meerkat or VS Code with Kotlin extensions

### Getting Started

1. **Fork and clone the repository**:
   ```bash
   git clone https://github.com/<your-username>/leo-ai.git
   cd leo-ai
   ```

2. **Verify your local environment**:
   ```bash
   gradle --version
   ```

3. **Build the debug APK**:
   ```bash
   gradle assembleDebug
   ```
   The generated APK will be placed at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Architectural Guidelines

LEO AI is built using modern Android best practices:

- **Language**: 100% Kotlin.
- **UI Framework**: Jetpack Compose adhering strictly to Material Design 3 (M3).
- **Design Archetype**: Warm, organic, earthy (`#FDFBF7` background, `#C86A51` terracotta primary, `#7E947A` sage secondary).
- **Architecture**: MVVM pattern (`ViewModel` + `StateFlow` + Repository layer).
- **Persistence**: Room Database (`leo_ai_room.db`) with `ChatMessageDao` and reactive `Flow` queries for offline durability.
- **Privacy & Security**: Zero client-side API keys; fallback companion engine runs locally on device.

---

## Vibe Coding Guardrails & AI Collaboration Guidelines

When building, extending, or refactoring features using AI ("Vibe Coding"), contributors must treat the AI not just as a coder, but as an assistant requiring active oversight. To eliminate "spaghetti code," follow this two-part framework:

### Part 1: Automated Technical Guardrails (CI Pipeline)

Every Pull Request and commit to `main` must pass the automated checks in `.github/workflows/build.yml`:
1. **Linter & Static Analysis**: Flags messy code, logic issues, unhandled errors, and unused variables.
2. **Security & Deprecation Checker**: Audits project dependencies and Gradle configurations against outdated or vulnerable packages.
3. **Formatter & Code Style**: Validates that all files adhere to unified Kotlin and Compose code style conventions.
4. **Automated Regression Tests**: Runs JVM unit tests (`testDebugUnitTest`) to verify that newly generated code hasn't broken existing functionality.
5. **APK Build & Packaging**: Ensures the APK compiles cleanly.

### Part 2: Process Guardrails (Prompting Strategy)

Tools catch syntax and regression errors, but architecture requires thoughtful prompting:

1. **The "Architect First" Rule**:
   - Never ask the AI to "just write the code."
   - *Bad Prompt:* "Make the chat history searchable."
   - *Good Prompt:* "Plan the architecture for chat history search. Inspect `ChatMessageDao` and `LeoRepository` for existing query functions we can reuse. Do not write code yet; outline the plan, data flow, and file impacts first."

2. **The Periodic Refactor Session**:
   - Every 3–4 features or prompt sessions: **Stop building new features.**
   - Dedicate a session strictly to code health and cleanup:
     *"Review all files in `app/src/main/java/com/leoai/app/data/`. Check for duplicated queries, unused imports, orphaned models, or inconsistent naming. Refactor them cleanly without changing external behavior, and verify with `gradle testDebugUnitTest`."*

3. **The "Rubber Duck" Technique**:
   - Before accepting newly generated code, prompt for architectural verification:
     *"Explain how this new function interacts with our existing Room database and ViewModel state. Are there any race conditions, coroutine leaks, or edge cases where it could fail or degrade UI responsiveness?"*

---

## Submitting Contributions

1. **Create a branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. **Make your changes**:
   - Follow standard Kotlin style guidelines (`kotlin.code.style=official`).
   - Keep Composables modular and add semantic `testTag` identifiers to interactive components.
   - Run local verification checks before opening a PR:
     ```bash
     gradle testDebugUnitTest
     gradle assembleDebug
     ```
3. **Commit your changes**:
   Write clear, concise commit messages following conventional commits:
   ```bash
   git commit -m "feat: add conversational search to history screen"
   ```
4. **Push and open a Pull Request**:
   Push your branch and open a PR against `main`. All CI guardrail jobs must pass before merging.

