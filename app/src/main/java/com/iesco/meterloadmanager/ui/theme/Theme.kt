package com.iesco.meterloadmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.iesco.meterloadmanager.utils.BillingCalculator

val CLR_SAFE = Color(0xFF2E7D32)
val CLR_WATCH = Color(0xFFF9A825)
val CLR_WARNING = Color(0xFFE65100)
val CLR_DANGER = Color(0xFFB71C1C)
val CLR_CRITICAL = Color(0xFF4A0000)
val CLR_PRIMARY = Color(0xFF1565C0)
val CLR_BG = Color(0xFFF0F4FA)
val CLR_600 = Color(0xFF1565C0)
val CLR_603 = Color(0xFF00695C)
val CLR_700 = Color(0xFF6A1B9A)

private val scheme = lightColorScheme(
    primary = CLR_PRIMARY,
    onPrimary = Color.White,
    background = CLR_BG,
    surface = Color.White,
    surfaceVariant = Color(0xFFE3EAF4)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, content = content)

fun BillingCalculator.Risk.color(): Color = when (this) {
    BillingCalculator.Risk.SAFE -> CLR_SAFE
    BillingCalculator.Risk.WATCH -> CLR_WATCH
    BillingCalculator.Risk.WARNING -> CLR_WARNING
    BillingCalculator.Risk.DANGER -> CLR_DANGER
    BillingCalculator.Risk.CRITICAL -> CLR_CRITICAL
}

fun meterColor(n: String): Color = when (n) {
    "600" -> CLR_600
    "603" -> CLR_603
    else -> CLR_700
}
