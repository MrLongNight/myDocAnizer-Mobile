package com.example.data

import androidx.room.*
import com.example.model.CustomFieldEntity
import com.example.model.DocumentCustomFieldValueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFieldDao {
    @Query("SELECT * FROM custom_fields ORDER BY createdAt ASC")
    fun getAllCustomFields(): Flow<List<CustomFieldEntity>>

    @Query("SELECT * FROM custom_fields ORDER BY createdAt ASC")
    suspend fun getAllCustomFieldsList(): List<CustomFieldEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomField(field: CustomFieldEntity)

    @Update
    suspend fun updateCustomField(field: CustomFieldEntity)

    @Delete
    suspend fun deleteCustomField(field: CustomFieldEntity)

    @Query("DELETE FROM custom_fields WHERE id = :id")
    suspend fun deleteCustomFieldById(id: String)

    // Document Custom Field Values
    @Query("SELECT * FROM document_custom_field_values WHERE documentId = :documentId")
    fun getCustomFieldValuesForDocument(documentId: Long): Flow<List<DocumentCustomFieldValueEntity>>

    @Query("SELECT * FROM document_custom_field_values WHERE documentId = :documentId")
    suspend fun getCustomFieldValuesForDocumentList(documentId: Long): List<DocumentCustomFieldValueEntity>

    @Query("SELECT * FROM document_custom_field_values")
    fun getAllDocumentCustomFieldValues(): Flow<List<DocumentCustomFieldValueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFieldValue(value: DocumentCustomFieldValueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFieldValues(values: List<DocumentCustomFieldValueEntity>)

    @Query("DELETE FROM document_custom_field_values WHERE documentId = :documentId AND customFieldId = :customFieldId")
    suspend fun deleteFieldValue(documentId: Long, customFieldId: String)

    @Query("DELETE FROM document_custom_field_values WHERE documentId = :documentId")
    suspend fun deleteAllValuesForDocument(documentId: Long)
}
