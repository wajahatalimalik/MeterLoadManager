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
    val color = progressColor(units)
    Box(modifier.height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFE0E0E0))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress)
            .clip(RoundedCornerShape(5.dp)).background(color))
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
    val readingTs = c.meter.currentReadingTimestamp
    val readingStr = dtFmt.format(Date(readingTs))
    val hoursAgo = (System.currentTimeMillis() - readingTs) / 3_600_000L
    val agoStr = when {
        hoursAgo < 1 -> "just now"
        hoursAgo < 24 -> "${hoursAgo}h ago"
        else -> "${hoursAgo / 24}d ago"
    }
    val barColor = progressColor(c.consumed)

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                MeterChip(c.meter.meterNumber)
                RiskChip(c.risk)
            }
            Spacer(Modifier.height(3.dp))
            Text(c.meter.purpose, fontSize = 11.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(6.dp))

            // Progress bar with color coding
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("${fmt(c.consumed)} units", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = barColor)
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

            // Last reading info
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F6FF)),
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("Last Reading", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    Text("${c.meter.currentReading}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("$readingStr  ($agoStr)", fontSize = 10.sp, color = Color.Gray)
                }
            }

            Spacer(Modifier.height(6.dp))
            Stat("Cycle Start Reading", c.meter.cycleStartReading.toString())
            Stat("Status", c.meter.status.name.replace("_", " "))

            Divider(Modifier.padding(vertical = 5.dp))
            Stat("Remaining to 150", fmt(c.to150))
            Stat("Remaining to 175 (target)", fmt(c.to175),
                if (c.to175 < 10) CLR_WARNING else Color.Unspecified)
            Stat("Remaining to 199", fmt(c.to199))

            Divider(Modifier.padding(vertical = 5.dp))
            Stat("Daily Average", "${fmt(c.dailyAvg)} units/day")
            Stat("Projected by Cycle End", "${fmt(c.projected)} units",
                progressColor(c.projected))
            Stat("Est. Bill", "Rs ${"%,.0f".format(c.estBill)}")

            // Date estimates
            c.date175?.let {
                Stat("Est. reach 175 units", it.toString(), CLR_WATCH)
            }
            c.date200?.let {
                Stat("Est. reach 200 units", it.toString(), CLR_DANGER)
            }
        }
    }
}

@Composable
fun RecCard(rec: BillingCalculator.Recommendation) {
    val bg = when (rec.urgency) {
        BillingCalculator.Risk.SAFE -> Color(0xFFE8F5E9)
        BillingCalculator.Risk.WATCH -> Color(0xFFFFFDE7)
        BillingCalculator.Risk.WARNING -> Color(0xFFFFF3E0)
        BillingCalculator.Risk.DANGER -> Color(0xFFFFEBEE)
        BillingCalculator.Risk.CRITICAL -> Color(0xFFFCE4EC)
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bg)) {
        Text(rec.msg, Modifier.padding(12.dp), color = rec.urgency.color(), fontSize = 13.sp)
    }
}

fun fmt(d: Double): String = String.format("%.1f", d)
