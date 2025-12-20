package com.scanium.app.camera

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.app.BuildConfig
import com.scanium.app.R
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.ml.DetectionResult
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.settings.ClassificationModeViewModel
import com.scanium.android.platform.adapters.toImageRefJpeg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Custom saver for ScanMode enum to persist across configuration changes.
 */
private val ScanModeSaver = Saver<ScanMode, String>(
    save = { it.name },
    restore = { savedValue -> ScanMode.valueOf(savedValue) }
)

/**
 * Camera screen with full-screen preview and Android-style shutter button.
 *
 * Features:
 * - Single shutter button for all capture interactions
 * - Tap to capture single frame
 * - Long-press to start continuous scanning
 * - Tap while scanning to stop
 * - Slide-in settings overlay for tuning and processing controls
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateToItems: () -> Unit,
    itemsViewModel: ItemsViewModel = viewModel(),
    classificationModeViewModel: ClassificationModeViewModel,
    cameraViewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
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
    var cameraErrorState by remember { mutableStateOf<CameraErrorState?>(null) }

    // Camera lens state
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var boundLensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isCameraBinding by remember { mutableStateOf(false) }
    var rebindAttempts by remember { mutableStateOf(0) }

    // Current scan mode (persisted across configuration changes)
    var currentScanMode by rememberSaveable(stateSaver = ScanModeSaver) {
        mutableStateOf(ScanMode.OBJECT_DETECTION)
    }

    // Flash animation state for mode transitions
    var showFlash by remember { mutableStateOf(false) }

    // Model download state for first-launch experience
    var modelDownloadState by remember { mutableStateOf<ModelDownloadState>(ModelDownloadState.Checking) }

    // Settings overlay state
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }

    // Items count from ViewModel
    val itemsCount by itemsViewModel.items.collectAsState()
    val similarityThreshold by itemsViewModel.similarityThreshold.collectAsState()
    val classificationMode by classificationModeViewModel.classificationMode.collectAsState()
    val captureResolution by cameraViewModel.captureResolution.collectAsState()
    var previousClassificationMode by remember { mutableStateOf<ClassificationMode?>(null) }

    // Detection overlay state
    var currentDetections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var imageSize by remember { mutableStateOf(Size(1280, 720)) }
    var previewSize by remember { mutableStateOf(Size(0, 0)) }

    var targetRotation by remember { mutableStateOf(view.display?.rotation ?: Surface.ROTATION_0) }

    DisposableEffect(view) {
        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                val rotation = view.display?.rotation ?: Surface.ROTATION_0
                if (rotation != targetRotation) {
                    targetRotation = rotation
                }
            }
        }

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }

        onDispose {
            orientationListener.disable()
        }
    }

    LaunchedEffect(targetRotation) {
        cameraManager.updateTargetRotation(targetRotation)
    }

    // Request permission on first launch
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (!cameraPermissionState.status.isGranted) {
            cameraManager.stopScanning()
            cameraState = CameraState.IDLE
            cameraErrorState = null
            currentDetections = emptyList()
        }
    }

    // Check if ML Kit model is downloaded (first launch requirement)
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            modelDownloadState = ModelDownloadState.Checking
            try {
                Log.d("CameraScreen", "Checking ML Kit model availability...")
                cameraManager.ensureModelsReady()
                Log.d("CameraScreen", "ML Kit models ready")
                modelDownloadState = ModelDownloadState.Ready
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error checking model availability", e)
                modelDownloadState = ModelDownloadState.Error("Error initializing ML Kit: ${e.message}")
            }
        }
    }

    LaunchedEffect(classificationMode) {
        val previousMode = previousClassificationMode
        if (previousMode != null && previousMode != classificationMode) {
            Toast.makeText(
                context,
                "Using ${classificationMode.displayName} classification",
                Toast.LENGTH_SHORT
            ).show()
        }
        previousClassificationMode = classificationMode
    }

    // Close settings on system back when open
    BackHandler(enabled = isSettingsOpen) {
        isSettingsOpen = false
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.shutdown()
            soundManager.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cameraPermissionState.status.isGranted && cameraState == CameraState.ERROR -> {
                CameraErrorContent(
                    error = cameraErrorState,
                    onRetry = {
                        cameraManager.stopScanning()
                        cameraErrorState = null
                        cameraState = CameraState.IDLE
                        currentDetections = emptyList()
                        rebindAttempts++
                    },
                    onViewItems = onNavigateToItems
                )
            }
            cameraPermissionState.status.isGranted -> {
                // Camera preview
                CameraPreview(
                    cameraManager = cameraManager,
                    lensFacing = lensFacing,
                    captureResolution = captureResolution,
                    rebindKey = rebindAttempts,
                    onPreviewSizeChanged = { size ->
                        previewSize = Size(size.width, size.height)
                    },
                    onBindingStateChange = { isBinding ->
                        isCameraBinding = isBinding
                    },
                    onLensFacingResolved = { resolvedLens ->
                        boundLensFacing = resolvedLens
                        cameraErrorState = null
                        if (cameraState == CameraState.ERROR) {
                            cameraState = CameraState.IDLE
                        }
                        if (lensFacing != resolvedLens) {
                            lensFacing = resolvedLens
                            Toast.makeText(
                                context,
                                "Selected lens unavailable. Switched cameras.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onBindingFailed = { error ->
                        lensFacing = boundLensFacing
                        error?.let {
                            cameraManager.stopScanning()
                            cameraState = CameraState.ERROR
                            cameraErrorState = CameraErrorState(
                                title = "Camera unavailable",
                                message = it.message ?: "Unable to start camera.",
                                canRetry = true
                            )
                            Toast.makeText(context, "Unable to start camera.", Toast.LENGTH_SHORT).show()
                            Log.e("CameraScreen", "Failed to bind camera", it)
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

                // Model download loading overlay (first launch)
                when (val state = modelDownloadState) {
                    is ModelDownloadState.Checking,
                    is ModelDownloadState.Downloading -> {
                        ModelLoadingOverlay(state = state)
                    }
                    is ModelDownloadState.Error -> {
                        ModelErrorDialog(
                            error = state.message,
                            onRetry = {
                                scope.launch {
                                    modelDownloadState = ModelDownloadState.Checking
                                    try {
                                        cameraManager.ensureModelsReady()
                                        modelDownloadState = ModelDownloadState.Ready
                                    } catch (e: Exception) {
                                        modelDownloadState = ModelDownloadState.Error(
                                            "Error initializing ML Kit: ${e.message}"
                                        )
                                    }
                                }
                            },
                            onDismiss = {
                                // User chose to exit - set to Ready to allow camera use
                                modelDownloadState = ModelDownloadState.Ready
                            }
                        )
                    }
                    ModelDownloadState.Ready -> {
                        // No overlay, camera fully functional
                    }
                }

                // Detection overlay - bounding boxes and labels
                if (currentDetections.isNotEmpty() && previewSize.width > 0 && previewSize.height > 0) {
                    DetectionOverlay(
                        detections = currentDetections,
                        imageSize = imageSize,
                        previewSize = previewSize
                    )
                }

                // Cloud configuration status banner
                ConfigurationStatusBanner(
                    classificationMode = classificationMode,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Overlay UI
                CameraOverlay(
                    itemsCount = itemsCount.size,
                    cameraState = cameraState,
                    scanMode = currentScanMode,
                    captureResolution = captureResolution,
                    onNavigateToItems = onNavigateToItems,
                    onOpenSettings = { isSettingsOpen = true },
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
                                        // Capture high-res image and update items with it
                                        scope.launch {
                                            val highResUri = cameraManager.captureHighResImage()
                                            val itemsWithHighRes = if (highResUri != null) {
                                                // Generate high-quality thumbnails from the high-res image
                                                items.map { item ->
                                                    val newThumbnail = ImageUtils.createThumbnailFromUri(context, highResUri)
                                                    val newThumbnailRef = newThumbnail?.toImageRefJpeg(quality = 85)
                                                    item.copy(
                                                        fullImageUri = highResUri,
                                                        thumbnail = newThumbnailRef ?: item.thumbnail,
                                                        thumbnailRef = newThumbnailRef ?: item.thumbnailRef
                                                    )
                                                }
                                            } else {
                                                // Fallback: use original items if high-res capture failed
                                                items
                                            }
                                            itemsViewModel.addItems(itemsWithHighRes)
                                            Toast.makeText(
                                                context,
                                                "Detected ${items.size} item(s)",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
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
                                        // Capture high-res image for continuous scan items
                                        scope.launch {
                                            val highResUri = cameraManager.captureHighResImage()
                                            val itemsWithHighRes = if (highResUri != null) {
                                                items.map { item ->
                                                    val newThumbnail = ImageUtils.createThumbnailFromUri(context, highResUri)
                                                    val newThumbnailRef = newThumbnail?.toImageRefJpeg(quality = 85)
                                                    item.copy(
                                                        fullImageUri = highResUri,
                                                        thumbnail = newThumbnailRef ?: item.thumbnail,
                                                        thumbnailRef = newThumbnailRef ?: item.thumbnailRef
                                                    )
                                                }
                                            } else {
                                                items
                                            }
                                            itemsViewModel.addItems(itemsWithHighRes)
                                        }
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
                    },
                    onFlipCamera = {
                        cameraState = CameraState.IDLE
                        currentDetections = emptyList()
                        cameraManager.stopScanning()
                        lensFacing = if (boundLensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    isFlipEnabled = !isCameraBinding
                )

                CameraSettingsOverlay(
                    visible = isSettingsOpen,
                    onDismiss = { isSettingsOpen = false },
                    similarityThreshold = similarityThreshold,
                    onThresholdChange = itemsViewModel::updateSimilarityThreshold,
                    classificationMode = classificationMode,
                    onProcessingModeChange = classificationModeViewModel::updateMode,
                    captureResolution = captureResolution,
                    onResolutionChange = cameraViewModel::updateCaptureResolution
                )
            }
            else -> {
                // Permission denied UI with educative content
                PermissionDeniedContent(
                    permissionState = cameraPermissionState,
                    onRequestPermission = {
                        cameraPermissionState.launchPermissionRequest()
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

/**
 * Camera preview integrated with AndroidView.
 */
