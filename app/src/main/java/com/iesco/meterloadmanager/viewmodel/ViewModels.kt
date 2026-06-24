package com.iesco.meterloadmanager.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.iesco.meterloadmanager.MeterApp
import com.iesco.meterloadmanager.data.entity.*
import com.iesco.meterloadmanager.utils.BillingCalculator
import com.iesco.meterloadmanager.utils.ExportManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Dashboard ──────────────────────────────────────────────────────────────────

data class MeterCard(
    val meter: Meter,
    val consumed: Double,
    val to150: Double, val to175: Double, val to199: Double,
    val dailyAvg: Double, val projected: Double,
    val risk: BillingCalculator.Risk, val progress: Float,
    val estBill: Double,
    val date175: LocalDate?, val date200: LocalDate?,
    val slabInfo: BillingCalculator.SlabInfo
)

data class DashState(
    val cycleStart: String = "", val cycleEnd: String = "",
    val daysPassed: Int = 0, val daysRemaining: Int = 0,
    val cards: List<MeterCard> = emptyList(),
    val totalConsumed: Double = 0.0, val totalDailyAvg: Double = 0.0,
    val totalEstBill: Double = 0.0,
    val recs: List<BillingCalculator.Recommendation> = emptyList(),
    val unreadAlerts: Int = 0
)

class DashboardVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo

    val state: StateFlow<DashState> =
        combine(repo.allMeters, repo.tariff, repo.unreadCount) { meters, tariff, unread ->
            val t = tariff ?: TariffSettings()
            val today = LocalDate.now()
            val cs = BillingCalculator.cycleStart(today)
            val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy")
            val cards = meters.map { m ->
                val consumed = BillingCalculator.consumed(m)
                val proj = BillingCalculator.projected(m)
                val isProtected = m.meterNumber != "700"
                MeterCard(
                    meter = m, consumed = consumed,
                    to150 = BillingCalculator.remainingTo(m, 150.0),
                    to175 = BillingCalculator.remainingTo(m, 175.0),
                    to199 = BillingCalculator.remainingTo(m, 199.0),
                    dailyAvg = BillingCalculator.dailyAvg(m),
                    projected = proj,
                    risk = BillingCalculator.risk(m),
                    progress = BillingCalculator.progress(m),
                    estBill = BillingCalculator.estimateBill(proj, isProtected, t),
                    date175 = BillingCalculator.estimatedDateToReach(m, 175.0),
                    date200 = BillingCalculator.estimatedDateToReach(m, 200.0),
                    slabInfo = BillingCalculator.slabInfo(consumed, isProtected, t)
                )
            }
            DashState(
                cycleStart = cs.format(fmt),
                cycleEnd = BillingCalculator.cycleEnd(today).format(fmt),
                daysPassed = BillingCalculator.daysPassed(cs).toInt(),
                daysRemaining = BillingCalculator.daysRemaining(cs).toInt(),
                cards = cards,
                totalConsumed = cards.sumOf { it.consumed },
                totalDailyAvg = cards.sumOf { it.dailyAvg },
                totalEstBill = cards.sumOf { it.estBill },
                recs = BillingCalculator.recommendations(meters, t),
                unreadAlerts = unread
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashState())

    init { viewModelScope.launch { repo.seedIfNeeded() } }
}

// ── Reading ────────────────────────────────────────────────────────────────────

class ReadingVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo
    val allReadings = repo.allReadings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(meterNo: String, reading: Double, ts: Long, status: MeterStatus, notes: String?) {
        viewModelScope.launch { repo.addReading(meterNo, reading, ts, status, notes) }
    }
    fun delete(id: Long, meterNo: String) { viewModelScope.launch { repo.deleteReading(id, meterNo) } }
    fun update(r: ManualReading) { viewModelScope.launch { repo.updateReading(r) } }
}

// ── History ────────────────────────────────────────────────────────────────────

data class MonthGroup(
    val monthLabel: String, val year: Int, val monthInt: Int,
    val records: List<MonthlyBillHistory>,
    val totalUnits: Int, val totalBill: Double, val avgCostPerUnit: Double,
    val isExpanded: Boolean = true
)

class HistoryVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo
    val selectedMeter = MutableStateFlow<String?>(null)
    val selectedYear  = MutableStateFlow<Int?>(null)
    val query = MutableStateFlow("")
    val expandedMonths = MutableStateFlow<Set<String>>(emptySet())

    val monthGroups: StateFlow<List<MonthGroup>> =
        combine(repo.allHistory, selectedMeter, selectedYear, query, expandedMonths) { hist, meter, year, q, expanded ->
            val filtered = hist
                .filter { meter == null || it.meterNumber == meter }
                .filter { year == null || it.billingYear == year }
                .filter { q.isBlank() || it.billingMonth.contains(q, true) || it.notes?.contains(q, true) == true }
            filtered.groupBy { it.billingMonth }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, List<MonthlyBillHistory>>> {
                    it.value.first().billingYear }.thenByDescending { it.value.first().billingMonthInt })
                .map { (month, records) ->
                    val total = records.sumOf { it.unitsConsumed }
                    val bill = records.sumOf { it.billAmount }
                    MonthGroup(
                        monthLabel = month, year = records.first().billingYear,
                        monthInt = records.first().billingMonthInt,
                        records = records.sortedBy { it.meterNumber },
                        totalUnits = total, totalBill = bill,
                        avgCostPerUnit = if (total > 0) bill / total else 0.0,
                        isExpanded = expanded.isEmpty() || month in expanded
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableYears: StateFlow<List<Int>> = repo.allHistory.map { hist ->
        hist.map { it.billingYear }.distinct().sortedDescending()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleMonth(monthLabel: String) {
        val cur = expandedMonths.value.toMutableSet()
        if (monthLabel in cur) cur.remove(monthLabel) else cur.add(monthLabel)
        expandedMonths.value = cur
    }
    fun add(h: MonthlyBillHistory)    { viewModelScope.launch { repo.addHistory(h) } }
    fun update(h: MonthlyBillHistory) { viewModelScope.launch { repo.updateHistory(h) } }
    fun delete(h: MonthlyBillHistory) { viewModelScope.launch { repo.deleteHistory(h) } }
}

// ── Appliances ─────────────────────────────────────────────────────────────────

data class ApplianceState(
    val appliances: List<Appliance> = emptyList(),
    val totalDailyUnits: Double = 0.0,
    val byMeter: Map<String, Double> = emptyMap()
)

class ApplianceVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo
    val filterMeter    = MutableStateFlow<String?>(null)
    val filterCategory = MutableStateFlow<ApplianceCategory?>(null)

    val state: StateFlow<ApplianceState> =
        combine(repo.allAppliances, filterMeter, filterCategory) { all, meter, cat ->
            val filtered = all
                .filter { meter == null || it.assignedMeter == meter }
                .filter { cat == null || it.category == cat }
            val active = all.filter { it.isActive }
            ApplianceState(
                appliances = filtered,
                totalDailyUnits = active.sumOf { it.dailyUnits },
                byMeter = active.groupBy { it.assignedMeter }
                    .mapValues { (_, l) -> l.sumOf { it.dailyUnits } }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApplianceState())

    fun add(a: Appliance)    { viewModelScope.launch { repo.addAppliance(a) } }
    fun update(a: Appliance) { viewModelScope.launch { repo.updateAppliance(a) } }
    fun delete(a: Appliance) { viewModelScope.launch { repo.deleteAppliance(a) } }
    fun toggle(a: Appliance) { viewModelScope.launch { repo.updateAppliance(a.copy(isActive = !a.isActive)) } }
}

// ── Switch ─────────────────────────────────────────────────────────────────────

class SwitchVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo
    val meters = repo.allMeters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val events = repo.allSwitchEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun changeStatus(meterNo: String, newStatus: MeterStatus, prevStatus: MeterStatus, notes: String?) {
        viewModelScope.launch { repo.changeMeterStatus(meterNo, newStatus, prevStatus, notes) }
    }
}

// ── Alerts / Notifications ─────────────────────────────────────────────────────

class AlertsVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo
    val notifications = repo.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unreadCount = repo.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun generateAlerts(meters: List<Meter>, tariff: TariffSettings = TariffSettings()) {
        viewModelScope.launch {
            val recs = BillingCalculator.recommendations(meters, tariff)
            for (rec in recs) {
                if (rec.urgency != BillingCalculator.Risk.SAFE) {
                    val cat = when (rec.urgency) {
                        BillingCalculator.Risk.CRITICAL -> NotificationCategory.CRITICAL
                        BillingCalculator.Risk.DANGER   -> NotificationCategory.WARNING
                        BillingCalculator.Risk.WARNING  -> NotificationCategory.WARNING
                        BillingCalculator.Risk.WATCH    -> NotificationCategory.WATCH
                        else -> NotificationCategory.INFORMATION
                    }
                    repo.addNotification(AppNotification(
                        title = "Meter ${rec.meterNo}: ${rec.urgency.label}",
                        message = rec.msg + if (rec.reason.isNotBlank()) "\n\n${rec.reason}" else "",
                        category = cat, meterNumber = rec.meterNo
                    ))
                }
            }
        }
    }

    fun markRead(id: Long)             { viewModelScope.launch { repo.markRead(id) } }
    fun dismiss(id: Long)              { viewModelScope.launch { repo.dismissNotification(id) } }
    fun snooze(id: Long, hours: Int)   { viewModelScope.launch { repo.snoozeNotification(id, hours) } }
    fun delete(n: AppNotification)     { viewModelScope.launch { repo.deleteNotification(n) } }
    fun clearDismissed()               { viewModelScope.launch { repo.clearDismissed() } }
}

