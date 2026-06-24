package com.iesco.meterloadmanager.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iesco.meterloadmanager.data.entity.*
import com.iesco.meterloadmanager.ui.components.*
import com.iesco.meterloadmanager.ui.theme.*
import com.iesco.meterloadmanager.utils.BillingCalculator
import com.iesco.meterloadmanager.viewmodel.*
import java.text.SimpleDateFormat
import java.util.*

// ══════════════════════════════════════════════════════════════════════
// DASHBOARD
// ══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardVM, onAddReading: () -> Unit, onAlerts: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Meter Load Manager", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CLR_PRIMARY, titleContentColor = Color.White),
                actions = {
                    BadgedBox(badge = { if (s.unreadAlerts > 0) Badge { Text("${s.unreadAlerts}") } }) {
                        IconButton(onClick = onAlerts) { Icon(Icons.Default.Notifications, null, tint = Color.White) }
                    }
                })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddReading, containerColor = CLR_PRIMARY) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp)) {

            // Cycle summary
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(Color(0xFFE3F2FD))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Current Billing Cycle", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Stat("Start", s.cycleStart); Stat("End", s.cycleEnd)
                        Stat("Days Passed", "${s.daysPassed}"); Stat("Days Remaining", "${s.daysRemaining}")
                        Divider(Modifier.padding(vertical = 6.dp))
                        Stat("Total Consumed", "${fmt(s.totalConsumed)} units")
                        Stat("Combined Daily Avg", "${fmt(s.totalDailyAvg)} units/day")
                        Stat("Est. Total Bill", "Rs ${"%,.0f".format(s.totalEstBill)}", CLR_PRIMARY)
                    }
                }
            }

            // Color legend
            item {
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(Color(0xFFF8F8F8))) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), Arrangement.SpaceEvenly) {
                        LegendDot("0-139 Safe", CLR_SAFE)
                        LegendDot("140-170 Watch", CLR_WATCH)
                        LegendDot("171-199 High", CLR_PURPLE)
                        LegendDot("200+ Over", CLR_DANGER)
                    }
                }
            }

            // Recommendations
            item { SectionTitle("Smart Recommendations") }
            items(s.recs) { RecCard(it) }

            item { SectionTitle("Meter Status") }
            items(s.cards) { MeterCardView(it) }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Text(label, fontSize = 9.sp, color = Color.DarkGray)
    }
}

// ══════════════════════════════════════════════════════════════════════
// ADD READING (dialog/sheet style)
// ══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReadingScreen(vm: ReadingVM) {
    val ctx = LocalContext.current
    var meter by remember { mutableStateOf("600") }
    var input by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(MeterStatus.RUNNING) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    var saved  by remember { mutableStateOf(false) }
    val cal = remember { Calendar.getInstance() }
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    var dispDt by remember { mutableStateOf(sdf.format(cal.time)) }
    var ts by remember { mutableStateOf(cal.timeInMillis) }

    fun pickDateTime() {
        DatePickerDialog(ctx, { _, y, m, d ->
            cal.set(y, m, d)
            TimePickerDialog(ctx, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                ts = cal.timeInMillis; dispDt = sdf.format(cal.time)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Add Reading", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = CLR_PRIMARY, titleContentColor = Color.White)) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Select Meter", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("600","603","700").forEach { m ->
                    FilterChip(selected = meter == m, onClick = { meter = m; saved = false }, label = { Text("Meter $m") })
                }
            }
            OutlinedTextField(value = input, onValueChange = { input = it; saved = false; errMsg = null },
                label = { Text("Meter Reading") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = errMsg != null,
                supportingText = { errMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) } })
            OutlinedTextField(value = dispDt, onValueChange = {}, label = { Text("Date & Time") },
                modifier = Modifier.fillMaxWidth(), readOnly = true,
                trailingIcon = { IconButton(onClick = { pickDateTime() }) { Icon(Icons.Default.CalendarMonth, null) } })
            Text("Status", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(MeterStatus.RUNNING, MeterStatus.SHARING, MeterStatus.PAUSED).forEach { s ->
                    FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.name, fontSize = 11.sp) })
                }
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it },
                label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(onClick = {
                val r = input.toDoubleOrNull(); if (r == null) { errMsg = "Enter a valid number"; return@Button }
                vm.save(meter, r, ts, status, notes.ifBlank { null })
                saved = true; input = ""; notes = ""
            }, modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CLR_PRIMARY)) {
                Icon(Icons.Default.Save, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                Text("Save Reading", color = Color.White, fontWeight = FontWeight.Bold)
            }
            if (saved) Card(colors = CardDefaults.cardColors(Color(0xFFE8F5E9))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = CLR_SAFE); Spacer(Modifier.width(8.dp))
                    Text("Reading saved successfully!", color = CLR_SAFE)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// HISTORY (grouped by month, expandable)
// ══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryVM) {
    val groups by vm.monthGroups.collectAsStateWithLifecycle()
    val selMeter by vm.selectedMeter.collectAsStateWithLifecycle()
    val selYear  by vm.selectedYear.collectAsStateWithLifecycle()
    val years by vm.availableYears.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MonthlyBillHistory?>(null) }
    var showYearMenu by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Billing History", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CLR_PRIMARY, titleContentColor = Color.White),
            actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null, tint = Color.White) } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Filter bar
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(value = q, onValueChange = { vm.query.value = it },
                    label = { Text("Search") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) })
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = selMeter == null, onClick = { vm.selectedMeter.value = null }, label = { Text("All") })
                    listOf("600","603","700").forEach { m ->
                        FilterChip(selected = selMeter == m, onClick = { vm.selectedMeter.value = m }, label = { Text("M-$m") })
                    }
                    Box {
                        FilterChip(selected = selYear != null, onClick = { showYearMenu = true },
                            label = { Text(selYear?.toString() ?: "Year") })
                        DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false }) {
                            DropdownMenuItem(text = { Text("All Years") }, onClick = { vm.selectedYear.value = null; showYearMenu = false })
                            years.forEach { y ->
                                DropdownMenuItem(text = { Text("$y") }, onClick = { vm.selectedYear.value = y; showYearMenu = false })
                            }
                        }
                    }
                }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups) { group ->
                    HistoryMonthGroup(group, onToggle = { vm.toggleMonth(group.monthLabel) },
                        onEdit = { editing = it }, onDelete = { vm.delete(it) })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (showAdd) AddHistDialog(onDismiss = { showAdd = false }, onSave = { vm.add(it); showAdd = false })
    editing?.let { item -> EditHistDialog(item, onDismiss = { editing = null }, onSave = { vm.update(it); editing = null }) }
}

