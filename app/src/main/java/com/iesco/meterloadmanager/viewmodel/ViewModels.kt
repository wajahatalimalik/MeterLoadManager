package com.iesco.meterloadmanager.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.iesco.meterloadmanager.MeterApp
import com.iesco.meterloadmanager.data.entity.ManualReading
import com.iesco.meterloadmanager.data.entity.MeterStatus
import com.iesco.meterloadmanager.data.entity.MonthlyBillHistory
import com.iesco.meterloadmanager.data.entity.SwitchEvent
import com.iesco.meterloadmanager.utils.BillingCalculator
import com.iesco.meterloadmanager.utils.ExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Dashboard ──────────────────────────────────────────────────────────────────

data class MeterCard(
    val meter: com.iesco.meterloadmanager.data.entity.Meter,
    val consumed: Double,
    val to150: Double,
    val to180: Double,
    val to190: Double,
    val to199: Double,
    val dailyAvg: Double,
    val projected: Double,
    val risk: BillingCalculator.Risk,
    val progress: Float
)

data class DashState(
    val cycleStart: String = "",
    val cycleEnd: String = "",
    val daysPassed: Int = 0,
    val daysRemaining: Int = 0,
    val cards: List<MeterCard> = emptyList(),
    val totalConsumed: Double = 0.0,
    val totalDailyAvg: Double = 0.0,
    val recs: List<BillingCalculator.Recommendation> = emptyList()
)

class DashboardVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo

    val state: StateFlow<DashState> = repo.allMeters.map { meters ->
        val today = LocalDate.now()
        val cs = BillingCalculator.cycleStart(today)
        val ce = BillingCalculator.cycleEnd(today)
        val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy")
        val cards = meters.map { m ->
            MeterCard(
                meter = m,
                consumed = BillingCalculator.consumed(m),
                to150 = BillingCalculator.remainingTo(m, 150.0),
                to180 = BillingCalculator.remainingTo(m, 180.0),
                to190 = BillingCalculator.remainingTo(m, 190.0),
                to199 = BillingCalculator.remainingTo(m, 199.0),
                dailyAvg = BillingCalculator.dailyAvg(m),
                projected = BillingCalculator.projected(m),
                risk = BillingCalculator.risk(m),
                progress = BillingCalculator.progress(m)
            )
        }
        DashState(
            cycleStart = cs.format(fmt),
            cycleEnd = ce.format(fmt),
            daysPassed = BillingCalculator.daysPassed(cs).toInt(),
            daysRemaining = BillingCalculator.daysRemaining(cs).toInt(),
            cards = cards,
            totalConsumed = cards.sumOf { it.consumed },
            totalDailyAvg = cards.sumOf { it.dailyAvg },
            recs = BillingCalculator.recommendations(meters)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashState())

    init {
        viewModelScope.launch { repo.seedIfNeeded() }
    }
}

// ── Reading ────────────────────────────────────────────────────────────────────

class ReadingVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo

    val allReadings = repo.allReadings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(meterNo: String, reading: Double, ts: Long, status: MeterStatus, notes: String?) {
        viewModelScope.launch { repo.addReading(meterNo, reading, ts, status, notes) }
    }

    fun delete(id: Long, meterNo: String) {
        viewModelScope.launch { repo.deleteReading(id, meterNo) }
    }

    fun update(r: ManualReading) {
        viewModelScope.launch { repo.updateReading(r) }
    }
}

// ── History ────────────────────────────────────────────────────────────────────

class HistoryVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo
    val selectedMeter = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")

    val history: StateFlow<List<MonthlyBillHistory>> =
        combine(repo.allHistory, selectedMeter, query) { hist, meter, q ->
            hist
                .filter { meter == null || it.meterNumber == meter }
                .filter {
                    q.isBlank() ||
                    it.billingMonth.contains(q, ignoreCase = true) ||
                    it.notes?.contains(q, ignoreCase = true) == true
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(h: MonthlyBillHistory) { viewModelScope.launch { repo.addHistory(h) } }
    fun update(h: MonthlyBillHistory) { viewModelScope.launch { repo.updateHistory(h) } }
    fun delete(h: MonthlyBillHistory) { viewModelScope.launch { repo.deleteHistory(h) } }
}

// ── Switch ─────────────────────────────────────────────────────────────────────

class SwitchVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo

    val meters = repo.allMeters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events = repo.allSwitchEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun changeStatus(meterNo: String, newStatus: MeterStatus, prevStatus: MeterStatus, notes: String?) {
        viewModelScope.launch { repo.changeMeterStatus(meterNo, newStatus, prevStatus, notes) }
    }
}

