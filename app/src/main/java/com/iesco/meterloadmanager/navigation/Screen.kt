package com.iesco.meterloadmanager.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Home",     Icons.Default.Home)
    object Reading   : Screen("reading",   "Reading",  Icons.Default.AddCircle)
    object History   : Screen("history",   "History",  Icons.Default.History)
    object Analytics : Screen("analytics", "Analytics",Icons.Default.BarChart)
    object Switches  : Screen("switches",  "Switches", Icons.Default.ToggleOn)
    object Export    : Screen("export",    "Export",   Icons.Default.FileDownload)
}

val NAV_ITEMS = listOf(
    Screen.Dashboard, Screen.Reading, Screen.History,
    Screen.Analytics, Screen.Switches, Screen.Export
)
