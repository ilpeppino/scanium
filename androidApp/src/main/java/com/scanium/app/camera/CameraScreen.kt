package com.scanium.app.camera

import android.Manifest
import androidx.annotation.VisibleForTesting
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.scanium.app.BuildConfig
import com.scanium.app.R
import com.scanium.app.audio.AppSound
import com.scanium.app.audio.LocalSoundManager
import com.scanium.app.data.SettingsRepository
import com.scanium.app.ftue.CameraFtueOverlay
import com.scanium.app.ftue.CameraFtueViewModel
import com.scanium.app.ftue.FtueRepository
import com.scanium.app.ftue.HintType
import com.scanium.app.ftue.tourTarget
import com.scanium.app.items.ItemsViewModel
import com.scanium.app.media.StorageHelper
import com.scanium.app.ml.classification.ClassificationMetrics
import com.scanium.app.ml.classification.ClassificationMode
import com.scanium.app.settings.ClassificationModeViewModel
import com.scanium.app.testing.TestSemantics
import com.scanium.app.ui.motion.MotionConfig
import com.scanium.app.ui.motion.MotionEnhancedOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    tourViewModel: com.scanium.app.ftue.TourViewModel? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val soundManager = LocalSoundManager.current
    val settingsRepository = remember { SettingsRepository(context) }
    val ftueRepository = remember { FtueRepository(context) }
    val cameraFtueViewModel = remember { CameraFtueViewModel(ftueRepository) }
    val autoSaveEnabled by settingsRepository.autoSaveEnabledFlow.collectAsState(initial = false)
    val saveDirectoryUri by settingsRepository.saveDirectoryUriFlow.collectAsState(initial = null)

    // Detection settings (for beta testing - developer toggles)
    val barcodeDetectionEnabled by settingsRepository.devBarcodeDetectionEnabledFlow.collectAsState(initial = true)
    val documentDetectionEnabled by settingsRepository.devDocumentDetectionEnabledFlow.collectAsState(initial = true)
    val adaptiveThrottlingEnabled by settingsRepository.devAdaptiveThrottlingEnabledFlow.collectAsState(initial = true)
    val scanningDiagnosticsEnabled by settingsRepository.devScanningDiagnosticsEnabledFlow.collectAsState(initial = false)

    // Scanning guidance settings
    val scanningGuidanceEnabled by settingsRepository.scanningGuidanceEnabledFlow.collectAsState(initial = true)
    val roiDiagnosticsEnabled by settingsRepository.devRoiDiagnosticsEnabledFlow.collectAsState(initial = false)
    val bboxMappingDebugEnabled by settingsRepository.devBboxMappingDebugEnabledFlow.collectAsState(initial = false)

    // Camera pipeline lifecycle debug
    val cameraPipelineDebugEnabled by settingsRepository.devCameraPipelineDebugEnabledFlow.collectAsState(initial = false)

    // Motion overlays (scan frame appear, lightning pulse)
    val motionOverlaysEnabled by settingsRepository.devMotionOverlaysEnabledFlow.collectAsState(initial = true)

    // Overlay accuracy filter (developer debug feature - step 0 = show all)
    val overlayAccuracyStep by settingsRepository.devOverlayAccuracyStepFlow.collectAsState(initial = 0)

    // Show/hide detection boxes overlay
    val showDetectionBoxes by settingsRepository.showDetectionBoxesFlow.collectAsState(initial = true)

    // Sync motion overlays setting with MotionConfig
    LaunchedEffect(motionOverlaysEnabled) {
        MotionConfig.setMotionOverlaysEnabled(motionOverlaysEnabled)
    }

    // Collect UI events from itemsViewModel (e.g., auto-navigation after scan)
    LaunchedEffect(itemsViewModel) {
        itemsViewModel.uiEvents.collect { event ->
            when (event) {
                is com.scanium.app.items.ItemsUiEvent.NavigateToItemList -> {
                    onNavigateToItems()
                }
            }
        }
    }

    // Language selection state (shown after camera permission is granted)
    // Use initial = true to prevent dialog flash for returning users before DataStore loads
    val languageSelectionShown by ftueRepository.languageSelectionShownFlow.collectAsState(initial = true)
    var showLanguageSelectionDialog by remember { mutableStateOf(false) }
    val currentAppLanguage by settingsRepository.appLanguageFlow.collectAsState(initial = com.scanium.app.model.AppLanguage.SYSTEM)

    // FTUE tour state
    val currentTourStep by tourViewModel?.currentStep?.collectAsState() ?: remember { mutableStateOf(null) }
    val isTourActive by tourViewModel?.isTourActive?.collectAsState() ?: remember { mutableStateOf(false) }
    val targetBounds by tourViewModel?.targetBounds?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }

    // Camera permission state
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val hasCameraPermission by remember {
        derivedStateOf { cameraPermissionState.status.isGranted }
    }

    // Initialize CameraX manager
    val cameraManager =
        remember {
            val app = context.applicationContext as? com.scanium.app.ScaniumApplication
            CameraXManager(context, lifecycleOwner, app?.telemetry)
        }

    // Pipeline diagnostics (must be after cameraManager initialization)
    val pipelineDiagnostics by cameraManager.pipelineDiagnostics.collectAsState()

    // Camera state machine
    var cameraState by remember { mutableStateOf(CameraState.IDLE) }
    var cameraErrorState by remember { mutableStateOf<CameraErrorState?>(null) }

    // Camera lens state
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var boundLensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isCameraBinding by remember { mutableStateOf(false) }
    var rebindAttempts by remember { mutableStateOf(0) }

    // Lifecycle resume counter - incremented on each ON_RESUME to restart detection
    // Fixes: frozen bboxes after backgrounding or navigating away
    var lifecycleResumeCount by remember { mutableStateOf(0) }

    val currentScanMode = ScanMode.OBJECT_DETECTION

    // Model download state for first-launch experience
    var modelDownloadState by remember { mutableStateOf<ModelDownloadState>(ModelDownloadState.Checking) }

    // Settings overlay state

    // Items count from ViewModel
    val itemsCount by itemsViewModel.items.collectAsState()
    val similarityThreshold by itemsViewModel.similarityThreshold.collectAsState()
    val classificationMode by classificationModeViewModel.classificationMode.collectAsState()
    val captureResolution by cameraViewModel.captureResolution.collectAsState()
    val saveCloudCrops by classificationModeViewModel.saveCloudCrops.collectAsState()
    val lowDataMode by classificationModeViewModel.lowDataMode.collectAsState()
    val verboseLogging by classificationModeViewModel.verboseLogging.collectAsState()
    val analysisFps by cameraManager.analysisFps.collectAsState()
    val shutterHintShown by ftueRepository.shutterHintShownFlow.collectAsState(initial = true)

    // Performance metrics
    val callsStarted by ClassificationMetrics.callsStarted.collectAsState()
    val callsCompleted by ClassificationMetrics.callsCompleted.collectAsState()
    val callsFailed by ClassificationMetrics.callsFailed.collectAsState()
    val lastLatency by ClassificationMetrics.lastLatencyMs.collectAsState()
    val queueDepth by ClassificationMetrics.queueDepth.collectAsState()
    val overlayTracks by itemsViewModel.overlayTracks.collectAsState()
    val latestQrUrl by itemsViewModel.latestQrUrl.collectAsState()
    val documentCandidateState by cameraManager.documentCandidateState.collectAsState()
    val scanGuidanceState by cameraManager.scanGuidanceState.collectAsState()
    val scanDiagnosticsEnabled by cameraManager.scanDiagnosticsEnabled.collectAsState()
    val lastRoiFilterResult by itemsViewModel.lastRoiFilterResult.collectAsState()

    // Camera FTUE state
    val cameraFtueCompleted by ftueRepository.cameraFtueCompletedFlow.collectAsState(initial = false)
    val cameraFtueCurrentStep by cameraFtueViewModel.currentStep.collectAsState()
    val cameraFtueIsActive by cameraFtueViewModel.isActive.collectAsState()
    val cameraFtueShowRoiHint by cameraFtueViewModel.showRoiHint.collectAsState()
    val cameraFtueShowBboxHint by cameraFtueViewModel.showBboxHint.collectAsState()
    val cameraFtueShowShutterHint by cameraFtueViewModel.showShutterHint.collectAsState()

    // FTUE debug toast (DEV-only)
    if (com.scanium.app.config.FeatureFlags.isDevBuild) {
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(cameraFtueCurrentStep) {
            if (cameraFtueCurrentStep != com.scanium.app.ftue.CameraFtueViewModel.CameraFtueStep.IDLE &&
                cameraFtueCurrentStep != com.scanium.app.ftue.CameraFtueViewModel.CameraFtueStep.COMPLETED
            ) {
                android.widget.Toast.makeText(
                    context,
                    "FTUE Camera step=${cameraFtueCurrentStep.name}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Compute ROI rect from scanGuidanceState
    val roiRect: androidx.compose.ui.geometry.Rect by remember(scanGuidanceState, previewSize) {
        derivedStateOf {
            val roi = scanGuidanceState.scanRoi
            val previewWidth = previewSize.width
            val previewHeight = previewSize.height

            if (previewWidth == 0 || previewHeight == 0) {
                androidx.compose.ui.geometry.Rect(0f, 0f, 0f, 0f)
            } else {
                androidx.compose.ui.geometry.Rect(
                    left = roi.left * previewWidth,
                    top = roi.top * previewHeight,
                    right = roi.right * previewWidth,
                    bottom = roi.bottom * previewHeight,
                )
            }
        }
    }

    var shutterButtonCenter by remember { mutableStateOf<Offset?>(null) }
    var firstDetectionSeen by remember { mutableStateOf(false) }

    // Document scan state
    var documentScanState by remember { mutableStateOf<DocumentScanState>(DocumentScanState.Idle) }
    var documentScanJob by remember { mutableStateOf<Job?>(null) }

    // Animation state for newly added items
    var lastAddedItem by remember { mutableStateOf<com.scanium.app.items.ScannedItem?>(null) }

    LaunchedEffect(itemsViewModel, tourViewModel) {
        itemsViewModel.itemAddedEvents.collect { item: com.scanium.app.items.ScannedItem ->
            lastAddedItem = item
            // Advance tour if on TAKE_FIRST_PHOTO step
            if (currentTourStep?.key == com.scanium.app.ftue.TourStepKey.TAKE_FIRST_PHOTO) {
                tourViewModel?.nextStep()
            }
        }
    }

    var previousClassificationMode by remember { mutableStateOf<ClassificationMode?>(null) }

    var imageSize by remember { mutableStateOf(Size(1280, 720)) }
    var previewSize by remember { mutableStateOf(Size(0, 0)) }
    var imageRotationDegrees by remember { mutableStateOf(90) } // Default portrait

    var targetRotation by remember { mutableStateOf(view.display?.rotation ?: Surface.ROTATION_0) }

    var showShutterHint by remember { mutableStateOf(false) }

    KeepScreenOn(enabled = cameraState == CameraState.SCANNING)

    LaunchedEffect(shutterHintShown) {
        if (!shutterHintShown) {
            showShutterHint = true
            delay(3000)
            showShutterHint = false
            ftueRepository.setShutterHintShown(true)
        }
    }

    LaunchedEffect(itemsViewModel) {
        itemsViewModel.itemAddedEvents.collect {
            soundManager.play(AppSound.ITEM_ADDED)
        }
    }

    DisposableEffect(view) {
        val orientationListener =
            object : OrientationEventListener(context) {
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

    // Lifecycle observer to restart detection on resume
    // Fixes: frozen bboxes after backgrounding (Issue 2) or navigating away (Issue 1)
    // CRITICAL FIX: Use session-based lifecycle management to ensure scope recreation
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                // Update diagnostics with lifecycle state
                cameraManager.updateDiagnosticsLifecycleState(event.name)

                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        // Start new camera session - recreates coroutine scope
                        cameraManager.startCameraSession()
                        // Increment to trigger preview detection restart via LaunchedEffect
                        lifecycleResumeCount++
                        Log.d("CameraScreen", "ON_RESUME: started session, lifecycleResumeCount=$lifecycleResumeCount")
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        // Stop camera session - cancels scope and clears analyzer
                        cameraManager.stopCameraSession()
                        // Clear overlay to avoid stale bboxes when returning
                        itemsViewModel.updateOverlayDetections(emptyList())
                        Log.d("CameraScreen", "ON_PAUSE: stopped session, cleared overlays")
                    }
                    else -> { /* Other events handled elsewhere */ }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ProcessLifecycleOwner observer for app-level background/foreground
    // Some OEMs keep destination RESUMED across backgrounding - this is a safety guard
    DisposableEffect(Unit) {
        val processLifecycle = ProcessLifecycleOwner.get().lifecycle
        val processObserver =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        // App going to background - ensure camera session is stopped
                        Log.d("CameraScreen", "PROCESS_ON_STOP: app backgrounding, stopping session")
                        cameraManager.stopCameraSession()
                        itemsViewModel.updateOverlayDetections(emptyList())
                    }
                    Lifecycle.Event.ON_START -> {
                        // App coming to foreground
                        // Only restart if we're still the active screen (lifecycleOwner is RESUMED)
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            Log.d("CameraScreen", "PROCESS_ON_START: app foregrounding, restarting session")
                            cameraManager.startCameraSession()
                            lifecycleResumeCount++
                        }
                    }
                    else -> { /* Ignore other events */ }
                }
            }
        processLifecycle.addObserver(processObserver)
        onDispose {
            processLifecycle.removeObserver(processObserver)
        }
    }

    LaunchedEffect(targetRotation) {
        cameraManager.updateTargetRotation(targetRotation)
    }

    // Apply detection settings to camera manager when they change
    LaunchedEffect(barcodeDetectionEnabled) {
        cameraManager.setBarcodeDetectionEnabled(barcodeDetectionEnabled)
    }

    LaunchedEffect(documentDetectionEnabled) {
        cameraManager.setDocumentDetectionEnabled(documentDetectionEnabled)
    }

    LaunchedEffect(adaptiveThrottlingEnabled) {
        cameraManager.setAdaptiveThrottlingEnabled(adaptiveThrottlingEnabled)
    }

    LaunchedEffect(scanningDiagnosticsEnabled) {
        cameraManager.setScanningDiagnosticsEnabled(scanningDiagnosticsEnabled)
    }

    // Request camera permission directly on first launch (permission-first flow)
    // Only show education dialog if user denied once (shouldShowRationale = true)
    var permissionRequestedThisSession by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted && !permissionRequestedThisSession) {
            permissionRequestedThisSession = true
            // Request permission immediately on first launch
            cameraPermissionState.launchPermissionRequest()
        }
    }

    // Show language selection after camera permission is granted (but before tour)
    LaunchedEffect(hasCameraPermission, languageSelectionShown) {
        if (hasCameraPermission && !languageSelectionShown) {
            // Camera permission granted, show language selection dialog
            delay(300) // Brief delay for smooth transition
            showLanguageSelectionDialog = true
        }
    }

    // Start tour after permission is granted AND language is selected (if tour is active)
    LaunchedEffect(hasCameraPermission, isTourActive, languageSelectionShown) {
        if (hasCameraPermission && isTourActive && languageSelectionShown) {
            delay(300) // Let camera initialize
            tourViewModel?.startTour()
        }
    }

    // Initialize Camera FTUE when permission is granted and camera is ready
    LaunchedEffect(hasCameraPermission, cameraFtueCompleted) {
        if (BuildConfig.FLAVOR == "dev") {
            Log.d("FTUE", "Camera: hasCameraPermission=$hasCameraPermission, cameraFtueCompleted=$cameraFtueCompleted")
        }
        if (hasCameraPermission && !cameraFtueCompleted) {
            if (BuildConfig.FLAVOR == "dev") {
                Log.d("FTUE", "Camera: Initializing FTUE (first time)")
            }
            delay(1000) // Wait for camera to fully initialize
            val hasExistingItems = itemsCount.isNotEmpty()
            cameraFtueViewModel.initialize(shouldStartFtue = true, hasExistingItems = hasExistingItems)
        }
    }

    // Track first detection for BBox hint
    LaunchedEffect(overlayTracks) {
        if (!firstDetectionSeen && overlayTracks.isNotEmpty() && cameraFtueIsActive) {
            firstDetectionSeen = true
            cameraFtueViewModel.onFirstDetectionAppeared()
        }
    }

    // Track when user captures an item to complete FTUE
    LaunchedEffect(itemsCount) {
        if (itemsCount.isNotEmpty() && cameraFtueIsActive) {
            cameraFtueViewModel.onItemCaptured()
        }
    }

    LaunchedEffect(cameraPermissionState) {
        snapshotFlow { hasCameraPermission }
            .distinctUntilChanged()
            .collect { isGranted ->
                cameraViewModel.onPermissionStateChanged(
                    isGranted = isGranted,
                    isScanning = cameraState == CameraState.SCANNING,
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
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            modelDownloadState = ModelDownloadState.Checking
            try {
                Log.d("CameraScreen", "Checking ML Kit model availability...")
                cameraManager.ensureModelsReady()
                Log.d("CameraScreen", "ML Kit models ready")
                modelDownloadState = ModelDownloadState.Ready
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error checking model availability", e)
                modelDownloadState =
                    ModelDownloadState.Error(
                        context.getString(R.string.camera_mlkit_error, e.message.orEmpty()),
                    )
            }
        }
    }

    // Start preview detection when camera is ready (shows bounding boxes immediately)
    // IMPORTANT: Must wait for camera binding to complete (!isCameraBinding) before starting
    // preview detection, otherwise imageAnalysis won't be set up yet
    // lifecycleResumeCount triggers restart after backgrounding or navigation return
    LaunchedEffect(modelDownloadState, cameraState, isCameraBinding, lifecycleResumeCount) {
        if (modelDownloadState == ModelDownloadState.Ready && cameraState == CameraState.IDLE && !isCameraBinding) {
            Log.d(
                "CameraScreen",
                "Starting preview detection (camera idle, model ready, binding complete, resumeCount=$lifecycleResumeCount)",
            )
            cameraManager.startPreviewDetection(
                onDetectionResult = { detections ->
                    // Update overlay with preview detections (no ROI filtering in preview mode)
                    itemsViewModel.updateOverlayDetections(
                        detections = detections,
                        scanRoi = scanGuidanceState.scanRoi,
                        lockedTrackingId = null,
                        isGoodState = false,
                    )
                },
                onFrameSize = { size ->
                    imageSize = size
                },
                onRotation = { rotation ->
                    imageRotationDegrees = rotation
                },
            )
        }
    }

    LaunchedEffect(classificationMode) {
        val previousMode = previousClassificationMode
        if (previousMode != null && previousMode != classificationMode) {
            Toast.makeText(
                context,
                "Using ${classificationMode.displayName} classification",
                Toast.LENGTH_SHORT,
            ).show()
        }
        previousClassificationMode = classificationMode
    }

    // Close settings on system back when open

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.stopPreviewDetection()
            cameraManager.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            hasCameraPermission && cameraState == CameraState.ERROR -> {
                CameraErrorContent(
                    error = cameraErrorState,
                    onRetry = {
                        cameraManager.stopScanning()
                        cameraErrorState = null
                        cameraState = CameraState.IDLE
                        itemsViewModel.updateOverlayDetections(emptyList())
                        rebindAttempts++
                    },
                    onViewItems = onNavigateToItems,
                )
            }
            hasCameraPermission -> {
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
                                context.getString(R.string.camera_lens_unavailable),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onBindingFailed = { error ->
                        lensFacing = boundLensFacing
                        error?.let {
                            cameraManager.stopScanning()
                            cameraState = CameraState.ERROR
                            cameraErrorState =
                                CameraErrorState(
                                    title = context.getString(R.string.camera_unavailable_title),
                                    message = it.message ?: context.getString(R.string.camera_unable_start_message),
                                    canRetry = true,
                                )
                            Toast.makeText(
                                context,
                                context.getString(R.string.camera_unable_start_message),
                                Toast.LENGTH_SHORT,
                            ).show()
                            Log.e("CameraScreen", "Failed to bind camera", it)
                        }
                    },
                )

                // Model download loading overlay (first launch)
                when (val state = modelDownloadState) {
                    is ModelDownloadState.Checking,
                    is ModelDownloadState.Downloading,
                    -> {
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
                                        modelDownloadState =
                                            ModelDownloadState.Error(
                                                context.getString(
                                                    R.string.camera_mlkit_error,
                                                    e.message.orEmpty(),
                                                ),
                                            )
                                    }
                                }
                            },
                            onDismiss = {
                                // User chose to exit - set to Ready to allow camera use
                                modelDownloadState = ModelDownloadState.Ready
                            },
                        )
                    }
                    ModelDownloadState.Ready -> {
                        // No overlay, camera fully functional
                    }
                }

                // Camera guidance overlay - scan zone and hints
                // Show when scanning (or idle) if guidance is enabled
                if (scanningGuidanceEnabled) {
                    if (cameraState == CameraState.SCANNING) {
                        CameraGuidanceOverlay(
                            guidanceState = scanGuidanceState,
                            // Debug info only visible in Dev flavor
                            showDebugInfo = (roiDiagnosticsEnabled || scanDiagnosticsEnabled) && BuildConfig.FLAVOR == "dev",
                            // PHASE 6: Pass ROI filter diagnostics
                            roiFilterResult = lastRoiFilterResult,
                            previewBboxCount = overlayTracks.size,
                        )
                    } else if (cameraState == CameraState.IDLE) {
                        CameraGuidanceOverlayIdle()
                    }
                }

                // PHASE 4: ROI centering hint
                // Show when detections exist but none are inside ROI
                if (cameraState == CameraState.SCANNING && lastRoiFilterResult?.hasDetectionsOutsideRoiOnly == true) {
                    RoiCenteringHint(
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }

                // Detection overlay - bounding boxes, labels, and motion animations
                // Uses MotionEnhancedOverlay for scan frame appear + lightning pulse
                // Gated by showDetectionBoxes setting (user toggle in Camera Settings)
                if (showDetectionBoxes && previewSize.width > 0 && previewSize.height > 0) {
                    MotionEnhancedOverlay(
                        detections = overlayTracks,
                        imageSize = imageSize,
                        previewSize = previewSize,
                        rotationDegrees = imageRotationDegrees,
                        // Geometry debug only visible in Dev flavor
                        showGeometryDebug = bboxMappingDebugEnabled && BuildConfig.FLAVOR == "dev",
                        overlayAccuracyStep = overlayAccuracyStep,
                        modifier = Modifier.testTag(TestSemantics.DETECTION_OVERLAY),
                    )
                }

                documentCandidateState?.let { candidateState ->
                    if (previewSize.width > 0 && previewSize.height > 0) {
                        DocumentAlignmentOverlay(
                            candidateState = candidateState,
                            imageSize = imageSize,
                        )
                    }
                }

                // Camera FTUE overlays - ROI, BBox, and Shutter hints
                if (cameraFtueShowRoiHint) {
                    CameraFtueOverlay(
                        isVisible = true,
                        hintType = HintType.ROI_PULSE,
                        roiRect = roiRect,
                        onDismiss = { cameraFtueViewModel.dismiss() },
                    )
                }

                if (cameraFtueShowBboxHint) {
                    CameraFtueOverlay(
                        isVisible = true,
                        hintType = HintType.BBOX_HINT,
                        targetRect = overlayTracks.firstOrNull()?.let { track ->
                            androidx.compose.ui.geometry.Rect(
                                left = track.bounds.left,
                                top = track.bounds.top,
                                right = track.bounds.right,
                                bottom = track.bounds.bottom,
                            )
                        },
                        onDismiss = { cameraFtueViewModel.dismiss() },
                    )
                }

                if (cameraFtueShowShutterHint) {
                    CameraFtueOverlay(
                        isVisible = true,
                        hintType = HintType.SHUTTER_PULSE,
                        shutterButtonCenter = shutterButtonCenter,
                        onDismiss = { cameraFtueViewModel.dismiss() },
                    )
                }

                // Cloud configuration status banner
                ConfigurationStatusBanner(
                    classificationMode = classificationMode,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                // DEV BUILD Indicator
                if (com.scanium.app.BuildConfig.DEBUG) {
                    Text(
                        text = stringResource(R.string.camera_dev_build),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 16.dp, end = 16.dp)
                                .background(Color.Red.copy(alpha = 0.7f), shape = MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }

                // Performance Overlay (Debug)
                // Only visible in Dev flavor (hidden in Beta and Prod)
                if (verboseLogging && BuildConfig.FLAVOR == "dev") {
                    PerfOverlay(
                        analysisFps = analysisFps,
                        classificationMode = classificationMode,
                        callsStarted = callsStarted,
                        callsCompleted = callsCompleted,
                        callsFailed = callsFailed,
                        lastLatency = lastLatency,
                        queueDepth = queueDepth,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 90.dp, start = 16.dp),
                    )
                }

                // Camera Pipeline Lifecycle Debug Overlay
                // Only visible in Dev flavor (hidden in Beta and Prod)
                if (cameraPipelineDebugEnabled && BuildConfig.FLAVOR == "dev") {
                    CameraPipelineDebugOverlay(
                        diagnostics = pipelineDiagnostics,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(top = if (verboseLogging) 250.dp else 90.dp, start = 16.dp),
                    )
                }

                latestQrUrl?.let { url ->
                    QrUrlOverlay(
                        url = url,
                        onOpen = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 120.dp),
                    )
                }

                // Document scan overlay - show when document candidate is detected
                DocumentScanOverlay(
                    isVisible = documentCandidateState != null && cameraState != CameraState.SCANNING,
                    scanState = documentScanState,
                    onScanClick = {
                        if (documentScanState is DocumentScanState.Idle) {
                            documentScanState = DocumentScanState.Scanning
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                            documentScanJob =
                                scope.launch {
                                    when (val result = cameraManager.scanDocument()) {
                                        is CameraXManager.DocumentScanResult.Success -> {
                                            // Add the scanned document to items
                                            itemsViewModel.addItem(result.item)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.camera_document_scan_success),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        is CameraXManager.DocumentScanResult.NoTextDetected -> {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.camera_document_scan_no_text),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        is CameraXManager.DocumentScanResult.Cancelled -> {
                                            // User cancelled - do nothing
                                        }
                                        is CameraXManager.DocumentScanResult.Error -> {
                                            Log.e("CameraScreen", "Document scan error: ${result.message}", result.exception)
                                            Toast.makeText(
                                                context,
                                                context.getString(
                                                    R.string.camera_document_scan_failed,
                                                    result.message,
                                                ),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                    documentScanState = DocumentScanState.Idle
                                }
                        }
                    },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 160.dp),
                )

                // Overlay UI
                CameraOverlay(
                    itemsCount = itemsCount.size,
                    lastAddedItem = lastAddedItem,
                    onAnimationFinished = { lastAddedItem = null },
                    cameraState = cameraState,
                    captureResolution = captureResolution,
                    onNavigateToItems = {
                        // If tour is on OPEN_ITEM_LIST step, advance before navigation
                        if (currentTourStep?.key == com.scanium.app.ftue.TourStepKey.OPEN_ITEM_LIST) {
                            tourViewModel?.nextStep()
                        }
                        onNavigateToItems()
                    },
                    onOpenSettings = {
                        onNavigateToSettings()
                    },
                    tourViewModel = tourViewModel,
                    showShutterHint = showShutterHint,
                    onShutterTap = {
                        // Notify Camera FTUE of shutter tap
                        cameraFtueViewModel.onShutterTapped()

                        // Single tap: capture one frame
                        if (cameraState == CameraState.IDLE) {
                            // PHASE 3: Check ROI eligibility before capture for better feedback
                            val hasEligibleBbox = overlayTracks.isNotEmpty()
                            val hasOutsideRoiOnly = lastRoiFilterResult?.hasDetectionsOutsideRoiOnly == true

                            // Show hint if capturing without eligible bbox but allow capture anyway
                            if (!hasEligibleBbox && hasOutsideRoiOnly) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.camera_hint_center_object),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }

                            cameraState = CameraState.CAPTURING
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            cameraManager.captureSingleFrame(
                                scanMode = currentScanMode,
                                onResult = { items ->
                                    cameraState = CameraState.IDLE
                                    if (items.isEmpty()) {
                                        // PHASE 3: More specific message based on what we detected
                                        val message =
                                            if (hasOutsideRoiOnly) {
                                                context.getString(R.string.camera_hint_outside_scan_zone)
                                            } else {
                                                context.getString(R.string.camera_hint_no_objects)
                                            }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        soundManager.play(AppSound.ERROR)
                                    } else {
                                        // Capture high-res image and update items with it
                                        scope.launch {
                                            val highResUri = cameraManager.captureHighResImage()
                                            soundManager.play(AppSound.CAPTURE)

                                            if (autoSaveEnabled && saveDirectoryUri != null && highResUri != null) {
                                                try {
                                                    context.contentResolver.openInputStream(highResUri)?.use { input ->
                                                        val savedUri =
                                                            StorageHelper.saveToDirectory(
                                                                context,
                                                                Uri.parse(saveDirectoryUri),
                                                                input,
                                                                "image/jpeg",
                                                                "Scanium",
                                                            )
                                                        if (savedUri == null) {
                                                            Log.e("CameraScreen", "Failed to auto-save image")
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("CameraScreen", "Error auto-saving image", e)
                                                }
                                            }

                                            val itemsWithHighRes =
                                                if (highResUri != null) {
                                                    items.map { item ->
                                                        item.copy(
                                                            fullImageUri = highResUri,
                                                        )
                                                    }
                                                } else {
                                                    // Fallback: use original items if high-res capture failed
                                                    items
                                                }
                                            itemsViewModel.addItemsWithVisionPrefill(context, itemsWithHighRes)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.camera_detected_items, items.size),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                                onDetectionResult = { detections ->
                                    // PHASE 2: Pass ROI to filter detections before rendering
                                    // Single-shot capture uses default ROI (not actively scanning)
                                    itemsViewModel.updateOverlayDetections(
                                        detections = detections,
                                        scanRoi = scanGuidanceState.scanRoi,
                                        lockedTrackingId = scanGuidanceState.lockedCandidateId,
                                        isGoodState = scanGuidanceState.state == com.scanium.core.models.scanning.GuidanceState.GOOD,
                                    )
                                },
                                onDetectionEvent = { event ->
                                    itemsViewModel.onDetectionEvent(event)
                                },
                                onFrameSize = { size ->
                                    imageSize = size
                                },
                                onRotation = { rotation ->
                                    imageRotationDegrees = rotation
                                },
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
                                                        val savedUri =
                                                            StorageHelper.saveToDirectory(
                                                                context,
                                                                Uri.parse(saveDirectoryUri),
                                                                input,
                                                                "image/jpeg",
                                                                "Scanium",
                                                            )
                                                        if (savedUri == null) {
                                                            Log.e("CameraScreen", "Failed to auto-save image")
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("CameraScreen", "Error auto-saving image", e)
                                                }
                                            }

                                            val itemsWithHighRes =
                                                if (highResUri != null) {
                                                    items.map { item ->
                                                        item.copy(fullImageUri = highResUri)
                                                    }
                                                } else {
                                                    items
                                                }
                                            itemsViewModel.addItemsWithVisionPrefill(context, itemsWithHighRes)
                                        }
                                    }
                                },
                                onDetectionResult = { detections ->
                                    // PHASE 2: Pass ROI and locked ID to filter detections
                                    // This ensures only ROI-eligible detections are shown
                                    // PHASE 1: Pass isGoodState to show READY visual state
                                    itemsViewModel.updateOverlayDetections(
                                        detections = detections,
                                        scanRoi = scanGuidanceState.scanRoi,
                                        lockedTrackingId = scanGuidanceState.lockedCandidateId,
                                        isGoodState = scanGuidanceState.state == com.scanium.core.models.scanning.GuidanceState.GOOD,
                                    )
                                },
                                onDetectionEvent = { event ->
                                    itemsViewModel.onDetectionEvent(event)
                                },
                                onFrameSize = { size ->
                                    imageSize = size
                                },
                                onRotation = { rotation ->
                                    imageRotationDegrees = rotation
                                },
                            )
                        }
                    },
                    onStopScanning = {
                        // Tap while scanning: stop
                        if (cameraState == CameraState.SCANNING) {
                            cameraState = CameraState.IDLE
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            cameraManager.stopScanning()
                            // Clear current detections, preview detection will restart via LaunchedEffect
                            itemsViewModel.updateOverlayDetections(emptyList())
                        }
                    },
                    onFlipCamera = {
                        cameraState = CameraState.IDLE
                        itemsViewModel.updateOverlayDetections(emptyList())
                        cameraManager.stopScanning()
                        lensFacing =
                            if (boundLensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                    },
                    isFlipEnabled = !isCameraBinding,
                )

                // FTUE Tour Overlays
                if (isTourActive && hasCameraPermission) {
                    when (currentTourStep?.key) {
                        com.scanium.app.ftue.TourStepKey.WELCOME -> {
                            com.scanium.app.ftue.WelcomeOverlay(
                                onStart = { tourViewModel?.nextStep() },
                                onSkip = { tourViewModel?.skipTour() },
                            )
                        }
                        com.scanium.app.ftue.TourStepKey.TAKE_FIRST_PHOTO,
                        com.scanium.app.ftue.TourStepKey.OPEN_ITEM_LIST,
                        -> {
                            currentTourStep?.let { step ->
                                val bounds = step.targetKey?.let { targetBounds[it] }
                                if (bounds != null || step.targetKey == null) {
                                    com.scanium.app.ftue.SpotlightTourOverlay(
                                        step = step,
                                        targetBounds = bounds,
                                        onNext = { tourViewModel?.nextStep() },
                                        onBack = { tourViewModel?.previousStep() },
                                        onSkip = { tourViewModel?.skipTour() },
                                    )
                                }
                            }
                        }
                        else -> { /* Other steps handled in ItemsListScreen or EditItemScreen */ }
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
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        context.startActivity(intent)
                    },
                )
            }
        }

        // Language selection dialog (shown after camera permission is granted)
        if (showLanguageSelectionDialog) {
            com.scanium.app.ftue.LanguageSelectionDialog(
                currentLanguage = currentAppLanguage,
                onLanguageSelected = { selectedLanguage ->
                    showLanguageSelectionDialog = false
                    scope.launch {
                        // CRITICAL: Save language selection flag FIRST, before locale change
                        // setApplicationLocales() triggers Activity recreation, which cancels this coroutine
                        // If we save after, the flag won't persist and dialog will show again
                        ftueRepository.setLanguageSelectionShown(true)

                        // Set app language (legacy setting)
                        settingsRepository.setAppLanguage(selectedLanguage)

                        // Set primary language (unified setting)
                        settingsRepository.setPrimaryLanguage(selectedLanguage.code)

                        // Map language to marketplace country and set it
                        val marketplaceCountry = settingsRepository.mapLanguageToMarketplaceCountry(selectedLanguage.code)
                        settingsRepository.setPrimaryRegionCountry(marketplaceCountry)

                        // Update app locale (this triggers Activity recreation)
                        val localeList =
                            when (selectedLanguage) {
                                com.scanium.app.model.AppLanguage.SYSTEM -> androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                                else -> androidx.core.os.LocaleListCompat.forLanguageTags(selectedLanguage.code)
                            }
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
                    }
                },
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
    onBindingFailed: (Throwable?) -> Unit,
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
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { intSize ->
                    onPreviewSizeChanged(intSize)
                },
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
