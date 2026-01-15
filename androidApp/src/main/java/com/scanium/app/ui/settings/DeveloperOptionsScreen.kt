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
import androidx.compose.ui.res.stringResource
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
                title = { Text(stringResource(R.string.settings_developer_options_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
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

            SettingsSectionHeader(stringResource(R.string.settings_developer_options_section_diagnostics_security))

            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_allow_screenshots_title),
                subtitle =
                    if (allowScreenshots) {
                        stringResource(R.string.settings_developer_options_allow_screenshots_on)
                    } else {
                        stringResource(R.string.settings_developer_options_allow_screenshots_off)
                    },
                icon = Icons.Default.ScreenLockPortrait,
                checked = allowScreenshots,
                onCheckedChange = { viewModel.setAllowScreenshots(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Developer Settings Section
            SettingsSectionHeader(stringResource(R.string.settings_developer_options_section_developer_settings))

            // In DEV builds, Developer Mode is always ON and cannot be toggled
            // Only show the toggle in non-DEV builds (shouldn't normally be reachable)
            if (showDeveloperModeToggle) {
                SettingSwitchRow(
                    title = stringResource(R.string.settings_developer_options_developer_mode_title),
                    subtitle = stringResource(R.string.settings_developer_options_developer_mode_subtitle),
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
            SettingsSectionHeader(stringResource(R.string.settings_developer_options_section_detection_performance))

            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_barcode_detection_title),
                subtitle =
                    if (barcodeDetectionEnabled) {
                        stringResource(R.string.settings_developer_options_barcode_detection_on)
                    } else {
                        stringResource(R.string.settings_developer_options_barcode_detection_off)
                    },
                icon = Icons.Default.CropFree,
                checked = barcodeDetectionEnabled,
                onCheckedChange = { viewModel.setBarcodeDetectionEnabled(it) },
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_document_detection_title),
                subtitle =
                    if (documentDetectionEnabled) {
                        stringResource(R.string.settings_developer_options_document_detection_on)
                    } else {
                        stringResource(R.string.settings_developer_options_document_detection_off)
                    },
                icon = Icons.Filled.Article,
                checked = documentDetectionEnabled,
                onCheckedChange = { viewModel.setDocumentDetectionEnabled(it) },
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_adaptive_throttling_title),
                subtitle =
                    if (adaptiveThrottlingEnabled) {
                        stringResource(R.string.settings_developer_options_adaptive_throttling_on)
                    } else {
                        stringResource(R.string.settings_developer_options_adaptive_throttling_off)
                    },
                icon = Icons.Default.Speed,
                checked = adaptiveThrottlingEnabled,
                onCheckedChange = { viewModel.setAdaptiveThrottlingEnabled(it) },
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_live_scan_diagnostics_title),
                subtitle =
                    if (liveScanDiagnosticsEnabled) {
                        stringResource(R.string.settings_developer_options_live_scan_diagnostics_on)
                    } else {
                        stringResource(R.string.settings_developer_options_live_scan_diagnostics_off)
                    },
                icon = Icons.Default.Analytics,
                checked = liveScanDiagnosticsEnabled,
                onCheckedChange = { viewModel.setScanningDiagnosticsEnabled(it) },
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_geometry_debug_title),
                subtitle =
                    if (bboxMappingDebugEnabled) {
                        stringResource(R.string.settings_developer_options_geometry_debug_on)
                    } else {
                        stringResource(R.string.settings_developer_options_geometry_debug_off)
                    },
                icon = Icons.Default.GridOn,
                checked = bboxMappingDebugEnabled,
                onCheckedChange = { viewModel.setBboxMappingDebugEnabled(it) },
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_correlation_debug_title),
                subtitle =
                    if (correlationDebugEnabled) {
                        stringResource(R.string.settings_developer_options_correlation_debug_on)
                    } else {
                        stringResource(R.string.settings_developer_options_correlation_debug_off)
                    },
                icon = Icons.Default.Compare,
                checked = correlationDebugEnabled,
                onCheckedChange = { viewModel.setCorrelationDebugEnabled(it) },
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_camera_pipeline_debug_title),
                subtitle =
                    if (cameraPipelineDebugEnabled) {
                        stringResource(R.string.settings_developer_options_camera_pipeline_debug_on)
                    } else {
                        stringResource(R.string.settings_developer_options_camera_pipeline_debug_off)
                    },
                icon = Icons.Default.Videocam,
                checked = cameraPipelineDebugEnabled,
                onCheckedChange = { viewModel.setCameraPipelineDebugEnabled(it) },
            )

            // Overlay Accuracy Filter (stepped slider)
            OverlayAccuracySliderRow(
                currentStep = overlayAccuracyStep,
                onStepChange = { viewModel.setOverlayAccuracyStep(it) },
            )

            SettingsSectionHeader(stringResource(R.string.settings_developer_options_section_classifier_diagnostics))
            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_save_cloud_crops_title),
                subtitle = stringResource(R.string.settings_developer_options_save_cloud_crops_subtitle),
                icon = Icons.Default.Cloud,
                checked = saveCloudCrops,
                onCheckedChange = classificationViewModel::updateSaveCloudCrops,
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_verbose_classifier_logging_title),
                subtitle = stringResource(R.string.settings_developer_options_verbose_classifier_logging_subtitle),
                icon = Icons.Default.Tune,
                checked = verboseLogging,
                onCheckedChange = classificationViewModel::updateVerboseLogging,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Testing Section
            SettingsSectionHeader(stringResource(R.string.settings_developer_options_section_testing_debug))

            SettingActionRow(
                title = stringResource(R.string.settings_developer_options_test_crash_reporting_title),
                subtitle = stringResource(R.string.settings_developer_options_test_crash_reporting_subtitle),
                icon = Icons.Default.BugReport,
                onClick = { viewModel.triggerCrashTest(throwCrash = false) },
            )

            SettingActionRow(
                title = stringResource(R.string.settings_developer_options_test_diagnostics_bundle_title),
                subtitle = stringResource(R.string.settings_developer_options_test_diagnostics_bundle_subtitle),
                icon = Icons.Default.BugReport,
                onClick = { viewModel.triggerDiagnosticsTest() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // First-Time Experience Section
            SettingsSectionHeader(stringResource(R.string.settings_developer_options_section_first_time_experience))

            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_force_first_time_tour_title),
                subtitle = stringResource(R.string.settings_developer_options_force_first_time_tour_subtitle),
                icon = Icons.Default.Info,
                checked = forceFtueTour,
                onCheckedChange = { viewModel.setForceFtueTour(it) },
            )

            SettingSwitchRow(
                title = stringResource(R.string.settings_developer_options_show_ftue_debug_bounds_title),
                subtitle = stringResource(R.string.settings_developer_options_show_ftue_debug_bounds_subtitle),
                icon = Icons.Default.CenterFocusStrong,
                checked = showFtueDebugBounds,
                onCheckedChange = { viewModel.setShowFtueDebugBounds(it) },
            )

            SettingActionRow(
                title = stringResource(R.string.settings_developer_options_reset_tour_progress_title),
                subtitle = stringResource(R.string.settings_developer_options_reset_tour_progress_subtitle),
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
                text = stringResource(R.string.settings_developer_options_developer_mode_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.settings_developer_options_developer_mode_always_on_subtitle),
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
                contentDescription = stringResource(R.string.settings_developer_options_overlay_accuracy_filter_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.settings_developer_options_overlay_accuracy_title),
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
            text = stringResource(R.string.settings_developer_options_overlay_accuracy_help),
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
                    text = stringResource(R.string.settings_developer_options_diagnostics_checks_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_developer_options_diagnostics_checks_body),
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
                        text = stringResource(R.string.settings_developer_options_notification_permission_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text =
                            when {
                                hasPermission && areNotificationsEnabled ->
                                    stringResource(R.string.settings_developer_options_notification_permission_granted)
                                !hasPermission && shouldShowRationale ->
                                    stringResource(R.string.settings_developer_options_notification_permission_needed)
                                !hasPermission ->
                                    stringResource(R.string.settings_developer_options_notification_permission_needed)
                                !areNotificationsEnabled ->
                                    stringResource(R.string.settings_developer_options_notification_permission_disabled)
                                else ->
                                    stringResource(R.string.settings_developer_options_notification_permission_unknown)
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
                            Text(stringResource(R.string.settings_developer_options_notification_permission_grant_button))
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
                            Text(stringResource(R.string.settings_developer_options_notification_permission_open_settings))
                        }
                    }
                }
            }
        }
    }
}
