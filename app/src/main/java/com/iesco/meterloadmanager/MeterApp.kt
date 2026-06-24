package com.iesco.meterloadmanager

import android.app.Application
import com.iesco.meterloadmanager.data.database.MeterDatabase
import com.iesco.meterloadmanager.data.repository.MeterRepository

class MeterApp : Application() {
    val db by lazy { MeterDatabase.get(this) }
    val repo by lazy {
        MeterRepository(
            meterDao        = db.meterDao(),
            historyDao      = db.historyDao(),
            readingDao      = db.readingDao(),
            switchDao       = db.switchDao(),
            settingsDao     = db.settingsDao(),
            applianceDao    = db.applianceDao(),
            tariffDao       = db.tariffDao(),
            notificationDao = db.notificationDao()
        )
    }
}
