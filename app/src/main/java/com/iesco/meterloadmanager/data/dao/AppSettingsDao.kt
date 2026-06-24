package com.iesco.meterloadmanager.data.dao

import androidx.room.*
import com.iesco.meterloadmanager.data.entity.AppSettings

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id=1")
    suspend fun get(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(s: AppSettings)

    @Query("UPDATE app_settings SET seedDataInserted=:v WHERE id=1")
    suspend fun setSeedInserted(v: Boolean)
}
