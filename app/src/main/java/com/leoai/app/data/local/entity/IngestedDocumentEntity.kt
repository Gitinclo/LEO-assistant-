package com.leoai.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.leoai.app.data.DocumentIngestionChunker
import com.leoai.app.data.IngestedDocument
import com.leoai.app.data.InputBoundsValidator
import com.leoai.app.data.local.Aes256GcmCrypto

/**
 * Room Entity storing raw ingested document text for a [com.leoai.app.data.TrainingProfile].
 *
 * All sensitive document contents (`sourcePath`, `rawContent`) are encrypted at rest with
 * Android Keystore AES-256-GCM ([Aes256GcmCrypto]) and bound to `profileId` as Associated
 * Authenticated Data (AAD) to prevent cross-profile tampering.
 */
@Entity(
    tableName = "ingested_documents",
    indices = [
        Index(value = ["profile_id", "created_at"]),
        Index(value = ["profile_id"])
    ]
)
data class IngestedDocumentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "profile_id")
    val profileId: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "source_path")
    val sourcePath: String,

    @ColumnInfo(name = "raw_content")
    val rawContent: String,

    @ColumnInfo(name = "chunk_count")
    val chunkCount: Int,

    @ColumnInfo(name = "char_count")
    val charCount: Int,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "created_at")
    val createdAt: String
) {
    fun toDomain(): IngestedDocument {
        val decryptedPath = Aes256GcmCrypto.decryptSafely(
            payload = sourcePath,
            associatedData = profileId
        )
        val decryptedContent = Aes256GcmCrypto.decryptSafely(
            payload = rawContent,
            associatedData = profileId
        )
        return IngestedDocument(
            id = id,
            profileId = profileId,
            fileName = fileName,
            sourcePath = decryptedPath,
            rawContent = decryptedContent,
            chunkCount = chunkCount.coerceAtLeast(1),
            charCount = charCount.coerceAtLeast(decryptedContent.length),
            status = status,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(doc: IngestedDocument): IngestedDocumentEntity {
            val safeProfileId = InputBoundsValidator.sanitizeId(doc.profileId)
            val safeFileName = doc.fileName.trim().ifBlank { "untitled_document.md" }.take(140)
            val safeSourcePath = doc.sourcePath.trim().ifBlank { "./data/raw_docs/$safeFileName" }.take(260)
            val normalizedContent = DocumentIngestionChunker
                .normalizeRawDocumentText(doc.rawContent)
                .take(32_000)
            val computedChunks = DocumentIngestionChunker.chunkDocument(
                docId = doc.id,
                fileName = safeFileName,
                sourcePath = safeSourcePath,
                rawText = normalizedContent
            )

            return IngestedDocumentEntity(
                id = InputBoundsValidator.sanitizeId(doc.id),
                profileId = safeProfileId,
                fileName = safeFileName,
                sourcePath = Aes256GcmCrypto.encrypt(
                    plaintext = safeSourcePath,
                    associatedData = safeProfileId
                ),
                rawContent = Aes256GcmCrypto.encrypt(
                    plaintext = normalizedContent,
                    associatedData = safeProfileId
                ),
                chunkCount = computedChunks.size.coerceAtLeast(1),
                charCount = normalizedContent.length,
                status = doc.status.ifBlank { "INGESTED" }.take(32),
                createdAt = doc.createdAt
            )
        }
    }
}
