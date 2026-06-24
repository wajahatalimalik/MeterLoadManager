package com.iesco.meterloadmanager.data.dao

import androidx.room.*
import com.iesco.meterloadmanager.data.entity.SwitchEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface SwitchEventDao {
    @Query("SELECT * FROM switch_events ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SwitchEvent>>

    @Insert
    suspend fun insert(e: SwitchEvent): Long

    @Delete
    suspend fun delete(e: SwitchEvent)
}
