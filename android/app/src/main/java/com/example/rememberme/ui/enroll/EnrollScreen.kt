package com.example.rememberme.ui.enroll

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.rememberme.data.EnrollRequest
import com.example.rememberme.data.NetworkClient
import com.example.rememberme.data.PreferencesManager
import com.example.rememberme.theme.LocalAppColors
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnrollScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val prefManager = remember { PreferencesManager(context) }
    val colors = LocalAppColors.current

    // State variables
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var caregiverPhone by remember { mutableStateOf("") }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturedBase64 by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Use a well-lit, front-facing photo with only one person visible.") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var capturedWithFrontCamera by remember { mutableStateOf(false) }

    // Camera capture tools
    val imageCapture = remember { ImageCapture.Builder().build() }
    var cameraPermissionGranted by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ink),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.ink,
                    titleContentColor = colors.topBarTitle
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Add Familiar Face",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.topBarTitle
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = "← Back", color = colors.topBarNavigationText, fontSize = 14.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.ink)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Titles
                Text(
                    text = "NEW PERSON",
                    color = colors.mint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Add a familiar face",
                    color = colors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Layout: Stack camera on top of form for standard mobile layout
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, colors.cardBorder, RoundedCornerShape(24.dp))
                        .background(colors.panel)
                ) {
                    if (capturedBitmap != null) {
                        Image(
                            bitmap = capturedBitmap!!.asImageBitmap(),
                            contentDescription = "Captured Enrollment Snapshot",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = if (capturedWithFrontCamera) -1f else 1f)
                        )
                    } else if (cameraPermissionGranted) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                            },
                            update = { previewView ->
                                val localIsFrontCamera = isFrontCamera
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val cameraSelector = if (localIsFrontCamera) {
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    } else {
                                        CameraSelector.DEFAULT_BACK_CAMERA
                                    }
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview,
                                            imageCapture
                                        )
                                    } catch (e: Exception) {
                                        Log.e("EnrollCamera", "Failed use case binding", e)
                                    }
                                }, ContextCompat.getMainExecutor(context))
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "Camera permission required.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Button overlay at the bottom center of the camera
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Button(
                            onClick = {
                                if (capturedBitmap != null) {
                                    // Retake
                                    capturedBitmap = null
                                    capturedBase64 = ""
                                    message = "Photo cleared. Take a new photo."
                                } else {
                                    capturedWithFrontCamera = isFrontCamera
                                    val executor = Executors.newSingleThreadExecutor()
                                    imageCapture.takePicture(
                                        executor,
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                                try {
                                                    val rawBitmap = imageProxy.toBitmap()
                                                    val orientedBitmap = resizeBitmap(rawBitmap, 640)

                                                    capturedBitmap = orientedBitmap
                                                    
                                                    val stream = ByteArrayOutputStream()
                                                    orientedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                                                    val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                                                    capturedBase64 = "data:image/jpeg;base64,$base64"
                                                    
                                                    message = "Photo captured. Submit when ready."
                                                } catch (e: Exception) {
                                                    Log.e("EnrollCapture", "Error capturing photo", e)
                                                    message = "Error capturing photo: ${e.localizedMessage}"
                                                } finally {
                                                    imageProxy.close()
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                message = "Capture failed: ${exception.message}"
                                            }
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.textPrimary),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = if (capturedBitmap != null) "Retake photo" else "Capture photo",
                                color = colors.panel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Switch Camera overlay at top right of the camera box
                    if (capturedBitmap == null && cameraPermissionGranted) {
                        IconButton(
                            onClick = { isFrontCamera = !isFrontCamera },
                            modifier = Modifier
                                .padding(16.dp)
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera",
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Registration Form Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        // Name Input
                        Text(
                            text = "Name",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                            placeholder = { Text("Jake", color = colors.textTertiary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.mint,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedContainerColor = colors.inputBackground,
                                unfocusedContainerColor = colors.inputBackground
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Relationship Input
                        Text(
                            text = "Relationship",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = relationship,
                            onValueChange = { relationship = it },
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                            placeholder = { Text("Son", color = colors.textTertiary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.mint,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedContainerColor = colors.inputBackground,
                                unfocusedContainerColor = colors.inputBackground
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Caregiver Phone Input
                        Text(
                            text = "Caregiver Phone",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = caregiverPhone,
                            onValueChange = { caregiverPhone = it },
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                            placeholder = { Text("9876543210", color = colors.textTertiary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.mint,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedContainerColor = colors.inputBackground,
                                unfocusedContainerColor = colors.inputBackground
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Guideline / Status Message Text
                        Text(
                            text = message,
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                if (capturedBase64.isEmpty()) {
                                    message = "Capture a clear photo before submitting."
                                    return@Button
                                }
                                if (name.trim().isEmpty() || relationship.trim().isEmpty()) {
                                    message = "Please fill in all inputs."
                                    return@Button
                                }

                                isSubmitting = true
                                message = "Adding person..."
                                coroutineScope.launch {
                                    try {
                                        val api = NetworkClient.createService(prefManager.apiUrl)
                                        val response = api.enrollPerson(
                                            EnrollRequest(
                                                name = name.trim(),
                                                relationship = relationship.trim(),
                                                image = capturedBase64,
                                                caregiver_phone = if (caregiverPhone.trim().isEmpty()) null else caregiverPhone.trim()
                                            )
                                        )
                                        message = "${response.name} has been added as ${response.relationship}."
                                        // Clear form
                                        name = ""
                                        relationship = ""
                                        caregiverPhone = ""
                                        capturedBitmap = null
                                        capturedBase64 = ""
                                    } catch (e: Exception) {
                                        Log.e("EnrollSubmit", "Enrollment request failed", e)
                                        val isNetwork = e is java.io.IOException || 
                                                        e is java.net.UnknownHostException || 
                                                        e is java.net.ConnectException || 
                                                        e is java.net.SocketTimeoutException || 
                                                        (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) || 
                                                        (e.message?.contains("Failed to connect", ignoreCase = true) == true)
                                        message = if (isNetwork) {
                                            "Unable to connect to the server. Please check your network connection."
                                        } else {
                                            "Enrollment failed. Please try again."
                                        }
                                    } finally {
                                        isSubmitting = false
                                    }
                                }
                            },
                            enabled = !isSubmitting && name.trim().isNotEmpty() && relationship.trim().isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.mint),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = if (isSubmitting) "Adding person..." else "Add familiar face",
                                color = if (colors.isDark) colors.ink else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
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
