package com.iesco.meterloadmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iesco.meterloadmanager.ui.theme.*
import com.iesco.meterloadmanager.utils.BillingCalculator
import com.iesco.meterloadmanager.viewmodel.MeterCard
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RiskBar(units: Double, modifier: Modifier = Modifier) {
    val progress = (units / 200.0).toFloat().coerceIn(0f, 1f)
    Box(modifier.height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE0E0E0))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress)
            .clip(RoundedCornerShape(6.dp)).background(progressColor(units)))
    }
}

@Composable
fun RiskChip(risk: BillingCalculator.Risk) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(risk.color())
        .padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(risk.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MeterChip(n: String) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(meterColor(n))
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text("M-$n", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Stat(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color(0xFF666666))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = CLR_PRIMARY, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
fun MeterCardView(c: MeterCard) {
    val dtFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val readTs = c.meter.currentReadingTimestamp
    val hoursAgo = (System.currentTimeMillis() - readTs) / 3_600_000L
    val agoStr = when { hoursAgo < 1 -> "just now"; hoursAgo < 24 -> "${hoursAgo}h ago"; else -> "${hoursAgo/24}d ago" }
    val barColor = progressColor(c.consumed)

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(14.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                MeterChip(c.meter.meterNumber)
                RiskChip(c.risk)
            }
            Spacer(Modifier.height(3.dp))
            Text(c.meter.purpose, fontSize = 11.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(8.dp))

            // Slab info card
            Card(colors = CardDefaults.cardColors(
                containerColor = if (c.slabInfo.isProtected) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)),
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("Current Billing Slab", fontSize = 10.sp, color = Color.Gray)
                    Text(c.slabInfo.label, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = if (c.slabInfo.isProtected) CLR_SAFE else CLR_WARNING)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Rs ${String.format("%.2f", c.slabInfo.rate)}/unit", fontSize = 11.sp, color = Color.Gray)
                        Text(if (c.slabInfo.isProtected) "Protected" else "Non-Protected",
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = if (c.slabInfo.isProtected) CLR_SAFE else CLR_DANGER)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            // Progress
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("${fmt(c.consumed)} units", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = barColor)
                Text("/ 200", fontSize = 12.sp, color = Color.Gray)
            }
            RiskBar(c.consumed, Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("0", fontSize = 9.sp, color = Color.Gray)
                Text("140", fontSize = 9.sp, color = CLR_WATCH)
                Text("171", fontSize = 9.sp, color = CLR_PURPLE)
                Text("200", fontSize = 9.sp, color = CLR_DANGER)
            }
            Divider(Modifier.padding(vertical = 6.dp))

            // Last reading
            Card(colors = CardDefaults.cardColors(Color(0xFFF3F6FF)), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("Last Reading", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    Text("${c.meter.currentReading}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${dtFmt.format(Date(readTs))}  ($agoStr)", fontSize = 10.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(6.dp))
            Stat("Cycle Start", c.meter.cycleStartReading.toString())
            Stat("Status", c.meter.status.name.replace("_"," "))
            Divider(Modifier.padding(vertical = 5.dp))
            Stat("Remaining to 150", fmt(c.to150))
            Stat("Remaining to 175 (target)", fmt(c.to175), if (c.to175 < 15) CLR_WARNING else Color.Unspecified)
            Stat("Remaining to 199", fmt(c.to199))
            Divider(Modifier.padding(vertical = 5.dp))
            Stat("Daily Average", "${fmt(c.dailyAvg)} u/day")
            Stat("Projected End-Cycle", "${fmt(c.projected)} units", progressColor(c.projected))
            Stat("Est. Bill (projected)", "Rs ${"%,.0f".format(c.estBill)}")
            c.date175?.let { Stat("Est. reach 175 units", it.toString(), CLR_WATCH) }
            c.date200?.let { Stat("Est. reach 200 units", it.toString(), CLR_DANGER) }
        }
    }
}

@Composable
fun RecCard(rec: BillingCalculator.Recommendation) {
    val bg = when (rec.urgency) {
        BillingCalculator.Risk.SAFE     -> Color(0xFFE8F5E9)
        BillingCalculator.Risk.WATCH    -> Color(0xFFFFFDE7)
        BillingCalculator.Risk.WARNING  -> Color(0xFFFFF3E0)
        BillingCalculator.Risk.DANGER   -> Color(0xFFFFEBEE)
        BillingCalculator.Risk.CRITICAL -> Color(0xFFFCE4EC)
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(Modifier.padding(12.dp)) {
            Text(rec.msg, color = rec.urgency.color(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (rec.reason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(rec.reason, fontSize = 11.sp, color = Color(0xFF555555))
            }
            if (rec.expectedImpact.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text("Impact: ${rec.expectedImpact}", fontSize = 11.sp, color = Color(0xFF777777))
            }
        }
    }
}

fun fmt(d: Double): String = String.format("%.1f", d)
