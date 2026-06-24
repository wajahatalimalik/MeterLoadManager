package com.iesco.meterloadmanager.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Editable tariff slab settings.
 * Protected = subsidised domestic slab.
 * Non-protected = commercial/higher rate.
 */
@Entity(tableName = "tariff_settings")
data class TariffSettings(
    @PrimaryKey val id: Int = 1,
    // Protected slabs (Rs/kWh)
    val protectedSlab1Units: Int = 100,      // First 100 units
    val protectedSlab1Rate: Double = 10.54,
    val protectedSlab2Units: Int = 100,      // Next units (101-200)
    val protectedSlab2Rate: Double = 13.01,
    val protectedFixedCharge: Double = 0.0,

    // Non-protected flat rate
    val nonProtectedRate: Double = 28.91,
    val nonProtectedFixedCharge: Double = 0.0,

    // Additional charges
    val fpaPerUnit: Double = 0.0,           // Fuel Price Adjustment
    val gstPercent: Double = 18.0,          // GST %
    val electricityDutyPercent: Double = 1.5,
    val tvFee: Double = 35.0,
    val meterRent: Double = 15.0
)
