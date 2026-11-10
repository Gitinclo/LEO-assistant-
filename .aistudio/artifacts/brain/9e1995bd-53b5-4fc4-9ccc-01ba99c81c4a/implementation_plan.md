# LEO AI — Encrypted Room Conversation Persistence, Input Guardrails & In-App Compliance Hub

Deliver a hardened local Room persistence architecture for chat messages (`ChatMessageEntity` and `ChatMessageDao`) featuring transparent **AES-256-GCM authenticated encryption at rest** with Android Keystore key management, strict **Input Bounds Validation & Sanitization Guardrails**, and an interactive **In-App Compliance & Legal Docs Viewer Dialog** styled in LEO AI's warm organic aesthetic.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and product choices were confirmed during Phase 1 and govern this implementation plan:

- **Confirmed Decision 1 — Entity & DAO Package Structure**: Retain `ChatMessageEntity` inside `com.leoai.app.data.local.entity` and `ChatMessageDao` inside `com.leoai.app.data`, connected through the singleton `LeoDatabase` (`leo_ai_room.db`) and `LeoRepository`.
- **Confirmed Decision 2 — AES-256-GCM At-Rest Message Encryption**: Encrypt stored chat message payloads (`content`) and session previews before writing to SQLite using **AES-256-GCM** (96-bit / 12-byte random nonce per message, 128-bit authentication tag, and Associated Authenticated Data bound to the conversation `sessionId`), backed by the **Android Keystore (`AES/GCM/NoPadding`)** on device with a deterministic local key provider fallback for JVM unit tests.
- **Confirmed Decision 3 — Input Bounds Guardrails & In-App Legal/Compliance Viewer**: Add an `InputBoundsValidator` enforcing strict length, role, and whitespace sanitization before persistence, and add an interactive **Compliance, Security & Legal Docs Hub Dialog** accessible from the Settings screen.

---

## 1. Overview & Core Concept

- **What It Does**:
  - **Room Entity & DAO for Local Conversation History**: Persists multi-turn user and assistant messages locally on the device with composite indexing on `(session_id, created_at)`, reactive Kotlin `Flow<List<ChatMessageEntity>>` streams, single-shot snapshots, session-scoped deletion, and full history clearing.
  - **Transparent AES-256-GCM Authenticated Encryption**: Protects sensitive personal reflections stored in the local database by encrypting every message with a unique 12-byte IV/nonce and binding the ciphertext to its `session_id` via Associated Authenticated Data (AAD) so records cannot be tampered with or moved across sessions.
  - **Input Bounds Validation & Coroutine Guardrails**: Sanitizes user prompts, session titles, previews, and message roles against strict character caps and valid enum domains while ensuring coroutine `CancellationException` is never swallowed.
  - **In-App Compliance & Governance Viewer**: Lets users inspect LEO AI's core governance and legal documents (`PRIVACY`, `TERMS`, `SECURITY`, `LICENSE`, `CONTRIBUTING`, `CODE OF CONDUCT`, `CHANGELOG`, and `README`) directly inside the Settings screen without leaving the app.
