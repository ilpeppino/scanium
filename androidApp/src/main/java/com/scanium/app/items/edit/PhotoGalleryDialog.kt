package com.scanium.app.items.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanium.app.R
import com.scanium.app.items.ThumbnailCache
import com.scanium.shared.core.models.model.ImageRef

/**
 * Represents a photo reference that can be displayed in the gallery.
 * This sealed class abstracts over different photo sources (ImageRef or file path).
 */
sealed class GalleryPhotoRef {
    /**
     * Photo from an ImageRef (primary thumbnail).
     */
    data class FromImageRef(val imageRef: ImageRef) : GalleryPhotoRef()

    /**
     * Photo from a file path (additional photos).
     */
    data class FromFilePath(val path: String) : GalleryPhotoRef()
}

/**
 * Full-screen modal overlay gallery for viewing item photos.
 *
 * Features:
 * - Horizontal swipe to navigate between photos
 * - Opens at the specified initial index
 * - Shows "current / total" indicator
 * - Close via X button or back gesture
 *
 * @param photos List of photo references to display
 * @param initialIndex Index of the photo to show initially
 * @param onDismiss Callback when the dialog is dismissed
 */
@Composable
fun PhotoGalleryDialog(
    photos: List<GalleryPhotoRef>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (photos.isEmpty()) {
        onDismiss()
        return
    }

    val safeInitialIndex = initialIndex.coerceIn(0, photos.lastIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        PhotoGalleryContent(
            photos = photos,
            initialIndex = safeInitialIndex,
            onClose = onDismiss,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGalleryContent(
    photos: List<GalleryPhotoRef>,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { photos.size },
    )

    // Track whether current page is zoomed (scale > 1f)
    var isZoomed by remember { mutableStateOf(false) }

    // Remember the initial page to scroll to on first composition
    val rememberedInitialIndex = rememberSaveable { initialIndex }

    LaunchedEffect(rememberedInitialIndex) {
        if (pagerState.currentPage != rememberedInitialIndex) {
            pagerState.scrollToPage(rememberedInitialIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Main photo pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1, // Prefetch adjacent images
            userScrollEnabled = !isZoomed, // Disable swipe when zoomed, enable when at 1x
        ) { page ->
            PhotoPage(
                photoRef = photos[page],
                contentDescription = stringResource(
                    R.string.photo_gallery_image_description,
                    page + 1,
                    photos.size,
                ),
                onZoomChanged = { zoomed -> isZoomed = zoomed },
            )
        }

        // Top bar with close button and page indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Close button
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            // Page indicator
            Text(
                text = stringResource(
                    R.string.photo_gallery_page_indicator,
                    pagerState.currentPage + 1,
                    photos.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )

            // Spacer to balance the layout
            Box(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun PhotoPage(
    photoRef: GalleryPhotoRef,
    contentDescription: String,
    onZoomChanged: (Boolean) -> Unit,
) {
    val bitmap: ImageBitmap? = remember(photoRef) {
        when (photoRef) {
            is GalleryPhotoRef.FromImageRef -> {
                photoRef.imageRef.toBitmap()?.asImageBitmap()
            }
            is GalleryPhotoRef.FromFilePath -> {
                try {
                    BitmapFactory.decodeFile(photoRef.path)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            ZoomableImage(
                bitmap = bitmap,
                contentDescription = contentDescription,
                onZoomChanged = onZoomChanged,
            )
        } else {
            // Fallback for failed loads
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.items_no_image),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Zoomable image composable with pinch-to-zoom and pan support.
 *
 * Features:
 * - Pinch to zoom (1x to 5x)
 * - Pan when zoomed in
 * - Double-tap to toggle between 1x and 2x zoom
 * - Constrained panning to keep image in view
 *
 * @param onZoomChanged Callback invoked when zoom state changes (true = zoomed, false = not zoomed)
 */
@Composable
private fun ZoomableImage(
    bitmap: ImageBitmap,
    contentDescription: String,
    onZoomChanged: (Boolean) -> Unit,
    minScale: Float = 1f,
    maxScale: Float = 5f,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Reset zoom when bitmap changes (navigating to different page)
    LaunchedEffect(bitmap) {
        scale = 1f
        offset = Offset.Zero
        onZoomChanged(false)
    }

    // Notify parent when zoom state changes
    LaunchedEffect(scale) {
        onZoomChanged(scale > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Apply zoom
                    val newScale = (scale * zoom).coerceIn(minScale, maxScale)

                    // Calculate max offset based on current scale
                    val maxX = if (newScale > 1f) {
                        (containerSize.width * (newScale - 1f)) / 2f
                    } else {
                        0f
                    }
                    val maxY = if (newScale > 1f) {
                        (containerSize.height * (newScale - 1f)) / 2f
                    } else {
                        0f
                    }

                    // Apply pan with constraints
                    val newOffset = if (newScale > 1f) {
                        Offset(
                            x = (offset.x + pan.x * newScale).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y * newScale).coerceIn(-maxY, maxY),
                        )
                    } else {
                        Offset.Zero
                    }

                    scale = newScale
                    offset = newOffset
                }
            }
            .pointerInput(bitmap) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        // Toggle between 1x and 2x zoom on double-tap
                        if (scale > 1.5f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2f
                            // Center zoom on tap position
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f
                            offset = Offset(
                                x = (centerX - tapOffset.x),
                                y = (centerY - tapOffset.y),
                            )
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * Extension function to convert ImageRef to Bitmap.
 * Handles both Bytes and CacheKey variants.
 */
private fun ImageRef.toBitmap(): Bitmap? {
    return when (this) {
        is ImageRef.Bytes -> {
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }
        is ImageRef.CacheKey -> {
            // Try to get from thumbnail cache
            ThumbnailCache.get(key)?.let { cached ->
                try {
                    BitmapFactory.decodeByteArray(cached.bytes, 0, cached.bytes.size)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
