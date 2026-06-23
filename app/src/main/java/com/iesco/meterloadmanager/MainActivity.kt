package com.iesco.meterloadmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import com.iesco.meterloadmanager.navigation.NAV_ITEMS
import com.iesco.meterloadmanager.navigation.Screen
import com.iesco.meterloadmanager.ui.screens.*
import com.iesco.meterloadmanager.ui.theme.AppTheme
import com.iesco.meterloadmanager.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { App() } }
    }
}

@Composable
fun App() {
    val nav = rememberNavController()
    val dashVM: DashboardVM = viewModel()
    val readVM: ReadingVM   = viewModel()
    val histVM: HistoryVM   = viewModel()
    val swVM:  SwitchVM     = viewModel()
    val anaVM: AnalyticsVM  = viewModel()
    val expVM: ExportVM     = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val entry by nav.currentBackStackEntryAsState()
                val current = entry?.destination
                NAV_ITEMS.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, screen.label) },
                        label = { Text(screen.label) },
                        selected = current?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            nav.navigate(screen.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = Screen.Dashboard.route, modifier = Modifier.padding(pad)) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(dashVM) { nav.navigate(Screen.Reading.route) }
            }
            composable(Screen.Reading.route)   { AddReadingScreen(readVM) }
            composable(Screen.History.route)   { HistoryScreen(histVM) }
            composable(Screen.Analytics.route) { AnalyticsScreen(anaVM) }
            composable(Screen.Switches.route)  { SwitchScreen(swVM) }
            composable(Screen.Export.route)    { ExportScreen(expVM) }
        }
    }
}
