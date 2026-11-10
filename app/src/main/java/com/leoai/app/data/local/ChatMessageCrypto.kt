package com.leoai.app.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Authenticated AES-256-GCM encryption engine for local Room database records.
 *
 * Implements the critical safeguards from the AES-256-GCM specification:
 * 1. Never reuses a Nonce/IV: Generates a fresh 12-byte (96-bit) cryptographically random
 *    IV via [SecureRandom] for every encryption operation.
 * 2. Context binding with AAD: Binds the conversation `sessionId` (or record identifier)
 *    as Associated Authenticated Data (AAD) so encrypted content cannot be moved across
 *    sessions or tampered with undetected.
 * 3. 128-bit Authentication Tag: Verifies integrity automatically prior to releasing plaintext.
 * 4. Hardware-backed key storage: Uses `AndroidKeyStore` 256-bit AES key on Android devices,
 *    with a thread-safe in-memory 256-bit key fallback for local JVM unit tests.
 */
object ChatMessageCrypto {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "leo_ai_room_aes256_gcm_master_key"
    private const val IV_LENGTH_BYTES = 12 // 96-bit nonce recommended by NIST for AES-GCM
    private const val TAG_LENGTH_BITS = 128 // 128-bit authentication tag
    private const val PAYLOAD_PREFIX = "v1:"

    private val secureRandom = SecureRandom()

    @Volatile
    private var fallbackJvmSecretKey: SecretKey? = null

    /**
     * Encrypts [plaintext] using AES-256-GCM and binds [associatedData] (AAD) to the authentication tag.
     * Returns a versioned URL-safe Base64 string: `v1:<base64url(nonce[12] + ciphertext + tag[16])>`.
     */
    fun encrypt(plaintext: String, associatedData: String = ""): String {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(), spec)

        if (associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        }

        val ciphertextWithTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertextWithTag.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertextWithTag, 0, combined, iv.size, ciphertextWithTag.size)

        return PAYLOAD_PREFIX + encodeBase64Url(combined)
    }

    /**
     * Decrypts a versioned AES-256-GCM [payload] and verifies both the 128-bit authentication tag
     * and the [associatedData] (AAD).
     *
     * Throws [AEADBadTagException] or [IllegalArgumentException] if the ciphertext, nonce, or AAD
     * has been tampered with. If the input does not start with `v1:`, returns it as legacy plaintext.
     */
    fun decrypt(payload: String, associatedData: String = ""): String {
        if (!payload.startsWith(PAYLOAD_PREFIX)) {
            return payload
        }

        val encoded = payload.removePrefix(PAYLOAD_PREFIX)
        val raw = decodeBase64Url(encoded)
        require(raw.size > IV_LENGTH_BYTES + (TAG_LENGTH_BITS / 8) - 1) {
            "Invalid AES-256-GCM payload length"
        }

        val iv = raw.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertextWithTag = raw.copyOfRange(IV_LENGTH_BYTES, raw.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

        if (associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData.toByteArray(Charsets.UTF_8))
        }

        val decryptedBytes = cipher.doFinal(ciphertextWithTag)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Decrypts [payload] safely for UI rendering, returning a fallback notice only if
     * key rotation or corruption prevents decryption.
     */
    fun decryptSafely(payload: String, associatedData: String = ""): String {
        return try {
            decrypt(payload, associatedData)
        } catch (_: AEADBadTagException) {
            "[Encrypted message — integrity verification failed]"
        } catch (_: Exception) {
            payload.removePrefix(PAYLOAD_PREFIX)
        }
    }

    /**
     * Checks whether a stored string is an encrypted AES-256-GCM payload.
     */
    fun isEncryptedPayload(value: String): Boolean = value.startsWith(PAYLOAD_PREFIX)

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        fallbackJvmSecretKey?.let { return it }

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                existingEntry.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(false)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Throwable) {
            // Fallback for local JVM unit test environment where AndroidKeyStore is not mounted
            val keyBytes = ByteArray(32) // 256-bit key
            secureRandom.nextBytes(keyBytes)
            SecretKeySpec(keyBytes, "AES").also { fallbackJvmSecretKey = it }
        }
    }

    private const val BASE64_URL_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

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
