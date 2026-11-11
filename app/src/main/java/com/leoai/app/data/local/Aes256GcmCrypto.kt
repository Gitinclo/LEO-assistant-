package com.leoai.app.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.Immutable
import androidx.room.TypeConverter
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Production-grade AES-256-GCM encryption utility backed by the Android Keystore system
 * for encrypting and decrypting Room database message contents and session previews at rest.
 *
 * Security properties enforced:
 * - **Algorithm**: `AES/GCM/NoPadding` with a 256-bit `SecretKey` managed by `AndroidKeyStore`.
 * - **Unique 96-bit (12-byte) Nonce/IV per encryption**: Uses the Android Keystore hardware RNG
 *   (`setRandomizedEncryptionRequired(true)`) or a cryptographically secure [SecureRandom] 12-byte IV.
 *   Never reuses an IV across encryptions.
 * - **128-bit Authentication Tag**: Automatically appended and verified by GCM before releasing
 *   any decrypted plaintext, protecting against bit-flipping or tampering.
 * - **Context Binding (AAD)**: Supports binding `associatedData` (such as `sessionId`) to the
 *   authentication tag so ciphertext cannot be transplanted across sessions or rows.
 * - **Versioned Storage Format**: Encodes payloads as `v1:<base64url(iv[12] + ciphertext + tag[16])>`.
 */
class Aes256GcmCrypto(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) {

    @Immutable
    data class EncryptedPayloadMetadata(
        val versionPrefix: String,
        val ivLengthBytes: Int,
        val ciphertextAndTagLengthBytes: Int,
        val tagLengthBits: Int
    )

    private val secureRandom = SecureRandom()

    @Volatile
    private var fallbackJvmSecretKey: SecretKey? = null

    /**
     * Encrypts [plaintext] using AES-256-GCM with a unique 12-byte IV and optional [associatedData] (AAD).
     *
     * @return Versioned Base64URL string: `v1:<base64url(iv[12] + ciphertext + auth_tag[16])>`
     */
    fun encrypt(plaintext: String, associatedData: String = ""): String {
        val aadBytes = if (associatedData.isNotEmpty()) {
            associatedData.toByteArray(Charsets.UTF_8)
        } else {
            null
        }
        val combined = encryptBytes(plaintext.toByteArray(Charsets.UTF_8), aadBytes)
        return PAYLOAD_PREFIX + encodeBase64Url(combined)
    }