@Composable
fun HistoryMonthGroup(
    group: MonthGroup,
    onToggle: () -> Unit,
    onEdit: (MonthlyBillHistory) -> Unit,
    onDelete: (MonthlyBillHistory) -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            // Month header (clickable to expand/collapse)
            Row(Modifier.fillMaxWidth().clickable { onToggle() }.padding(12.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(group.monthLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${group.totalUnits} units total  |  Rs ${"%.0f".format(group.totalBill)}  |  ${String.format("%.2f",group.avgCostPerUnit)} Rs/u",
                        fontSize = 11.sp, color = Color.Gray)
                }
                Icon(if (group.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = CLR_PRIMARY)
            }

            if (group.isExpanded) {
                Divider()
                group.records.forEach { h ->
                    HistoryRecordRow(h, onEdit = { onEdit(h) }, onDelete = { onDelete(h) })
                    Divider(color = Color(0xFFF0F0F0))
                }
            }
        }
    }
}

@Composable
fun HistoryRecordRow(h: MonthlyBillHistory, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MeterChip(h.meterNumber)
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${h.unitsConsumed} u", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        color = if (h.isOverLimit) CLR_DANGER else CLR_PRIMARY)
                    if (h.isOverLimit) Text("200+", color = CLR_DANGER, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFEBEE)).padding(horizontal=4.dp, vertical=1.dp))
                }
                Text("Rs ${"%.0f".format(h.billAmount)}", fontSize = 12.sp, color = Color.Gray)
                h.paymentMade?.let { Text("Paid: Rs ${"%.0f".format(it)}", fontSize = 11.sp, color = Color.Gray) }
            }
        }
        Row {
            IconButton(onClick = onEdit, Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, tint = CLR_PRIMARY, modifier = Modifier.size(16.dp)) }
            IconButton(onClick = onDelete, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, tint = CLR_DANGER, modifier = Modifier.size(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHistDialog(onDismiss: () -> Unit, onSave: (MonthlyBillHistory) -> Unit) {
    var meter by remember { mutableStateOf("600") }; var month by remember { mutableStateOf("") }
    var units by remember { mutableStateOf("") }; var bill by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add Bill Record") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("600","603","700").forEach { m -> FilterChip(selected=meter==m, onClick={meter=m}, label={Text(m)}) }
                }
                OutlinedTextField(value=month, onValueChange={month=it}, label={Text("Month (e.g. Jul 2026)")}, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=units, onValueChange={units=it}, label={Text("Units")}, modifier=Modifier.fillMaxWidth(), keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                OutlinedTextField(value=bill, onValueChange={bill=it}, label={Text("Bill (Rs)")}, modifier=Modifier.fillMaxWidth(), keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton(onClick = {
            val u = units.toIntOrNull() ?: return@TextButton
            val b = bill.toDoubleOrNull() ?: return@TextButton
            onSave(MonthlyBillHistory(meterNumber=meter, billingMonth=month, billingYear=2026, billingMonthInt=7, unitsConsumed=u, billAmount=b, isOverLimit=u>=200))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick=onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHistDialog(h: MonthlyBillHistory, onDismiss: () -> Unit, onSave: (MonthlyBillHistory) -> Unit) {
    var units by remember { mutableStateOf(h.unitsConsumed.toString()) }
    var bill  by remember { mutableStateOf(h.billAmount.toString()) }
    var notes by remember { mutableStateOf(h.notes ?: "") }
    AlertDialog(onDismissRequest=onDismiss, title={Text("Edit ${h.billingMonth} – M${h.meterNumber}")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value=units, onValueChange={units=it}, label={Text("Units")}, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=bill, onValueChange={bill=it}, label={Text("Bill (Rs)")}, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=notes, onValueChange={notes=it}, label={Text("Notes")}, modifier=Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = {
            val u = units.toIntOrNull() ?: return@TextButton
            val b = bill.toDoubleOrNull() ?: return@TextButton
            onSave(h.copy(unitsConsumed=u, billAmount=b, notes=notes.ifBlank{null}, isOverLimit=u>=200))
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick=onDismiss) { Text("Cancel") } })
}

// ══════════════════════════════════════════════════════════════════════
// ANALYTICS (with year comparison)
// ══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(vm: AnalyticsVM) {
    val s by vm.state.collectAsStateWithLifecycle()
    var selM by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar={TopAppBar(title={Text("Analytics", fontWeight=FontWeight.Bold)},
        colors=TopAppBarDefaults.topAppBarColors(containerColor=CLR_PRIMARY, titleContentColor=Color.White))}
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=14.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp), contentPadding=PaddingValues(vertical=14.dp)) {

            // Current cycle
            item {
                SectionTitle("Current Cycle Estimates")
                Card(shape=RoundedCornerShape(14.dp), elevation=CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        s.currentUnits.forEach { (m, u) ->
                            val proj = s.projectedUnits[m] ?: 0.0
                            val estB = s.estBillByMeter[m] ?: 0.0
                            Row(Modifier.fillMaxWidth().padding(vertical=4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                MeterChip(m)
                                Column(horizontalAlignment=Alignment.End) {
                                    Text("${fmt(u)} units now", fontWeight=FontWeight.SemiBold)
                                    Text("Proj: ${fmt(proj)} u | Est: Rs ${"%,.0f".format(estB)}", fontSize=11.sp, color=Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            // Year-on-year comparison
            s.yearComparison?.let { yc ->
                item {
                    SectionTitle("Year-on-Year: ${yc.monthLabel}")
                    Card(shape=RoundedCornerShape(14.dp), elevation=CardDefaults.cardElevation(2.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Meter", fontSize=11.sp, fontWeight=FontWeight.Bold, color=Color.Gray, modifier=Modifier.weight(1f))
                                Text("${yc.currentYear}", fontSize=11.sp, fontWeight=FontWeight.Bold, color=CLR_PRIMARY)
                                Spacer(Modifier.width(12.dp))
                                Text("${yc.prevYear}", fontSize=11.sp, fontWeight=FontWeight.Bold, color=Color.Gray)
                                Spacer(Modifier.width(12.dp))
                                Text("Chg%", fontSize=11.sp, fontWeight=FontWeight.Bold, color=Color.Gray)
                            }
                            Divider(Modifier.padding(vertical=4.dp))
                            listOf("600","603","700").forEach { m ->
                                val cu = yc.currentUnits[m] ?: 0; val pu = yc.prevUnits[m] ?: 0
                                val pct = yc.pctChange[m] ?: 0.0
                                Row(Modifier.fillMaxWidth().padding(vertical=2.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    MeterChip(m)
                                    Text("$cu u", fontSize=12.sp, color=CLR_PRIMARY, fontWeight=FontWeight.SemiBold)
                                    Text("$pu u", fontSize=12.sp, color=Color.Gray)
                                    Text("${if(pct>0)"+" else ""}${String.format("%.0f",pct)}%", fontSize=12.sp,
                                        color=if(pct>0) CLR_DANGER else CLR_SAFE, fontWeight=FontWeight.Bold)
                                }
                                val cb = yc.currentBill[m] ?: 0.0; val pb = yc.prevBill[m] ?: 0.0
                                if (cb > 0 || pb > 0) Row(Modifier.fillMaxWidth().padding(start=8.dp, bottom=4.dp), Arrangement.SpaceBetween) {
                                    Spacer(Modifier.width(40.dp))
                                    Text("Rs ${"%.0f".format(cb)}", fontSize=11.sp, color=Color.Gray)
                                    Text("Rs ${"%.0f".format(pb)}", fontSize=11.sp, color=Color.Gray)
                                    val bdiff = cb - pb
                                    Text("${if(bdiff>0)"+" else ""}Rs ${"%.0f".format(bdiff)}", fontSize=11.sp,
                                        color=if(bdiff>0) CLR_DANGER else CLR_SAFE)
                                }
                            }
                            Divider(Modifier.padding(vertical=4.dp))
                            val totalCur = yc.currentUnits.values.sum(); val totalPrev = yc.prevUnits.values.sum()
                            val totalBcur = yc.currentBill.values.sum(); val totalBprev = yc.prevBill.values.sum()
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Combined", fontWeight=FontWeight.Bold, fontSize=12.sp)
                                Text("$totalCur u", fontWeight=FontWeight.Bold, color=CLR_PRIMARY)
                                Text("$totalPrev u", color=Color.Gray, fontSize=12.sp)
                                val dp = if (totalPrev>0) (totalCur-totalPrev).toDouble()/totalPrev*100 else 0.0
                                Text("${if(dp>0)"+" else ""}${String.format("%.0f",dp)}%",
                                    color=if(dp>0) CLR_DANGER else CLR_SAFE, fontWeight=FontWeight.Bold, fontSize=12.sp)
                            }
                            Text("Bill: Rs ${"%.0f".format(totalBcur)} vs Rs ${"%.0f".format(totalBprev)}  (diff Rs ${"%.0f".format(totalBcur-totalBprev)})",
                                fontSize=11.sp, color=Color.Gray, modifier=Modifier.padding(top=4.dp))
                        }
                    }
                }
            }

            // Averages
            item {
                SectionTitle("All-Time Averages")
                Card(shape=RoundedCornerShape(14.dp), elevation=CardDefaults.cardElevation(2.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.SpaceEvenly) {
                        listOf("600","603","700").forEach { m ->
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                MeterChip(m); Spacer(Modifier.height(4.dp))
                                Text("${fmt(s.avgUnits[m]?:0.0)} u/mo", fontWeight=FontWeight.Bold)
                                Text("Rs ${"%.0f".format(s.avgBill[m]?:0.0)}", fontSize=11.sp, color=Color.Gray)
                                Text("${fmt(s.costPerUnit[m]?:0.0)} Rs/u", fontSize=10.sp, color=Color.Gray)
                            }
                        }
                    }
                }
            }

            // Over-limit
            item { SectionTitle("Months Over 200 Units") }
            if (s.overLimit.isEmpty()) item { Text("No months over 200 units.", color=Color.Gray) }
            items(s.overLimit) { h ->
                Card(shape=RoundedCornerShape(10.dp), colors=CardDefaults.cardColors(Color(0xFFFFEBEE))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
                            MeterChip(h.meterNumber); Text(h.billingMonth, fontWeight=FontWeight.SemiBold)
                        }
                        Text("${h.unitsConsumed}u  Rs ${"%.0f".format(h.billAmount)}", color=CLR_DANGER, fontWeight=FontWeight.Bold, fontSize=12.sp)
                    }
                }
            }

            // Appliance estimates
            item { SectionTitle("Appliance Consumption Estimates") }
            item {
                Card(shape=RoundedCornerShape(14.dp), elevation=CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Top 5 by Daily Units", fontWeight=FontWeight.SemiBold, fontSize=13.sp)
                        Spacer(Modifier.height(6.dp))
                        s.topAppliances.forEach { a ->
                            Row(Modifier.fillMaxWidth().padding(vertical=2.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text(a.name, fontSize=12.sp, modifier=Modifier.weight(1f))
                                MeterChip(a.assignedMeter); Spacer(Modifier.width(8.dp))
                                Text("${fmt(a.dailyUnits)} u/day", fontSize=12.sp, fontWeight=FontWeight.SemiBold, color=CLR_PRIMARY)
                            }
                        }
                        Divider(Modifier.padding(vertical=6.dp))
                        s.applianceByMeter.forEach { (m, u) ->
                            Stat("Meter $m estimated daily", "${fmt(u)} units", meterColor(m))
                        }
                    }
                }
            }

            // Trend chart
            item { SectionTitle("Units Trend (Last 6 Months)") }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected=selM==null, onClick={selM=null}, label={Text("All")})
                    listOf("600","603","700").forEach { m -> FilterChip(selected=selM==m, onClick={selM=m}, label={Text("M-$m")}) }
                }
            }
            item {
                val data: List<Pair<String,Int>> = if (selM==null) {
                    s.byMeter.values.flatten()
                        .groupBy { it.billingMonth }
                        .entries
                        .sortedWith(compareBy({ it.value.first().billingYear },{ it.value.first().billingMonthInt }))
                        .takeLast(6).map { it.key to it.value.sumOf { h -> h.unitsConsumed } }
                } else {
                    (s.byMeter[selM] ?: emptyList()).takeLast(6).map { it.billingMonth to it.unitsConsumed }
                }
                BarChart(data)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun BarChart(data: List<Pair<String, Int>>) {
    if (data.isEmpty()) return
    val maxV = data.maxOf { it.second }.toFloat().coerceAtLeast(1f)
    val colors = listOf(CLR_600, CLR_603, CLR_700, CLR_PRIMARY, CLR_WATCH, CLR_WARNING)
    Card(shape=RoundedCornerShape(14.dp), elevation=CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp)) {
            data.forEachIndexed { i, (month, units) ->
                Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.padding(vertical=2.dp)) {
                    Text(month, fontSize=10.sp, modifier=Modifier.width(72.dp), color=Color.Gray)
                    Box(Modifier.weight((units/maxV).coerceAtLeast(0.01f)).height(16.dp)
                        .clip(RoundedCornerShape(4.dp)).background(colors[i%colors.size]))
                    Spacer(Modifier.width(4.dp))
                    Text("$units", fontSize=10.sp, color=if(units>=200) CLR_DANGER else Color.DarkGray,
                        fontWeight=if(units>=200) FontWeight.Bold else FontWeight.Normal)
                }
            }
            Text("Bold red = 200+ units (higher slab risk)", fontSize=10.sp, color=Color.Gray, modifier=Modifier.padding(top=4.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// SWITCHES (improved with full-load toggle)
// ══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitchScreen(vm: SwitchVM) {
    val meters by vm.meters.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val recs = remember(meters) { BillingCalculator.recommendations(meters) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf<SwitchEvent?>(null) }
    val dtFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    val m600 = meters.find { it.meterNumber == "600" }
    val m603 = meters.find { it.meterNumber == "603" }
    val m700 = meters.find { it.meterNumber == "700" }
    val fullLoadMeter = meters.find { it.status == MeterStatus.RUNNING || it.status == MeterStatus.FULL_LOAD }

    Scaffold(topBar={TopAppBar(title={Text("Switch Management", fontWeight=FontWeight.Bold)},
        colors=TopAppBarDefaults.topAppBarColors(containerColor=CLR_PRIMARY, titleContentColor=Color.White))}
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=14.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp), contentPadding=PaddingValues(vertical=14.dp)) {

            // Full-load toggle (600 <-> 700)
            item {
                SectionTitle("Ground Floor Primary Load")
                Card(shape=RoundedCornerShape(14.dp), elevation=CardDefaults.cardElevation(2.dp),
                    colors=CardDefaults.cardColors(Color(0xFFF3F6FF))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Only one meter can carry Full Load at a time", fontSize=12.sp, color=Color.Gray)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                            // M600
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                MeterChip("600")
                                Spacer(Modifier.height(4.dp))
                                Text(m600?.status?.name?.replace("_"," ") ?: "", fontSize=11.sp,
                                    color=if(m600?.status==MeterStatus.RUNNING||m600?.status==MeterStatus.FULL_LOAD) CLR_SAFE else Color.Gray,
                                    fontWeight=FontWeight.Bold)
                                Text("${fmt(m600?.let { BillingCalculator.consumed(it) } ?: 0.0)} u", fontSize=11.sp, color=Color.Gray)
                            }
                            // Toggle
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                Text("⟵ Full Load ⟶", fontSize=12.sp, color=CLR_PRIMARY, fontWeight=FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Button(onClick={
                                    val current = meters.find { it.status==MeterStatus.RUNNING||it.status==MeterStatus.FULL_LOAD }
                                    if (current?.meterNumber == "600") {
                                        vm.changeStatus("700", MeterStatus.RUNNING, m700?.status ?: MeterStatus.PAUSED, "Switched primary from 600 to 700")
                                        vm.changeStatus("600", MeterStatus.PAUSED, MeterStatus.RUNNING, "Switched primary from 600 to 700")
                                    } else {
                                        vm.changeStatus("600", MeterStatus.RUNNING, m600?.status ?: MeterStatus.PAUSED, "Switched primary from 700 to 600")
                                        vm.changeStatus("700", MeterStatus.PAUSED, MeterStatus.RUNNING, "Switched primary from 700 to 600")
                                    }
                                }, colors=ButtonDefaults.buttonColors(containerColor=CLR_PRIMARY)) { Text("Switch", color=Color.White) }
                            }
                            // M700
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                MeterChip("700")
                                Spacer(Modifier.height(4.dp))
                                Text(m700?.status?.name?.replace("_"," ") ?: "", fontSize=11.sp,
                                    color=if(m700?.status==MeterStatus.RUNNING||m700?.status==MeterStatus.FULL_LOAD) CLR_SAFE else Color.Gray,
                                    fontWeight=FontWeight.Bold)
                                Text("${fmt(m700?.let { BillingCalculator.consumed(it) } ?: 0.0)} u", fontSize=11.sp, color=Color.Gray)
                            }
                        }
                    }
                }
            }

            // Meter 603 sharing toggle
            item {
                SectionTitle("Meter 603 – Sharing Control")
                Card(shape=RoundedCornerShape(14.dp), elevation=CardDefaults.cardElevation(2.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            MeterChip("603"); Spacer(Modifier.height(4.dp))
                            Text(m603?.status?.name?.replace("_"," ") ?: "", fontWeight=FontWeight.Bold,
                                color=if(m603?.status==MeterStatus.SHARING) CLR_SAFE else Color.Gray)
                            Text("${fmt(m603?.let { BillingCalculator.consumed(it) } ?: 0.0)} units consumed", fontSize=12.sp, color=Color.Gray)
                        }
                        Column(horizontalAlignment=Alignment.End) {
                            Text("Sharing", fontSize=12.sp, fontWeight=FontWeight.SemiBold)
                            Switch(checked=m603?.status==MeterStatus.SHARING,
                                onCheckedChange={ enabled ->
                                    val newS = if(enabled) MeterStatus.SHARING else MeterStatus.PAUSED
                                    vm.changeStatus("603", newS, m603?.status ?: MeterStatus.PAUSED,
                                        if(enabled) "Sharing enabled" else "Sharing disabled")
                                })
                        }
                    }
                }
            }

            // All meters current state
            item { SectionTitle("All Meter States") }
            items(meters) { m ->
                Card(shape=RoundedCornerShape(12.dp), elevation=CardDefaults.cardElevation(2.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            MeterChip(m.meterNumber); Spacer(Modifier.height(3.dp))
                            val sc = when(m.status) { MeterStatus.PAUSED->Color.Gray; MeterStatus.SHARING,MeterStatus.PARTIAL_LOAD->CLR_WATCH; else->CLR_SAFE }
                            Text(m.status.name.replace("_"," "), color=sc, fontWeight=FontWeight.Bold)
                            Text("${fmt(BillingCalculator.consumed(m))} u consumed", fontSize=12.sp, color=Color.Gray)
                        }
                        Button(onClick={dialog=m.meterNumber}, colors=ButtonDefaults.buttonColors(containerColor=CLR_PRIMARY)) {
                            Icon(Icons.Default.Edit, null, tint=Color.White); Spacer(Modifier.width(4.dp)); Text("Change", color=Color.White)
                        }
                    }
                }
            }

            item { SectionTitle("Smart Recommendations") }
            items(recs) { RecCard(it) }

            item { SectionTitle("Switch Event Log") }
            if (events.isEmpty()) item { Text("No switch events yet.", color=Color.Gray, modifier=Modifier.padding(8.dp)) }
            items(events) { e ->
                Card(shape=RoundedCornerShape(10.dp), elevation=CardDefaults.cardElevation(1.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment=Alignment.Top) {
                        Icon(Icons.Default.SwapHoriz, null, tint=CLR_PRIMARY, modifier=Modifier.size(20.dp).padding(top=2.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically) {
                                MeterChip(e.meterNumber)
                                Text("${e.previousStatus} -> ${e.newStatus}", fontWeight=FontWeight.SemiBold, fontSize=12.sp)
                            }
                            Text(dtFmt.format(Date(e.timestamp)), fontSize=11.sp, color=Color.Gray)
                            e.unitsAtSwitch?.let { Text("Units: ${fmt(it)}", fontSize=11.sp, color=Color.Gray) }
                            e.notes?.let { Text(it, fontSize=11.sp, color=Color(0xFF555555)) }
                        }
                        IconButton(onClick={ deleteConfirm = e }, Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, null, tint=CLR_DANGER, modifier=Modifier.size(16.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Status change dialog
    dialog?.let { mno ->
        val m = meters.find { it.meterNumber == mno }
        if (m != null) {
            var newS by remember { mutableStateOf(m.status) }
            var notesTxt by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest={dialog=null}, title={Text("Change Status: Meter $mno")},
                text={
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        MeterStatus.values().forEach { s ->
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                RadioButton(selected=newS==s, onClick={newS=s})
                                Text(s.name.replace("_"," "))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value=notesTxt, onValueChange={notesTxt=it},
                            label={Text("Notes (optional)")}, modifier=Modifier.fillMaxWidth())
                    }
                },
                confirmButton={TextButton(onClick={ vm.changeStatus(mno,newS,m.status,notesTxt.ifBlank{null}); dialog=null }){Text("Update")}},
                dismissButton={TextButton(onClick={dialog=null}){Text("Cancel")}})
        }
    }

    // Delete confirmation
    deleteConfirm?.let { e ->
        AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            title = { Text("Delete Switch Event?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { TextButton(onClick = { deleteConfirm = null }) { Text("Delete", color = CLR_DANGER) } },
            dismissButton = { TextButton(onClick = { deleteConfirm = null }) { Text("Cancel") } }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
// APPLIANCES
// ══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppliancesScreen(vm: ApplianceVM) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Appliance?>(null) }

    Scaffold(topBar={TopAppBar(title={Text("Appliances", fontWeight=FontWeight.Bold)},
        colors=TopAppBarDefaults.topAppBarColors(containerColor=CLR_PRIMARY, titleContentColor=Color.White),
        actions={IconButton(onClick={showAdd=true}){Icon(Icons.Default.Add, null, tint=Color.White)}})
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal=14.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp), contentPadding=PaddingValues(vertical=12.dp)) {

            item {
                Card(shape=RoundedCornerShape(14.dp), colors=CardDefaults.cardColors(Color(0xFFE8F5E9))) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Estimated Daily Consumption", fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Stat("Total Active Appliances", "${fmt(s.totalDailyUnits)} units/day")
                        Stat("Monthly (30d)", "${fmt(s.totalDailyUnits * 30)} units")
                        Divider(Modifier.padding(vertical=6.dp))
                        listOf("600","603","700").forEach { m ->
                            val u = s.byMeter[m] ?: 0.0
                            if (u > 0) Stat("Meter $m", "${fmt(u)} u/day", meterColor(m))
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected=vm.filterMeter.value==null, onClick={vm.filterMeter.value=null}, label={Text("All")})
                    listOf("600","603","700").forEach { m ->
                        FilterChip(selected=vm.filterMeter.value==m, onClick={vm.filterMeter.value=m}, label={Text("M-$m")})
                    }
                }
            }

            item { SectionTitle("Appliances (${s.appliances.size})") }
            items(s.appliances) { a ->
                ApplianceCard(a, onToggle={vm.toggle(a)}, onEdit={editing=a}, onDelete={vm.delete(a)})
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (showAdd) ApplianceDialog(null, onDismiss={showAdd=false}, onSave={vm.add(it); showAdd=false})
    editing?.let { a -> ApplianceDialog(a, onDismiss={editing=null}, onSave={vm.update(it); editing=null}) }
}

@Composable
fun ApplianceCard(a: Appliance, onToggle: ()->Unit, onEdit: ()->Unit, onDelete: ()->Unit) {
    Card(shape=RoundedCornerShape(12.dp), elevation=CardDefaults.cardElevation(2.dp),
        colors=CardDefaults.cardColors(if(a.isActive) Color.White else Color(0xFFF5F5F5))) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically) {
                        Text(a.name, fontWeight=FontWeight.SemiBold, fontSize=14.sp,
                            color=if(a.isActive) Color.Black else Color.Gray)
                        MeterChip(a.assignedMeter)
                    }
                    Text("${a.category.name.lowercase().replaceFirstChar{it.uppercase()}} | ${a.quantity}x ${a.wattagePerUnit.toInt()}W | ${a.dailyHours}h/day",
                        fontSize=11.sp, color=Color.Gray)
                }
                Row {
                    IconButton(onClick=onToggle, Modifier.size(32.dp)) {
                        Icon(if(a.isActive) Icons.Default.ToggleOn else Icons.Default.PowerSettingsNew, null,
                            tint=if(a.isActive) CLR_SAFE else Color.Gray, modifier=Modifier.size(22.dp))
                    }
                    IconButton(onClick=onEdit, Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, tint=CLR_PRIMARY, modifier=Modifier.size(16.dp)) }
                    IconButton(onClick=onDelete, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, null, tint=CLR_DANGER, modifier=Modifier.size(16.dp)) }
                }
            }
            if (a.isActive) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Daily: ${fmt(a.dailyUnits)} units", fontSize=12.sp, color=CLR_PRIMARY, fontWeight=FontWeight.SemiBold)
                    Text("Monthly: ${fmt(a.monthlyUnits())} units", fontSize=12.sp, color=Color.Gray)
                }
                a.notes?.let { Text(it, fontSize=10.sp, color=Color.Gray, modifier=Modifier.padding(top=2.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplianceDialog(existing: Appliance?, onDismiss: ()->Unit, onSave: (Appliance)->Unit) {
    var name  by remember { mutableStateOf(existing?.name ?: "") }
    var meter by remember { mutableStateOf(existing?.assignedMeter ?: "600") }
    var cat   by remember { mutableStateOf(existing?.category ?: ApplianceCategory.OTHER) }
    var qty   by remember { mutableStateOf(existing?.quantity?.toString() ?: "1") }
    var watts by remember { mutableStateOf(existing?.wattagePerUnit?.toString() ?: "") }
    var hours by remember { mutableStateOf(existing?.dailyHours?.toString() ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    AlertDialog(onDismissRequest=onDismiss, title={Text(if(existing==null)"Add Appliance" else "Edit Appliance")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value=name, onValueChange={name=it}, label={Text("Appliance Name")}, modifier=Modifier.fillMaxWidth())
                Text("Assigned Meter", fontSize=12.sp, fontWeight=FontWeight.SemiBold)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("600","603","700","Unassigned").forEach { m ->
                        FilterChip(selected=meter==m, onClick={meter=m}, label={Text(m, fontSize=11.sp)})
                    }
                }
                Text("Category", fontSize=12.sp, fontWeight=FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    listOf(ApplianceCategory.COOLING, ApplianceCategory.FAN, ApplianceCategory.LIGHTING, ApplianceCategory.KITCHEN).forEach { c ->
                        FilterChip(selected=cat==c, onClick={cat=c}, label={Text(c.name.take(4), fontSize=10.sp)})
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    listOf(ApplianceCategory.LAUNDRY, ApplianceCategory.MOTOR, ApplianceCategory.ENTERTAINMENT, ApplianceCategory.OTHER).forEach { c ->
                        FilterChip(selected=cat==c, onClick={cat=c}, label={Text(c.name.take(4), fontSize=10.sp)})
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value=qty, onValueChange={qty=it}, label={Text("Qty")}, modifier=Modifier.weight(1f), keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                    OutlinedTextField(value=watts, onValueChange={watts=it}, label={Text("Watts")}, modifier=Modifier.weight(1f), keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                    OutlinedTextField(value=hours, onValueChange={hours=it}, label={Text("Hrs/Day")}, modifier=Modifier.weight(1f), keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                }
                val calc = ((qty.toIntOrNull()?:0)*(watts.toDoubleOrNull()?:0.0)*(hours.toDoubleOrNull()?:0.0))/1000.0
                if (calc>0) Text("Est. daily: ${fmt(calc)} units", fontSize=12.sp, color=CLR_PRIMARY)
                OutlinedTextField(value=notes, onValueChange={notes=it}, label={Text("Notes (optional)")}, modifier=Modifier.fillMaxWidth())
            }
        },
        confirmButton={TextButton(onClick={
            val q2=qty.toIntOrNull()?:return@TextButton
            val w2=watts.toDoubleOrNull()?:return@TextButton
            val h2=hours.toDoubleOrNull()?:return@TextButton
            val base = existing ?: Appliance(name=name,assignedMeter=meter,category=cat,quantity=q2,wattagePerUnit=w2,dailyHours=h2,notes=notes.ifBlank{null})
            onSave(base.copy(name=name,assignedMeter=meter,category=cat,quantity=q2,wattagePerUnit=w2,dailyHours=h2,notes=notes.ifBlank{null}))
        }){Text("Save")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

// ══════════════════════════════════════════════════════════════════════
// ALERTS / NOTIFICATION CENTER
// ══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(vm: AlertsVM, dashVM: DashboardVM) {
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val unread by vm.unreadCount.collectAsStateWithLifecycle()
    val dash by dashVM.state.collectAsStateWithLifecycle()

    Scaffold(topBar={TopAppBar(title={Text("Alert Center ($unread unread)", fontWeight=FontWeight.Bold)},
        colors=TopAppBarDefaults.topAppBarColors(containerColor=CLR_PRIMARY, titleContentColor=Color.White),
        actions={
            IconButton(onClick={vm.clearDismissed()}){Icon(Icons.Default.Delete, null, tint=Color.White)}
        })}
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Generate fresh alerts button
            Button(onClick={
                val meters = dash.cards.map { it.meter }
                vm.generateAlerts(meters)
            }, modifier=Modifier.fillMaxWidth().padding(12.dp),
                colors=ButtonDefaults.buttonColors(containerColor=CLR_PRIMARY)) {
                Icon(Icons.Default.Refresh, null, tint=Color.White); Spacer(Modifier.width(8.dp))
                Text("Refresh Alerts", color=Color.White)
            }

            if (notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Notifications, null, tint=Color.Gray, modifier=Modifier.size(64.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No alerts", color=Color.Gray)
                    }
                }
            } else {
                LazyColumn(contentPadding=PaddingValues(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(notifications) { n ->
                        NotificationCard(n,
                            onRead={ vm.markRead(n.id) },
                            onSnooze={ vm.snooze(n.id, 2) },
                            onDismiss={ vm.dismiss(n.id) },
                            onDelete={ vm.delete(n) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(n: AppNotification, onRead:()->Unit, onSnooze:()->Unit, onDismiss:()->Unit, onDelete:()->Unit) {
    val bgColor = when(n.category) {
        NotificationCategory.CRITICAL      -> Color(0xFFFCE4EC)
        NotificationCategory.WARNING       -> Color(0xFFFFEBEE)
        NotificationCategory.WATCH         -> Color(0xFFFFFDE7)
        NotificationCategory.RECOMMENDATION-> Color(0xFFE8F5E9)
        NotificationCategory.INFORMATION   -> Color(0xFFE3F2FD)
    }
    val borderColor = when(n.category) {
        NotificationCategory.CRITICAL      -> CLR_DANGER
        NotificationCategory.WARNING       -> CLR_WARNING
        NotificationCategory.WATCH         -> CLR_WATCH
        NotificationCategory.RECOMMENDATION-> CLR_SAFE
        NotificationCategory.INFORMATION   -> CLR_PRIMARY
    }

    Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(12.dp),
        colors=CardDefaults.cardColors(bgColor)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(borderColor))
                        Text(n.title, fontWeight=if(n.status==NotificationStatus.UNREAD) FontWeight.Bold else FontWeight.Normal,
                            fontSize=13.sp, color=borderColor)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(n.message, fontSize=12.sp, color=Color(0xFF444444))
                    Spacer(Modifier.height(4.dp))
                    Text(SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(n.timestamp)),
                        fontSize=10.sp, color=Color.Gray)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if (n.status == NotificationStatus.UNREAD) {
                    TextButton(onClick=onRead, modifier=Modifier.height(28.dp),
                        contentPadding=PaddingValues(horizontal=8.dp, vertical=0.dp)) { Text("Mark Read", fontSize=11.sp) }
                }
                TextButton(onClick=onSnooze, modifier=Modifier.height(28.dp),
                    contentPadding=PaddingValues(horizontal=8.dp, vertical=0.dp)) { Text("Snooze 2h", fontSize=11.sp) }
                TextButton(onClick=onDismiss, modifier=Modifier.height(28.dp),
                    contentPadding=PaddingValues(horizontal=8.dp, vertical=0.dp)) { Text("Dismiss", fontSize=11.sp) }
                TextButton(onClick=onDelete, modifier=Modifier.height(28.dp),
                    contentPadding=PaddingValues(horizontal=8.dp, vertical=0.dp)) { Text("Delete", fontSize=11.sp, color=CLR_DANGER) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// EXPORT
// ══════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(vm: ExportVM) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val shareIntent by vm.shareIntent.observeAsState()
    val status by vm.status.observeAsState("")
    LaunchedEffect(shareIntent) {
        shareIntent?.let { launcher.launch(Intent.createChooser(it,"Share")); vm.clearIntent() }
    }
    Scaffold(topBar={TopAppBar(title={Text("Export & Settings", fontWeight=FontWeight.Bold)},
        colors=TopAppBarDefaults.topAppBarColors(containerColor=CLR_PRIMARY, titleContentColor=Color.White))}
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Export all meter data, appliances, readings and history.", fontSize=13.sp, color=Color.Gray)
            ExportButton("Export as CSV","Opens in Excel / Google Sheets", Icons.Default.TableChart, Color(0xFF1B5E20)){vm.exportCsv()}
            ExportButton("Export JSON Backup","Full backup including appliances", Icons.Default.Code, Color(0xFF0D47A1)){vm.exportJson()}
            ExportButton("Full Text Report","Complete report with all stats", Icons.Default.Article, Color(0xFF4A148C)){vm.exportReport()}
            if (!status.isNullOrBlank()) Card(colors=CardDefaults.cardColors(Color(0xFFE8F5E9))) {
                Row(Modifier.padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint=CLR_SAFE); Spacer(Modifier.width(8.dp))
                    Text(status?:"", color=CLR_SAFE, fontSize=13.sp)
                }
            }
            Divider()
            Text("App Info", fontWeight=FontWeight.Bold, fontSize=16.sp)
            Text("Meter Load Manager v3.0\nM-600: 03 14622 1335600\nM-603: 03 14622 1335603\nM-700: 03 14622 1335700\n\n" +
                "Billing cycle: 13th of each month.\nTarget: keep each meter below 175 units.\nHard limit: 200 units per cycle.",
                fontSize=12.sp, color=Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportButton(label: String, desc: String, icon: ImageVector, color: Color, onClick: ()->Unit) {
    Card(onClick=onClick, shape=RoundedCornerShape(14.dp), elevation=CardDefaults.cardElevation(2.dp), modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint=color, modifier=Modifier.size(30.dp))
            Column(Modifier.weight(1f)) { Text(label, fontWeight=FontWeight.SemiBold, color=color); Text(desc, fontSize=11.sp, color=Color.Gray) }
            Icon(Icons.Default.ChevronRight, null, tint=Color.LightGray)
        }
    }
}
