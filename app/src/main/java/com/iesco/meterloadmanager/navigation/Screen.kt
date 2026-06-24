package com.iesco.meterloadmanager.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard   : Screen("dashboard",   "Home",       Icons.Default.Home)
    object History     : Screen("history",     "History",    Icons.Default.History)
    object Analytics   : Screen("analytics",   "Analytics",  Icons.Default.BarChart)
    object Switches    : Screen("switches",    "Switches",   Icons.Default.ToggleOn)
    object Appliances  : Screen("appliances",  "Appliances", Icons.Default.Bolt)
    object Export      : Screen("export",      "Export",     Icons.Default.FileDownload)
    // Not in bottom nav – opened via FAB
    object Reading     : Screen("reading",     "Reading",    Icons.Default.AddCircle)
    object Alerts      : Screen("alerts",      "Alerts",     Icons.Default.Notifications)
}

val NAV_ITEMS = listOf(
    Screen.Dashboard, Screen.History, Screen.Analytics,
    Screen.Switches, Screen.Appliances, Screen.Export
)
