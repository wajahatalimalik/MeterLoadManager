package com.iesco.meterloadmanager.utils

import com.iesco.meterloadmanager.data.entity.Meter
import com.iesco.meterloadmanager.data.entity.MeterStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object BillingCalculator {

    const val T_SWITCH  = 150.0
    const val T_WARNING = 180.0
    const val T_DANGER  = 190.0
    const val T_LIMIT   = 199.0
    const val T_SLAB    = 200.0

    // Returns the 13th of the current billing cycle
    fun cycleStart(date: LocalDate = LocalDate.now()): LocalDate =
        if (date.dayOfMonth >= 13) date.withDayOfMonth(13)
        else date.minusMonths(1).withDayOfMonth(13)

    // Returns the 13th of next month (cycle end)
    fun cycleEnd(date: LocalDate = LocalDate.now()): LocalDate =
        cycleStart(date).plusMonths(1)

    fun daysPassed(start: LocalDate = cycleStart()): Long =
        ChronoUnit.DAYS.between(start, LocalDate.now()).coerceAtLeast(1)

    fun daysRemaining(start: LocalDate = cycleStart()): Long =
        ChronoUnit.DAYS.between(LocalDate.now(), cycleEnd(start.plusDays(1))).coerceAtLeast(0)

    fun totalDays(start: LocalDate = cycleStart()): Long =
        ChronoUnit.DAYS.between(start, start.plusMonths(1))

    // ── Per-meter calculations ─────────────────────────────────────────────────

    fun consumed(m: Meter): Double = (m.currentReading - m.cycleStartReading).coerceAtLeast(0.0)

    fun remainingTo(m: Meter, t: Double) = (t - consumed(m)).coerceAtLeast(0.0)

    fun dailyAvg(m: Meter): Double {
        val readDate = Instant.ofEpochMilli(m.currentReadingTimestamp)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val days = ChronoUnit.DAYS.between(cycleStart(), readDate).coerceAtLeast(1)
        return consumed(m) / days
    }

    fun projected(m: Meter): Double = dailyAvg(m) * totalDays()

    fun progress(m: Meter): Float = (consumed(m) / T_SLAB).toFloat().coerceIn(0f, 1f)

    // ── Risk ──────────────────────────────────────────────────────────────────

    enum class Risk(val label: String) {
        SAFE("Safe"), WATCH("Watch"), WARNING("Warning"), DANGER("Danger"), CRITICAL("Critical")
    }

    fun risk(m: Meter): Risk {
        val u = consumed(m)
        return when {
            u < T_SWITCH  -> Risk.SAFE
            u < T_WARNING -> Risk.WATCH
            u < T_DANGER  -> Risk.WARNING
            u < T_LIMIT   -> Risk.DANGER
            else           -> Risk.CRITICAL
        }
    }

    // ── Switch recommendations ────────────────────────────────────────────────

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
                    "⛔ Meter ${m.meterNumber} CRITICAL (${u.f()} units): Stop! Do NOT use before next billing cycle."))
                u >= T_DANGER  -> recs.add(Recommendation(m.meterNumber, Risk.DANGER,
                    "🚨 Meter ${m.meterNumber} DANGER (${u.f()} units): Minimize immediately to avoid higher slab."))
                u >= T_WARNING -> recs.add(Recommendation(m.meterNumber, Risk.WARNING,
                    "⚠️ Meter ${m.meterNumber} WARNING (${u.f()} units): Reduce load now."))
                u >= T_SWITCH  -> recs.add(Recommendation(m.meterNumber, Risk.WATCH,
                    "👀 Meter ${m.meterNumber} at 150 units. Consider sharing load with other meters."))
            }
        }

        // Specific M600 → M700 trigger
        if (m600 != null && m700 != null) {
            val u600 = consumed(m600)
            if (u600 >= T_SWITCH && m700.status == MeterStatus.PAUSED) {
                recs.add(Recommendation("700", Risk.WATCH,
                    "💡 Meter 600 reached ${u600.f()} units. Turn ON Meter 700 and transfer partial load from 600."))
            }
        }

        // M603 headroom suggestion
        if (m603 != null) {
            val u603 = consumed(m603)
            val headroom = T_SWITCH - u603
            val highOthers = meters.filter { it.meterNumber != "603" && consumed(it) > T_SWITCH }
            if (headroom > 20 && highOthers.isNotEmpty()) {
                recs.add(Recommendation("603", Risk.SAFE,
                    "✅ Meter 603 has ${headroom.f()} units before threshold. Consider shifting load here."))
            }
        }

        if (recs.isEmpty()) recs.add(Recommendation("ALL", Risk.SAFE,
            "✅ All meters within safe limits. Current load distribution is optimal."))

        return recs
    }

    private fun Double.f() = String.format("%.1f", this)
}
