package com.iesco.meterloadmanager.data.repository

import com.iesco.meterloadmanager.data.dao.*
import com.iesco.meterloadmanager.data.entity.*
import com.iesco.meterloadmanager.utils.BillingCalculator
import com.iesco.meterloadmanager.utils.SeedData
import java.time.Instant
import java.time.ZoneId

class MeterRepository(
    private val meterDao: MeterDao,
    private val historyDao: MonthlyBillHistoryDao,
    private val readingDao: ManualReadingDao,
    private val switchDao: SwitchEventDao,
    private val settingsDao: AppSettingsDao
) {
    val allMeters = meterDao.getAllMeters()
    val allHistory = historyDao.getAll()
    val allReadings = readingDao.getAll()
    val allSwitchEvents = switchDao.getAll()

    suspend fun seedIfNeeded() {
        val s = settingsDao.get()
        if (s == null) settingsDao.insert(AppSettings())
        if (settingsDao.get()?.seedDataInserted == false) {
            meterDao.insertAll(SeedData.meters())
            historyDao.insertAll(SeedData.history())
            readingDao.insertAll(SeedData.readings())
            settingsDao.setSeedInserted(true)
        }
    }

    suspend fun addReading(meterNo: String, reading: Double, ts: Long,
                           status: MeterStatus, notes: String?) {
        val readDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
        val cycleStart = BillingCalculator.cycleStart(readDate).toString()
        readingDao.insert(ManualReading(meterNumber=meterNo, reading=reading, timestamp=ts,
            status=status, notes=notes, billingCycleStart=cycleStart))
        meterDao.updateReading(meterNo, reading, ts)
        meterDao.updateStatus(meterNo, status)
    }

    suspend fun deleteReading(id: Long, meterNo: String) {
        readingDao.softDelete(id)
        readingDao.getLatest(meterNo)?.let { meterDao.updateReading(meterNo, it.reading, it.timestamp) }
    }

    suspend fun updateReading(r: ManualReading) {
        readingDao.update(r)
        readingDao.getLatest(r.meterNumber)?.let { meterDao.updateReading(r.meterNumber, it.reading, it.timestamp) }
    }

    suspend fun changeMeterStatus(meterNo: String, newStatus: MeterStatus,
                                   prevStatus: MeterStatus, notes: String?) {
        meterDao.updateStatus(meterNo, newStatus)
        val m = meterDao.getByNumber(meterNo)
        val units = m?.let { BillingCalculator.consumed(it) }
        val trigger = when {
            units != null && units >= BillingCalculator.T_LIMIT   -> SwitchTrigger.THRESHOLD_199
            units != null && units >= BillingCalculator.T_DANGER  -> SwitchTrigger.THRESHOLD_190
            units != null && units >= BillingCalculator.T_WARNING -> SwitchTrigger.THRESHOLD_180
            units != null && units >= BillingCalculator.T_SWITCH  -> SwitchTrigger.THRESHOLD_150
            else -> SwitchTrigger.MANUAL
        }
        switchDao.insert(SwitchEvent(meterNumber=meterNo, previousStatus=prevStatus,
            newStatus=newStatus, timestamp=System.currentTimeMillis(),
            triggeredBy=trigger, notes=notes, unitsAtSwitch=units))
    }

    suspend fun addHistory(h: MonthlyBillHistory) = historyDao.insert(h)
    suspend fun updateHistory(h: MonthlyBillHistory) = historyDao.update(h)
    suspend fun deleteHistory(h: MonthlyBillHistory) = historyDao.delete(h)
}