// ── Analytics ──────────────────────────────────────────────────────────────────

data class YearComparison(
    val monthLabel: String, val currentYear: Int, val prevYear: Int,
    val currentUnits: Map<String, Int>, val prevUnits: Map<String, Int>,
    val currentBill: Map<String, Double>, val prevBill: Map<String, Double>,
    val unitsDiff: Map<String, Int>, val billDiff: Map<String, Double>,
    val pctChange: Map<String, Double>
)

data class AnalyticsState(
    val byMeter: Map<String, List<MonthlyBillHistory>> = emptyMap(),
    val overLimit: List<MonthlyBillHistory> = emptyList(),
    val currentUnits: Map<String, Double> = emptyMap(),
    val projectedUnits: Map<String, Double> = emptyMap(),
    val avgUnits: Map<String, Double> = emptyMap(),
    val avgBill: Map<String, Double> = emptyMap(),
    val costPerUnit: Map<String, Double> = emptyMap(),
    val applianceByMeter: Map<String, Double> = emptyMap(),
    val topAppliances: List<Appliance> = emptyList(),
    val estBillByMeter: Map<String, Double> = emptyMap(),
    val yearComparison: YearComparison? = null
)

class AnalyticsVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo

    val state: StateFlow<AnalyticsState> =
        combine(repo.allHistory, repo.allMeters, repo.activeAppliances, repo.tariff) { hist, meters, apps, tariff ->
            val t = tariff ?: TariffSettings()
            val byMeter = mapOf(
                "600" to hist.filter { it.meterNumber == "600" }.sortedWith(compareBy({ it.billingYear },{ it.billingMonthInt })),
                "603" to hist.filter { it.meterNumber == "603" }.sortedWith(compareBy({ it.billingYear },{ it.billingMonthInt })),
                "700" to hist.filter { it.meterNumber == "700" }.sortedWith(compareBy({ it.billingYear },{ it.billingMonthInt }))
            )
            fun avgU(m: String) = byMeter[m]?.map { it.unitsConsumed.toDouble() }?.average() ?: 0.0
            fun avgB(m: String) = byMeter[m]?.map { it.billAmount }?.average() ?: 0.0
            fun cpu(m: String): Double {
                val h = byMeter[m] ?: return 0.0
                val u = h.sumOf { it.unitsConsumed }; val b = h.sumOf { it.billAmount }
                return if (u > 0) b / u else 0.0
            }
            val today = LocalDate.now()
            val curMonth = today.monthValue; val curYear = today.year; val prevYear = curYear - 1
            val monthNames = arrayOf("","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            val curRec  = hist.filter { it.billingMonthInt == curMonth && it.billingYear == curYear }
            val prevRec = hist.filter { it.billingMonthInt == curMonth && it.billingYear == prevYear }
            val mnos = listOf("600","603","700")
            val curU  = mnos.associateWith { m -> curRec.find  { it.meterNumber == m }?.unitsConsumed ?: 0 }
            val prevU = mnos.associateWith { m -> prevRec.find { it.meterNumber == m }?.unitsConsumed ?: 0 }
            val curB  = mnos.associateWith { m -> curRec.find  { it.meterNumber == m }?.billAmount ?: 0.0 }
            val prevB = mnos.associateWith { m -> prevRec.find { it.meterNumber == m }?.billAmount ?: 0.0 }
            val yearComp = if (curRec.isNotEmpty() || prevRec.isNotEmpty()) YearComparison(
                monthLabel="${monthNames[curMonth]} $curYear vs ${monthNames[curMonth]} $prevYear",
                currentYear=curYear, prevYear=prevYear,
                currentUnits=curU, prevUnits=prevU, currentBill=curB, prevBill=prevB,
                unitsDiff=mnos.associateWith { m -> (curU[m]?:0)-(prevU[m]?:0) },
                billDiff =mnos.associateWith { m -> (curB[m]?:0.0)-(prevB[m]?:0.0) },
                pctChange=mnos.associateWith { m -> val p=prevU[m]?:0; if(p==0) 0.0 else ((curU[m]?:0)-p).toDouble()/p*100 }
            ) else null

            AnalyticsState(
                byMeter=byMeter, overLimit=hist.filter { it.isOverLimit },
                currentUnits=meters.associate { it.meterNumber to BillingCalculator.consumed(it) },
                projectedUnits=meters.associate { it.meterNumber to BillingCalculator.projected(it) },
                avgUnits=mapOf("600" to avgU("600"),"603" to avgU("603"),"700" to avgU("700")),
                avgBill=mapOf("600" to avgB("600"),"603" to avgB("603"),"700" to avgB("700")),
                costPerUnit=mapOf("600" to cpu("600"),"603" to cpu("603"),"700" to cpu("700")),
                applianceByMeter=apps.groupBy{it.assignedMeter}.mapValues{(_,l)->l.sumOf{it.dailyUnits}},
                topAppliances=apps.sortedByDescending{it.dailyUnits}.take(5),
                estBillByMeter=meters.associate { m -> m.meterNumber to
                    BillingCalculator.estimateBill(BillingCalculator.projected(m), m.meterNumber!="700", t) },
                yearComparison=yearComp
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsState())
}

// ── Export ─────────────────────────────────────────────────────────────────────

class ExportVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo
    private val ctx: Context = app.applicationContext
    private val _intent = MutableLiveData<Intent?>()
    val shareIntent: LiveData<Intent?> = _intent
    private val _status = MutableLiveData("")
    val status: LiveData<String> = _status

    private fun doExport(block: suspend () -> Pair<java.io.File, String>) {
        viewModelScope.launch {
            try {
                val (file, mime) = block()
                _intent.value = ExportManager.share(ctx, file, mime)
                _status.value = "Exported: ${file.name}"
            } catch (e: Exception) { _status.value = "Error: ${e.message}" }
        }
    }
    fun exportCsv()    = doExport { ExportManager.csv(ctx, repo.allMeters.first(), repo.allHistory.first(), repo.allReadings.first(), repo.allSwitchEvents.first(), repo.allAppliances.first()) to "text/csv" }
    fun exportJson()   = doExport { ExportManager.json(ctx, repo.allMeters.first(), repo.allHistory.first(), repo.allReadings.first(), repo.allSwitchEvents.first(), repo.allAppliances.first()) to "application/json" }
    fun exportReport() = doExport { ExportManager.report(ctx, repo.allMeters.first(), repo.allHistory.first(), repo.allReadings.first(), repo.allSwitchEvents.first(), repo.allAppliances.first()) to "text/plain" }
    fun clearIntent()  { _intent.value = null }
}
