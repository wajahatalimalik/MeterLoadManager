package com.iesco.meterloadmanager.data.dao

import androidx.room.*
import com.iesco.meterloadmanager.data.entity.ManualReading
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualReadingDao {
    @Query("SELECT * FROM manual_readings WHERE isDeleted=0 ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ManualReading>>

    @Query("SELECT * FROM manual_readings WHERE meterNumber=:m AND isDeleted=0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(m: String): ManualReading?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ManualReading>)

    @Insert
    suspend fun insert(r: ManualReading): Long

    @Update
    suspend fun update(r: ManualReading)

    @Query("UPDATE manual_readings SET isDeleted=1 WHERE id=:id")
    suspend fun softDelete(id: Long)
}