@Composable
private fun CameraPreview(
    cameraManager: CameraXManager,
    lensFacing: Int,
    captureResolution: CaptureResolution,
    rebindKey: Int,
    onPreviewSizeChanged: (androidx.compose.ui.unit.IntSize) -> Unit,
    onBindingStateChange: (Boolean) -> Unit,
    onLensFacingResolved: (Int) -> Unit,
    onBindingFailed: (Throwable?) -> Unit
) {
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }.also { createdView ->
                previewView = createdView
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { intSize ->
                onPreviewSizeChanged(intSize)
            }
    )

    // Rebind camera when lens or resolution changes
    LaunchedEffect(previewView, lensFacing, captureResolution, rebindKey) {
        val view = previewView ?: return@LaunchedEffect
        onBindingStateChange(true)
        val result = cameraManager.startCamera(view, lensFacing, captureResolution)
        onBindingStateChange(false)
        if (result.success) {
            onLensFacingResolved(result.lensFacingUsed)
        } else {
            onBindingFailed(result.error)
        }
    }
}

/**
 * Overlay UI on top of camera preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.CameraOverlay(
    itemsCount: Int,
    cameraState: CameraState,
    scanMode: ScanMode,
    captureResolution: CaptureResolution,
    onNavigateToItems: () -> Unit,
    onOpenSettings: () -> Unit,
    onModeChanged: (ScanMode) -> Unit,
    onShutterTap: () -> Unit,
    onShutterLongPress: () -> Unit,
    onStopScanning: () -> Unit,
    onFlipCamera: () -> Unit,
    isFlipEnabled: Boolean
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
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(48.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                )
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open settings",
                tint = Color.White
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.scanium_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }

    // Bottom UI: Mode switcher and shutter button
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Current mode indicator for clarity
        Text(
            text = when (scanMode) {
                ScanMode.OBJECT_DETECTION -> "Object Detection"
                ScanMode.BARCODE -> "Barcode Scanner"
                ScanMode.DOCUMENT_TEXT -> "Text Recognition"
            },
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Mode switcher (always visible)
        ModeSwitcher(
            currentMode = scanMode,
            onModeChanged = onModeChanged,
            modifier = Modifier
        )

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                BadgedBox(
                    badge = {
                        if (itemsCount > 0) {
                            Badge {
                                Text(itemsCount.toString())
                            }
                        }
                    }
                ) {
                    IconButton(
                        onClick = onNavigateToItems,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.small
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "View items",
                            tint = Color.White
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ShutterButton(
                    cameraState = cameraState,
                    onTap = onShutterTap,
                    onLongPress = onShutterLongPress,
                    onStopScanning = onStopScanning,
                    modifier = Modifier.offset(y = 6.dp)
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                IconButton(
                    onClick = onFlipCamera,
                    enabled = isFlipEnabled,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Flip camera",
                        tint = Color.White
                    )
                }
            }
        }

        // Resolution indicator
        Text(
            text = getResolutionLabel(captureResolution),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.small
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/**
 * Formats the resolution setting for display.
 */
