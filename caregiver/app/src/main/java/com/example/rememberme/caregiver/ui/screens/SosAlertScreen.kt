package com.example.rememberme.caregiver.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rememberme.caregiver.data.NetworkClient
import com.example.rememberme.caregiver.data.PreferencesManager
import com.example.rememberme.caregiver.data.SosAlert
import com.example.rememberme.caregiver.ui.theme.*

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed class SosUiState {
    object Loading : SosUiState()
    data class Active(val alert: SosAlert) : SosUiState()
    data class Calm(val patientName: String, val lastSeen: String) : SosUiState()
    data class Error(val message: String) : SosUiState()
}

class SosViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<SosUiState>(SosUiState.Loading)
    val uiState: StateFlow<SosUiState> = _uiState.asStateFlow()

    private var pollingJob: kotlinx.coroutines.Job? = null
    private var lastSeenTimestamp: String = "Never seen"

    fun startPolling(caregiverPhone: String, apiUrl: String) {
        if (caregiverPhone.isBlank() || apiUrl.isBlank()) {
            _uiState.value = SosUiState.Error("Please configure settings in Settings tab.")
            return
        }
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val api = NetworkClient.createService(apiUrl)
                    val activeSos = api.getActiveSos(caregiverPhone)
                    if (activeSos != null && !activeSos.resolved) {
                        _uiState.value = SosUiState.Active(activeSos)
                        lastSeenTimestamp = formatTimestamp(activeSos.timestamp)
                    } else {
                        val patientName = preferencesManager.patientNameSync
                        _uiState.value = SosUiState.Calm(patientName, lastSeenTimestamp)
                    }
                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 404) {
                        val patientName = preferencesManager.patientNameSync
                        _uiState.value = SosUiState.Calm(patientName, lastSeenTimestamp)
                    } else {
                        _uiState.value = SosUiState.Error("Server returned code ${e.code()}. Check settings.")
                    }
                } catch (e: Exception) {
                    _uiState.value = SosUiState.Error("Cannot reach backend. Retrying...")
                }
                delay(30000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    fun resolveSos(alertId: String, caregiverPhone: String, apiUrl: String) {
        viewModelScope.launch {
            try {
                val api = NetworkClient.createService(apiUrl)
                val response = api.resolveSos(alertId)
                if (response.isSuccessful) {
                    val patientName = preferencesManager.patientNameSync
                    _uiState.value = SosUiState.Calm(patientName, lastSeenTimestamp)
                    startPolling(caregiverPhone, apiUrl)
                }
            } catch (e: Exception) {
                // Ignore resolve failures temporarily
            }
        }
    }

    private fun formatTimestamp(timestampStr: String): String {
        return try {
            val zonedDateTime = ZonedDateTime.parse(timestampStr)
            val localDateTime = zonedDateTime.withZoneSameInstant(ZoneId.systemDefault())
            localDateTime.format(DateTimeFormatter.ofPattern("hh:mm a, MMM dd"))
        } catch (e: Exception) {
            timestampStr
        }
    }
}

@Composable
fun SosAlertScreen(
    preferencesManager: PreferencesManager,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val caregiverPhone by preferencesManager.caregiverPhoneFlow.collectAsState(initial = "")
    val patientPhone by preferencesManager.patientPhoneFlow.collectAsState(initial = "")
    val apiUrl by preferencesManager.apiUrlFlow.collectAsState(initial = "")

    val factory = remember(preferencesManager) {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SosViewModel(preferencesManager) as T
            }
        }
    }
    
    val viewModel: SosViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(caregiverPhone, apiUrl) {
        if (caregiverPhone.isNotBlank() && apiUrl.isNotBlank()) {
            viewModel.startPolling(caregiverPhone, apiUrl)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPolling()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (uiState is SosUiState.Active) SosRed else InkColor)
    ) {
        when (val state = uiState) {
            is SosUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MintColor)
                }
            }
            is SosUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.startPolling(caregiverPhone, apiUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = MintColor)
                        ) {
                            Text("Retry Connection", color = InkColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is SosUiState.Calm -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            .border(2.dp, MintColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "💙", fontSize = 48.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = state.patientName,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Status: All Good",
                        color = MintColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Last seen: ${state.lastSeen}",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = onNavigateToHistory,
                        colors = ButtonDefaults.buttonColors(containerColor = PanelColor),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Default.History, contentDescription = null, tint = Color.White)
                            Text("View History", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is SosUiState.Active -> {
                val alert = state.alert
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Header Alert Text
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Text(
                            text = "CRITICAL ALARM ACTIVE",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "${alert.patientName} Triggered SOS",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Emergency alert at ${alert.timestamp}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(alert.locationLink))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PanelColor),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("📍 Open Location in Maps", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:$patientPhone")
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.Call, contentDescription = null, tint = SosRed)
                                Text("Call Patient", color = SosRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { viewModel.resolveSos(alert.id, caregiverPhone, apiUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                                Text("Mark as Safe", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
