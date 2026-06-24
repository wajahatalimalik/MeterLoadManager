package com.iesco.meterloadmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "switch_events")
data class SwitchEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meterNumber: String,
    val previousStatus: MeterStatus,
    val newStatus: MeterStatus,
    val timestamp: Long,
    val triggeredBy: SwitchTrigger,
    val notes: String? = null,
    val unitsAtSwitch: Double? = null
)

enum class SwitchTrigger {
    MANUAL, THRESHOLD_150, THRESHOLD_180, THRESHOLD_190, THRESHOLD_199, SYSTEM
}
