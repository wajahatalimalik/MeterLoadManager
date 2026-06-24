package com.iesco.meterloadmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manual_readings")
data class ManualReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meterNumber: String,
    val reading: Double,
    val timestamp: Long,
    val status: MeterStatus,
    val notes: String? = null,
    val billingCycleStart: String,
    val isDeleted: Boolean = false
)
