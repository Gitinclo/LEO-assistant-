package com.leoai.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.leoai.app.data.InputBoundsValidator
import com.leoai.app.data.MessageValidator
import com.leoai.app.data.TrainingQaPair
import com.leoai.app.data.local.Aes256GcmCrypto

/**
 * Room Entity storing a synthetic or user-curated Question-and-Answer pair in `training_qa_pairs`.
 * `question`, `answer`, and `judgeRationale` are encrypted at rest with AES-256-GCM bound to `profileId`.
 */
@Immutable
@Entity(
    tableName = "training_qa_pairs",
    indices = [
        Index(value = ["profile_id", "quality_score"]),
        Index(value = ["profile_id"])
    ]
)
data class TrainingQaPairEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "profile_id")
    val profileId: String,
    @ColumnInfo(name = "question")
    val question: String,
    @ColumnInfo(name = "answer")
    val answer: String,
    @ColumnInfo(name = "quality_score")
    val qualityScore: Int,
    @ColumnInfo(name = "judge_rationale")
    val judgeRationale: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String
) {
    fun toDomain(): TrainingQaPair = TrainingQaPair(
        id = id,
        profileId = profileId,
        question = Aes256GcmCrypto.decryptSafely(question, associatedData = profileId),
        answer = Aes256GcmCrypto.decryptSafely(answer, associatedData = profileId),
        qualityScore = qualityScore.coerceIn(1, 10),
        judgeRationale = Aes256GcmCrypto.decryptSafely(judgeRationale, associatedData = profileId),
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(
            pair: TrainingQaPair,
            encryptAtRest: Boolean = true
        ): TrainingQaPairEntity {
            val safeId = InputBoundsValidator.sanitizeId(pair.id)
            val safeProfileId = InputBoundsValidator.sanitizeId(pair.profileId)
            val safeQuestion = MessageValidator.sanitize(pair.question, 1_500)
            val safeAnswer = MessageValidator.sanitize(pair.answer, MessageValidator.MAX_MESSAGE_LENGTH)
            val safeRationale = MessageValidator.sanitize(pair.judgeRationale, 600)
            val clampedScore = pair.qualityScore.coerceIn(1, 10)

            fun encryptField(value: String): String {
                if (value.isEmpty()) return ""
                return if (encryptAtRest && !Aes256GcmCrypto.isEncryptedPayload(value)) {
                    Aes256GcmCrypto.encrypt(value, associatedData = safeProfileId)
                } else {
                    value
                }
            }

            return TrainingQaPairEntity(
                id = safeId,
                profileId = safeProfileId,
                question = encryptField(safeQuestion),
                answer = encryptField(safeAnswer),
                qualityScore = clampedScore,
                judgeRationale = encryptField(safeRationale),
                createdAt = pair.createdAt
            )
        }
    }
}
