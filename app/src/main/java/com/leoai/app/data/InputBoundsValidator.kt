package com.leoai.app.data

/**
 * Stateless input sanitization and schema validation utility enforcing strict
 * character length bounds and domain constraints before Room persistence.
 */
object InputBoundsValidator {

    const val MAX_MESSAGE_CONTENT_LEN = 4_000
    const val MAX_SESSION_TITLE_LEN = 80
    const val MAX_SESSION_PREVIEW_LEN = 140
    const val MAX_ID_LEN = 128

    const val ROLE_USER = "user"
    const val ROLE_ASSISTANT = "assistant"
    val ALLOWED_ROLES = setOf(ROLE_USER, ROLE_ASSISTANT)

    const val DEFAULT_SESSION_TITLE = "New conversation"

    /**
     * Sanitizes, strips control characters, normalizes line endings, and clamps user or assistant
     * message content to [MAX_MESSAGE_CONTENT_LEN] characters via [MessageValidator].
     */
    fun sanitizeMessageContent(input: String): String =
        MessageValidator.sanitize(input, MAX_MESSAGE_CONTENT_LEN)

    /**
     * Trims, collapses internal newlines, and clamps session titles to [MAX_SESSION_TITLE_LEN] characters.
     */
    fun sanitizeSessionTitle(input: String): String {
        val cleaned = input
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_SESSION_TITLE_LEN)
        return cleaned.ifEmpty { DEFAULT_SESSION_TITLE }
    }

    /**
     * Derives a concise session title from the initial user prompt (`<= 48` chars with ellipsis if truncated,
     * and bounded by [MAX_SESSION_TITLE_LEN]).
     */
    fun deriveTitleFromPrompt(prompt: String): String {
        val normalized = prompt
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return DEFAULT_SESSION_TITLE
        return if (normalized.length > 48) {
            normalized.take(48).trimEnd() + "…"
        } else {
            normalized.take(MAX_SESSION_TITLE_LEN)
        }
    }

    /**
     * Trims, collapses whitespace, and clamps session preview snippets to [MAX_SESSION_PREVIEW_LEN] characters.
     */
    fun sanitizeSessionPreview(input: String): String =
        input
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_SESSION_PREVIEW_LEN)

    /**
     * Validates that the message role is strictly `"user"` or `"assistant"`, defaulting to `"user"` if invalid.
     */
    fun sanitizeRole(role: String): String {
        val normalized = role.trim().lowercase()
        return if (normalized in ALLOWED_ROLES) normalized else ROLE_USER
    }

    /**
     * Sanitizes an identifier string to ensure it is non-blank and bounded by [MAX_ID_LEN].
     */
    fun sanitizeId(id: String): String =
        id.trim().take(MAX_ID_LEN)
}
