package com.example.rememberme.caregiver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rememberme.caregiver.data.NetworkClient
import com.example.rememberme.caregiver.data.PreferencesManager
import com.example.rememberme.caregiver.data.VisitorEntry
import com.example.rememberme.caregiver.ui.theme.BorderColor
import com.example.rememberme.caregiver.ui.theme.InkColor
import com.example.rememberme.caregiver.ui.theme.MintColor
import com.example.rememberme.caregiver.ui.theme.PanelColor
import com.example.rememberme.caregiver.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorLogScreen(
    preferencesManager: PreferencesManager,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val caregiverPhone by preferencesManager.caregiverPhoneFlow.collectAsState(initial = "")
    val apiUrl by preferencesManager.apiUrlFlow.collectAsState(initial = "")

    var visitorsList by remember { mutableStateOf<List<VisitorEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun fetchVisitorLog() {
        if (caregiverPhone.isBlank() || apiUrl.isBlank()) return
        coroutineScope.launch {
            isLoading = true
            errorMsg = null
            try {
                val api = NetworkClient.createService(apiUrl)
                val result = api.getMemoryLog(caregiverPhone)
                visitorsList = result
            } catch (e: Exception) {
                errorMsg = "Failed to load visitor log. Check server."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(caregiverPhone, apiUrl) {
        if (caregiverPhone.isNotBlank() && apiUrl.isNotBlank()) {
            fetchVisitorLog()
        }
    }

    val groupedEntries = remember(visitorsList) {
        val today = LocalDate.now(ZoneId.systemDefault())
        val yesterday = today.minusDays(1)

        visitorsList.groupBy { entry ->
            try {
                val zonedDateTime = ZonedDateTime.parse(entry.timestamp)
                val localDate = zonedDateTime.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                when (localDate) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> "Earlier"
                }
            } catch (e: Exception) {
                "Earlier"
            }
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
                    text = "VISITOR HISTORY",
                    color = MintColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Visitor Log",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = { fetchVisitorLog() },
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
                        onClick = { fetchVisitorLog() },
                        colors = ButtonDefaults.buttonColors(containerColor = MintColor)
                    ) {
                        Text("Retry", color = InkColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (visitorsList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No visitor events recorded yet.", color = Color.Gray, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val categories = listOf("Today", "Yesterday", "Earlier")
                for (category in categories) {
                    val entries = groupedEntries[category]
                    if (!entries.isNullOrEmpty()) {
                        item {
                            Text(
                                text = category.uppercase(),
                                color = MintColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(entries) { entry ->
                            VisitorCard(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisitorCard(entry: VisitorEntry) {
    val formattedTime = remember(entry.timestamp) {
        try {
            val zonedDateTime = ZonedDateTime.parse(entry.timestamp)
            val localTime = zonedDateTime.withZoneSameInstant(ZoneId.systemDefault()).toLocalTime()
            localTime.format(DateTimeFormatter.ofPattern("hh:mm a"))
        } catch (e: Exception) {
            ""
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PanelColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = entry.personName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = entry.relationship,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                if (formattedTime.isNotBlank()) {
                    Text(
                        text = formattedTime,
                        color = MintColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (entry.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = BorderColor)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = entry.summary,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
