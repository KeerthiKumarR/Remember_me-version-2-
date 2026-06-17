package com.example.rememberme.caregiver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.rememberme.caregiver.data.PreferencesManager
import com.example.rememberme.caregiver.service.SosPollingService
import com.example.rememberme.caregiver.ui.navigation.BottomNavigationBar
import com.example.rememberme.caregiver.ui.navigation.Screen
import com.example.rememberme.caregiver.ui.screens.DailySummaryScreen
import com.example.rememberme.caregiver.ui.screens.SettingsScreen
import com.example.rememberme.caregiver.ui.screens.SosAlertScreen
import com.example.rememberme.caregiver.ui.screens.VisitorLogScreen
import com.example.rememberme.caregiver.ui.theme.RememberMeCaregiverTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Permissions callback received: $permissions")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(this)

        requestRequiredPermissions()
        startPollingService()

        setContent {
            RememberMeCaregiverTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                LaunchedEffect(intent) {
                    if (intent?.getBooleanExtra("LAUNCH_SOS_SCREEN", false) == true) {
                        navController.navigate(Screen.SosAlert.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                Scaffold(
                    bottomBar = {
                        BottomNavigationBar(
                            currentRoute = currentRoute,
                            onNavigate = { screen ->
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.SosAlert.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.SosAlert.route) {
                            SosAlertScreen(
                                preferencesManager = preferencesManager,
                                onNavigateToHistory = {
                                    navController.navigate(Screen.VisitorLog.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                        composable(Screen.VisitorLog.route) {
                            VisitorLogScreen(preferencesManager = preferencesManager)
                        }
                        composable(Screen.DailySummary.route) {
                            DailySummaryScreen(preferencesManager = preferencesManager)
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(preferencesManager = preferencesManager)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startPollingService() {
        val serviceIntent = Intent(this, SosPollingService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed starting SosPollingService", e)
        }
    }
}
