# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.1.x   | :white_check_mark: |
| 1.0.x   | :white_check_mark: |

---

## Local Encryption & Database Security Architecture

- **AES-256-GCM Authenticated Encryption at Rest (`ChatMessageCrypto.kt`)**:
  - Every chat message (`ChatMessageEntity`) and session preview (`ChatSessionEntity`) persisted in the local Room database (`leo_ai_room.db`) is encrypted using `AES/GCM/NoPadding` with a 256-bit key managed by the hardware-backed `AndroidKeyStore`.
  - **Unique 96-Bit Nonce/IV**: Generates a fresh 12-byte cryptographically random IV via `SecureRandom` for every write, never reusing nonces.
  - **Context Binding with AAD**: Binds the conversation `sessionId` as Associated Authenticated Data (AAD) alongside the 128-bit authentication tag so ciphertexts cannot be moved across sessions or modified undetected.
- **Input Sanitization & Parameterized SQL (`InputBoundsValidator.kt` & `ChatMessageDao.kt`)**:
  - Enforces strict character bounds (`<= 4,000` chars for messages, `<= 80` chars for session titles, `<= 140` chars for previews) and role validation (`user` | `assistant`) prior to persistence.
  - Uses compile-time KSP-verified Room `@Query` bindings exclusively.

---

## Reporting a Vulnerability

We take the security and privacy of our users very seriously.

If you believe you have discovered a security vulnerability in LEO AI, please **do not open a public issue** on GitHub. Instead, report it privately so we can address it before disclosure.

### Contact

Please send an email to:
**creatergame31@gmail.com**

Please include in your report:
- A description of the vulnerability and its potential impact.
- Step-by-step reproduction instructions or proof-of-concept code.
- Device, Android OS version, and app version tested.

### What to Expect

- **Acknowledgment**: You will receive an acknowledgment of your report within 48 hours.
- **Assessment**: We will investigate and validate the issue, providing updates on remediation progress.
- **Resolution**: Once resolved, we will publish a patch release and credit you (if desired).
