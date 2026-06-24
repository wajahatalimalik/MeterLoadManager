package com.iesco.meterloadmanager.utils

import com.iesco.meterloadmanager.data.entity.Meter
import com.iesco.meterloadmanager.data.entity.MeterStatus
import com.iesco.meterloadmanager.data.entity.TariffSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object BillingCalculator {

    const val T_WATCH   = 140.0
    const val T_SWITCH  = 150.0
    const val T_SOFT    = 175.0
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

    fun estimatedDateToReach(m: Meter, threshold: Double): LocalDate? {
        val remaining = remainingTo(m, threshold)
        if (remaining <= 0) return null
        val avg = dailyAvg(m)
        if (avg <= 0) return null
        return LocalDate.now().plusDays((remaining / avg).toLong())
    }

    fun daysUntil(m: Meter, threshold: Double): Double? {
        val remaining = remainingTo(m, threshold)
        if (remaining <= 0) return null
        val avg = dailyAvg(m)
        if (avg <= 0) return null
        return remaining / avg
    }

    // ── Slab info ──────────────────────────────────────────────────────────────

    data class SlabInfo(
        val label: String,          // "Protected 0-100 Units"
        val rate: Double,           // Rs/unit for current slab
        val isProtected: Boolean,
        val slabRange: String       // "0-100" or "101-200" etc
    )

    fun slabInfo(units: Double, isProtected: Boolean, tariff: TariffSettings): SlabInfo {
        return if (isProtected) {
            when {
                units <= tariff.protectedSlab1Units -> SlabInfo(
                    label = "Protected 0-${tariff.protectedSlab1Units} Units",
                    rate  = tariff.protectedSlab1Rate,
                    isProtected = true,
                    slabRange = "0-${tariff.protectedSlab1Units}"
                )
                else -> SlabInfo(
                    label = "Protected ${tariff.protectedSlab1Units+1}-${tariff.protectedSlab1Units+tariff.protectedSlab2Units} Units",
                    rate  = tariff.protectedSlab2Rate,
                    isProtected = true,
                    slabRange = "${tariff.protectedSlab1Units+1}-${tariff.protectedSlab1Units+tariff.protectedSlab2Units}"
                )
            }
        } else {
            SlabInfo(
                label = "Non-Protected (High Tariff)",
                rate  = tariff.nonProtectedRate,
                isProtected = false,
                slabRange = "All Units"
            )
        }
    }

    // ── Bill estimation ────────────────────────────────────────────────────────

    fun estimateBill(units: Double, isProtected: Boolean, tariff: TariffSettings): Double {
        if (units <= 0) return 0.0
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
        val withFpa   = base + (units * tariff.fpaPerUnit)
        val withGst   = withFpa * (1 + tariff.gstPercent / 100)
        val withDuty  = withGst * (1 + tariff.electricityDutyPercent / 100)
        return withDuty + tariff.tvFee + tariff.meterRent
    }

    // ── Risk ──────────────────────────────────────────────────────────────────

    enum class Risk(val label: String) {
        SAFE("Safe"), WATCH("Watch"), WARNING("Warning"), DANGER("Danger"), CRITICAL("Critical")
    }

    fun risk(m: Meter): Risk {
        val u = consumed(m)
        return when {
            u < T_WATCH  -> Risk.SAFE
            u < T_SOFT   -> Risk.WATCH
            u < T_DANGER -> Risk.WARNING
            u < T_LIMIT  -> Risk.DANGER
            else          -> Risk.CRITICAL
        }
    }

    // ── Dynamic recommendation engine ─────────────────────────────────────────

    data class Recommendation(
        val meterNo: String,
        val urgency: Risk,
        val msg: String,
        val reason: String = "",
        val expectedImpact: String = ""
    )

    fun recommendations(
        meters: List<Meter>,
        tariff: TariffSettings = TariffSettings()
    ): List<Recommendation> {
        val recs = mutableListOf<Recommendation>()
        val remaining = daysRemaining()
        val totalDays = totalDays()

        // Per-meter threshold alerts
        for (m in meters) {
            val u = consumed(m)
            val avg = dailyAvg(m)
            val proj = projected(m)
            val d175 = daysUntil(m, T_SOFT)
            val d200 = daysUntil(m, T_SLAB)
            val headroom175 = remainingTo(m, T_SOFT)

            when {
                u >= T_LIMIT -> recs.add(Recommendation(m.meterNumber, Risk.CRITICAL,
                    "STOP: Meter ${m.meterNumber} at ${u.f()} units — do NOT use before next cycle.",
                    "Unit consumption has exceeded the hard limit of ${T_LIMIT}.",
                    "Continued use will lock meter into higher tariff slab for 6 months."))

                u >= T_DANGER -> recs.add(Recommendation(m.meterNumber, Risk.DANGER,
                    "DANGER: Meter ${m.meterNumber} at ${u.f()} units. Stop all non-critical load immediately.",
                    "Only ${remainingTo(m, T_LIMIT).f()} units remain before hard limit.",
                    "Projected to reach 200 units in ${d200?.f() ?: "< 1"} days at current avg ${avg.f()} u/day."))

                u >= T_SOFT -> recs.add(Recommendation(m.meterNumber, Risk.WARNING,
                    "WARNING: Meter ${m.meterNumber} at ${u.f()} units. Reduce load now.",
                    "${remainingTo(m, T_LIMIT).f()} units remain. ${remaining} days left in cycle.",
                    "At ${avg.f()} u/day, projected end-cycle: ${proj.f()} units."))

                u >= T_SWITCH -> {
                    if (d175 != null && d175 < remaining) {
                        recs.add(Recommendation(m.meterNumber, Risk.WATCH,
                            "Meter ${m.meterNumber} at ${u.f()} units. Transfer load before reaching 175 units.",
                            "Projected to reach soft limit in ${d175.f()} days.",
                            "Shifting ${(avg * 0.4).f()} u/day to another meter could extend safe window."))
                    }
                }

                u >= T_WATCH -> recs.add(Recommendation(m.meterNumber, Risk.WATCH,
                    "Meter ${m.meterNumber} at ${u.f()} units — entering Watch zone.",
                    "Daily average is ${avg.f()} units. ${headroom175.f()} units remain to soft limit.",
                    "Monitor closely. Projected end-cycle: ${proj.f()} units."))
            }
        }

        // Cross-meter switching opportunities
        val m600 = meters.find { it.meterNumber == "600" }
        val m603 = meters.find { it.meterNumber == "603" }
        val m700 = meters.find { it.meterNumber == "700" }

        if (m600 != null && m700 != null) {
            val u600 = consumed(m600); val u700 = consumed(m700)
            val h700 = remainingTo(m700, T_SOFT)
            val h600 = remainingTo(m600, T_SOFT)
            if (u600 >= T_SWITCH && m700.status == MeterStatus.PAUSED && h700 > 30) {
                recs.add(Recommendation("700", Risk.WATCH,
                    "Turn ON Meter 700 — transfer primary load from Meter 600.",
                    "Meter 600 has consumed ${u600.f()} units. Meter 700 has ${h700.f()} units of safe capacity.",
                    "Distributing load will reduce Meter 600 projected units by approximately ${(dailyAvg(m600) * remaining * 0.5).f()}."))
            }
            if (u600 > u700 + 40 && m700.status != MeterStatus.PAUSED) {
                recs.add(Recommendation("700", Risk.SAFE,
                    "Consider shifting more load to Meter 700 — it has more remaining capacity.",
                    "Meter 600: ${u600.f()} units  |  Meter 700: ${u700.f()} units.",
                    "Balancing load can keep both meters under 175 units."))
            }
        }

        if (m603 != null) {
            val u603 = consumed(m603)
            val h603 = remainingTo(m603, T_SOFT)
            val highMeters = meters.filter { it.meterNumber != "603" && consumed(it) > T_SWITCH }
            if (h603 > 30 && highMeters.isNotEmpty()) {
                recs.add(Recommendation("603", Risk.SAFE,
                    "Meter 603 has ${h603.f()} units of headroom — shift steady loads here.",
                    "Refrigerator, lighting, or fan load can be shared through Meter 603.",
                    "Redistributing ~${(h603 * 0.3).f()} units to Meter 603 optimises overall load."))
            }
        }

        // Underutilisation alert
        for (m in meters) {
            if (m.status != MeterStatus.PAUSED) {
                val u = consumed(m)
                val safeCapacity = remainingTo(m, T_SOFT)
                if (u < 20 && remaining < 15) {
                    recs.add(Recommendation(m.meterNumber, Risk.SAFE,
                        "Meter ${m.meterNumber} is significantly underutilised — consider shifting load.",
                        "Only ${u.f()} units consumed with $remaining days remaining.",
                        "Absorbing ${safeCapacity.f()} more units before soft limit."))
                }
            }
        }

        if (recs.isEmpty()) recs.add(Recommendation("ALL", Risk.SAFE,
            "All meters within safe limits. Load distribution is optimal.",
            "No thresholds are at risk based on current daily averages.",
            "Continue monitoring. Next review when any meter reaches ${T_WATCH} units."))

        return recs.sortedByDescending { it.urgency.ordinal }
    }

    private fun Double.f() = String.format("%.1f", this)
}
