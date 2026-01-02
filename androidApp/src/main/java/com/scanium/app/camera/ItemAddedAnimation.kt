package com.scanium.app.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.scanium.app.R
import com.scanium.app.items.ScannedItem
import com.scanium.app.model.toImageBitmap
import kotlinx.coroutines.launch

@Composable
fun ItemAddedAnimation(
    item: ScannedItem,
    onAnimationFinished: () -> Unit,
) {
    val scale = remember { Animatable(0.4f) }
    val alpha = remember { Animatable(0f) }
    val translationY = remember { Animatable(0f) }

    LaunchedEffect(item.id) {
        launch {
            // Pop in
            alpha.snapTo(1f)
            scale.animateTo(
                targetValue = 1.2f,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            )
            // Settle
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing),
            )
        }

        launch {
            // Float up and fade out
            translationY.animateTo(
                targetValue = -60f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            )
        }

        launch {
            // Delay fade out slightly
            kotlinx.coroutines.delay(300)
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300, easing = LinearEasing),
            )
            onAnimationFinished()
        }
    }

    val thumbnailBitmap =
        remember(item.id) {
            (item.thumbnailRef ?: item.thumbnail)?.toImageBitmap()
        }

    if (thumbnailBitmap != null) {
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(0, translationY.value.toInt()) }
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .background(Color.White.copy(alpha = 0.2f)),
            // Border effect
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = thumbnailBitmap,
                contentDescription = stringResource(R.string.cd_item_thumbnail),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
