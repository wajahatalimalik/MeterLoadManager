package com.iesco.meterloadmanager.data.database

import android.content.Context
import androidx.room.*
import com.iesco.meterloadmanager.data.dao.*
import com.iesco.meterloadmanager.data.entity.*

@Database(
    entities = [
        Meter::class,
        MonthlyBillHistory::class,
        ManualReading::class,
        SwitchEvent::class,
        AppSettings::class,
        Appliance::class,
        TariffSettings::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(DbConverters::class)
abstract class MeterDatabase : RoomDatabase() {
    abstract fun meterDao(): MeterDao
    abstract fun historyDao(): MonthlyBillHistoryDao
    abstract fun readingDao(): ManualReadingDao
    abstract fun switchDao(): SwitchEventDao
    abstract fun settingsDao(): AppSettingsDao
    abstract fun applianceDao(): ApplianceDao
    abstract fun tariffDao(): TariffDao

    companion object {
        @Volatile private var INSTANCE: MeterDatabase? = null
        fun get(context: Context): MeterDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(
                context.applicationContext,
                MeterDatabase::class.java,
                "mlm.db"
            )
            .fallbackToDestructiveMigration()   // OK for dev; seed data will re-insert
            .build().also { INSTANCE = it }
        }
    }
}

class DbConverters {
    @TypeConverter fun statusToStr(s: MeterStatus): String = s.name
    @TypeConverter fun strToStatus(s: String): MeterStatus = MeterStatus.valueOf(s)
    @TypeConverter fun triggerToStr(t: SwitchTrigger): String = t.name
    @TypeConverter fun strToTrigger(s: String): SwitchTrigger = SwitchTrigger.valueOf(s)
    @TypeConverter fun categoryToStr(c: ApplianceCategory): String = c.name
    @TypeConverter fun strToCategory(s: String): ApplianceCategory = ApplianceCategory.valueOf(s)
}
