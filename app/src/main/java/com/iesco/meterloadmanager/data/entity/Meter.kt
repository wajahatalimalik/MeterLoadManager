package com.iesco.meterloadmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meters")
data class Meter(
    @PrimaryKey val meterNumber: String,
    val referenceNo: String,
    val purpose: String,
    val cycleStartReading: Double,
    val currentReading: Double,
    val currentReadingTimestamp: Long,
    val status: MeterStatus
)

enum class MeterStatus {
    RUNNING, SHARING, PAUSED, FULL_LOAD, PARTIAL_LOAD
}