private fun getResolutionLabel(resolution: CaptureResolution): String {
    return when (resolution) {
        CaptureResolution.LOW -> "Resolution: Low (720p)"
        CaptureResolution.NORMAL -> "Resolution: Normal (1080p)"
        CaptureResolution.HIGH -> "Resolution: High (4K)"
    }
}

/**
 * UI shown when camera hardware is unavailable or binding fails.
 */
@Composable
private fun CameraErrorContent(
    error: CameraErrorState?,
    onRetry: () -> Unit,
    onViewItems: () -> Unit
) {
    val resolvedError = error ?: CameraErrorState(
        title = "Camera unavailable",
        message = "Unable to access the camera right now.",
        canRetry = true
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = resolvedError.title,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = resolvedError.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (resolvedError.canRetry) {
            Button(onClick = onRetry) {
                Text("Retry")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        androidx.compose.material3.TextButton(onClick = onViewItems) {
            Text("View Items")
        }
    }
}

/**
 * UI shown when camera permission is denied.
 *
 * Provides educative content and context-aware actions based on permission state:
 * - First request: Shows rationale and grant permission button
 * - Denied with rationale: Explains importance and offers to try again
 * - Permanently denied: Guides user to open system settings
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionDeniedContent(
    permissionState: com.google.accompanist.permissions.PermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isPermanentlyDenied = !permissionState.status.shouldShowRationale &&
                              !permissionState.status.isGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Camera icon
        Icon(
            imageVector = Icons.Default.Camera,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = "Camera Access Required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description - varies based on permission state
        Text(
            text = if (isPermanentlyDenied) {
                "Camera access has been disabled for Scanium. To use object detection, barcode scanning, and document text recognition, please enable camera access in your device settings."
            } else {
                "Scanium uses your camera to detect and catalog objects, scan barcodes, and recognize text in your environment."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Feature list - what camera enables
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Camera enables:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FeatureItem("📦", "Object detection and cataloging")
                FeatureItem("📱", "Barcode and QR code scanning")
                FeatureItem("📄", "Document and text recognition")
                FeatureItem("📸", "High-quality image capture")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Privacy note
        Text(
            text = "🔒 All processing happens locally on your device. Images are never uploaded without your permission.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons - different based on permission state
        if (isPermanentlyDenied) {
            // Permission permanently denied - guide to settings
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Settings")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap 'Permissions' → 'Camera' → 'Allow'",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            // Permission can still be requested
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Grant Camera Access")
            }
        }
    }
}

/**
 * Helper composable for feature list items
 */
@Composable
private fun FeatureItem(icon: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(32.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Loading overlay shown during ML Kit model download on first launch.
 */
@Composable
private fun ModelLoadingOverlay(state: ModelDownloadState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Card(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(0.85f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = when (state) {
                        is ModelDownloadState.Checking -> "Preparing ML models..."
                        is ModelDownloadState.Downloading -> "Downloading AI models..."
                        else -> "Loading..."
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "First launch requires downloading\nML Kit object detection models (~15 MB)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/**
 * Error dialog shown when ML Kit model download fails.
 */
@Composable
private fun ModelErrorDialog(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model Initialization Failed") },
        text = {
            Column {
                Text(error)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Please ensure you have:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("• Active internet connection", style = MaterialTheme.typography.bodySmall)
                Text("• At least 20 MB free storage", style = MaterialTheme.typography.bodySmall)
                Text("• Network access for this app", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onRetry) {
                Text("Retry")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Continue Anyway")
            }
        }
    )
}

/**
 * Configuration status banner shown when cloud mode is enabled but not configured.
 *
 * Surfaces the configuration requirement from DEV_GUIDE.md to avoid accidental
 * network use once API keys are provided.
 */
@Composable
private fun ConfigurationStatusBanner(
    classificationMode: ClassificationMode,
    modifier: Modifier = Modifier
) {
    val isCloudConfigured = BuildConfig.SCANIUM_API_BASE_URL.isNotBlank()
    val showBanner = classificationMode == ClassificationMode.CLOUD && !isCloudConfigured

    if (showBanner) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
            tonalElevation = 4.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 72.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Cloud mode not configured",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Classification will use on-device processing until SCANIUM_API_BASE_URL is configured.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
