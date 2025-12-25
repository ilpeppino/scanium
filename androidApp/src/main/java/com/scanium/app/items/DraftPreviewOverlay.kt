package com.scanium.app.items

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.scanium.app.model.toImageBitmap
import kotlin.math.roundToInt

/**
 * A full-screen overlay that displays a high-resolution preview of a scanned item.
 * It animates from the item's original position in the list to a centered full-screen view.
 *
 * @param item The item to preview.
 * @param sourceBounds The bounding box of the item in the list (window coordinates).
 * @param isVisible Whether the preview should be shown.
 */
@Composable
fun DraftPreviewOverlay(
    item: ScannedItem?,
    sourceBounds: Rect?,
    isVisible: Boolean
) {
    val density = LocalDensity.current

    // Animation state
    // We animate the "progress" of the expansion (0f = collapsed/at source, 1f = expanded)
    val expansionProgress by animateFloatAsState(
        targetValue = if (isVisible && item != null) 1f else 0f,
        animationSpec = tween(
            durationMillis = 250,
            easing = FastOutSlowInEasing
        ),
        label = "expansionProgress"
    )

    if (expansionProgress > 0f && item != null && sourceBounds != null) {
        // Use the same image source as the list thumbnail for consistency
        // This ensures list and preview show the same exact image, just at different sizes
        val displayImage = remember(item) { (item.thumbnailRef ?: item.thumbnail).toImageBitmap() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f * expansionProgress)) // Dim background
        ) {
            // Calculate interpolated bounds
            val targetWidth = with(density) { (LocalContext.current.resources.displayMetrics.widthPixels).toFloat() - 32.dp.toPx() }
            // Aspect ratio estimation (assume square or 4:3 if unknown, or use image ratio)
            val imageRatio = if (displayImage != null) {
                displayImage.width.toFloat() / displayImage.height.toFloat()
            } else 1f
            
            // Limit height to 60% of screen
            val screenHeight = with(density) { LocalContext.current.resources.displayMetrics.heightPixels.toFloat() }
            val targetHeight = (targetWidth / imageRatio).coerceAtMost(screenHeight * 0.6f)
            
            val targetLeft = (with(density) { LocalContext.current.resources.displayMetrics.widthPixels.toFloat() } - targetWidth) / 2f
            val targetTop = (screenHeight - targetHeight) / 2f

            val currentLeft = lerp(sourceBounds.left, targetLeft, expansionProgress)
            val currentTop = lerp(sourceBounds.top, targetTop, expansionProgress)
            val currentWidth = lerp(sourceBounds.width, targetWidth, expansionProgress)
            val currentHeight = lerp(sourceBounds.height, targetHeight, expansionProgress)

            // Card container
            Surface(
                modifier = Modifier
                    .offset { IntOffset(currentLeft.roundToInt(), currentTop.roundToInt()) }
                    .size(
                        width = with(density) { currentWidth.toDp() },
                        height = with(density) { currentHeight.toDp() } // This controls the surface size
                    ),
                shape = RoundedCornerShape(lerp(8f, 16f, expansionProgress).dp),
                shadowElevation = lerp(2f, 12f, expansionProgress).dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                // We use a Box to layer image and text.
                // The text fades in as we expand.
                Box(modifier = Modifier.fillMaxSize()) {
                    if (displayImage != null) {
                        Image(
                            bitmap = displayImage,
                            contentDescription = "Full size preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    
                    // Info overlay (gradient + text)
                    if (expansionProgress > 0.5f) {
                         Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(16.dp)
                                .alpha((expansionProgress - 0.5f) * 2f)
                        ) {
                            Column {
                                Text(
                                    text = item.displayLabel,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.formattedPriceRange,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                // Additional details
                                Text(
                                    text = "Confidence: ${item.formattedConfidence}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return (1 - fraction) * start + fraction * stop
}
