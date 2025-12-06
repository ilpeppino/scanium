package com.example.objecta.camera

import android.Manifest
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.objecta.items.ItemsViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Camera screen with full-screen preview, tap/long-press gestures, and overlay UI.
 *
 * Features:
 * - Tap to capture single frame and detect objects
 * - Long-press to start continuous scanning
 * - Top bar with app title and items count button
 * - Bottom hint text
 * - Scanning indicator when in scan mode
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateToItems: () -> Unit,
    itemsViewModel: ItemsViewModel = viewModel()
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

    // Scanning state
    var isScanning by remember { mutableStateOf(false) }

    // Current scan mode
    var currentScanMode by remember { mutableStateOf(ScanMode.OBJECT_DETECTION) }

    // Flash animation state for mode transitions
    var showFlash by remember { mutableStateOf(false) }

    // Items count from ViewModel
    val itemsCount by itemsViewModel.items.collectAsState()

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
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cameraPermissionState.status.isGranted -> {
                // Camera preview with gesture detection
                CameraPreviewWithGestures(
                    cameraManager = cameraManager,
                    scanMode = currentScanMode,
                    isScanning = isScanning,
                    onScanningChanged = { scanning ->
                        isScanning = scanning
                        if (scanning) {
                            cameraManager.startScanning(currentScanMode) { items ->
                                if (items.isNotEmpty()) {
                                    itemsViewModel.addItems(items)
                                }
                            }
                        } else {
                            cameraManager.stopScanning()
                        }
                    },
                    onCapture = {
                        cameraManager.captureSingleFrame(currentScanMode) { items ->
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
                        }
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

                // Overlay UI
                CameraOverlay(
                    itemsCount = itemsCount.size,
                    isScanning = isScanning,
                    scanMode = currentScanMode,
                    onNavigateToItems = onNavigateToItems,
                    onModeChanged = { newMode ->
                        if (newMode != currentScanMode) {
                            // Stop scanning if active
                            if (isScanning) {
                                isScanning = false
                                cameraManager.stopScanning()
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
 * Camera preview integrated with AndroidView and gesture detection.
 */
@Composable
private fun CameraPreviewWithGestures(
    cameraManager: CameraXManager,
    scanMode: ScanMode,
    isScanning: Boolean,
    onScanningChanged: (Boolean) -> Unit,
    onCapture: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isCameraStarted by remember { mutableStateOf(false) }
    val scanningState by rememberUpdatedState(isScanning)

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(scanningState) {
                detectTapGestures(
                    onTap = {
                        // Single tap: capture one frame
                        if (!scanningState) {
                            onCapture()
                        }
                    },
                    onLongPress = {
                        // Long press: start scanning
                        if (!scanningState) {
                            onScanningChanged(true)
                        }
                    },
                    onDoubleTap = {
                        // Double tap: stop scanning if active
                        if (scanningState) {
                            onScanningChanged(false)
                        }
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
    isScanning: Boolean,
    scanMode: ScanMode,
    onNavigateToItems: () -> Unit,
    onModeChanged: (ScanMode) -> Unit
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
            text = "Objecta (EU PoC)",
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

    // Bottom UI: Mode switcher and hint text
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hint text
        Text(
            text = if (isScanning) {
                "Double-tap to stop scanning"
            } else {
                when (scanMode) {
                    ScanMode.OBJECT_DETECTION -> "Tap to capture • Long-press to scan"
                    ScanMode.BARCODE -> "Tap to scan barcode • Long-press for continuous scan"
                    ScanMode.DOCUMENT_TEXT -> "Tap to scan document • Long-press for continuous scan"
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Mode switcher
        ModeSwitcher(
            currentMode = scanMode,
            onModeChanged = onModeChanged,
            modifier = Modifier
        )
    }

    // Scanning indicator
    if (isScanning) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "SCANNING...",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
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
            text = "Objecta needs camera access to detect objects in your environment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}
