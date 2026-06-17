package com.example.rememberme.caregiver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rememberme.caregiver.data.DailySummary
import com.example.rememberme.caregiver.data.NetworkClient
import com.example.rememberme.caregiver.data.PreferencesManager
import com.example.rememberme.caregiver.ui.theme.BorderColor
import com.example.rememberme.caregiver.ui.theme.InkColor
import com.example.rememberme.caregiver.ui.theme.MintColor
import com.example.rememberme.caregiver.ui.theme.PanelColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySummaryScreen(
    preferencesManager: PreferencesManager,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val caregiverPhone by preferencesManager.caregiverPhoneFlow.collectAsState(initial = "")
    val apiUrl by preferencesManager.apiUrlFlow.collectAsState(initial = "")

    var summaryData by remember { mutableStateOf<DailySummary?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun fetchSummary() {
        if (caregiverPhone.isBlank() || apiUrl.isBlank()) return
        coroutineScope.launch {
            isLoading = true
            errorMsg = null
            try {
                val api = NetworkClient.createService(apiUrl)
                val result = api.getDailySummary(caregiverPhone)
                summaryData = result
            } catch (e: Exception) {
                errorMsg = "Failed to load summary. Check server connection."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(caregiverPhone, apiUrl) {
        if (caregiverPhone.isNotBlank() && apiUrl.isNotBlank()) {
            fetchSummary()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(InkColor)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DAILY REPORT",
                    color = MintColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Summary",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = { fetchSummary() },
                enabled = !isLoading,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MintColor)
            }
        } else if (errorMsg != null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = errorMsg ?: "", color = Color.Red, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { fetchSummary() },
                        colors = ButtonDefaults.buttonColors(containerColor = MintColor)
                    ) {
                        Text("Retry", color = InkColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            val summary = summaryData
            if (summary == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No summary information available. Set up caregiver phone in Settings.", color = Color.Gray, textAlign = TextAlign.Center)
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = PanelColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "AI Companion Summary",
                                color = MintColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Text(
                                text = summary.summary,
                                color = Color.White,
                                fontSize = 16.sp,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    Text(
                        text = "TODAY'S METRICS",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = "People Met",
                            value = summary.peopleMetCount.toString(),
                            icon = Icons.Default.People,
                            iconColor = MintColor,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            title = "App Opens",
                            value = summary.appOpenCount.toString(),
                            icon = Icons.Default.Smartphone,
                            iconColor = Color(0xFF3B82F6),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    StatCard(
                        title = "SOS Events Triggered",
                        value = summary.sosCount.toString(),
                        icon = Icons.Default.Warning,
                        iconColor = if (summary.sosCount > 0) Color(0xFFDC2626) else Color.Gray,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        modifier = modifier.border(1.dp, BorderColor, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = title, color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
