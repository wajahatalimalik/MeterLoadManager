package com.iesco.meterloadmanager.data.dao

import androidx.room.*
import com.iesco.meterloadmanager.data.entity.TariffSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface TariffDao {
    @Query("SELECT * FROM tariff_settings WHERE id = 1")
    fun get(): Flow<TariffSettings?>

    @Query("SELECT * FROM tariff_settings WHERE id = 1")
    suspend fun getOnce(): TariffSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: TariffSettings)

    @Update
    suspend fun update(t: TariffSettings)
}
