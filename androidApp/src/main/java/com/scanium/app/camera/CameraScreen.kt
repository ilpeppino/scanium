package com.scanium.app.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
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
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.QrCodeScanner
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanium.app.BuildConfig
import com.scanium.app.R
import com.scanium.app.data.SettingsRepository
import com.scanium.app.data.ThemeMode
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.ml.DetectionResult
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.ml.classification.ClassificationMetrics
import com.scanium.app.settings.ClassificationModeViewModel
import com.scanium.app.media.StorageHelper
import com.scanium.app.ftue.FtueRepository
import com.scanium.app.ftue.PermissionEducationDialog
import com.scanium.app.ftue.tourTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
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
    onNavigateToSettings: () -> Unit,
    itemsViewModel: ItemsViewModel = viewModel(),
    classificationModeViewModel: ClassificationModeViewModel,
    cameraViewModel: CameraViewModel = viewModel(),
    tourViewModel: com.scanium.app.ftue.TourViewModel? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }
    val ftueRepository = remember { FtueRepository(context) }
    val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val autoSaveEnabled by settingsRepository.autoSaveEnabledFlow.collectAsState(initial = false)
    val saveDirectoryUri by settingsRepository.saveDirectoryUriFlow.collectAsState(initial = null)

    // Permission education state (shown before first permission request)
    val permissionEducationShown by ftueRepository.permissionEducationShownFlow.collectAsState(initial = true)
    var showPermissionEducationDialog by remember { mutableStateOf(false) }

    // FTUE tour state
    val currentTourStep by tourViewModel?.currentStep?.collectAsState() ?: remember { mutableStateOf(null) }
    val isTourActive by tourViewModel?.isTourActive?.collectAsState() ?: remember { mutableStateOf(false) }
    val targetBounds by tourViewModel?.targetBounds?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }

    // Camera permission state
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Initialize CameraX manager
    val cameraManager = remember {
        val app = context.applicationContext as? com.scanium.app.ScaniumApplication
        CameraXManager(context, lifecycleOwner, app?.telemetry)
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
    val saveCloudCrops by classificationModeViewModel.saveCloudCrops.collectAsState()
    val lowDataMode by classificationModeViewModel.lowDataMode.collectAsState()
    val verboseLogging by classificationModeViewModel.verboseLogging.collectAsState()
    val analysisFps by cameraManager.analysisFps.collectAsState()
    
    // Performance metrics
    val callsStarted by ClassificationMetrics.callsStarted.collectAsState()
    val callsCompleted by ClassificationMetrics.callsCompleted.collectAsState()
    val callsFailed by ClassificationMetrics.callsFailed.collectAsState()
    val lastLatency by ClassificationMetrics.lastLatencyMs.collectAsState()
    val queueDepth by ClassificationMetrics.queueDepth.collectAsState()
    val overlayTracks by itemsViewModel.overlayTracks.collectAsState()
    
    // Animation state for newly added items
    var lastAddedItem by remember { mutableStateOf<com.scanium.app.items.ScannedItem?>(null) }
    
    LaunchedEffect(itemsViewModel) {
        itemsViewModel.itemAddedEvents.collect { item: com.scanium.app.items.ScannedItem ->
            lastAddedItem = item
        }
    }

    var previousClassificationMode by remember { mutableStateOf<ClassificationMode?>(null) }

    var imageSize by remember { mutableStateOf(Size(1280, 720)) }
    var previewSize by remember { mutableStateOf(Size(0, 0)) }

    var targetRotation by remember { mutableStateOf(view.display?.rotation ?: Surface.ROTATION_0) }

    KeepScreenOn(enabled = cameraState == CameraState.SCANNING)

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

    // Show permission education dialog or request permission on first launch
    LaunchedEffect(permissionEducationShown) {
        if (!cameraPermissionState.status.isGranted) {
            if (!permissionEducationShown) {
                // First launch: show education dialog before requesting permission
                showPermissionEducationDialog = true
            } else {
                // Education already shown: request permission directly
                cameraPermissionState.launchPermissionRequest()
            }
        }
    }

    // Start tour after permission is granted (if tour is active)
    LaunchedEffect(cameraPermissionState.status.isGranted, isTourActive) {
        if (cameraPermissionState.status.isGranted && isTourActive) {
            delay(300) // Let camera initialize
            tourViewModel?.startTour()
        }
    }

    LaunchedEffect(cameraPermissionState) {
        snapshotFlow { cameraPermissionState.status.isGranted }
            .distinctUntilChanged()
            .collect { isGranted ->
                cameraViewModel.onPermissionStateChanged(
                    isGranted = isGranted,
                    isScanning = cameraState == CameraState.SCANNING
                )
            }
    }

    LaunchedEffect(cameraViewModel) {
        cameraViewModel.stopScanningRequests.collect {
            cameraManager.stopScanning()
            cameraState = CameraState.IDLE
            cameraErrorState = null
            itemsViewModel.updateOverlayDetections(emptyList())
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
                        itemsViewModel.updateOverlayDetections(emptyList())
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
                if (overlayTracks.isNotEmpty() && previewSize.width > 0 && previewSize.height > 0) {
                    DetectionOverlay(
                        detections = overlayTracks,
                        imageSize = imageSize,
                        previewSize = previewSize
                    )
                }

                // Cloud configuration status banner
                ConfigurationStatusBanner(
                    classificationMode = classificationMode,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                
                // DEV BUILD Indicator
                if (com.scanium.app.BuildConfig.DEBUG) {
                    Text(
                        text = "DEV BUILD",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .background(Color.Red.copy(alpha = 0.7f), shape = MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Performance Overlay (Debug)
                if (verboseLogging) {
                    PerfOverlay(
                        analysisFps = analysisFps,
                        classificationMode = classificationMode,
                        callsStarted = callsStarted,
                        callsCompleted = callsCompleted,
                        callsFailed = callsFailed,
                        lastLatency = lastLatency,
                        queueDepth = queueDepth,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 90.dp, start = 16.dp)
                    )
                }

                // Overlay UI
                CameraOverlay(
                    itemsCount = itemsCount.size,
                    lastAddedItem = lastAddedItem,
                    onAnimationFinished = { lastAddedItem = null },
                    cameraState = cameraState,
                    scanMode = currentScanMode,
                    captureResolution = captureResolution,
                    onNavigateToItems = {
                        // If tour is on Items button step, advance before navigation
                        if (currentTourStep?.key == com.scanium.app.ftue.TourStepKey.CAMERA_ITEMS_BUTTON) {
                            tourViewModel?.nextStep()
                        }
                        onNavigateToItems()
                    },
                    onOpenSettings = { isSettingsOpen = true },
                    tourViewModel = tourViewModel,
                    onModeChanged = { newMode ->
                        if (newMode != currentScanMode) {
                            // Stop scanning if active
                            if (cameraState == CameraState.SCANNING) {
                                cameraState = CameraState.IDLE
                                cameraManager.stopScanning()
                                itemsViewModel.updateOverlayDetections(emptyList())
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
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                                            
                                            if (autoSaveEnabled && saveDirectoryUri != null && highResUri != null) {
                                                try {
                                                     context.contentResolver.openInputStream(highResUri)?.use { input ->
                                                         val savedUri = StorageHelper.saveToDirectory(
                                                             context, 
                                                             Uri.parse(saveDirectoryUri), 
                                                             input, 
                                                             "image/jpeg", 
                                                             "Scanium"
                                                         )
                                                         if (savedUri == null) {
                                                             Log.e("CameraScreen", "Failed to auto-save image")
                                                         }
                                                     }
                                                } catch (e: Exception) {
                                                    Log.e("CameraScreen", "Error auto-saving image", e)
                                                }
                                            }

                                            val itemsWithHighRes = if (highResUri != null) {
                                                items.map { item ->
                                                    item.copy(
                                                        fullImageUri = highResUri
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
                                    itemsViewModel.updateOverlayDetections(detections)
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
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            cameraManager.startScanning(
                                scanMode = currentScanMode,
                                onResult = { items ->
                                    if (items.isNotEmpty()) {
                                        // Capture high-res image for continuous scan items
                                        scope.launch {
                                            val highResUri = cameraManager.captureHighResImage()

                                            if (autoSaveEnabled && saveDirectoryUri != null && highResUri != null) {
                                                try {
                                                     context.contentResolver.openInputStream(highResUri)?.use { input ->
                                                         val savedUri = StorageHelper.saveToDirectory(
                                                             context, 
                                                             Uri.parse(saveDirectoryUri), 
                                                             input, 
                                                             "image/jpeg", 
                                                             "Scanium"
                                                         )
                                                         if (savedUri == null) {
                                                             Log.e("CameraScreen", "Failed to auto-save image")
                                                         }
                                                     }
                                                } catch (e: Exception) {
                                                    Log.e("CameraScreen", "Error auto-saving image", e)
                                                }
                                            }

                                            val itemsWithHighRes = if (highResUri != null) {
                                                items.map { item ->
                                                    item.copy(fullImageUri = highResUri)
                                                }
                                            } else {
                                                items
                                            }
                                            itemsViewModel.addItems(itemsWithHighRes)
                                        }
                                    }
                                },
                                onDetectionResult = { detections ->
                                    itemsViewModel.updateOverlayDetections(detections)
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
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                cameraManager.stopScanning()
                                itemsViewModel.updateOverlayDetections(emptyList())
                            }
                        },
                        onFlipCamera = {
                            cameraState = CameraState.IDLE
                            itemsViewModel.updateOverlayDetections(emptyList())
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
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        scope.launch { settingsRepository.setThemeMode(mode) }
                    },
                    similarityThreshold = similarityThreshold,
                    onThresholdChange = itemsViewModel::updateSimilarityThreshold,
                    classificationMode = classificationMode,
                    onProcessingModeChange = classificationModeViewModel::updateMode,
                    captureResolution = captureResolution,
                    onResolutionChange = cameraViewModel::updateCaptureResolution,
                    saveCloudCropsEnabled = saveCloudCrops,
                    onSaveCloudCropsChange = classificationModeViewModel::updateSaveCloudCrops,
                    lowDataModeEnabled = lowDataMode,
                    onLowDataModeChange = classificationModeViewModel::updateLowDataMode,
                    verboseLoggingEnabled = verboseLogging,
                    onVerboseLoggingChange = classificationModeViewModel::updateVerboseLogging,
                    onNavigateToSettings = {
                        isSettingsOpen = false
                        onNavigateToSettings()
                    }
                )

                // FTUE Tour Overlays
                if (isTourActive && cameraPermissionState.status.isGranted) {
                    when (currentTourStep?.key) {
                        com.scanium.app.ftue.TourStepKey.WELCOME -> {
                            com.scanium.app.ftue.WelcomeOverlay(
                                onStart = { tourViewModel?.nextStep() },
                                onSkip = { tourViewModel?.skipTour() }
                            )
                        }
                        com.scanium.app.ftue.TourStepKey.CAMERA_SETTINGS,
                        com.scanium.app.ftue.TourStepKey.CAMERA_MODE_ICONS,
                        com.scanium.app.ftue.TourStepKey.CAMERA_SHUTTER,
                        com.scanium.app.ftue.TourStepKey.CAMERA_ITEMS_BUTTON -> {
                            currentTourStep?.let { step ->
                                val bounds = step.targetKey?.let { targetBounds[it] }
                                if (bounds != null || step.targetKey == null) {
                                    com.scanium.app.ftue.SpotlightTourOverlay(
                                        step = step,
                                        targetBounds = bounds,
                                        onNext = { tourViewModel?.nextStep() },
                                        onBack = { tourViewModel?.previousStep() },
                                        onSkip = { tourViewModel?.skipTour() }
                                    )
                                }
                            }
                        }
                        else -> { /* Other steps handled in ItemsListScreen */ }
                    }
                }
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

        // Permission education dialog (shown before first permission request)
        if (showPermissionEducationDialog) {
            PermissionEducationDialog(
                onContinue = {
                    showPermissionEducationDialog = false
                    scope.launch {
                        ftueRepository.setPermissionEducationShown(true)
                    }
                    cameraPermissionState.launchPermissionRequest()
                }
            )
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
 * Icon-based scan mode selector for the top bar.
 *
 * Displays three icons horizontally with clear selected state styling.
 * Touch targets are at least 48dp for accessibility.
 *
 * @param currentMode The currently selected scan mode
 * @param onModeChanged Callback when the user selects a different mode
 * @param modifier Optional modifier for the selector container
 */
@Composable
private fun ScanModeIconSelector(
    currentMode: ScanMode,
    onModeChanged: (ScanMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScanMode.values().forEach { mode ->
            val isSelected = mode == currentMode
            val icon = when (mode) {
                ScanMode.OBJECT_DETECTION -> Icons.Outlined.Category
                ScanMode.BARCODE -> Icons.Outlined.QrCodeScanner
                ScanMode.DOCUMENT_TEXT -> Icons.Outlined.Description
            }
            val contentDescription = when (mode) {
                ScanMode.OBJECT_DETECTION -> "Items mode"
                ScanMode.BARCODE -> "Barcode mode"
                ScanMode.DOCUMENT_TEXT -> "Document mode"
            }

            IconButton(
                onClick = { onModeChanged(mode) },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (isSelected) {
                            Color.White.copy(alpha = 0.25f)
                        } else {
                            Color.Black.copy(alpha = 0.5f)
                        },
                        shape = MaterialTheme.shapes.small
                    )
                    .semantics {
                        role = Role.RadioButton
                        selected = isSelected
                        this.contentDescription = "$contentDescription. ${if (isSelected) "Selected" else "Not selected"}"
                    }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.6f)
                    }
                )
            }
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
    lastAddedItem: com.scanium.app.items.ScannedItem?,
    onAnimationFinished: () -> Unit,
    cameraState: CameraState,
    scanMode: ScanMode,
    captureResolution: CaptureResolution,
    onNavigateToItems: () -> Unit,
    onOpenSettings: () -> Unit,
    tourViewModel: com.scanium.app.ftue.TourViewModel?,
    onModeChanged: (ScanMode) -> Unit,
    onShutterTap: () -> Unit,
    onShutterLongPress: () -> Unit,
    onStopScanning: () -> Unit,
    onFlipCamera: () -> Unit,
    isFlipEnabled: Boolean
) {
    // Top bar with three-slot layout: hamburger (left), mode icons (center), logo (right)
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(16.dp)
            .semantics { traversalIndex = 0f },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left slot: Hamburger menu (fixed width)
        Box(
            modifier = Modifier.width(48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    )
                    .then(
                        if (tourViewModel != null) {
                            Modifier.tourTarget("camera_settings", tourViewModel)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open settings",
                    tint = Color.White
                )
            }
        }

        // Center slot: Mode icon selector (fills remaining space, centered)
        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (tourViewModel != null) {
                        Modifier.tourTarget("camera_mode_icons", tourViewModel)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            ScanModeIconSelector(
                currentMode = scanMode,
                onModeChanged = onModeChanged
            )
        }

        // Right slot: Scanium logo (fixed width)
        Box(
            modifier = Modifier.width(48.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
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
                    contentDescription = "Scanium logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    // Bottom UI: Shutter button and controls
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .semantics { traversalIndex = 1f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                            .then(
                                if (tourViewModel != null) {
                                    Modifier.tourTarget("camera_items_button", tourViewModel)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "View items",
                            tint = Color.White
                        )
                    }
                }
                
                // Item added animation overlay
                lastAddedItem?.let { item ->
                    key(item.id) {
                        Box(
                            modifier = Modifier.align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            ItemAddedAnimation(
                                item = item,
                                onAnimationFinished = onAnimationFinished
                            )
                        }
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
                    modifier = Modifier
                        .offset(y = 6.dp)
                        .then(
                            if (tourViewModel != null) {
                                Modifier.tourTarget("camera_shutter", tourViewModel)
                            } else {
                                Modifier
                            }
                        )
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
            contentDescription = "Camera permission required",
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

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity, enabled) {
        if (enabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            if (enabled) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
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
                    contentDescription = "Configuration warning",
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
