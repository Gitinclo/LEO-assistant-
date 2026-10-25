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
- **Persistence**: SQLite on-device database (`LeoDatabaseHelper.kt`) for private, offline-first session management.
- **Privacy & Security**: Zero client-side API keys; fallback companion engine runs locally on device.

---

## Submitting Contributions

1. **Create a branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. **Make your changes**:
   - Follow standard Kotlin style guidelines (`kotlin.code.style=official`).
   - Keep Composables modular and add semantic `testTag` identifiers to interactive components.
   - Ensure the app builds with zero errors:
     ```bash
     gradle assembleDebug
     ```
3. **Commit your changes**:
   Write clear, concise commit messages following conventional commits:
   ```bash
   git commit -m "feat: add conversational search to history screen"
   ```
4. **Push and open a Pull Request**:
   Push your branch and open a PR against `main`. Describe what your change does, why it is needed, and screenshots of the UI if applicable.
