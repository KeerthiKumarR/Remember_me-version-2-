package com.example.rememberme.caregiver.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object SosAlert : Screen("sos_alert", "SOS", Icons.Default.Warning)
    object VisitorLog : Screen("visitor_log", "Visitor Log", Icons.Default.People)
    object DailySummary : Screen("daily_summary", "Summary", Icons.Default.Assignment)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}
