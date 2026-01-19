package com.scanium.app.camera

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.ftue.CameraUiFtueAnchorRegistry
import com.scanium.app.ftue.CameraUiFtueViewModel
import com.scanium.app.ftue.ftueAnchor
import com.scanium.app.ftue.ftuePulse
import com.scanium.app.ftue.tourTarget
import com.scanium.app.ScannedItem
import com.scanium.app.ui.common.DonationContent
import kotlinx.coroutines.delay

/**
 * Overlay UI on top of camera preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.CameraOverlay(
    itemsCount: Int,
    lastAddedItem: ScannedItem?,
    onAnimationFinished: () -> Unit,
    cameraState: CameraState,
    captureResolution: CaptureResolution,
    onNavigateToItems: () -> Unit,
    onOpenSettings: () -> Unit,
    tourViewModel: com.scanium.app.ftue.TourViewModel?,
    cameraUiFtueRegistry: CameraUiFtueAnchorRegistry?,
    cameraUiFtueStep: CameraUiFtueViewModel.CameraUiFtueStep = CameraUiFtueViewModel.CameraUiFtueStep.IDLE,
    showShutterHint: Boolean,
    onShutterTap: () -> Unit,
    onShutterLongPress: () -> Unit,
    onStopScanning: () -> Unit,
    onFlipCamera: () -> Unit,
    isFlipEnabled: Boolean,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Support sheet state
    var showSupportSheet by remember { mutableStateOf(false) }

    // Top bar with two-slot layout: hamburger (left), logo (right)
    Row(
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .semantics { traversalIndex = 0f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left slot: Hamburger menu
        TopCornerButtonContainer(
            contentAlignment = Alignment.CenterStart,
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier =
                    Modifier
                        .size(48.dp)
                        .ftuePulse(enabled = cameraUiFtueStep == CameraUiFtueViewModel.CameraUiFtueStep.SETTINGS)
                        .then(
                            if (tourViewModel != null) {
                                Modifier.tourTarget("camera_settings", tourViewModel)
                            } else {
                                Modifier
                            },
                        ).then(
                            if (cameraUiFtueRegistry != null) {
                                Modifier.ftueAnchor(
                                    id = CameraUiFtueViewModel.ANCHOR_SETTINGS,
                                    registry = cameraUiFtueRegistry,
                                )
                            } else {
                                Modifier
                            },
                        ),
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.cd_open_settings),
                    tint = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Right slot: Scanium logo
        TopCornerButtonContainer(
            contentAlignment = Alignment.CenterEnd,
            onClick = { showSupportSheet = true },
        ) {
            Image(
                painter = painterResource(id = R.drawable.scanium_logo),
                contentDescription = stringResource(R.string.cd_scanium_logo),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }

    if (isLandscape) {
        // Landscape layout: Shutter on right edge, other buttons at bottom corners
        CameraOverlayLandscape(
            itemsCount = itemsCount,
            lastAddedItem = lastAddedItem,
            onAnimationFinished = onAnimationFinished,
            cameraState = cameraState,
            captureResolution = captureResolution,
            onNavigateToItems = onNavigateToItems,
            tourViewModel = tourViewModel,
            cameraUiFtueRegistry = cameraUiFtueRegistry,
            cameraUiFtueStep = cameraUiFtueStep,
            showShutterHint = showShutterHint,
            onShutterTap = onShutterTap,
            onShutterLongPress = onShutterLongPress,
            onStopScanning = onStopScanning,
            onFlipCamera = onFlipCamera,
            isFlipEnabled = isFlipEnabled,
        )
    } else {
        // Portrait layout: Bottom-centered controls
        CameraOverlayPortrait(
            itemsCount = itemsCount,
            lastAddedItem = lastAddedItem,
            onAnimationFinished = onAnimationFinished,
            cameraState = cameraState,
            captureResolution = captureResolution,
            onNavigateToItems = onNavigateToItems,
            tourViewModel = tourViewModel,
            cameraUiFtueRegistry = cameraUiFtueRegistry,
            cameraUiFtueStep = cameraUiFtueStep,
            showShutterHint = showShutterHint,
            onShutterTap = onShutterTap,
            onShutterLongPress = onShutterLongPress,
            onStopScanning = onStopScanning,
            onFlipCamera = onFlipCamera,
            isFlipEnabled = isFlipEnabled,
        )
    }

    // Support Scanium bottom sheet
    if (showSupportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSupportSheet = false },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
            ) {
                // Title
                Text(
                    text = stringResource(R.string.camera_support_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Body text
                Text(
                    text = stringResource(R.string.camera_support_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Donation buttons (reuse from Settings)
                DonationContent(
                    onDonationClicked = { amount ->
                        // Optional: emit analytics event
                        // TelemetryService.trackEvent("camera_donation_clicked", mapOf("amount" to amount))
                    },
                )
            }
        }
    }
}

/**
 * Portrait layout for camera controls.
 * Bottom-centered with items, shutter, and flip camera in a row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.CameraOverlayPortrait(
    itemsCount: Int,
    lastAddedItem: ScannedItem?,
    onAnimationFinished: () -> Unit,
    cameraState: CameraState,
    captureResolution: CaptureResolution,
    onNavigateToItems: () -> Unit,
    tourViewModel: com.scanium.app.ftue.TourViewModel?,
    cameraUiFtueRegistry: CameraUiFtueAnchorRegistry?,
    cameraUiFtueStep: CameraUiFtueViewModel.CameraUiFtueStep,
    showShutterHint: Boolean,
    onShutterTap: () -> Unit,
    onShutterLongPress: () -> Unit,
    onStopScanning: () -> Unit,
    onFlipCamera: () -> Unit,
    isFlipEnabled: Boolean,
) {
    // Badge animation state for portrait layout
    var previousItemsCount by remember { mutableIntStateOf(itemsCount) }
    var badgeScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(itemsCount) {
        if (itemsCount > previousItemsCount) {
            // Trigger animation: scale from 1.0 to 1.18 back to 1.0
            badgeScale = 1.18f
            delay(100)
            badgeScale = 1f
        }
        previousItemsCount = itemsCount
    }

    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .semantics { traversalIndex = 1f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                BadgedBox(
                    badge = {
                        if (itemsCount > 0) {
                            Badge(
                                modifier =
                                    Modifier.graphicsLayer(
                                        scaleX = badgeScale,
                                        scaleY = badgeScale,
                                    ),
                            ) {
                                Text(itemsCount.toString())
                            }
                        }
                    },
                ) {
                    IconButton(
                        onClick = onNavigateToItems,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .ftuePulse(enabled = cameraUiFtueStep == CameraUiFtueViewModel.CameraUiFtueStep.ITEM_LIST)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.small,
                                ).then(
                                    if (tourViewModel != null) {
                                        Modifier.tourTarget("camera_items_button", tourViewModel)
                                    } else {
                                        Modifier
                                    },
                                ).then(
                                    if (cameraUiFtueRegistry != null) {
                                        Modifier.ftueAnchor(
                                            id = CameraUiFtueViewModel.ANCHOR_ITEMS,
                                            registry = cameraUiFtueRegistry,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Checklist,
                            contentDescription = stringResource(R.string.cd_view_scanned_items),
                            tint = Color.White,
                        )
                    }
                }

                // Item added animation overlay
                lastAddedItem?.let { item ->
                    key(item.id) {
                        Box(
                            modifier = Modifier.align(Alignment.Center),
                            contentAlignment = Alignment.Center,
                        ) {
                            ItemAddedAnimation(
                                item = item,
                                onAnimationFinished = onAnimationFinished,
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                ShutterButton(
                    cameraState = cameraState,
                    onTap = onShutterTap,
                    onLongPress = onShutterLongPress,
                    onStopScanning = onStopScanning,
                    showHint = showShutterHint,
                    modifier =
                        Modifier
                            .offset(y = 6.dp)
                            .ftuePulse(enabled = cameraUiFtueStep == CameraUiFtueViewModel.CameraUiFtueStep.SHUTTER)
                            .then(
                                if (tourViewModel != null) {
                                    Modifier.tourTarget("camera_shutter", tourViewModel)
                                } else {
                                    Modifier
                                },
                            ).then(
                                if (cameraUiFtueRegistry != null) {
                                    Modifier.ftueAnchor(
                                        id = CameraUiFtueViewModel.ANCHOR_SHUTTER,
                                        registry = cameraUiFtueRegistry,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                IconButton(
                    onClick = onFlipCamera,
                    enabled = isFlipEnabled,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .ftuePulse(enabled = cameraUiFtueStep == CameraUiFtueViewModel.CameraUiFtueStep.FLIP_CAMERA)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.small,
                            ).then(
                                if (cameraUiFtueRegistry != null) {
                                    Modifier.ftueAnchor(
                                        id = CameraUiFtueViewModel.ANCHOR_FLIP,
                                        registry = cameraUiFtueRegistry,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = stringResource(R.string.cd_flip_camera),
                        tint = Color.White,
                    )
                }
            }
        }

        // Resolution indicator
        Text(
            text = getResolutionLabel(captureResolution),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier =
                Modifier
                    .background(
                        Color.Black.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.small,
                    ).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/**
 * Landscape layout for camera controls.
 * - Shutter button: center-right of screen
 * - Items button: bottom-left corner
 * - Flip camera button: bottom-right corner
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.CameraOverlayLandscape(
    itemsCount: Int,
    lastAddedItem: ScannedItem?,
    onAnimationFinished: () -> Unit,
    cameraState: CameraState,
    captureResolution: CaptureResolution,
    onNavigateToItems: () -> Unit,
    tourViewModel: com.scanium.app.ftue.TourViewModel?,
    cameraUiFtueRegistry: CameraUiFtueAnchorRegistry?,
    cameraUiFtueStep: CameraUiFtueViewModel.CameraUiFtueStep,
    showShutterHint: Boolean,
    onShutterTap: () -> Unit,
    onShutterLongPress: () -> Unit,
    onStopScanning: () -> Unit,
    onFlipCamera: () -> Unit,
    isFlipEnabled: Boolean,
) {
    // Badge animation state for landscape layout
    var previousItemsCount by remember { mutableIntStateOf(itemsCount) }
    var badgeScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(itemsCount) {
        if (itemsCount > previousItemsCount) {
            // Trigger animation: scale from 1.0 to 1.18 back to 1.0
            badgeScale = 1.18f
            delay(100)
            badgeScale = 1f
        }
        previousItemsCount = itemsCount
    }

    // Shutter button: centered vertically on right edge
    Box(
        modifier =
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
                .semantics { traversalIndex = 1f },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShutterButton(
                cameraState = cameraState,
                onTap = onShutterTap,
                onLongPress = onShutterLongPress,
                onStopScanning = onStopScanning,
                showHint = showShutterHint,
                modifier =
                    Modifier
                        .ftuePulse(enabled = cameraUiFtueStep == CameraUiFtueViewModel.CameraUiFtueStep.SHUTTER)
                        .then(
                            if (tourViewModel != null) {
                                Modifier.tourTarget("camera_shutter", tourViewModel)
                            } else {
                                Modifier
                            },
                        ).then(
                            if (cameraUiFtueRegistry != null) {
                                Modifier.ftueAnchor(
                                    id = CameraUiFtueViewModel.ANCHOR_SHUTTER,
                                    registry = cameraUiFtueRegistry,
                                )
                            } else {
                                Modifier
                            },
                        ),
            )

            // Resolution indicator below shutter
            Text(
                text = getResolutionLabel(captureResolution),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier =
                    Modifier
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.small,
                        ).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }

    // Items button: bottom-left corner
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp)
                .semantics { traversalIndex = 2f },
        contentAlignment = Alignment.Center,
    ) {
        BadgedBox(
            badge = {
                if (itemsCount > 0) {
                    Badge(
                        modifier =
                            Modifier.graphicsLayer(
                                scaleX = badgeScale,
                                scaleY = badgeScale,
                            ),
                    ) {
                        Text(itemsCount.toString())
                    }
                }
            },
        ) {
            IconButton(
                onClick = onNavigateToItems,
                modifier =
                    Modifier
                        .size(48.dp)
                        .ftuePulse(enabled = cameraUiFtueStep == CameraUiFtueViewModel.CameraUiFtueStep.ITEM_LIST)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.small,
                        ).then(
                            if (tourViewModel != null) {
                                Modifier.tourTarget("camera_items_button", tourViewModel)
                            } else {
                                Modifier
                            },
                        ).then(
                            if (cameraUiFtueRegistry != null) {
                                Modifier.ftueAnchor(
                                    id = CameraUiFtueViewModel.ANCHOR_ITEMS,
                                    registry = cameraUiFtueRegistry,
                                )
                            } else {
                                Modifier
                            },
                        ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Checklist,
                    contentDescription = stringResource(R.string.cd_view_scanned_items),
                    tint = Color.White,
                )
            }
        }

        // Item added animation overlay
        lastAddedItem?.let { item ->
            key(item.id) {
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    ItemAddedAnimation(
                        item = item,
                        onAnimationFinished = onAnimationFinished,
                    )
                }
            }
        }
    }

    // Flip camera button: bottom-right corner
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
                .semantics { traversalIndex = 3f },
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onFlipCamera,
            enabled = isFlipEnabled,
            modifier =
                Modifier
                    .size(48.dp)
                    .ftuePulse(enabled = cameraUiFtueStep == CameraUiFtueViewModel.CameraUiFtueStep.FLIP_CAMERA)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                    ).then(
                        if (cameraUiFtueRegistry != null) {
                            Modifier.ftueAnchor(
                                id = CameraUiFtueViewModel.ANCHOR_FLIP,
                                registry = cameraUiFtueRegistry,
                            )
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = stringResource(R.string.cd_flip_camera),
                tint = Color.White,
            )
        }
    }
}

/**
 * Formats the resolution setting for display.
 */
@Composable
internal fun getResolutionLabel(resolution: CaptureResolution): String =
    when (resolution) {
        CaptureResolution.LOW -> stringResource(R.string.camera_resolution_low)
        CaptureResolution.NORMAL -> stringResource(R.string.camera_resolution_normal)
        CaptureResolution.HIGH -> stringResource(R.string.camera_resolution_high)
    }

/**
 * Shared container for top corner buttons (hamburger menu and logo).
 * Provides consistent styling: size, background, shape, and padding.
 */
@Composable
internal fun TopCornerButtonContainer(
    contentAlignment: Alignment,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.width(48.dp),
        contentAlignment = contentAlignment,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                    ).let { baseModifier ->
                        if (onClick != null) {
                            baseModifier.clickable(onClick = onClick)
                        } else {
                            baseModifier
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