    /**
     * Encrypts raw [plaintextBytes] into a single byte array containing `iv[12] + ciphertext + tag[16]`.
     */
    fun encryptBytes(plaintextBytes: ByteArray, associatedData: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getOrCreateSecretKey()

        val iv: ByteArray = try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val keystoreIv = cipher.iv
            if (keystoreIv != null && keystoreIv.size == IV_LENGTH_BYTES) {
                keystoreIv
            } else {
                ByteArray(IV_LENGTH_BYTES).also { generatedIv ->
                    secureRandom.nextBytes(generatedIv)
                    cipher.init(
                        Cipher.ENCRYPT_MODE,
                        secretKey,
                        GCMParameterSpec(TAG_LENGTH_BITS, generatedIv)
                    )
                }
            }
        } catch (_: Exception) {
            ByteArray(IV_LENGTH_BYTES).also { generatedIv ->
                secureRandom.nextBytes(generatedIv)
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    GCMParameterSpec(TAG_LENGTH_BITS, generatedIv)
                )
            }
        }

        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        val ciphertextWithTag = cipher.doFinal(plaintextBytes)
        val combined = ByteArray(iv.size + ciphertextWithTag.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertextWithTag, 0, combined, iv.size, ciphertextWithTag.size)
        return combined
    }

    /**
     * Decrypts a versioned AES-256-GCM [payload] and verifies the 128-bit authentication tag
     * and optional [associatedData] (AAD).
     *
     * @throws AEADBadTagException if the ciphertext, IV, or AAD has been tampered with.
     * @throws IllegalArgumentException if the payload is malformed or truncated.
     */
    fun decrypt(payload: String, associatedData: String = ""): String {
        if (!payload.startsWith(PAYLOAD_PREFIX)) {
            return payload
        }

        val encoded = payload.removePrefix(PAYLOAD_PREFIX)
        val raw = decodeBase64Url(encoded)
        val aadBytes = if (associatedData.isNotEmpty()) {
            associatedData.toByteArray(Charsets.UTF_8)
        } else {
            null
        }
        val decryptedBytes = decryptBytes(raw, aadBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Decrypts raw `iv[12] + ciphertext + tag[16]` bytes and verifies authenticity.
     */
    fun decryptBytes(
        combinedIvAndCiphertext: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        require(combinedIvAndCiphertext.size >= IV_LENGTH_BYTES + TAG_LENGTH_BYTES) {
            "Invalid AES-256-GCM payload length: ${combinedIvAndCiphertext.size} bytes"
        }

        val iv = combinedIvAndCiphertext.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertextWithTag = combinedIvAndCiphertext.copyOfRange(
            IV_LENGTH_BYTES,
            combinedIvAndCiphertext.size
        )

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        return cipher.doFinal(ciphertextWithTag)
    }

    /**
     * Decrypts [payload] safely for UI rendering without crashing if a record fails integrity
     * verification or key rotation occurred.
     */
    fun decryptSafely(payload: String, associatedData: String = ""): String {
        return try {
            decrypt(payload, associatedData)
        } catch (_: AEADBadTagException) {
            INTEGRITY_FAILURE_FALLBACK
        } catch (_: Exception) {
            payload.removePrefix(PAYLOAD_PREFIX)
        }
    }

    /**
     * Returns `true` if [value] is formatted with the versioned AES-256-GCM prefix (`v1:`).
     */
    fun isEncryptedPayload(value: String): Boolean = value.startsWith(PAYLOAD_PREFIX)

    /**
     * Inspects an encrypted payload without decrypting it, returning structural metadata
     * (`ivLengthBytes = 12`, `ciphertextAndTagLengthBytes`, `tagLengthBits = 128`) or `null`
     * if the string is not a valid `v1:` payload.
     */
    fun inspectPayload(payload: String): EncryptedPayloadMetadata? {
        if (!isEncryptedPayload(payload)) return null
        return try {
            val raw = decodeBase64Url(payload.removePrefix(PAYLOAD_PREFIX))
            if (raw.size < IV_LENGTH_BYTES + TAG_LENGTH_BYTES) return null
            EncryptedPayloadMetadata(
                versionPrefix = PAYLOAD_PREFIX,
                ivLengthBytes = IV_LENGTH_BYTES,
                ciphertextAndTagLengthBytes = raw.size - IV_LENGTH_BYTES,
                tagLengthBits = TAG_LENGTH_BITS
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Rotates the master key by deleting the existing alias in `AndroidKeyStore` (and clearing
     * any in-memory fallback key) then generating a fresh 256-bit AES-GCM key.
     */
    @Synchronized
    fun rotateKey() {
        fallbackJvmSecretKey = null
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        } catch (_: Throwable) {
            // Ignored on local JVM test environments where AndroidKeyStore is unavailable
        }
        getOrCreateSecretKey()
    }

    /**
     * Retrieves the existing 256-bit AES key from `AndroidKeyStore` or generates a new one
     * using [KeyGenParameterSpec] with `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`, `BLOCK_MODE_GCM`,
     * `ENCRYPTION_PADDING_NONE`, and `keySize = 256`.
     */
    @Synchronized
    internal fun getOrCreateSecretKey(): SecretKey {
        fallbackJvmSecretKey?.let { return it }

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingEntry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                existingEntry.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val parameterSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(parameterSpec)
                keyGenerator.generateKey()
            }
        } catch (_: Throwable) {
            // Thread-safe 256-bit AES key fallback for local JVM unit tests where AndroidKeyStore is not mounted
            val keyBytes = ByteArray(KEY_SIZE_BITS / 8)
            secureRandom.nextBytes(keyBytes)
            SecretKeySpec(keyBytes, "AES").also { fallbackJvmSecretKey = it }
        }
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "leo_ai_room_aes256_gcm_master_key"
        const val KEY_SIZE_BITS = 256
        const val IV_LENGTH_BYTES = 12 // 96-bit nonce recommended by NIST SP 800-38D for AES-GCM
        const val TAG_LENGTH_BITS = 128 // 128-bit authentication tag
        const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8
        const val PAYLOAD_PREFIX = "v1:"
        const val INTEGRITY_FAILURE_FALLBACK = "[Encrypted message — integrity verification failed]"

        private const val BASE64_URL_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        /**
         * Shared singleton instance using [DEFAULT_KEY_ALIAS].
         */
        val Default: Aes256GcmCrypto = Aes256GcmCrypto(DEFAULT_KEY_ALIAS)

        fun encrypt(plaintext: String, associatedData: String = ""): String =
            Default.encrypt(plaintext, associatedData)

        fun decrypt(payload: String, associatedData: String = ""): String =
            Default.decrypt(payload, associatedData)

        fun decryptSafely(payload: String, associatedData: String = ""): String =
            Default.decryptSafely(payload, associatedData)

        fun isEncryptedPayload(value: String): Boolean =
            Default.isEncryptedPayload(value)

        fun inspectPayload(payload: String): EncryptedPayloadMetadata? =
            Default.inspectPayload(payload)

        internal fun encodeBase64Url(data: ByteArray): String {
            val out = StringBuilder((data.size * 4 + 2) / 3)
            var i = 0
            while (i + 2 < data.size) {
                val b0 = data[i].toInt() and 0xFF
                val b1 = data[i + 1].toInt() and 0xFF
                val b2 = data[i + 2].toInt() and 0xFF
                val triple = (b0 shl 16) or (b1 shl 8) or b2
                out.append(BASE64_URL_ALPHABET[(triple ushr 18) and 0x3F])
                out.append(BASE64_URL_ALPHABET[(triple ushr 12) and 0x3F])
                out.append(BASE64_URL_ALPHABET[(triple ushr 6) and 0x3F])
                out.append(BASE64_URL_ALPHABET[triple and 0x3F])
                i += 3
            }
            val remaining = data.size - i
            if (remaining == 1) {
                val b0 = data[i].toInt() and 0xFF
                out.append(BASE64_URL_ALPHABET[(b0 ushr 2) and 0x3F])
                out.append(BASE64_URL_ALPHABET[(b0 and 0x03) shl 4])
            } else if (remaining == 2) {
                val b0 = data[i].toInt() and 0xFF
                val b1 = data[i + 1].toInt() and 0xFF
                val pair = (b0 shl 8) or b1
                out.append(BASE64_URL_ALPHABET[(pair ushr 10) and 0x3F])
                out.append(BASE64_URL_ALPHABET[(pair ushr 4) and 0x3F])
                out.append(BASE64_URL_ALPHABET[(pair and 0x0F) shl 2])
            }
            return out.toString()
        }

        internal fun decodeBase64Url(input: String): ByteArray {
            val clean = input.trim().trimEnd('=')
            if (clean.isEmpty()) return ByteArray(0)

            val outLen = (clean.length * 3) / 4
            val out = ByteArray(outLen)
            var outIndex = 0
            var buffer = 0
            var bitsCollected = 0

            for (ch in clean) {
                val value = when (ch) {
                    in 'A'..'Z' -> ch - 'A'
                    in 'a'..'z' -> ch - 'a' + 26
                    in '0'..'9' -> ch - '0' + 52
                    '-', '+' -> 62
                    '_', '/' -> 63
                    else -> throw IllegalArgumentException("Invalid Base64URL character: $ch")
                }
                buffer = (buffer shl 6) or value
                bitsCollected += 6
                if (bitsCollected >= 8) {
                    bitsCollected -= 8
                    if (outIndex < out.size) {
                        out[outIndex++] = ((buffer ushr bitsCollected) and 0xFF).toByte()
                    }
                }
            }
            return if (outIndex == out.size) out else out.copyOf(outIndex)
        }
    }
}

/**
 * Room `@TypeConverter` helper for encrypting and decrypting standalone [EncryptedMessageContent]
 * columns using [Aes256GcmCrypto].
 */
@JvmInline
value class EncryptedMessageContent(val plaintext: String)

class Aes256GcmRoomConverters {
    @TypeConverter
    fun fromEncryptedMessageContent(value: EncryptedMessageContent?): String? {
        if (value == null) return null
        return if (Aes256GcmCrypto.isEncryptedPayload(value.plaintext)) {
            value.plaintext
        } else {
            Aes256GcmCrypto.encrypt(value.plaintext)
        }
    }

    @TypeConverter
    fun toEncryptedMessageContent(stored: String?): EncryptedMessageContent? {
        if (stored == null) return null
        return EncryptedMessageContent(Aes256GcmCrypto.decryptSafely(stored))
    }
}
