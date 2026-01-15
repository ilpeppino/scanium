package com.scanium.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.scanium.app.R
import com.scanium.app.camera.ConfidenceTiers
import com.scanium.app.config.FeatureFlags
import com.scanium.app.ui.settings.developer.AssistantDiagnosticsSection
import com.scanium.app.ui.settings.developer.HealthMonitorSection
import com.scanium.app.ui.settings.developer.HealthMonitorSectionState
import com.scanium.app.ui.settings.developer.PreflightDiagnosticsSection
import com.scanium.app.ui.settings.developer.SystemHealthSection
import com.scanium.app.ui.settings.developer.SystemHealthSectionState

/**
 * Developer Options screen with System Health diagnostics.
 * Only visible in debug builds.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DeveloperOptionsScreen(
    viewModel: DeveloperOptionsViewModel,
    classificationViewModel: com.scanium.app.settings.ClassificationModeViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val diagnosticsState by viewModel.diagnosticsState.collectAsState()
    val assistantDiagnosticsState by viewModel.assistantDiagnosticsState.collectAsState()
    val preflightState by viewModel.preflightState.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    val forceFtueTour by viewModel.forceFtueTour.collectAsState()
    val showFtueDebugBounds by viewModel.showFtueDebugBounds.collectAsState()
    val autoRefreshEnabled by viewModel.autoRefreshEnabled.collectAsState()
    val copyResult by viewModel.copyResult.collectAsState()
    val allowScreenshots by viewModel.allowScreenshots.collectAsState()
    val barcodeDetectionEnabled by viewModel.barcodeDetectionEnabled.collectAsState()
    val documentDetectionEnabled by viewModel.documentDetectionEnabled.collectAsState()
    val adaptiveThrottlingEnabled by viewModel.adaptiveThrottlingEnabled.collectAsState()
    val liveScanDiagnosticsEnabled by viewModel.scanningDiagnosticsEnabled.collectAsState()
    val bboxMappingDebugEnabled by viewModel.bboxMappingDebugEnabled.collectAsState()
    val correlationDebugEnabled by viewModel.correlationDebugEnabled.collectAsState()
    val cameraPipelineDebugEnabled by viewModel.cameraPipelineDebugEnabled.collectAsState()
    val overlayAccuracyStep by viewModel.overlayAccuracyStep.collectAsState()
    val scrollState = rememberScrollState()
    val saveCloudCrops by classificationViewModel.saveCloudCrops.collectAsState()
    val verboseLogging by classificationViewModel.verboseLogging.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val showNotificationPermission by remember {
        derivedStateOf {
            FeatureFlags.isDevBuild && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        }
    }
    val showDeveloperModeToggle by remember {
        derivedStateOf { !FeatureFlags.isDevBuild }
    }

    // Health Monitor state
    val healthMonitorState by viewModel.healthMonitorState.collectAsState()
    val healthMonitorConfig by viewModel.healthMonitorConfig.collectAsState()
    val healthMonitorWorkState by viewModel.healthMonitorWorkState.collectAsState()

    // Show snackbar for copy result
    LaunchedEffect(copyResult) {
        copyResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCopyResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Options") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState),
        ) {
            // Diagnostics description (DEV-only)
            if (FeatureFlags.isDevBuild) {
                DiagnosticsDescriptionCard()
            }

            // Notification Permission Section (DEV-only, Android 13+)
            if (showNotificationPermission) {
                NotificationPermissionSection()
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // System Health Section
            SystemHealthSection(
                state = SystemHealthSectionState(
                    diagnosticsState = diagnosticsState,
                    autoRefreshEnabled = autoRefreshEnabled,
                ),
                onRefresh = { viewModel.refreshDiagnostics() },
                onCopyDiagnostics = { viewModel.copyDiagnosticsToClipboard() },
                onAutoRefreshChange = { viewModel.setAutoRefreshEnabled(it) },
                onResetBaseUrl = { viewModel.resetBaseUrlOverride() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Assistant / AI Diagnostics Section
            AssistantDiagnosticsSection(
                state = assistantDiagnosticsState,
                onRecheck = { viewModel.refreshAssistantDiagnostics() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Preflight Diagnostics Section
            PreflightDiagnosticsSection(
                state = preflightState,
                onRefresh = { viewModel.refreshPreflight() },
                onClearCache = { viewModel.clearPreflightCache() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Background Health Monitor Section
            HealthMonitorSection(
                state = HealthMonitorSectionState(
                    monitorState = healthMonitorState,
                    monitorConfig = healthMonitorConfig,
                    workState = healthMonitorWorkState,
                    effectiveBaseUrl = viewModel.getHealthMonitorEffectiveBaseUrl(),
                ),
                onEnabledChange = { viewModel.setHealthMonitorEnabled(it) },
                onNotifyRecoveryChange = { viewModel.setHealthMonitorNotifyOnRecovery(it) },
                onBaseUrlChange = { viewModel.setHealthMonitorBaseUrl(it) },
                onRunNow = { viewModel.runHealthCheckNow() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader("Diagnostics & Security")

            SettingSwitchRow(
                title = "Allow screenshots",
                subtitle = if (allowScreenshots) "Screenshots allowed" else "Screenshots blocked (FLAG_SECURE)",
                icon = Icons.Default.ScreenLockPortrait,
                checked = allowScreenshots,
                onCheckedChange = { viewModel.setAllowScreenshots(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Developer Settings Section
            SettingsSectionHeader("Developer Settings")

            // In DEV builds, Developer Mode is always ON and cannot be toggled
            // Only show the toggle in non-DEV builds (shouldn't normally be reachable)
            if (showDeveloperModeToggle) {
                SettingSwitchRow(
                    title = "Developer Mode",
                    subtitle = "Unlock all features for testing",
                    icon = Icons.Default.BugReport,
                    checked = isDeveloperMode,
                    onCheckedChange = { viewModel.setDeveloperMode(it) },
                )
            } else {
                // DEV flavor: Show static indicator that dev mode is always on
                DevModeAlwaysOnRow()
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Detection Settings Section (Performance & Low-Power Mode)
            SettingsSectionHeader("Detection & Performance")

            SettingSwitchRow(
                title = "Barcode/QR Detection",
                subtitle = if (barcodeDetectionEnabled) "Scanning for barcodes and QR codes" else "Barcode detection disabled",
                icon = Icons.Default.CropFree,
                checked = barcodeDetectionEnabled,
                onCheckedChange = { viewModel.setBarcodeDetectionEnabled(it) },
            )

            SettingSwitchRow(
                title = "Document Detection",
                subtitle = if (documentDetectionEnabled) "Detecting document candidates" else "Document detection disabled",
                icon = Icons.Filled.Article,
                checked = documentDetectionEnabled,
                onCheckedChange = { viewModel.setDocumentDetectionEnabled(it) },
            )

            SettingSwitchRow(
                title = "Adaptive Throttling",
                subtitle = if (adaptiveThrottlingEnabled) "Low-power mode: auto-adjusts scan rate" else "Fixed scan rate (may drain battery)",
                icon = Icons.Default.Speed,
                checked = adaptiveThrottlingEnabled,
                onCheckedChange = { viewModel.setAdaptiveThrottlingEnabled(it) },
            )

            SettingSwitchRow(
                title = "Live Scan Diagnostics",
                subtitle = if (liveScanDiagnosticsEnabled) "Detailed LiveScan logs enabled (Logcat tag: LiveScan)" else "Diagnostic logging disabled",
                icon = Icons.Default.Analytics,
                checked = liveScanDiagnosticsEnabled,
                onCheckedChange = { viewModel.setScanningDiagnosticsEnabled(it) },
            )

            SettingSwitchRow(
                title = "Geometry Debug Overlay",
                subtitle = if (bboxMappingDebugEnabled) "Shows bbox mapping info: rotation, scale, dimensions (Logcat tag: GeomMap)" else "Geometry debug disabled",
                icon = Icons.Default.GridOn,
                checked = bboxMappingDebugEnabled,
                onCheckedChange = { viewModel.setBboxMappingDebugEnabled(it) },
            )

            SettingSwitchRow(
                title = "Bbox↔Snapshot Correlation",
                subtitle = if (correlationDebugEnabled) "Validates bbox AR matches crop AR (Logcat tag: CORR)" else "Correlation validation disabled",
                icon = Icons.Default.Compare,
                checked = correlationDebugEnabled,
                onCheckedChange = { viewModel.setCorrelationDebugEnabled(it) },
            )

            SettingSwitchRow(
                title = "Camera Pipeline Debug",
                subtitle = if (cameraPipelineDebugEnabled) "Shows lifecycle/session overlay (Logcat tag: CAM_LIFE)" else "Pipeline debug disabled",
                icon = Icons.Default.Videocam,
                checked = cameraPipelineDebugEnabled,
                onCheckedChange = { viewModel.setCameraPipelineDebugEnabled(it) },
            )

            // Overlay Accuracy Filter (stepped slider)
            OverlayAccuracySliderRow(
                currentStep = overlayAccuracyStep,
                onStepChange = { viewModel.setOverlayAccuracyStep(it) },
            )

            SettingsSectionHeader("Classifier Diagnostics")
            SettingSwitchRow(
                title = "Save cloud crops",
                subtitle = "Writes outgoing classifier crops to cache (cleared on uninstall)",
                icon = Icons.Default.Cloud,
                checked = saveCloudCrops,
                onCheckedChange = classificationViewModel::updateSaveCloudCrops,
            )

            SettingSwitchRow(
                title = "Verbose classifier logging",
                subtitle = "Adds extra classifier details to Logcat (debug builds only)",
                icon = Icons.Default.Tune,
                checked = verboseLogging,
                onCheckedChange = classificationViewModel::updateVerboseLogging,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Testing Section
            SettingsSectionHeader("Testing & Debug")

            SettingActionRow(
                title = "Test Crash Reporting",
                subtitle = "Send test event to Sentry (handled exception)",
                icon = Icons.Default.BugReport,
                onClick = { viewModel.triggerCrashTest(throwCrash = false) },
            )

            SettingActionRow(
                title = "Test Diagnostics Bundle",
                subtitle = "Capture exception with diagnostics.json attachment",
                icon = Icons.Default.BugReport,
                onClick = { viewModel.triggerDiagnosticsTest() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // First-Time Experience Section
            SettingsSectionHeader("First-Time Experience")

            SettingSwitchRow(
                title = "Force First-Time Tour",
                subtitle = "Always show tour on app launch (debug only)",
                icon = Icons.Default.Info,
                checked = forceFtueTour,
                onCheckedChange = { viewModel.setForceFtueTour(it) },
            )

            SettingSwitchRow(
                title = "Show FTUE debug bounds",
                subtitle = "Draw spotlight outlines and center line",
                icon = Icons.Default.CenterFocusStrong,
                checked = showFtueDebugBounds,
                onCheckedChange = { viewModel.setShowFtueDebugBounds(it) },
            )

            SettingActionRow(
                title = "Reset Tour Progress",
                subtitle = "Clear tour completion flag",
                icon = Icons.Default.Refresh,
                onClick = { viewModel.resetFtueTour() },
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DevModeAlwaysOnRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(
                text = "Developer Mode",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Always enabled in DEV builds",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun OverlayAccuracySliderRow(
    currentStep: Int,
    onStepChange: (Int) -> Unit,
) {
    val stepCount = ConfidenceTiers.stepCount
    val currentTier = ConfidenceTiers.getTier(currentStep)
    val displayText = ConfidenceTiers.getDisplayText(currentStep)

    // Slider value state (convert to/from Float for Slider component)
    var sliderValue by remember(currentStep) { mutableStateOf(currentStep.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Header row with icon and title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = "Overlay accuracy filter",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(
                    text = "Aggregation Accuracy (Overlay Filter)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Slider with stepped values
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                sliderValue = newValue
            },
            onValueChangeFinished = {
                // Snap to integer and notify
                val snappedStep = sliderValue.toInt().coerceIn(0, stepCount - 1)
                sliderValue = snappedStep.toFloat()
                if (snappedStep != currentStep) {
                    onStepChange(snappedStep)
                }
            },
            valueRange = 0f..(stepCount - 1).toFloat(),
            steps = stepCount - 2, // steps parameter is intermediate steps, so (count - 2)
            modifier = Modifier.fillMaxWidth(),
        )

        // Step labels row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ConfidenceTiers.tiers.forEachIndexed { index, tier ->
                val isSelected = index == currentStep
                Text(
                    text = tier.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Helper text
        Text(
            text = "Filters which bboxes are drawn on camera overlay. Does not affect detection or aggregation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DiagnosticsDescriptionCard() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(
                    text = "Diagnostics & Checks",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Diagnostics & checks help verify connectivity to your backend services " +
                        "(health, config, preflight, assistant) and alert you when something breaks. " +
                        "Use them while testing to quickly spot disruptions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ==================== Notification Permission Section (DEV-only) ====================

/**
 * Notification permission card for DEV builds on Android 13+.
 * Requests POST_NOTIFICATIONS permission and provides settings deep link if denied.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NotificationPermissionSection() {
    val context = LocalContext.current
    val notificationPermissionState = rememberPermissionState(
        Manifest.permission.POST_NOTIFICATIONS
    )

    // Check if notifications are enabled at system level
    val areNotificationsEnabled = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val hasPermission = notificationPermissionState.status.isGranted
    val shouldShowRationale = notificationPermissionState.status.shouldShowRationale

    Surface(
        color = when {
            hasPermission && areNotificationsEnabled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when {
                        hasPermission && areNotificationsEnabled -> Icons.Default.Notifications
                        else -> Icons.Default.NotificationsOff
                    },
                    contentDescription = null,
                    tint = when {
                        hasPermission && areNotificationsEnabled -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notification Permission",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            hasPermission && areNotificationsEnabled ->
                                "✓ Granted - Background monitor can send notifications"
                            !hasPermission && shouldShowRationale ->
                                "Permission needed for background health monitor notifications"
                            !hasPermission ->
                                "Permission needed for background health monitor notifications"
                            !areNotificationsEnabled ->
                                "Notifications disabled at system level"
                            else ->
                                "Status unknown"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            hasPermission && areNotificationsEnabled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        },
                    )
                }
            }

            // Action buttons
            if (!hasPermission || !areNotificationsEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Request permission button
                    if (!hasPermission) {
                        Button(
                            onClick = { notificationPermissionState.launchPermissionRequest() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Permission")
                        }
                    }

                    // Open settings button (always show if notifications aren't working)
                    if (!hasPermission || !areNotificationsEnabled) {
                        OutlinedButton(
                            onClick = {
                                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                } else {
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                }
                                context.startActivity(intent)
                            },
                            modifier = if (!hasPermission) Modifier else Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Settings")
                        }
                    }
                }
            }
        }
    }
}
