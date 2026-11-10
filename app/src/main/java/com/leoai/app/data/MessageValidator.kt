package com.leoai.app.data

import androidx.compose.runtime.Immutable

/**
 * Dedicated message validator and sanitization utility that enforces character limit guardrails,
 * strips control characters, normalizes line endings, and validates message integrity before
 * processing with the companion engine or persisting in the local Room database.
 */
object MessageValidator {

    const val MAX_MESSAGE_LENGTH = 4_000
    const val MIN_MESSAGE_LENGTH = 1
    const val NEAR_LIMIT_THRESHOLD = 3_600
    const val MAX_SESSION_TITLE_LENGTH = 80
    const val MAX_SESSION_PREVIEW_LENGTH = 140

    private val CONTROL_CHARS_REGEX = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")
    private val EXCESSIVE_NEWLINES_REGEX = Regex("\\n{3,}")

    enum class ValidationError(val defaultMessage: String) {
        EMPTY_OR_BLANK("Message cannot be empty or contain only whitespace."),
        EXCEEDS_MAX_LENGTH("Message exceeds the maximum character limit of $MAX_MESSAGE_LENGTH."),
        INVALID_ROLE("Message role must be either 'user' or 'assistant'.")
    }

    @Immutable
    data class MessageValidationResult(
        val isValid: Boolean,
        val sanitizedContent: String,
        val originalLength: Int,
        val sanitizedLength: Int,
        val wasTruncated: Boolean,
        val errorReason: ValidationError? = null,
        val errorMessage: String? = null
    )

    /**
     * Sanitizes raw user or assistant message text by:
     * 1. Removing null bytes and non-printable ASCII control characters (preserving `\n` and `\t`).
     * 2. Normalizing `\r\n` and `\r` line breaks to `\n`.
     * 3. Collapsing 3+ consecutive newlines down to a clean paragraph break (`\n\n`).
     * 4. Trimming leading/trailing whitespace and enforcing [maxLength].
     */
    fun sanitize(
        rawInput: String,
        maxLength: Int = MAX_MESSAGE_LENGTH
    ): String {
        val safeMax = maxLength.coerceAtLeast(1)
        val withoutControlChars = rawInput
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(CONTROL_CHARS_REGEX, "")
            .replace(EXCESSIVE_NEWLINES_REGEX, "\n\n")
            .trim()

        return if (withoutControlChars.length > safeMax) {
            withoutControlChars.take(safeMax).trimEnd()
        } else {
            withoutControlChars
        }
    }

    /**
     * Clamps live text-field input as the user types so the compose input field never exceeds
     * [maxLength] characters and strips control characters without aggressively trimming trailing
     * spaces mid-word.
     */
    fun clampLiveInput(
        liveInput: String,
        maxLength: Int = MAX_MESSAGE_LENGTH
    ): String {
        val safeMax = maxLength.coerceAtLeast(1)
        val cleaned = liveInput
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(CONTROL_CHARS_REGEX, "")
        return if (cleaned.length > safeMax) cleaned.take(safeMax) else cleaned
    }

    /**
     * Validates and sanitizes [rawInput] against character limit guardrails.
     *
     * @param rawInput The raw input string provided by the user or caller.
     * @param maxLength The maximum allowed character count (defaults to [MAX_MESSAGE_LENGTH]).
     * @param truncateIfExceeded If `true`, truncates over-length content to [maxLength] and marks
     *        `isValid = true` with `wasTruncated = true`. If `false`, rejects inputs whose
     *        cleaned length exceeds [maxLength] with [ValidationError.EXCEEDS_MAX_LENGTH].
     */
    fun validate(
        rawInput: String,
        maxLength: Int = MAX_MESSAGE_LENGTH,
        truncateIfExceeded: Boolean = true
    ): MessageValidationResult {
        val safeMax = maxLength.coerceAtLeast(1)
        val normalized = rawInput
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(CONTROL_CHARS_REGEX, "")
            .replace(EXCESSIVE_NEWLINES_REGEX, "\n\n")
            .trim()

        if (normalized.isEmpty()) {
            return MessageValidationResult(
                isValid = false,
                sanitizedContent = "",
                originalLength = rawInput.length,
                sanitizedLength = 0,
                wasTruncated = false,
                errorReason = ValidationError.EMPTY_OR_BLANK,
                errorMessage = ValidationError.EMPTY_OR_BLANK.defaultMessage
            )
        }

        val exceedsLimit = normalized.length > safeMax
        if (exceedsLimit && !truncateIfExceeded) {
            return MessageValidationResult(
                isValid = false,
                sanitizedContent = normalized.take(safeMax).trimEnd(),
                originalLength = rawInput.length,
                sanitizedLength = normalized.length,
                wasTruncated = false,
                errorReason = ValidationError.EXCEEDS_MAX_LENGTH,
                errorMessage = ValidationError.EXCEEDS_MAX_LENGTH.defaultMessage
            )
        }

        val finalContent = if (exceedsLimit) {
            normalized.take(safeMax).trimEnd()
        } else {
            normalized
        }

        return MessageValidationResult(
            isValid = finalContent.length >= MIN_MESSAGE_LENGTH,
            sanitizedContent = finalContent,
            originalLength = rawInput.length,
            sanitizedLength = finalContent.length,
            wasTruncated = exceedsLimit,
            errorReason = null,
            errorMessage = null
        )
    }

    /**
     * Returns `true` if [rawInput] produces a non-empty valid message within [maxLength].
     */
    fun isValidMessage(
        rawInput: String,
        maxLength: Int = MAX_MESSAGE_LENGTH,
        allowTruncation: Boolean = true
    ): Boolean = validate(
        rawInput = rawInput,
        maxLength = maxLength,
        truncateIfExceeded = allowTruncation
    ).isValid

    /**
     * Returns the number of remaining characters before reaching [maxLength].
     */
    fun remainingCharacters(
        currentInput: String,
        maxLength: Int = MAX_MESSAGE_LENGTH
    ): Int = (maxLength - currentInput.length).coerceAtLeast(0)

    /**
     * Returns `true` when the input length is at or above [NEAR_LIMIT_THRESHOLD] (90% of max).
     */
    fun isNearCharacterLimit(
        currentInput: String,
        threshold: Int = NEAR_LIMIT_THRESHOLD
    ): Boolean = currentInput.length >= threshold

    /**
     * Sanitizes a domain [ChatMessage] before processing or Room database storage.
     */
    fun sanitizeChatMessage(message: ChatMessage): ChatMessage = message.copy(
        id = InputBoundsValidator.sanitizeId(message.id),
        sessionId = InputBoundsValidator.sanitizeId(message.sessionId),
        role = InputBoundsValidator.sanitizeRole(message.role),
        content = sanitize(message.content, MAX_MESSAGE_LENGTH)
    )
}