- **Target Audience / Persona**: Thoughtful users who rely on LEO AI as a private, offline-capable space for drafting replies, journaling, and reflection, and who expect cryptographic privacy alongside transparent legal and security policies.
- **Key Value**: Combines reactive offline-first Room persistence with zero-trust authenticated encryption and visible compliance transparency.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Encrypted Conversation Persistence Flow**: When a user starts a prompt from the Welcome screen or sends a message in the Chat screen, the input is validated and sanitized by the bounds guardrail, encrypted via AES-256-GCM with the session ID as AAD, and persisted via `ChatMessageDao`. Reactive `Flow` queries automatically decrypt and emit plaintext `ChatMessage` domain objects to the UI.
  2. **Chronological History & Deletion Flow**: Opening the Conversations History screen streams sessions grouped into **TODAY**, **YESTERDAY**, and **EARLIER**. Deleting a single session or tapping **Clear all conversations** in Settings immediately purges the corresponding encrypted records via `ChatMessageDao`.
  3. **In-App Compliance & Legal Docs Flow**: Inside the Settings screen, a dedicated **LEGAL & COMPLIANCE** section presents an interactive **Privacy, Terms & Security Docs** card and encryption status badge (`AES-256-GCM Active`). Tapping the card opens the **Compliance & Governance Hub Dialog**, where users can switch between document tabs via warm organic filter chips and read structured summaries and clauses.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Warm, tactile, unhurried organic minimalism matching LEO AI's signature look.
  - *Color Palette & Mood*: Warm Ivory background (`#FDFBF7`), Tactile Stone surface cards (`#F4F0EA`), Terracotta Pottery primary accent (`#C86A51`), Botanical Sage secondary (`#7E947A`), Deep Charcoal ink (`#2D332C`), and Soft Pebble borders (`#E6E0D6`).
  - *Typography & Hierarchy*: Editorial Serif display headers (`FontFamily.Serif`) paired with clean, generous body copy (`15sp` with `22sp` line height) and uppercase tracked section eyebrows (`11sp`, `2sp` letter spacing).
  - *Component Styling & Layout*: Rounded stone surfaces (`16dp`–`24dp` radius), `1dp` subtle borders, and minimum `48dp` accessible touch targets with Material ripple feedback.
- **Interactive Feedback & Motion**:
  - Optimistic message bubbles appear immediately upon send while the 3-dot typing indicator animates (`thinking…`).
  - Smooth tab transitions inside the Compliance Dialog and transient confirmation feedback (`Cleared.`) when wiping local history.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Transparent Repository-Level AES-256-GCM Encryption with AAD Context Binding**
  - *Chosen Approach*: Keep `ChatMessageEntity` as a clean Room `@Entity` storing the Base64-encoded AES-256-GCM payload (`IV [12B] + Ciphertext + AuthTag [16B]`) in the `content` column, while `ChatMessageCrypto` performs encryption/decryption during `fromDomain()` / `toDomain()` mapping using `sessionId` as the Associated Authenticated Data (AAD).
  - *Why*: Preserves full Room KSP compile-time SQL verification and index performance on `session_id` and `created_at` without requiring third-party native SQLCipher binaries, while guaranteeing authenticated confidentiality and preventing cross-session ciphertext substitution.
  - *Alternatives Considered*: Plaintext SQLite storage (lacks cryptographic protection if a backup or rooted device image is inspected) or full-file SQLCipher native library (adds heavy NDK binary bloat and complicates local JVM unit testing).
- **Decision 2: Strict Input Bounds & Coroutine Safety Guardrails**
  - *Chosen Approach*: Introduce a pure, stateless `InputBoundsValidator` that trims and clamps user messages (`<= 4,000` chars), session titles (`<= 80` chars), session previews (`<= 140` chars), and validates message roles (`user` or `assistant`) before any DAO write, alongside explicit `CancellationException` rethrowing in coroutine blocks.
  - *Why*: Prevents UI layout overflow, database bloat, and corrupted coroutine state during rapid screen navigation.
- **Decision 3: Backward-Compatible Decryption for Existing Local Records**
  - *Chosen Approach*: Prefix AES-256-GCM payloads with a versioned header (`v1:`) so `ChatMessageCrypto` can authenticate and decrypt encrypted rows while gracefully reading any pre-existing plaintext rows created prior to the upgrade.
  - *Why*: Ensures zero data loss or crashes on devices upgrading from an earlier build.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                  Jetpack Compose UI Layer (Material 3)                     │
│  • WelcomeScreen / ChatScreen / HistoryScreen                              │
│  • SettingsScreen + ComplianceDocsDialog (8 Governance & Legal Tabs)       │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ StateFlow<List<ChatMessage>>
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                      LeoViewModel (MVVM State Holder)                      │
│  • Manages screen backstack, optimistic UI updates & dialog visibility     │
│  • Propagates Coroutine CancellationException cleanly                      │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                LeoRepository & Guardrail / Crypto Pipeline                 │
├─────────────────────────────────────┬──────────────────────────────────────┤
│ InputBoundsValidator                │ ChatMessageCrypto (AES-256-GCM)      │
│ • Prompt cap (<= 4000 chars)        │ • AndroidKeyStore 256-bit SecretKey  │
│ • Title cap (<= 80 chars)           │ • 12-byte SecureRandom IV per write  │
│ • Preview cap (<= 140 chars)        │ • 128-bit GCM Auth Tag + AAD binding │
│ • Role domain check (user/assistant)│ • Versioned "v1:" Base64URL payload  │
└─────────────────────────────────────┴───────────────────┬──────────────────┘
                                                          │
                                                          ▼
