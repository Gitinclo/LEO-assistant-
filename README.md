# LEO AI

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#building--running)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Vibe Coded with AI](https://img.shields.io/badge/built%20with-🤖%20AI%20Studio-blue)](#vibe-coding--ai-disclosure)

> A calm, thoughtful AI companion built with Kotlin and Jetpack Compose for Android.

LEO AI is designed around an earthy, organic aesthetic (pottery warmth `#C86A51`, botanical sage `#7E947A`, warm ivory `#FDFBF7`, tactile stone surfaces, and serif typography). It provides a quiet, unhurried space for thinking out loud, drafting sensitive replies, planning routines, brainstorming, and exploring ideas.

---

## Key Features

- **Welcome Screen**:
  - Time-of-day dynamic greeting (*"Good morning."*, *"Good afternoon."*, *"Late night thoughts?"*).
  - 5 curated starter prompt chips to immediately spark reflection or writing.
  - Subtle botanical artwork overlay and warm ambient background.
  - Direct message input bar routing straight to a new conversation.

- **Conversation Screen**:
  - Full multi-turn dialog with optimistic user message rendering.
  - Animated 3-dot typing indicator (*"thinking…"*, *"here when you need"*).
  - Automatic session titling derived from the initial user prompt.
  - Persistent message history per session.
  - Built-in companion response engine: works out of the box with zero external configuration or required API keys.

- **Conversations History**:
  - Conversations grouped chronologically by **TODAY**, **YESTERDAY**, and **EARLIER**.
  - One-tap conversation resume and session deletion.
  - Empty state with quick "Start a conversation" CTA.

- **Settings**:
  - Brand overview card with LEO gradient avatar.
  - About section highlighting the calm companion philosophy.
  - One-click "Clear all conversations" with transient confirmation feedback.

---

## Security & Key Management

- **Zero Exposed Keys**: No API keys or credentials are required or exposed in `.env.example` or code.
- **Local Persistence**: Conversations and messages are persisted locally using **Room Database** (`leo_ai_room.db`) with reactive `Flow` observation for offline durability.

---

## Building & Running

Built with Gradle 9.3.1, Android SDK 36, and Jetpack Compose (Material 3).

```bash
# Run automated unit tests
gradle testDebugUnitTest

# Assemble debug APK
gradle assembleDebug
```

Output APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Vibe Coding & AI Disclosure

This project was built adopting the modern **"Vibe Coding"** methodology:
- **Director-Led Architecture**: The product vision, calm tactile UI design system, UX flows, and feature specifications were guided by human prompting and creative direction.
- **AI-Assisted Implementation**: The Kotlin/Compose codebase, Room persistence layer, and tests were synthesized using Google AI Studio.
- **Safeguarding Against Spaghetti Code**:
  - Modular clean MVVM architecture with strict layer isolation (`data`, `domain`, `ui`, `theme`).
  - Automated JVM unit tests (`LeoAppUnitTest.kt`) verifying critical business logic (date groupings, time-of-day greetings, entity-domain mapping, and response engine).
  - CI pipeline (`.github/workflows/build.yml`) that runs `gradle testDebugUnitTest` before building APKs to catch regressions instantly.
- **Store & Distribution Compliance**:
  - Meets AI disclosure requirements for platforms requiring transparency on AI-assisted code or asset creation (e.g., Google Play and Steam Developer disclosures).

---

## Legal & Policies

- [MIT License](LICENSE) — Open source software license.
- [Privacy Policy](PRIVACY.md) — Covers data storage, retention, the Right to be Forgotten, encryption, and third-party policies.
- [Terms & Conditions](TERMS.md) — Covers account security, termination rights, refund & subscription policies, AI disclaimers, and limitation of liability.
- [Security Policy](SECURITY.md) — Vulnerability reporting and disclosure protocols.
- [Code of Conduct](CODE_OF_CONDUCT.md) — Contributor community guidelines.
