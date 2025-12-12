package com.scanium.app.camera

import android.Manifest
import android.util.Size
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.ml.DetectionResult
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.settings.ClassificationModeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Camera screen with full-screen preview and Android-style shutter button.
 *
 * Features:
 * - Single shutter button for all capture interactions
 * - Tap to capture single frame
 * - Long-press to start continuous scanning
 * - Tap while scanning to stop
 * - Advanced controls (threshold slider, classification toggle) hidden by default
 * - Tap preview to show advanced controls (auto-hide after 3 seconds)
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateToItems: () -> Unit,
    itemsViewModel: ItemsViewModel = viewModel(),
    classificationModeViewModel: ClassificationModeViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Camera permission state
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // CameraX manager
    val cameraManager = remember {
        CameraXManager(context, lifecycleOwner)
    }

    // Sound manager for camera feedback
    val soundManager = remember {
        CameraSoundManager()
    }

    // Camera state machine
    var cameraState by remember { mutableStateOf(CameraState.IDLE) }

    // Current scan mode
    var currentScanMode by remember { mutableStateOf(ScanMode.OBJECT_DETECTION) }

    // Flash animation state for mode transitions
    var showFlash by remember { mutableStateOf(false) }

    // Advanced controls visibility state
    var advancedControlsVisible by remember { mutableStateOf(false) }
    var autoHideJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Items count from ViewModel
    val itemsCount by itemsViewModel.items.collectAsState()
    val classificationMode by classificationModeViewModel.classificationMode.collectAsState()

    // Detection overlay state
    var currentDetections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var imageSize by remember { mutableStateOf(Size(1280, 720)) }
    var previewSize by remember { mutableStateOf(Size(0, 0)) }

    // Auto-hide timer for advanced controls
    fun startAutoHideTimer() {
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(3000)
            advancedControlsVisible = false
        }
    }

    // Reset auto-hide timer when controls are interacted with
    fun resetAutoHideTimer() {
        if (advancedControlsVisible) {
            startAutoHideTimer()
        }
    }

    // Request permission on first launch
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.shutdown()
            soundManager.release()
            autoHideJob?.cancel()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cameraPermissionState.status.isGranted -> {
                // Camera preview with tap detection
                CameraPreviewWithTapDetection(
                    cameraManager = cameraManager,
                    cameraState = cameraState,
                    onPreviewTap = {
                        // Show advanced controls on preview tap
                        advancedControlsVisible = true
                        startAutoHideTimer()
                    },
                    onPreviewSizeChanged = { size ->
                        previewSize = Size(size.width, size.height)
                    }
                )

                // Flash animation overlay for mode transitions
                if (showFlash) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.6f))
                    )
                }

                // Detection overlay - bounding boxes and labels
                if (currentDetections.isNotEmpty() && previewSize.width > 0 && previewSize.height > 0) {
                    DetectionOverlay(
                        detections = currentDetections,
                        imageSize = imageSize,
                        previewSize = previewSize
                    )
                }

                // Advanced controls (hidden by default)
                if (advancedControlsVisible) {
                    // Vertical threshold slider on right side
                    val currentThreshold by itemsViewModel.similarityThreshold.collectAsState()
                    VerticalThresholdSlider(
                        value = currentThreshold,
                        onValueChange = { newValue ->
                            itemsViewModel.updateSimilarityThreshold(newValue)
                            resetAutoHideTimer()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                    )

                    // Classification mode toggle on left side
                    ClassificationModeToggle(
                        currentMode = classificationMode,
                        onModeSelected = { mode ->
                            classificationModeViewModel.updateMode(mode)
                            resetAutoHideTimer()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                    )
                }

                // Overlay UI
                CameraOverlay(
                    itemsCount = itemsCount.size,
                    cameraState = cameraState,
                    scanMode = currentScanMode,
                    onNavigateToItems = onNavigateToItems,
                    onModeChanged = { newMode ->
                        if (newMode != currentScanMode) {
                            // Stop scanning if active
                            if (cameraState == CameraState.SCANNING) {
                                cameraState = CameraState.IDLE
                                cameraManager.stopScanning()
                                currentDetections = emptyList()
                            }

                            // Trigger flash animation
                            scope.launch {
                                showFlash = true
                                delay(150)
                                currentScanMode = newMode
                                delay(100)
                                showFlash = false
                            }
                        }
                    },
                    onShutterTap = {
                        // Single tap: capture one frame
                        if (cameraState == CameraState.IDLE) {
                            cameraState = CameraState.CAPTURING
                            soundManager.playShutterClick()
                            cameraManager.captureSingleFrame(
                                scanMode = currentScanMode,
                                onResult = { items ->
                                    cameraState = CameraState.IDLE
                                    if (items.isEmpty()) {
                                        val message = when (currentScanMode) {
                                            ScanMode.OBJECT_DETECTION -> "No objects detected. Try pointing at prominent items."
                                            ScanMode.BARCODE -> "No barcode detected. Point at a barcode or QR code."
                                            ScanMode.DOCUMENT_TEXT -> "No text detected. Point at a document or text."
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    } else {
                                        itemsViewModel.addItems(items)
                                        Toast.makeText(
                                            context,
                                            "Detected ${items.size} item(s)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onDetectionResult = { detections ->
                                    currentDetections = detections
                                },
                                onFrameSize = { size ->
                                    imageSize = size
                                }
                            )
                        }
                    },
                    onShutterLongPress = {
                        // Long press: start scanning
                        if (cameraState == CameraState.IDLE) {
                            cameraState = CameraState.SCANNING
                            soundManager.playScanStartMelody()
                            cameraManager.startScanning(
                                scanMode = currentScanMode,
                                onResult = { items ->
                                    if (items.isNotEmpty()) {
                                        itemsViewModel.addItems(items)
                                    }
                                },
                                onDetectionResult = { detections ->
                                    currentDetections = detections
                                },
                                onFrameSize = { size ->
                                    imageSize = size
                                }
                            )
                        }
                    },
                    onStopScanning = {
                        // Tap while scanning: stop
                        if (cameraState == CameraState.SCANNING) {
                            cameraState = CameraState.IDLE
                            soundManager.playScanStopMelody()
                            cameraManager.stopScanning()
                            currentDetections = emptyList()
                        }
                    }
                )
            }
            else -> {
                // Permission denied UI
                PermissionDeniedContent(
                    onRequestPermission = {
                        cameraPermissionState.launchPermissionRequest()
                    }
                )
            }
        }
    }
}

/**
 * Camera preview integrated with AndroidView and tap detection.
 */
@Composable
private fun CameraPreviewWithTapDetection(
    cameraManager: CameraXManager,
    cameraState: CameraState,
    onPreviewTap: () -> Unit,
    onPreviewSizeChanged: (androidx.compose.ui.unit.IntSize) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isCameraStarted by remember { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { intSize ->
                onPreviewSizeChanged(intSize)
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Tap on preview shows advanced controls
                        onPreviewTap()
                    }
                )
            }
    ) { previewView ->
        if (!isCameraStarted) {
            scope.launch {
                cameraManager.startCamera(previewView) { items ->
                    // Handle any additional detection results if needed
                }
                isCameraStarted = true
            }
        }
    }
}