┌────────────────────────────────────────────────────────────────────────────┐
│               Room Persistence Layer (leo_ai_room.db / KSP)                │
├─────────────────────────────────────┬──────────────────────────────────────┤
│ ChatMessageDao (com.leoai.app.data) │ ChatMessageEntity & ChatSessionEntity│
│ • insertMessage(entity)             │ • Table: "chat_messages"             │
│ • getAllChatHistory(): Flow         │ • Index: (session_id, created_at)    │
│ • getChatHistoryForSession(): Flow  │ • Foreign-key logical binding        │
│ • deleteMessageHistory() / BySession│ • Encrypted content at rest          │
└─────────────────────────────────────┴──────────────────────────────────────┘
```

### Data Model & State
- **`ChatMessageEntity` (`com.leoai.app.data.local.entity`)**:
  - `@Entity(tableName = "chat_messages", indices = [Index(value = ["session_id", "created_at"])])`
  - `id: String` (`@PrimaryKey`), `sessionId: String` (`@ColumnInfo(name = "session_id")`), `role: String` (`"user"` | `"assistant"`), `content: String` (AES-256-GCM encrypted payload at rest), `createdAt: String` (`@ColumnInfo(name = "created_at")`, UTC ISO-8601 timestamp).
- **`ChatMessageDao` (`com.leoai.app.data`)**:
  - `suspend fun insertMessage(message: ChatMessageEntity)` and `suspend fun insert(message: ChatMessageEntity)`
  - `suspend fun deleteMessageHistory()` and `suspend fun deleteMessageHistoryBySession(sessionId: String)`
  - `fun getAllChatHistory(): Flow<List<ChatMessageEntity>>` and `fun getChatHistoryOrderedByTimestamp(): Flow<List<ChatMessageEntity>>`
  - `fun getChatHistoryForSession(sessionId: String): Flow<List<ChatMessageEntity>>` and snapshot `suspend` queries.
- **`ChatMessageCrypto`**:
  - Generates or retrieves a 256-bit AES key from `AndroidKeyStore` (or an in-memory 256-bit key in unit test environments).
  - `encrypt(plaintext: String, associatedData: String): String`: Generates a fresh 12-byte nonce via `SecureRandom`, binds `associatedData` (`sessionId`) via `cipher.updateAAD(...)`, and returns `"v1:" + Base64URL(nonce + ciphertextWithTag)`.
  - `decrypt(payload: String, associatedData: String): String`: Verifies the `"v1:"` prefix, extracts the 12-byte nonce, binds `associatedData` via `cipher.updateAAD(...)`, verifies the 128-bit GCM authentication tag, and returns UTF-8 plaintext.

### Interactive Component & State Mapping
- **Message Submission & Validation**: Submitting a prompt in `WelcomeScreen` or `ChatScreen` invokes `InputBoundsValidator.sanitizeMessageContent(rawText)`. Blank or whitespace-only inputs are rejected before firing coroutines; valid inputs are encrypted with AES-256-GCM and stored via `ChatMessageDao.insertMessage()`.
- **Session History & Cascading Purge**: Deleting a session in `HistoryScreen` calls `ChatMessageDao.deleteMessageHistoryBySession(sessionId)` followed by `ChatDao.deleteSessionById(sessionId)`. Tapping `clear-conversations-button` in `SettingsScreen` wipes all messages and sessions atomically.
- **In-App Compliance & Legal Docs Hub (`ComplianceDocsDialog`)**: Tapping `open-compliance-docs-button` in `SettingsScreen` opens a modal dialog with selectable `FilterChip` tabs (`PRIVACY`, `TERMS`, `SECURITY`, `GUARDRAILS`, `LICENSE`, `CONTRIBUTING`, `CODE OF CONDUCT`, `README`), rendering the corresponding root document's clauses and AES-256-GCM / GDPR / CCPA policies.
