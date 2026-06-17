package com.example.rememberme.caregiver.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rememberme.caregiver.data.PreferencesManager
import com.example.rememberme.caregiver.ui.theme.BorderColor
import com.example.rememberme.caregiver.ui.theme.InkColor
import com.example.rememberme.caregiver.ui.theme.MintColor
import com.example.rememberme.caregiver.ui.theme.PanelColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentPatientName by preferencesManager.patientNameFlow.collectAsState(initial = "Patient")
    val currentPatientPhone by preferencesManager.patientPhoneFlow.collectAsState(initial = "1234567890")
    val currentCaregiverPhone by preferencesManager.caregiverPhoneFlow.collectAsState(initial = "9876543210")
    val currentApiUrl by preferencesManager.apiUrlFlow.collectAsState(initial = "https://miraiwininghacathonproject-production.up.railway.app")

    var patientName by remember { mutableStateOf("") }
    var patientPhone by remember { mutableStateOf("") }
    var caregiverPhone by remember { mutableStateOf("") }
    var apiUrl by remember { mutableStateOf("") }

    LaunchedEffect(currentPatientName, currentPatientPhone, currentCaregiverPhone, currentApiUrl) {
        patientName = currentPatientName
        patientPhone = currentPatientPhone
        caregiverPhone = currentCaregiverPhone
        apiUrl = currentApiUrl
    }

    var isSaving by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(InkColor)
            .padding(24.dp)
    ) {
        Text(
            text = "CONFIGURATION",
            color = MintColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = PanelColor),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Caregiver & Patient Details",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it },
                    label = { Text("Patient Name", color = Color.Gray) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintColor,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = MintColor
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = patientPhone,
                    onValueChange = { patientPhone = it },
                    label = { Text("Patient Phone Number", color = Color.Gray) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintColor,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = MintColor
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = caregiverPhone,
                    onValueChange = { caregiverPhone = it },
                    label = { Text("Caregiver Phone (Sent to Backend)", color = Color.Gray) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintColor,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = MintColor
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = { apiUrl = it },
                    label = { Text("API Server Base URL", color = Color.Gray) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintColor,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = MintColor
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (patientName.isBlank() || patientPhone.isBlank() || caregiverPhone.isBlank() || apiUrl.isBlank()) {
                            Toast.makeText(context, "Please fill in all fields.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSaving = true
                        coroutineScope.launch {
                            preferencesManager.saveSettings(
                                patientName = patientName.trim(),
                                patientPhone = patientPhone.trim(),
                                caregiverPhone = caregiverPhone.trim(),
                                apiUrl = apiUrl.trim()
                            )
                            isSaving = false
                            Toast.makeText(context, "Settings Saved Successfully!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = MintColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = if (isSaving) "Saving..." else "Save Configuration",
                        color = InkColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
