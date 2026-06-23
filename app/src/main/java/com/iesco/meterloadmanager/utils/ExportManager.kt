package com.iesco.meterloadmanager.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.iesco.meterloadmanager.data.entity.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object ExportManager {

    private val ts  = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
    private val disp = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

    fun csv(ctx: Context, meters: List<Meter>, history: List<MonthlyBillHistory>,
            readings: List<ManualReading>, events: List<SwitchEvent>): File {
        val f = File(ctx.cacheDir, "MLM_export_${ts.format(Date())}.csv")
        FileWriter(f).use { w ->
            w.appendLine("=== METERS ===")
            w.appendLine("Meter,Ref,Purpose,CycleStart,CurrentReading,Status,ConsumedUnits")
            meters.forEach { m ->
                val c = BillingCalculator.consumed(m)
                w.appendLine("${m.meterNumber},${m.referenceNo},\"${m.purpose}\",${m.cycleStartReading},${m.currentReading},${m.status},%.2f".trimEnd().format(c))
            }
            w.appendLine()
            w.appendLine("=== MONTHLY HISTORY ===")
            w.appendLine("Meter,Month,Units,Bill(Rs),Payment,PrevReading,PresentReading,OverLimit")
            history.sortedWith(compareBy({it.meterNumber},{it.billingYear},{it.billingMonthInt})).forEach {
                w.appendLine("${it.meterNumber},${it.billingMonth},${it.unitsConsumed},${it.billAmount},${it.paymentMade?:""},${it.previousReading?:""},${it.presentReading?:""},${it.isOverLimit}")
            }
            w.appendLine()
            w.appendLine("=== MANUAL READINGS ===")
            w.appendLine("Meter,Reading,Timestamp,Status,CycleStart,Notes")
            readings.forEach {
                w.appendLine("${it.meterNumber},${it.reading},\"${disp.format(Date(it.timestamp))}\",${it.status},${it.billingCycleStart},\"${it.notes?:""}\"")
            }
            w.appendLine()
            w.appendLine("=== SWITCH EVENTS ===")
            w.appendLine("Meter,From,To,Timestamp,Trigger,Units,Notes")
            events.forEach {
                w.appendLine("${it.meterNumber},${it.previousStatus},${it.newStatus},\"${disp.format(Date(it.timestamp))}\",${it.triggeredBy},${it.unitsAtSwitch?:""},\"${it.notes?:""}\"")
            }
        }
        return f
    }

    fun json(ctx: Context, meters: List<Meter>, history: List<MonthlyBillHistory>,
             readings: List<ManualReading>, events: List<SwitchEvent>): File {
        val f = File(ctx.cacheDir, "MLM_backup_${ts.format(Date())}.json")
        val data = mapOf("exportedAt" to disp.format(Date()), "meters" to meters,
            "history" to history, "readings" to readings, "switchEvents" to events)
        FileWriter(f).use { it.write(GsonBuilder().setPrettyPrinting().create().toJson(data)) }
        return f
    }

    fun report(ctx: Context, meters: List<Meter>, history: List<MonthlyBillHistory>,
               readings: List<ManualReading>, events: List<SwitchEvent>): File {
        val f = File(ctx.cacheDir, "MLM_report_${ts.format(Date())}.txt")
        val sb = StringBuilder()
        val line = "=".repeat(58)
        sb.appendLine(line)
        sb.appendLine("    METER LOAD MANAGER – FULL REPORT")
        sb.appendLine("    Generated: ${disp.format(Date())}")
        sb.appendLine(line)
        sb.appendLine()

        val cs = BillingCalculator.cycleStart()
        sb.appendLine("BILLING CYCLE  $cs → ${BillingCalculator.cycleEnd()}")
        sb.appendLine("Days passed: ${BillingCalculator.daysPassed(cs)}   Remaining: ${BillingCalculator.daysRemaining(cs)}")
        sb.appendLine()

        sb.appendLine(line); sb.appendLine("METER STATUS"); sb.appendLine(line)
        meters.forEach { m ->
            val c = BillingCalculator.consumed(m)
            val r = BillingCalculator.risk(m)
            sb.appendLine()
            sb.appendLine("  Meter ${m.meterNumber}  [${m.status}]  Risk: ${r.label}")
            sb.appendLine("  Reference : ${m.referenceNo}")
            sb.appendLine("  Purpose   : ${m.purpose}")
            sb.appendLine("  Cycle Start : ${m.cycleStartReading}   Current : ${m.currentReading}")
            sb.appendLine("  Consumed  : %.2f".format(c) + "   Daily avg: %.2f/day".format(BillingCalculator.dailyAvg(m)))
            sb.appendLine("  Projected : %.1f units".format(BillingCalculator.projected(m)))
            sb.appendLine("  → 150: %.1f  → 180: %.1f  → 190: %.1f  → 199: %.1f".format(
                BillingCalculator.remainingTo(m,150.0), BillingCalculator.remainingTo(m,180.0),
                BillingCalculator.remainingTo(m,190.0), BillingCalculator.remainingTo(m,199.0)))
        }

        sb.appendLine(); sb.appendLine(line); sb.appendLine("MONTHLY HISTORY"); sb.appendLine(line)
        listOf("600","603","700").forEach { meter ->
            sb.appendLine(); sb.appendLine("  Meter $meter")
            sb.appendLine("  %-10s %6s %12s %8s".format("Month","Units","Bill (Rs)","Flag"))
            history.filter { it.meterNumber == meter }
                .sortedWith(compareBy({it.billingYear},{it.billingMonthInt}))
                .forEach { h ->
                    sb.appendLine("  %-10s %6d %12.0f %8s".format(
                        h.billingMonth, h.unitsConsumed, h.billAmount,
                        if (h.isOverLimit) "⚠️200+" else ""))
                }
        }

        sb.appendLine(); sb.appendLine(line); sb.appendLine("SWITCH EVENTS"); sb.appendLine(line)
        if (events.isEmpty()) sb.appendLine("  None recorded.")
        events.sortedByDescending { it.timestamp }.forEach { e ->
            sb.appendLine("  [${disp.format(Date(e.timestamp))}] M${e.meterNumber}: ${e.previousStatus}→${e.newStatus} (${e.triggeredBy})")
            e.notes?.let { sb.appendLine("    Notes: $it") }
        }
        sb.appendLine(); sb.appendLine(line); sb.appendLine("END OF REPORT"); sb.appendLine(line)
        f.writeText(sb.toString())
        return f
    }

    fun share(ctx: Context, file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
