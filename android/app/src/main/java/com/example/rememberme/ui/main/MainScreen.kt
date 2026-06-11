package com.example.rememberme.ui.main

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import android.os.Build
import com.example.rememberme.notification.ReminderScheduler
import com.example.rememberme.data.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors

// Color Theme
val InkColor = Color(0xFF080B12)
val PanelColor = Color(0xFF111827)
val MintColor = Color(0xFF63E6BE)
val BorderColor = Color(0xFF2E333F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToEnroll: () -> Unit,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Preferences and states
    val prefManager = remember { PreferencesManager(context) }
    var statusText by remember { mutableStateOf("Looking for a familiar face...") }
    var recognition by remember { mutableStateOf<SummaryResponse?>(null) }
    
    // API config modal
    var showApiSettings by remember { mutableStateOf(false) }
    var apiInputUrl by remember { mutableStateOf(prefManager.apiUrl) }
    var caregiverInputName by remember { mutableStateOf(prefManager.caregiverName) }
    var caregiverInputPhone by remember { mutableStateOf(prefManager.caregiverPhone) }
    var remindersInputEnabled by remember { mutableStateOf(prefManager.remindersEnabled) }

    // Memory modal
    var showMemoryModal by remember { mutableStateOf(false) }
    var memoryNote by remember { mutableStateOf("") }
    var isSavingMemory by remember { mutableStateOf(false) }
    var isMemorySaved by remember { mutableStateOf(false) }

    // Active Caregiver tracking states
    var activeCaregiverName by remember { mutableStateOf("") }
    var activeCaregiverPhone by remember { mutableStateOf("") }

    // SOS States
    var isSosActive by remember { mutableStateOf(false) }
    val locationClient = remember { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context) }

    fun hasLocationPermissions(c: Context): Boolean {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isLocationServicesEnabled(c: Context): Boolean {
        val locationManager = c.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        return gpsEnabled || networkEnabled
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            if (isLocationServicesEnabled(context)) {
                isSosActive = true
            } else {
                android.widget.Toast.makeText(context, "Please enable GPS/Location Services.", android.widget.Toast.LENGTH_LONG).show()
                try {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } else {
            android.widget.Toast.makeText(context, "Location permissions are required for SOS.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(isSosActive) {
        if (isSosActive) {
            while (isSosActive) {
                sendSosLocationUpdate(
                    context = context,
                    locationClient = locationClient,
                    prefManager = prefManager,
                    activeName = activeCaregiverName,
                    activePhone = activeCaregiverPhone,
                    coroutineScope = coroutineScope,
                    onStatusChange = { statusText = it }
                )
                delay(30000)
            }
        }
    }

    // Camera states
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        FaceDetection.getClient(options)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(InkColor),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = InkColor,
                    titleContentColor = Color.White
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Remember",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Me",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MintColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        apiInputUrl = prefManager.apiUrl
                        caregiverInputName = prefManager.caregiverName
                        caregiverInputPhone = prefManager.caregiverPhone
                        remindersInputEnabled = prefManager.remindersEnabled
                        showApiSettings = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "API Settings",
                            tint = Color.LightGray
                        )
                    }
                    Button(
                        onClick = {
                            prefManager.cameraEnabled = true
                            onNavigateToEnroll()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    ) {
                        Text(
                            text = "Add familiar face",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isSosActive) {
                        isSosActive = false
                        statusText = "SOS Alert Cancelled"
                        android.widget.Toast.makeText(context, "SOS Alert Cancelled", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        if (hasLocationPermissions(context)) {
                            if (isLocationServicesEnabled(context)) {
                                isSosActive = true
                            } else {
                                android.widget.Toast.makeText(context, "Please enable GPS/Location Services.", android.widget.Toast.LENGTH_LONG).show()
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                },
                containerColor = if (isSosActive) Color(0xFFDC2626) else Color(0xFFEF4444),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(68.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (isSosActive) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SOS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("ACTIVE", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    } else {
                        Text("SOS", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(InkColor)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Titles
                Text(
                    text = "YOUR MEMORY COMPANION",
                    color = MintColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "See someone you know.",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Main Camera Feed Container (Refactored to nested function to avoid ColumnScope resolution conflict)
                DashboardCamera(
                    cameraPermissionGranted = cameraPermissionGranted,
                    prefManager = prefManager,
                    faceDetector = faceDetector,
                    statusText = statusText,
                    recognition = recognition,
                    activeCaregiverName = activeCaregiverName,
                    activeCaregiverPhone = activeCaregiverPhone,
                    onStatusTextChange = { statusText = it },
                    onRecognitionChange = { recognition = it },
                    onActiveCaregiverNameChange = { activeCaregiverName = it },
                    onActiveCaregiverPhoneChange = { activeCaregiverPhone = it },
                    onShowMemoryModal = { showMemoryModal = true },
                    isActive = isActive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    // Modal Dialog for adding Memory Logs
    if (showMemoryModal && recognition != null) {
        val rec = recognition!!
        Dialog(onDismissRequest = {
            showMemoryModal = false
            memoryNote = ""
            isMemorySaved = false
        }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Add a memory",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Write a note about ${rec.name} — it will make future summaries more personal.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Text Area input
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        if (memoryNote.isEmpty()) {
                            Text(
                                text = "e.g. \"${rec.name} visited today, we watched the game and had tea.\"",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = memoryNote,
                            onValueChange = { memoryNote = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(MintColor),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (memoryNote.trim().isNotEmpty()) {
                                    isSavingMemory = true
                                    coroutineScope.launch {
                                        try {
                                            val api = NetworkClient.createService(prefManager.apiUrl)
                                            api.logMemory(MemoryLogRequest(rec.person_id, memoryNote.trim()))
                                            isMemorySaved = true
                                            delay(1200)
                                            showMemoryModal = false
                                            memoryNote = ""
                                            isMemorySaved = false
                                        } catch (e: Exception) {
                                            Log.e("SaveMemory", "Failed to save memory", e)
                                            android.widget.Toast.makeText(context, "Failed to save memory. Please check your network connection.", android.widget.Toast.LENGTH_LONG).show()
                                        } finally {
                                            isSavingMemory = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSavingMemory && memoryNote.trim().isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MintColor),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isMemorySaved) "✓ Saved!" else if (isSavingMemory) "Saving..." else "Save memory",
                                color = InkColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                showMemoryModal = false
                                memoryNote = ""
                                isMemorySaved = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                        ) {
                            Text(text = "Cancel", color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog for configuring dynamic API Base URL
    if (showApiSettings) {
        Dialog(onDismissRequest = { showApiSettings = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    var titleClickCount by remember { mutableStateOf(0) }

                    Text(
                        text = "Server Configuration",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .clickable { titleClickCount++ }
                    )
                    Text(
                        text = "Configure caregiver details used for SOS alerts.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // URL text input (Developer Mode)
                    if (titleClickCount >= 5) {
                        OutlinedTextField(
                            value = apiInputUrl,
                            onValueChange = { apiInputUrl = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            label = { Text("API Base URL (Developer Mode)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MintColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedLabelColor = MintColor,
                                unfocusedLabelColor = Color.Gray
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Caregiver Name input
                    OutlinedTextField(
                        value = caregiverInputName,
                        onValueChange = { caregiverInputName = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        label = { Text("Default Caregiver Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MintColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = MintColor,
                            unfocusedLabelColor = Color.Gray
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Caregiver Phone input
                    OutlinedTextField(
                        value = caregiverInputPhone,
                        onValueChange = { caregiverInputPhone = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        label = { Text("Default Caregiver Phone") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MintColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = MintColor,
                            unfocusedLabelColor = Color.Gray
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Dementia Reminders Row
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dementia Reminders",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enable periodic voice and push notifications.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = remindersInputEnabled,
                            onCheckedChange = { remindersInputEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MintColor,
                                checkedTrackColor = MintColor.copy(alpha = 0.4f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }

                    // Battery Optimization Row
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
                        val isIgnoring = remember {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                powerManager.isIgnoringBatteryOptimizations(context.packageName)
                            } else {
                                true
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery Optimization",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isIgnoring) "Status: Unrestricted (Good)" else "Status: Optimized (May delay reminders)",
                                color = if (isIgnoring) MintColor else Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        if (!isIgnoring) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent().apply {
                                            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("Settings", "Failed battery request", e)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MintColor),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Disable", color = InkColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("✓", color = MintColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                prefManager.apiUrl = apiInputUrl.trim()
                                prefManager.caregiverName = caregiverInputName.trim()
                                prefManager.caregiverPhone = caregiverInputPhone.trim()
                                prefManager.remindersEnabled = remindersInputEnabled
                                if (remindersInputEnabled) {
                                    ReminderScheduler.schedule(context)
                                } else {
                                    ReminderScheduler.cancel(context)
                                }
                                showApiSettings = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MintColor),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Save", color = InkColor, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showApiSettings = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                        ) {
                            Text(text = "Cancel", color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCamera(
    cameraPermissionGranted: Boolean,
    prefManager: PreferencesManager,
    faceDetector: com.google.mlkit.vision.face.FaceDetector,
    statusText: String,
    recognition: SummaryResponse?,
    activeCaregiverName: String,
    activeCaregiverPhone: String,
    onStatusTextChange: (String) -> Unit,
    onRecognitionChange: (SummaryResponse?) -> Unit,
    onActiveCaregiverNameChange: (String) -> Unit,
    onActiveCaregiverPhoneChange: (String) -> Unit,
    onShowMemoryModal: () -> Unit,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Wrap state arguments in rememberUpdatedState to avoid stale captures on background thread
    val latestRecognition by rememberUpdatedState(recognition)
    val latestOnStatusTextChange by rememberUpdatedState(onStatusTextChange)
    val latestOnRecognitionChange by rememberUpdatedState(onRecognitionChange)
    val latestOnActiveCaregiverNameChange by rememberUpdatedState(onActiveCaregiverNameChange)
    val latestOnActiveCaregiverPhoneChange by rememberUpdatedState(onActiveCaregiverPhoneChange)

    var lastIdentifyTime by remember { mutableLongStateOf(0L) }
    var isIdentifyInFlight by remember { mutableStateOf(false) }
    var cachedPersonId by remember { mutableStateOf("") }

    LaunchedEffect(recognition) {
        if (recognition == null) {
            cachedPersonId = ""
            onActiveCaregiverNameChange("")
            onActiveCaregiverPhoneChange("")
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(32.dp))
            .background(PanelColor)
    ) {
        var isCameraActive by remember(prefManager.cameraEnabled, isActive) {
            mutableStateOf(prefManager.cameraEnabled && isActive)
        }

        DisposableEffect(isCameraActive) {
            onDispose {
                if (!prefManager.cameraEnabled) {
                    try {
                        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                        cameraProvider.unbindAll()
                    } catch (e: Exception) {
                        Log.e("CameraSetup", "Error unbinding camera on pause", e)
                    }
                }
            }
        }

        LaunchedEffect(isCameraActive) {
            if (!isCameraActive) {
                onStatusTextChange("Camera is inactive")
                onRecognitionChange(null)
                onActiveCaregiverNameChange("")
                onActiveCaregiverPhoneChange("")
            } else if (cameraPermissionGranted) {
                onStatusTextChange("Looking for a familiar face...")
            }
        }

        if (cameraPermissionGranted && isCameraActive) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(
                            Executors.newSingleThreadExecutor()
                        ) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                faceDetector.process(inputImage)
                                    .addOnSuccessListener { faces ->
                                        if (faces.isNotEmpty()) {
                                            val face = faces[0]
                                            val smile = face.smilingProbability ?: 0f
                                            val yaw = face.headEulerAngleY

                                            val now = System.currentTimeMillis()
                                            if (now - lastIdentifyTime > 3000 && !isIdentifyInFlight) {
                                                lastIdentifyTime = now
                                                isIdentifyInFlight = true

                                                try {
                                                    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                                    val rawBitmap = imageProxy.toBitmap()
                                                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                                    val rotatedBitmap = if (rotationDegrees != 0) {
                                                        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                                        val rb = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                                        if (rb != rawBitmap) {
                                                            rawBitmap.recycle()
                                                        }
                                                        rb
                                                    } else {
                                                        rawBitmap
                                                    }

                                                    val bitmap = resizeBitmap(rotatedBitmap, 640)
                                                    val stream = ByteArrayOutputStream()
                                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)

                                                    if (bitmap != rotatedBitmap) {
                                                        bitmap.recycle()
                                                    }
                                                    rotatedBitmap.recycle()

                                                    val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                                                    val imagePayload = "data:image/jpeg;base64,$base64"

                                                    coroutineScope.launch {
                                                        try {
                                                            latestOnStatusTextChange("Checking this face...")
                                                            Log.d("IdentifyAPI", "Sending base64 image to server...")
                                                            val api = NetworkClient.createService(prefManager.apiUrl)
                                                            val response = api.identifyFace(ImagePayload(imagePayload))
                                                            val match = response.match
                                                            Log.d("IdentifyAPI", "Result: match=${match?.name}, confidence=${response.confidence}")
                                                            if (match != null) {
                                                                latestOnStatusTextChange("Familiar face recognized")
                                                                latestOnActiveCaregiverNameChange(match.name)
                                                                latestOnActiveCaregiverPhoneChange(match.caregiver_phone ?: "")
                                                                if (cachedPersonId != match.person_id) {
                                                                    val summary = api.summarizePerson(SummarizeRequest(match.person_id))
                                                                    latestOnRecognitionChange(summary)
                                                                    cachedPersonId = match.person_id
                                                                } else {
                                                                    latestOnRecognitionChange(
                                                                        latestRecognition?.copy(
                                                                            name = match.name,
                                                                            relationship = match.relationship
                                                                        )
                                                                    )
                                                                }
                                                            } else {
                                                                latestOnRecognitionChange(null)
                                                                latestOnStatusTextChange("Looking for a familiar face...")
                                                                latestOnActiveCaregiverNameChange("")
                                                                latestOnActiveCaregiverPhoneChange("")
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("IdentifyAPI", "Error calling identify API", e)
                                                            latestOnRecognitionChange(null)
                                                            val isNetwork = e is java.io.IOException || 
                                                                            e is java.net.UnknownHostException || 
                                                                            e is java.net.ConnectException || 
                                                                            e is java.net.SocketTimeoutException || 
                                                                            (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) || 
                                                                            (e.message?.contains("Failed to connect", ignoreCase = true) == true)
                                                            if (isNetwork) {
                                                                latestOnStatusTextChange("Unable to connect to server")
                                                            } else {
                                                                latestOnStatusTextChange("Connection failed")
                                                            }
                                                            latestOnActiveCaregiverNameChange("")
                                                            latestOnActiveCaregiverPhoneChange("")
                                                        } finally {
                                                            isIdentifyInFlight = false
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("ImageConversion", "Failed converting frame to bitmap", e)
                                                    isIdentifyInFlight = false
                                                }
                                            }
                                        } else {
                                            latestOnStatusTextChange("Looking for a familiar face...")
                                            latestOnRecognitionChange(null)
                                            latestOnActiveCaregiverNameChange("")
                                            latestOnActiveCaregiverPhoneChange("")
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("CameraSetup", "Use case binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Right Stop Camera Button
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
            ) {
                Button(
                    onClick = {
                        isCameraActive = false
                        prefManager.cameraEnabled = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Text(
                            text = "Stop Camera",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else if (cameraPermissionGranted && !isCameraActive) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.03f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Text(
                            text = "📷",
                            fontSize = 32.sp
                        )
                    }

                    Text(
                        text = "Camera is stopped",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Turn on the camera to start recognizing faces.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            isCameraActive = true
                            prefManager.cameraEnabled = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MintColor,
                            contentColor = InkColor
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "Start Camera",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(32.dp)
            ) {
                Text(
                    text = "Camera access is needed to recognize familiar faces.",
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp
                )
            }
        }

        // Shadow Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.20f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        // Top Left Status Pill
        Box(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isCameraActive) MintColor else Color.Gray)
                )
                Text(
                    text = statusText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
            }
        }


        // Bottom Overlay card for Recognition Results (Now resolves to top-level AnimatedVisibility perfectly!)
        AnimatedVisibility(
            visible = recognition != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            recognition?.let { rec ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xEC090D16)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MintColor.copy(alpha = 0.15f))
                                    .border(1.dp, MintColor.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                            ) {
                                Text(
                                    text = rec.name.take(1).uppercase(Locale.ROOT),
                                    color = MintColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = rec.relationship.uppercase(Locale.ROOT),
                                    color = MintColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = rec.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MintColor.copy(alpha = 0.10f))
                                    .border(1.dp, MintColor.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Verified Match",
                                    color = MintColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = rec.summary,
                            color = Color(0xFFD1D5DB),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Button(
                            onClick = onShowMemoryModal,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Text(
                                text = "＋ Add a memory",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDimension && height <= maxDimension) return bitmap
    
    val aspectRatio = width.toFloat() / height.toFloat()
    val newWidth: Int
    val newHeight: Int
    if (width > height) {
        newWidth = maxDimension
        newHeight = (maxDimension / aspectRatio).toInt()
    } else {
        newHeight = maxDimension
        newWidth = (maxDimension * aspectRatio).toInt()
    }
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

@android.annotation.SuppressLint("MissingPermission")
private fun sendSosLocationUpdate(
    context: Context,
    locationClient: com.google.android.gms.location.FusedLocationProviderClient,
    prefManager: PreferencesManager,
    activeName: String,
    activePhone: String,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onStatusChange: (String) -> Unit
) {
    onStatusChange("Fetching current location...")
    
    // 1. Check if location services are enabled
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
    val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    
    if (!isGpsEnabled && !isNetworkEnabled) {
        val isEmulator = (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.PRODUCT.contains("sdk_google")
                || android.os.Build.PRODUCT.contains("google_sdk")
                || android.os.Build.PRODUCT.contains("sdk")
                || android.os.Build.PRODUCT.contains("sdk_x86")
                || android.os.Build.PRODUCT.contains("vbox86p")
                || android.os.Build.PRODUCT.contains("emulator")
                || android.os.Build.PRODUCT.contains("simulator")

        val errorMsg = if (isEmulator) {
            "Location is disabled. Please enable it in the emulator settings."
        } else {
            "Location is disabled. Please enable GPS/Location Services."
        }
        android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
        onStatusChange("SOS failed: GPS disabled")
        return
    }

    // 2. Define a helper to perform the SOS API call
    fun sendSosWithLocation(loc: android.location.Location) {
        val lat = loc.latitude
        val lon = loc.longitude
        val mapsLink = "https://maps.google.com/?q=$lat,$lon"
        
        val nameToSend = if (activeName.isNotEmpty()) activeName else if (prefManager.caregiverName.isNotEmpty()) prefManager.caregiverName else "User"
        val phoneToSend = if (activePhone.isNotEmpty()) activePhone else if (prefManager.caregiverPhone.isNotEmpty()) prefManager.caregiverPhone else "9876543210"
        
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        coroutineScope.launch {
            try {
                val api = NetworkClient.createService(prefManager.apiUrl)
                api.sendSos(
                    SosRequest(
                        person_name = nameToSend,
                        caregiver_phone = phoneToSend,
                        latitude = lat,
                        longitude = lon,
                        location_link = mapsLink,
                        timestamp = timestamp
                    )
                )
                Log.d("SOS_API", "SOS successfully sent to $phoneToSend")
                android.widget.Toast.makeText(context, "Emergency alert sent to caregiver!", android.widget.Toast.LENGTH_SHORT).show()
                onStatusChange("Emergency SOS Alert Sent")
            } catch (e: Exception) {
                Log.e("SOS_API", "Failed to send SOS", e)
                val isNetwork = e is java.io.IOException || 
                                e is java.net.UnknownHostException || 
                                e is java.net.ConnectException || 
                                e is java.net.SocketTimeoutException || 
                                (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) || 
                                (e.message?.contains("Failed to connect", ignoreCase = true) == true)
                val userMsg = if (isNetwork) "Unable to connect to the server. Please check your network connection." else "SOS alert failed. Please try again."
                android.widget.Toast.makeText(context, userMsg, android.widget.Toast.LENGTH_LONG).show()
                onStatusChange("SOS send failed")
            }
        }
    }

    // 3. Define a fallback to check lastLocation
    fun tryLastLocation(fallbackReason: String) {
        Log.d("SOS_GPS", "Attempting fallback to lastLocation. Reason: $fallbackReason")
        locationClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null) {
                sendSosWithLocation(lastLoc)
            } else {
                val isEmulator = (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                        || android.os.Build.FINGERPRINT.startsWith("generic")
                        || android.os.Build.FINGERPRINT.startsWith("unknown")
                        || android.os.Build.HARDWARE.contains("goldfish")
                        || android.os.Build.HARDWARE.contains("ranchu")
                        || android.os.Build.MODEL.contains("google_sdk")
                        || android.os.Build.MODEL.contains("Emulator")
                        || android.os.Build.MODEL.contains("Android SDK built for x86")
                        || android.os.Build.MANUFACTURER.contains("Genymotion")
                        || android.os.Build.PRODUCT.contains("sdk_google")
                        || android.os.Build.PRODUCT.contains("google_sdk")
                        || android.os.Build.PRODUCT.contains("sdk")
                        || android.os.Build.PRODUCT.contains("sdk_x86")
                        || android.os.Build.PRODUCT.contains("vbox86p")
                        || android.os.Build.PRODUCT.contains("emulator")
                        || android.os.Build.PRODUCT.contains("simulator")

                val msg = if (isEmulator) {
                    "Unable to acquire location. On emulator, open 'Extended Controls' (three dots) -> 'Location' and click 'Set Location'."
                } else {
                    "GPS cannot establish a fix. Please go outdoors or ensure GPS is enabled."
                }
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                onStatusChange("SOS failed: GPS unavailable")
            }
        }.addOnFailureListener { e ->
            Log.e("SOS_API", "Last location also failed", e)
            android.widget.Toast.makeText(context, "GPS failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            onStatusChange("SOS failed: GPS error")
        }
    }

    // 4. Try current location first
    locationClient.getCurrentLocation(
        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
        null
    ).addOnSuccessListener { location ->
        if (location != null) {
            sendSosWithLocation(location)
        } else {
            tryLastLocation("getCurrentLocation returned null")
        }
    }.addOnFailureListener { e ->
        Log.w("SOS_API", "getCurrentLocation failed, trying lastLocation", e)
        tryLastLocation("getCurrentLocation error: ${e.message}")
    }
}