/**
 * Overlay UI on top of camera preview.
 */
@Composable
private fun BoxScope.CameraOverlay(
    itemsCount: Int,
    cameraState: CameraState,
    scanMode: ScanMode,
    onNavigateToItems: () -> Unit,
    onModeChanged: (ScanMode) -> Unit,
    onShutterTap: () -> Unit,
    onShutterLongPress: () -> Unit,
    onStopScanning: () -> Unit
) {
    // Top bar
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App title
        Text(
            text = "Scanium",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // Items button
        Button(
            onClick = onNavigateToItems,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = "View items",
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Items ($itemsCount)", color = Color.White)
        }
    }

    // Bottom UI: Mode switcher and shutter button
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Mode switcher (always visible)
        ModeSwitcher(
            currentMode = scanMode,
            onModeChanged = onModeChanged,
            modifier = Modifier
        )

        // Shutter button
        ShutterButton(
            cameraState = cameraState,
            onTap = onShutterTap,
            onLongPress = onShutterLongPress,
            onStopScanning = onStopScanning,
            modifier = Modifier
        )
    }
}

/**
 * UI shown when camera permission is denied.
 */
@Composable
private fun PermissionDeniedContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Scanium needs camera access to detect objects in your environment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

/**
 * Classification mode toggle (shown only when advanced controls are visible).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassificationModeToggle(
    currentMode: ClassificationMode,
    onModeSelected: (ClassificationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentMode == ClassificationMode.ON_DEVICE,
            onClick = { onModeSelected(ClassificationMode.ON_DEVICE) },
            label = { Text("On-device", style = MaterialTheme.typography.bodySmall) }
        )

        FilterChip(
            selected = currentMode == ClassificationMode.CLOUD,
            onClick = { onModeSelected(ClassificationMode.CLOUD) },
            label = { Text("Cloud", style = MaterialTheme.typography.bodySmall) }
        )
    }
}
