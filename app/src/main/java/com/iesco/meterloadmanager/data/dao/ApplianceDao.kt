package com.iesco.meterloadmanager.data.dao

import androidx.room.*
import com.iesco.meterloadmanager.data.entity.Appliance
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplianceDao {
    @Query("SELECT * FROM appliances ORDER BY assignedMeter ASC, name ASC")
    fun getAll(): Flow<List<Appliance>>

    @Query("SELECT * FROM appliances WHERE assignedMeter = :meter ORDER BY name ASC")
    fun getByMeter(meter: String): Flow<List<Appliance>>

    @Query("SELECT * FROM appliances WHERE isActive = 1 ORDER BY assignedMeter ASC, name ASC")
    fun getActive(): Flow<List<Appliance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Appliance>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(a: Appliance): Long

    @Update
    suspend fun update(a: Appliance)

    @Delete
    suspend fun delete(a: Appliance)

    @Query("SELECT COUNT(*) FROM appliances")
    suspend fun count(): Int
}
