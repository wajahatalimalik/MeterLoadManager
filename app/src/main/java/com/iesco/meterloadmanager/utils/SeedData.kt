package com.iesco.meterloadmanager.utils

import com.iesco.meterloadmanager.data.entity.*
import java.time.LocalDateTime
import java.time.ZoneId

object SeedData {

    private val TS_READING: Long = LocalDateTime.of(2026, 6, 23, 19, 0)
        .atZone(ZoneId.systemDefault()).toEpochSecond() * 1000L

    private const val CYCLE_START = "2026-06-13"

    fun meters() = listOf(
        Meter("600", "03 14622 1335600", "Ground floor – primary load bearer",
            cycleStartReading = 2169.82, currentReading = 2288.0,
            currentReadingTimestamp = TS_READING, status = MeterStatus.RUNNING),
        Meter("603", "03 14622 1335603", "1st & 2nd floor + partial ground floor sharing",
            cycleStartReading = 1908.64, currentReading = 1945.0,
            currentReadingTimestamp = TS_READING, status = MeterStatus.SHARING),
        Meter("700", "03 14622 1335700", "Ground floor backup (paused – turn on at 150 units on M600)",
            cycleStartReading = 2016.23, currentReading = 2016.0,
            currentReadingTimestamp = TS_READING, status = MeterStatus.PAUSED)
    )

    fun readings() = listOf(
        ManualReading(meterNumber="600", reading=2288.0, timestamp=TS_READING,
            status=MeterStatus.RUNNING, billingCycleStart=CYCLE_START,
            notes="Cycle start reading after June 2026 bill"),
        ManualReading(meterNumber="603", reading=1945.0, timestamp=TS_READING,
            status=MeterStatus.SHARING, billingCycleStart=CYCLE_START,
            notes="Cycle start reading after June 2026 bill"),
        ManualReading(meterNumber="700", reading=2016.0, timestamp=TS_READING,
            status=MeterStatus.PAUSED, billingCycleStart=CYCLE_START,
            notes="Meter paused – near zero consumption this cycle")
    )

    fun history(): List<MonthlyBillHistory> {
        fun h(meter: String, month: String, yr: Int, mi: Int, units: Int, bill: Double,
              pay: Double? = null, prev: Double? = null, pres: Double? = null
        ) = MonthlyBillHistory(meterNumber=meter, billingMonth=month, billingYear=yr,
            billingMonthInt=mi, unitsConsumed=units, billAmount=bill, paymentMade=pay,
            previousReading=prev, presentReading=pres, isOverLimit=units >= 200)

        return listOf(
            // ── Meter 600 ──────────────────────────────────────────────────────
            h("600","Jun 2025",2025,6,257,9890.0),
            h("600","Jul 2025",2025,7,301,13989.0,14574.0),
            h("600","Aug 2025",2025,8,106,3621.0),
            h("600","Sep 2025",2025,9,202,7677.0),
            h("600","Oct 2025",2025,10,133,4811.0),
            h("600","Nov 2025",2025,11,118,4266.0),
            h("600","Dec 2025",2025,12,88,2574.0),
            h("600","Jan 2026",2026,1,138,5227.0),
            h("600","Feb 2026",2026,2,48,1542.0),
            h("600","Mar 2026",2026,3,110,5250.0),
            h("600","Apr 2026",2026,4,75,1570.0),
            h("600","May 2026",2026,5,165,3135.0),
            h("600","Jun 2026",2026,6,163,3033.0,null,2006.33,2169.82),
            // ── Meter 603 ──────────────────────────────────────────────────────
            h("603","Jun 2025",2025,6,246,9418.0),
            h("603","Jul 2025",2025,7,254,10117.0,10540.0),
            h("603","Aug 2025",2025,8,130,4505.0),
            h("603","Sep 2025",2025,9,132,4232.0),
            h("603","Oct 2025",2025,10,121,4378.0),
            h("603","Nov 2025",2025,11,76,2164.0),
            h("603","Dec 2025",2025,12,70,2031.0),
            h("603","Jan 2026",2026,1,73,2184.0),
            h("603","Feb 2026",2026,2,79,1107.0),
            h("603","Mar 2026",2026,3,106,2304.0),
            h("603","Apr 2026",2026,4,108,2330.0),
            h("603","May 2026",2026,5,97,1787.0),
            h("603","Jun 2026",2026,6,171,3203.0,null,1737.33,1908.64),
            // ── Meter 700 ──────────────────────────────────────────────────────
            h("700","Jun 2025",2025,6,98,914.0),
            h("700","Jul 2025",2025,7,193,2109.0,2197.0),
            h("700","Aug 2025",2025,8,223,9241.0),
            h("700","Sep 2025",2025,9,198,7163.0),
            h("700","Oct 2025",2025,10,204,8404.0),
            h("700","Nov 2025",2025,11,70,1958.0),
            h("700","Dec 2025",2025,12,127,4686.0),
            h("700","Jan 2026",2026,1,79,2378.0),
            h("700","Feb 2026",2026,2,201,8881.0),
            h("700","Mar 2026",2026,3,82,3352.0),
            h("700","Apr 2026",2026,4,137,6375.0),
            h("700","May 2026",2026,5,70,2826.0),
            h("700","Jun 2026",2026,6,184,7854.0,null,1832.13,2016.23)
        )
    }
}
