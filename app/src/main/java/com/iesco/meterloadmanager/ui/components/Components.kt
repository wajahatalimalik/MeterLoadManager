package com.iesco.meterloadmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iesco.meterloadmanager.ui.theme.CLR_PRIMARY
import com.iesco.meterloadmanager.ui.theme.color
import com.iesco.meterloadmanager.ui.theme.meterColor
import com.iesco.meterloadmanager.utils.BillingCalculator
import com.iesco.meterloadmanager.viewmodel.MeterCard

@Composable
fun RiskBar(progress: Float, risk: BillingCalculator.Risk, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFFE0E0E0))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .clip(RoundedCornerShape(5.dp))
                .background(risk.color())
        )
    }
}

@Composable
fun RiskChip(risk: BillingCalculator.Risk) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(risk.color())
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(risk.label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MeterChip(n: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(meterColor(n))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text("M-$n", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Stat(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF666666))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = CLR_PRIMARY,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun MeterCardView(c: MeterCard) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                MeterChip(c.meter.meterNumber)
                RiskChip(c.risk)
            }
            Spacer(Modifier.height(4.dp))
            Text(c.meter.purpose, fontSize = 11.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(6.dp))
            Text("${fmt(c.consumed)} / 199 units", fontSize = 12.sp)
            RiskBar(c.progress, c.risk, Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Divider(Modifier.padding(vertical = 6.dp))
            Stat("Cycle Start Reading", c.meter.cycleStartReading.toString())
            Stat("Current Reading", c.meter.currentReading.toString())
            Stat("Units Consumed", fmt(c.consumed), c.risk.color())
            Stat("Status", c.meter.status.name.replace("_", " "))
            Divider(Modifier.padding(vertical = 5.dp))
            Stat("Remaining to 150", fmt(c.to150))
            Stat("Remaining to 180", fmt(c.to180))
            Stat("Remaining to 190", fmt(c.to190))
            Stat("Remaining to 199", fmt(c.to199))
            Divider(Modifier.padding(vertical = 5.dp))
            Stat("Daily Average", "${fmt(c.dailyAvg)} units/day")
            Stat(
                "Projected by Cycle End",
                "${fmt(c.projected)} units",
                when {
                    c.projected > 199 -> Color(0xFF4A0000)
                    c.projected > 150 -> Color(0xFFE65100)
                    else -> Color(0xFF2E7D32)
                }
            )
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
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Text(rec.msg, Modifier.padding(12.dp), color = rec.urgency.color(), fontSize = 13.sp)
    }
}

fun fmt(d: Double): String = String.format("%.1f", d)
