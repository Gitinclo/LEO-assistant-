package com.leoai.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.leoai.app.data.InputBoundsValidator
import com.leoai.app.data.MessageValidator
import com.leoai.app.data.TrainingProfile
import com.leoai.app.data.local.Aes256GcmCrypto

/**
 * Room Entity storing a custom AI training profile in the `training_profiles` table.
 * Sensitive fields (`domainDescription`, `sourceNotes`, `systemPrompt`, `qloraScript`, `ollamaModelfile`)
 * are encrypted at rest using Android Keystore AES-256-GCM bound to the profile `id` as AAD.
 */
@Immutable
@Entity(
    tableName = "training_profiles",
    indices = [
        Index(value = ["updated_at"]),
        Index(value = ["is_active"])
    ]
)
data class TrainingProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "domain_description")
    val domainDescription: String,
    @ColumnInfo(name = "source_notes")
    val sourceNotes: String,
    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String,
    @ColumnInfo(name = "hardware_target")
    val hardwareTarget: String,
    @ColumnInfo(name = "recommended_base_model")
    val recommendedBaseModel: String,
    @ColumnInfo(name = "qlora_script")
    val qloraScript: String,
    @ColumnInfo(name = "ollama_modelfile")
    val ollamaModelfile: String,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String
) {
    fun toDomain(): TrainingProfile = TrainingProfile(
        id = id,
        name = InputBoundsValidator.sanitizeSessionTitle(name),
        domainDescription = Aes256GcmCrypto.decryptSafely(domainDescription, associatedData = id),
        sourceNotes = Aes256GcmCrypto.decryptSafely(sourceNotes, associatedData = id),
        systemPrompt = Aes256GcmCrypto.decryptSafely(systemPrompt, associatedData = id),
        hardwareTarget = hardwareTarget,
        recommendedBaseModel = recommendedBaseModel,
        qloraScript = if (qloraScript.isEmpty()) "" else Aes256GcmCrypto.decryptSafely(qloraScript, associatedData = id),
        ollamaModelfile = if (ollamaModelfile.isEmpty()) "" else Aes256GcmCrypto.decryptSafely(ollamaModelfile, associatedData = id),
        isActive = isActive,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(
            profile: TrainingProfile,
            encryptAtRest: Boolean = true
        ): TrainingProfileEntity {
            val safeId = InputBoundsValidator.sanitizeId(profile.id)
            val safeName = InputBoundsValidator.sanitizeSessionTitle(profile.name)
            val safeDomain = MessageValidator.sanitize(profile.domainDescription, 1_000)
            val safeNotes = MessageValidator.sanitize(profile.sourceNotes, MessageValidator.MAX_MESSAGE_LENGTH)
            val safeSystem = MessageValidator.sanitize(profile.systemPrompt, 2_000)
            val safeScript = profile.qloraScript.trim().take(12_000)
            val safeModelfile = profile.ollamaModelfile.trim().take(4_000)

            fun encryptField(value: String): String {
                if (value.isEmpty()) return ""
                return if (encryptAtRest && !Aes256GcmCrypto.isEncryptedPayload(value)) {
                    Aes256GcmCrypto.encrypt(value, associatedData = safeId)
                } else {
                    value
                }
            }

            return TrainingProfileEntity(
                id = safeId,
                name = safeName,
                domainDescription = encryptField(safeDomain),
                sourceNotes = encryptField(safeNotes),
                systemPrompt = encryptField(safeSystem),
                hardwareTarget = profile.hardwareTarget.trim().take(64),
                recommendedBaseModel = profile.recommendedBaseModel.trim().take(128),
                qloraScript = encryptField(safeScript),
                ollamaModelfile = encryptField(safeModelfile),
                isActive = profile.isActive,
                updatedAt = profile.updatedAt
            )
        }
    }
}
