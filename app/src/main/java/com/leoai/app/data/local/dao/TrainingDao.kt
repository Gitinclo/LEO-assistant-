package com.leoai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.leoai.app.data.local.entity.IngestedDocumentEntity
import com.leoai.app.data.local.entity.TrainingProfileEntity
import com.leoai.app.data.local.entity.TrainingQaPairEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingDao {

    @Query("SELECT * FROM training_profiles ORDER BY updated_at DESC")
    fun getAllProfiles(): Flow<List<TrainingProfileEntity>>

    @Query("SELECT * FROM training_profiles WHERE is_active = 1 ORDER BY updated_at DESC LIMIT 1")
    fun getActiveProfile(): Flow<TrainingProfileEntity?>

    @Query("SELECT * FROM training_profiles WHERE is_active = 1 ORDER BY updated_at DESC LIMIT 1")
    suspend fun getActiveProfileSnapshot(): TrainingProfileEntity?

    @Query("SELECT * FROM training_profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: String): TrainingProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: TrainingProfileEntity)

    @Query("UPDATE training_profiles SET is_active = 0")
    suspend fun deactivateAllProfiles()

    @Query("UPDATE training_profiles SET is_active = :active WHERE id = :profileId")
    suspend fun setProfileActive(profileId: String, active: Boolean)

    @Query("DELETE FROM training_profiles WHERE id = :profileId")
    suspend fun deleteProfileById(profileId: String)

    @Query("SELECT * FROM training_qa_pairs WHERE profile_id = :profileId ORDER BY quality_score DESC, created_at ASC")
    fun getQaPairsForProfile(profileId: String): Flow<List<TrainingQaPairEntity>>

    @Query("SELECT * FROM training_qa_pairs WHERE profile_id = :profileId ORDER BY quality_score DESC, created_at ASC")
    suspend fun getQaPairsForProfileSnapshot(profileId: String): List<TrainingQaPairEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQaPair(pair: TrainingQaPairEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQaPairs(pairs: List<TrainingQaPairEntity>)

    @Query("DELETE FROM training_qa_pairs WHERE id = :pairId")
    suspend fun deleteQaPairById(pairId: String)

    @Query("DELETE FROM training_qa_pairs WHERE profile_id = :profileId AND quality_score < :minScore")
    suspend fun pruneQaPairsBelowScore(profileId: String, minScore: Int)

    @Query("DELETE FROM training_qa_pairs WHERE profile_id = :profileId")
    suspend fun deleteQaPairsForProfile(profileId: String)

    @Query("SELECT * FROM ingested_documents WHERE profile_id = :profileId ORDER BY created_at DESC")
    fun getDocumentsForProfile(profileId: String): Flow<List<IngestedDocumentEntity>>

    @Query("SELECT * FROM ingested_documents WHERE profile_id = :profileId ORDER BY created_at ASC")
    suspend fun getDocumentsForProfileSnapshot(profileId: String): List<IngestedDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: IngestedDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocuments(documents: List<IngestedDocumentEntity>)

    @Query("UPDATE ingested_documents SET status = :status WHERE id = :documentId")
    suspend fun updateDocumentStatus(documentId: String, status: String)

    @Query("DELETE FROM ingested_documents WHERE id = :documentId")
    suspend fun deleteDocumentById(documentId: String)

    @Query("DELETE FROM ingested_documents WHERE profile_id = :profileId")
    suspend fun deleteDocumentsForProfile(profileId: String)
}
