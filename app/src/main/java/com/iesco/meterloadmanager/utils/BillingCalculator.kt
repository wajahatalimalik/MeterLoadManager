package com.iesco.meterloadmanager.utils

import com.iesco.meterloadmanager.data.entity.Meter
import com.iesco.meterloadmanager.data.entity.MeterStatus
import com.iesco.meterloadmanager.data.entity.TariffSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object BillingCalculator {

    // Thresholds
    const val T_WATCH   = 140.0
    const val T_SWITCH  = 150.0
    const val T_AMBER   = 170.0
    const val T_WARNING = 175.0
    const val T_PURPLE  = 171.0
    const val T_DANGER  = 190.0
    const val T_LIMIT   = 199.0
    const val T_SLAB    = 200.0

    fun cycleStart(date: LocalDate = LocalDate.now()): LocalDate =
        if (date.dayOfMonth >= 13) date.withDayOfMonth(13)
        else date.minusMonths(1).withDayOfMonth(13)

    fun cycleEnd(date: LocalDate = LocalDate.now()): LocalDate =
        cycleStart(date).plusMonths(1)

    fun daysPassed(start: LocalDate = cycleStart()): Long =
        ChronoUnit.DAYS.between(start, LocalDate.now()).coerceAtLeast(1)

    fun daysRemaining(start: LocalDate = cycleStart()): Long =
        ChronoUnit.DAYS.between(LocalDate.now(), cycleEnd()).coerceAtLeast(0)

    fun totalDays(start: LocalDate = cycleStart()): Long =
        ChronoUnit.DAYS.between(start, start.plusMonths(1))

    fun consumed(m: Meter): Double =
        (m.currentReading - m.cycleStartReading).coerceAtLeast(0.0)

    fun remainingTo(m: Meter, t: Double) = (t - consumed(m)).coerceAtLeast(0.0)

    fun dailyAvg(m: Meter): Double {
        val readDate = Instant.ofEpochMilli(m.currentReadingTimestamp)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val days = ChronoUnit.DAYS.between(cycleStart(), readDate).coerceAtLeast(1)
        return consumed(m) / days
    }

    fun projected(m: Meter): Double = dailyAvg(m) * totalDays()

    fun progress(m: Meter): Float = (consumed(m) / T_SLAB).toFloat().coerceIn(0f, 1f)

    /** Date when meter will hit a threshold at current daily average */
    fun estimatedDateToReach(m: Meter, threshold: Double): LocalDate? {
        val remaining = remainingTo(m, threshold)
        if (remaining <= 0) return null
        val avg = dailyAvg(m)
        if (avg <= 0) return null
        return LocalDate.now().plusDays((remaining / avg).toLong())
    }

    // ── Risk ──────────────────────────────────────────────────────────────────

    enum class Risk(val label: String) {
        SAFE("Safe"), WATCH("Watch"), WARNING("Warning"), DANGER("Danger"), CRITICAL("Critical")
    }

    fun risk(m: Meter): Risk {
        val u = consumed(m)
        return when {
            u < T_WATCH   -> Risk.SAFE
            u < T_WARNING -> Risk.WATCH
            u < T_DANGER  -> Risk.WARNING
            u < T_LIMIT   -> Risk.DANGER
            else           -> Risk.CRITICAL
        }
    }

    // ── Bill estimation ────────────────────────────────────────────────────────

    fun estimateBill(units: Double, isProtected: Boolean, tariff: TariffSettings): Double {
        val base = if (isProtected) {
            when {
                units <= tariff.protectedSlab1Units ->
                    units * tariff.protectedSlab1Rate
                else ->
                    (tariff.protectedSlab1Units * tariff.protectedSlab1Rate) +
                    ((units - tariff.protectedSlab1Units) * tariff.protectedSlab2Rate)
            }
        } else {
            units * tariff.nonProtectedRate
        }
        val withFpa = base + (units * tariff.fpaPerUnit)
        val withGst = withFpa * (1 + tariff.gstPercent / 100)
        val withDuty = withGst * (1 + tariff.electricityDutyPercent / 100)
        return withDuty + tariff.tvFee + tariff.meterRent
    }

    // ── Recommendations ────────────────────────────────────────────────────────

    data class Recommendation(val meterNo: String, val urgency: Risk, val msg: String)

    fun recommendations(meters: List<Meter>): List<Recommendation> {
        val recs = mutableListOf<Recommendation>()
        val m600 = meters.find { it.meterNumber == "600" }
        val m603 = meters.find { it.meterNumber == "603" }
        val m700 = meters.find { it.meterNumber == "700" }

        for (m in meters) {
            val u = consumed(m)
            when {
                u >= T_LIMIT   -> recs.add(Recommendation(m.meterNumber, Risk.CRITICAL,
                    "STOP Meter ${m.meterNumber} (${u.f()} units). Do NOT use before next cycle!"))
                u >= T_DANGER  -> recs.add(Recommendation(m.meterNumber, Risk.DANGER,
                    "DANGER: Meter ${m.meterNumber} at ${u.f()} units. Stop all non-critical load now."))
                u >= T_WARNING -> recs.add(Recommendation(m.meterNumber, Risk.WARNING,
                    "WARNING: Meter ${m.meterNumber} at ${u.f()} units. Reduce load immediately."))
                u >= T_PURPLE  -> recs.add(Recommendation(m.meterNumber, Risk.WARNING,
                    "Meter ${m.meterNumber} at ${u.f()} units. Approaching limit - consider switching load."))
                u >= T_WATCH   -> recs.add(Recommendation(m.meterNumber, Risk.WATCH,
                    "Meter ${m.meterNumber} at ${u.f()} units. Monitor closely."))
            }
        }

        if (m600 != null && m700 != null) {
            val u600 = consumed(m600)
            if (u600 >= T_SWITCH && m700.status == MeterStatus.PAUSED) {
                recs.add(Recommendation("700", Risk.WATCH,
                    "Meter 600 has reached ${u600.f()} units. Turn ON Meter 700 and shift partial load."))
            }
        }

        if (m603 != null) {
            val u603 = consumed(m603)
            val headroom = T_WARNING - u603
            val highOthers = meters.filter { it.meterNumber != "603" && consumed(it) > T_SWITCH }
            if (headroom > 20 && highOthers.isNotEmpty()) {
                recs.add(Recommendation("603", Risk.SAFE,
                    "Meter 603 has ${headroom.f()} units of headroom. Consider shifting refrigerator or steady loads here."))
            }
        }

        // Estimated dates
        for (m in meters) {
            val d175 = estimatedDateToReach(m, T_WARNING)
            val d200 = estimatedDateToReach(m, T_SLAB)
            if (d200 != null && d200.isBefore(cycleEnd())) {
                recs.add(Recommendation(m.meterNumber, Risk.DANGER,
                    "Meter ${m.meterNumber} projected to hit 200 units by $d200. Redistribute load now."))
            } else if (d175 != null) {
                recs.add(Recommendation(m.meterNumber, Risk.WATCH,
                    "Meter ${m.meterNumber} projected to reach 175 units around $d175."))
            }
        }

        if (recs.isEmpty()) recs.add(Recommendation("ALL", Risk.SAFE,
            "All meters within safe limits. Current load distribution is optimal."))

        return recs
    }

    private fun Double.f() = String.format("%.1f", this)
}
