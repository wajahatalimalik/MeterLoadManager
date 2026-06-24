package com.iesco.meterloadmanager.data.dao

import androidx.room.*
import com.iesco.meterloadmanager.data.entity.MonthlyBillHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyBillHistoryDao {
    @Query("SELECT * FROM monthly_bill_history ORDER BY billingYear DESC, billingMonthInt DESC")
    fun getAll(): Flow<List<MonthlyBillHistory>>

    @Query("SELECT * FROM monthly_bill_history WHERE meterNumber=:m ORDER BY billingYear ASC, billingMonthInt ASC")
    suspend fun getAllForMeterSorted(m: String): List<MonthlyBillHistory>

    @Query("SELECT * FROM monthly_bill_history ORDER BY billingYear DESC, billingMonthInt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MonthlyBillHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<MonthlyBillHistory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(h: MonthlyBillHistory): Long

    @Update
    suspend fun update(h: MonthlyBillHistory)

    @Delete
    suspend fun delete(h: MonthlyBillHistory)
}
