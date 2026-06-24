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
    private val ts   = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
    private val disp = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

    fun csv(ctx: Context, meters: List<Meter>, history: List<MonthlyBillHistory>,
            readings: List<ManualReading>, events: List<SwitchEvent>,
            appliances: List<Appliance>): File {
        val f = File(ctx.cacheDir, "MLM_${ts.format(Date())}.csv")
        FileWriter(f).use { w ->
            w.appendLine("=== METERS ===")
            w.appendLine("Meter,Ref,CycleStart,Current,Status,Consumed")
            meters.forEach { m ->
                w.appendLine("${m.meterNumber},${m.referenceNo},${m.cycleStartReading},${m.currentReading},${m.status},${"%.2f".format(BillingCalculator.consumed(m))}")
            }
            w.appendLine()
            w.appendLine("=== MONTHLY HISTORY ===")
            w.appendLine("Meter,Month,Units,Bill,Payment,PrevReading,PresentReading,OverLimit")
            history.sortedWith(compareBy({it.meterNumber},{it.billingYear},{it.billingMonthInt})).forEach {
                w.appendLine("${it.meterNumber},${it.billingMonth},${it.unitsConsumed},${it.billAmount},${it.paymentMade?:""},${it.previousReading?:""},${it.presentReading?:""},${it.isOverLimit}")
            }
            w.appendLine()
            w.appendLine("=== MANUAL READINGS ===")
            w.appendLine("Meter,Reading,Timestamp,Status,Notes")
            readings.forEach {
                w.appendLine("${it.meterNumber},${it.reading},\"${disp.format(Date(it.timestamp))}\",${it.status},\"${it.notes?:""}\"")
            }
            w.appendLine()
            w.appendLine("=== SWITCH EVENTS ===")
            w.appendLine("Meter,From,To,Timestamp,Trigger,Units,Notes")
            events.forEach {
                w.appendLine("${it.meterNumber},${it.previousStatus},${it.newStatus},\"${disp.format(Date(it.timestamp))}\",${it.triggeredBy},${it.unitsAtSwitch?:""},\"${it.notes?:""}\"")
            }
            w.appendLine()
            w.appendLine("=== APPLIANCES ===")
            w.appendLine("Name,Meter,Category,Qty,Watts,Hours/Day,DailyUnits,Active,Notes")
            appliances.forEach {
                w.appendLine("\"${it.name}\",${it.assignedMeter},${it.category},${it.quantity},${it.wattagePerUnit},${it.dailyHours},${"%.3f".format(it.dailyUnits)},${it.isActive},\"${it.notes?:""}\"")
            }
        }
        return f
    }

    fun json(ctx: Context, meters: List<Meter>, history: List<MonthlyBillHistory>,
             readings: List<ManualReading>, events: List<SwitchEvent>,
             appliances: List<Appliance>): File {
        val f = File(ctx.cacheDir, "MLM_backup_${ts.format(Date())}.json")
        val data = mapOf("exportedAt" to disp.format(Date()), "meters" to meters,
            "history" to history, "readings" to readings, "switchEvents" to events,
            "appliances" to appliances)
        FileWriter(f).use { it.write(GsonBuilder().setPrettyPrinting().create().toJson(data)) }
        return f
    }

    fun report(ctx: Context, meters: List<Meter>, history: List<MonthlyBillHistory>,
               readings: List<ManualReading>, events: List<SwitchEvent>,
               appliances: List<Appliance>): File {
        val f = File(ctx.cacheDir, "MLM_report_${ts.format(Date())}.txt")
        val sb = StringBuilder()
        val line = "=".repeat(58)
        sb.appendLine(line)
        sb.appendLine("    METER LOAD MANAGER – FULL REPORT")
        sb.appendLine("    ${disp.format(Date())}")
        sb.appendLine(line)
        sb.appendLine()
        sb.appendLine("BILLING CYCLE: ${BillingCalculator.cycleStart()} → ${BillingCalculator.cycleEnd()}")
        sb.appendLine("Days passed: ${BillingCalculator.daysPassed()}   Remaining: ${BillingCalculator.daysRemaining()}")
        sb.appendLine()
        sb.appendLine(line); sb.appendLine("METER STATUS"); sb.appendLine(line)
        meters.forEach { m ->
            val c = BillingCalculator.consumed(m)
            sb.appendLine()
            sb.appendLine("  Meter ${m.meterNumber}  [${m.status}]  Risk: ${BillingCalculator.risk(m).label}")
            sb.appendLine("  Ref: ${m.referenceNo} | Purpose: ${m.purpose}")
            sb.appendLine("  Cycle Start: ${m.cycleStartReading}  Current: ${m.currentReading}")
            sb.appendLine("  Consumed: ${"%.2f".format(c)} units")
            sb.appendLine("  Remaining to 175: ${"%.1f".format(BillingCalculator.remainingTo(m,175.0))}")
            sb.appendLine("  Remaining to 199: ${"%.1f".format(BillingCalculator.remainingTo(m,199.0))}")
            sb.appendLine("  Projected: ${"%.1f".format(BillingCalculator.projected(m))} units")
        }
        sb.appendLine()
        sb.appendLine(line); sb.appendLine("APPLIANCES"); sb.appendLine(line)
        listOf("600","603","700").forEach { meter ->
            val list = appliances.filter { it.assignedMeter == meter && it.isActive }
            if (list.isNotEmpty()) {
                sb.appendLine("\n  Meter $meter: ${"%.2f".format(list.sumOf{it.dailyUnits})} units/day")
                list.forEach { sb.appendLine("    - ${it.name}: ${"%.3f".format(it.dailyUnits)} u/day") }
            }
        }
        sb.appendLine()
        sb.appendLine(line); sb.appendLine("MONTHLY HISTORY"); sb.appendLine(line)
        listOf("600","603","700").forEach { meter ->
            sb.appendLine("\n  Meter $meter")
            history.filter { it.meterNumber == meter }
                .sortedWith(compareBy({it.billingYear},{it.billingMonthInt}))
                .forEach { h ->
                    sb.appendLine("  %-10s  %3d units  Rs %7.0f  ${if(h.isOverLimit)"[OVER 200]" else ""}".format(
                        h.billingMonth, h.unitsConsumed, h.billAmount))
                }
        }
        sb.appendLine()
        sb.appendLine(line); sb.appendLine("SWITCH EVENTS"); sb.appendLine(line)
        if (events.isEmpty()) sb.appendLine("  None.")
        events.sortedByDescending { it.timestamp }.forEach { e ->
            sb.appendLine("  [${disp.format(Date(e.timestamp))}] M${e.meterNumber}: ${e.previousStatus}→${e.newStatus}")
            e.notes?.let { sb.appendLine("    $it") }
        }
        sb.appendLine(); sb.appendLine(line); sb.appendLine("END"); sb.appendLine(line)
        f.writeText(sb.toString())
        return f
    }

    fun share(ctx: Context, file: File, mime: String): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
