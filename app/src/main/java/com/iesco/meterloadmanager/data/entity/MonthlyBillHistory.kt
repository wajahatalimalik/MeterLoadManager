package com.iesco.meterloadmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monthly_bill_history")
data class MonthlyBillHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meterNumber: String,
    val billingMonth: String,
    val billingYear: Int,
    val billingMonthInt: Int,
    val unitsConsumed: Int,
    val billAmount: Double,
    val paymentMade: Double? = null,
    val previousReading: Double? = null,
    val presentReading: Double? = null,
    val notes: String? = null,
    val isOverLimit: Boolean = false
)
