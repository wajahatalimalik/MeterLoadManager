package com.iesco.meterloadmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ApplianceCategory {
    COOLING, LIGHTING, FAN, KITCHEN, LAUNDRY, MOTOR, ENTERTAINMENT, OTHER
}

@Entity(tableName = "appliances")
data class Appliance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val assignedMeter: String,          // "600","603","700","Unassigned"
    val category: ApplianceCategory,
    val quantity: Int,
    val wattagePerUnit: Double,         // Watts
    val dailyHours: Double,             // Average hours used per day
    val isActive: Boolean = true,
    val notes: String? = null
) {
    /** Daily kWh = (quantity × wattage × hours) / 1000 */
    val dailyUnits: Double get() = (quantity * wattagePerUnit * dailyHours) / 1000.0
    /** Monthly projected kWh (30-day estimate) */
    fun monthlyUnits(cycleDays: Int = 30): Double = dailyUnits * cycleDays
}
