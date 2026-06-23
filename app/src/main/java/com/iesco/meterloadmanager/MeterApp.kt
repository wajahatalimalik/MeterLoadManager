package com.iesco.meterloadmanager

import android.app.Application
import com.iesco.meterloadmanager.data.database.MeterDatabase
import com.iesco.meterloadmanager.data.repository.MeterRepository

class MeterApp : Application() {
    val db by lazy { MeterDatabase.get(this) }
    val repo by lazy {
        MeterRepository(db.meterDao(), db.historyDao(), db.readingDao(), db.switchDao(), db.settingsDao())
    }
}
