package com.iesco.meterloadmanager.data.dao

import androidx.room.*
import com.iesco.meterloadmanager.data.entity.Meter
import com.iesco.meterloadmanager.data.entity.MeterStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterDao {
    @Query("SELECT * FROM meters ORDER BY meterNumber ASC")
    fun getAllMeters(): Flow<List<Meter>>

    @Query("SELECT * FROM meters WHERE meterNumber = :n")
    suspend fun getByNumber(n: String): Meter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meters: List<Meter>)

    @Update
    suspend fun update(meter: Meter)

    @Query("UPDATE meters SET currentReading=:r, currentReadingTimestamp=:t WHERE meterNumber=:n")
    suspend fun updateReading(n: String, r: Double, t: Long)

    @Query("UPDATE meters SET status=:s WHERE meterNumber=:n")
    suspend fun updateStatus(n: String, s: MeterStatus)

    @Query("UPDATE meters SET cycleStartReading=:r WHERE meterNumber=:n")
    suspend fun updateCycleStart(n: String, r: Double)
}