// ── Analytics ──────────────────────────────────────────────────────────────────

data class AnalyticsState(
    val byMeter: Map<String, List<MonthlyBillHistory>> = emptyMap(),
    val overLimit: List<MonthlyBillHistory> = emptyList(),
    val currentUnits: Map<String, Double> = emptyMap(),
    val avgUnits: Map<String, Double> = emptyMap(),
    val avgBill: Map<String, Double> = emptyMap(),
    val costPerUnit: Map<String, Double> = emptyMap()
)

class AnalyticsVM(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MeterApp).repo

    val state: StateFlow<AnalyticsState> =
        combine(repo.allHistory, repo.allMeters) { hist, meters ->
            val byMeter = mapOf(
                "600" to hist.filter { it.meterNumber == "600" }
                    .sortedWith(compareBy({ it.billingYear }, { it.billingMonthInt })),
                "603" to hist.filter { it.meterNumber == "603" }
                    .sortedWith(compareBy({ it.billingYear }, { it.billingMonthInt })),
                "700" to hist.filter { it.meterNumber == "700" }
                    .sortedWith(compareBy({ it.billingYear }, { it.billingMonthInt }))
            )

            fun avgU(m: String) = byMeter[m]?.map { it.unitsConsumed.toDouble() }?.average() ?: 0.0
            fun avgB(m: String) = byMeter[m]?.map { it.billAmount }?.average() ?: 0.0
            fun cpu(m: String): Double {
                val h = byMeter[m] ?: return 0.0
                val u = h.sumOf { it.unitsConsumed }
                val b = h.sumOf { it.billAmount }
                return if (u > 0) b / u else 0.0
            }

            AnalyticsState(
                byMeter = byMeter,
                overLimit = hist.filter { it.isOverLimit },
                currentUnits = meters.associate { it.meterNumber to BillingCalculator.consumed(it) },
                avgUnits = mapOf("600" to avgU("600"), "603" to avgU("603"), "700" to avgU("700")),
                avgBill = mapOf("600" to avgB("600"), "603" to avgB("603"), "700" to avgB("700")),
                costPerUnit = mapOf("600" to cpu("600"), "603" to cpu("603"), "700" to cpu("700"))
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

    fun exportCsv() {
        viewModelScope.launch {
            try {
                val m = repo.allMeters.first()
                val h = repo.allHistory.first()
                val r = repo.allReadings.first()
                val s = repo.allSwitchEvents.first()
                val file = ExportManager.csv(ctx, m, h, r, s)
                _intent.value = ExportManager.share(ctx, file, "text/csv")
                _status.value = "Exported: ${file.name}"
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
            }
        }
    }

    fun exportJson() {
        viewModelScope.launch {
            try {
                val m = repo.allMeters.first()
                val h = repo.allHistory.first()
                val r = repo.allReadings.first()
                val s = repo.allSwitchEvents.first()
                val file = ExportManager.json(ctx, m, h, r, s)
                _intent.value = ExportManager.share(ctx, file, "application/json")
                _status.value = "Exported: ${file.name}"
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
            }
        }
    }

    fun exportReport() {
        viewModelScope.launch {
            try {
                val m = repo.allMeters.first()
                val h = repo.allHistory.first()
                val r = repo.allReadings.first()
                val s = repo.allSwitchEvents.first()
                val file = ExportManager.report(ctx, m, h, r, s)
                _intent.value = ExportManager.share(ctx, file, "text/plain")
                _status.value = "Exported: ${file.name}"
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
            }
        }
    }

    fun clearIntent() {
        _intent.value = null
    }
}
