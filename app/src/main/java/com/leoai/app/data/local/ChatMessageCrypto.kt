package com.leoai.app.data.local

/**
 * Facade delegating to [Aes256GcmCrypto] for encrypting and decrypting Room database
 * message contents and session previews at rest using Android Keystore AES-256-GCM.
 */
object ChatMessageCrypto {

    fun encrypt(plaintext: String, associatedData: String = ""): String =
        Aes256GcmCrypto.encrypt(plaintext, associatedData)

    fun decrypt(payload: String, associatedData: String = ""): String =
        Aes256GcmCrypto.decrypt(payload, associatedData)

    fun decryptSafely(payload: String, associatedData: String = ""): String =
        Aes256GcmCrypto.decryptSafely(payload, associatedData)

    fun isEncryptedPayload(value: String): Boolean =
        Aes256GcmCrypto.isEncryptedPayload(value)

    fun inspectPayload(payload: String): Aes256GcmCrypto.EncryptedPayloadMetadata? =
        Aes256GcmCrypto.inspectPayload(payload)
}
