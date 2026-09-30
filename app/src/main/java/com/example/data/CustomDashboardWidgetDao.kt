package com.example.data

import androidx.room.*
import com.example.model.CustomDashboardWidgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomDashboardWidgetDao {
    @Query("SELECT * FROM custom_dashboard_widgets ORDER BY position ASC, createdAt ASC")
    fun getAllWidgetsFlow(): Flow<List<CustomDashboardWidgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWidget(widget: CustomDashboardWidgetEntity)

    @Delete
    suspend fun deleteWidget(widget: CustomDashboardWidgetEntity)

    @Query("DELETE FROM custom_dashboard_widgets WHERE id = :id")
    suspend fun deleteWidgetById(id: String)

    @Query("UPDATE custom_dashboard_widgets SET position = :newPosition WHERE id = :id")
    suspend fun updateWidgetPosition(id: String, newPosition: Int)

    @Query("UPDATE custom_dashboard_widgets SET isEnabled = :enabled WHERE id = :id")
    suspend fun updateWidgetEnabled(id: String, enabled: Boolean)
}
