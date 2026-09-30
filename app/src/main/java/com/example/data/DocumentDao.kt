package com.example.data

import androidx.room.*
import com.example.model.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getTrashDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT COUNT(*) FROM documents WHERE isDeleted = 1")
    fun getTrashCount(): Flow<Int>

    @Query("""
        SELECT * FROM documents 
        WHERE isDeleted = 0 
          AND (cancellationDeadline IS NOT NULL OR contractEndDate IS NOT NULL)
        ORDER BY 
          CASE 
            WHEN cancellationDeadline IS NOT NULL THEN cancellationDeadline 
            ELSE contractEndDate 
          END ASC
    """)
    fun getContractDeadlineDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE isDeleted = 0 AND docType = :docType ORDER BY createdAt DESC")
    fun getDocumentsByDocType(docType: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isDeleted = 0 AND mainCategory = :category ORDER BY createdAt DESC")
    fun getDocumentsByCategory(category: String): Flow<List<DocumentEntity>>

    @Query("""
        SELECT * FROM documents 
        WHERE isDeleted = 0 AND (
           title LIKE '%' || :query || '%' 
           OR sender LIKE '%' || :query || '%' 
           OR ocrText LIKE '%' || :query || '%' 
           OR tags LIKE '%' || :query || '%'
        )
        ORDER BY createdAt DESC
    """)
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>

    @Query("""
        SELECT documents.* FROM documents 
        JOIN documents_fts ON documents.id = documents_fts.docid 
        WHERE documents.isDeleted = 0 
          AND documents_fts MATCH :query
        ORDER BY documents.createdAt DESC
    """)
    fun searchDocumentsFts(query: String): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE documents SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteDocument(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    suspend fun restoreDocument(id: Long)

    @Query("DELETE FROM documents WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM documents WHERE isDeleted = 1 AND deletedAt < :thresholdTimestamp")
    suspend fun deleteTrashOlderThan(thresholdTimestamp: Long)

    @Query("UPDATE documents SET docType = :newDocType WHERE id = :id")
    suspend fun updateDocType(id: Long, newDocType: String)

    @Query("UPDATE documents SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    suspend fun getAllDocumentsList(): List<DocumentEntity>
}
