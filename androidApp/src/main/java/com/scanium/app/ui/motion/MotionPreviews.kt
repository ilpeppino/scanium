package com.scanium.app.ui.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanium.app.ui.theme.DeepNavy
import com.scanium.app.ui.theme.LightningYellow
import com.scanium.app.ui.theme.ScaniumBluePrimary
import kotlinx.coroutines.delay

/**
 * Compose preview showcase for Scanium motion language components.
 *
 * These previews demonstrate:
 * - ScanFrameAppear: Quick fade-in (<=100ms)
 * - LightningScanPulse: Single yellow pulse (200-300ms)
 * - PriceCountUp: Animated price display (1.0-1.5s)
 */

// ==================== ScanFrameAppear Previews ====================

@Preview(
    name = "ScanFrameAppear - Visible",
    showBackground = true,
    backgroundColor = 0xFF050B18
)
@Composable
private fun ScanFrameAppearVisiblePreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        ScanFrameAppear(
            rect = Rect(0.15f, 0.25f, 0.85f, 0.75f),
            isVisible = true,
            frameColor = ScaniumBluePrimary
        )
    }
}

@Preview(
    name = "ScanFrameAppear - Small Frame",
    showBackground = true,
    backgroundColor = 0xFF050B18
)
@Composable
private fun ScanFrameAppearSmallPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        ScanFrameAppear(
            rect = Rect(0.3f, 0.35f, 0.7f, 0.65f),
            isVisible = true,
            frameColor = ScaniumBluePrimary
        )
    }
}

// ==================== LightningScanPulse Previews ====================

@Preview(
    name = "LightningScanPulse - Vertical",
    showBackground = true,
    backgroundColor = 0xFF050B18
)
@Composable
private fun LightningScanPulseVerticalPreview() {
    var triggerKey by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Retrigger animation periodically for preview
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            triggerKey = System.currentTimeMillis()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        LightningScanPulse(
            rect = Rect(0.1f, 0.2f, 0.9f, 0.8f),
            triggerKey = triggerKey,
            direction = PulseDirection.VERTICAL_DOWN,
            pulseColor = LightningYellow
        )
    }
}

@Preview(
    name = "LightningScanPulse - Horizontal",
    showBackground = true,
    backgroundColor = 0xFF050B18
)
@Composable
private fun LightningScanPulseHorizontalPreview() {
    var triggerKey by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            triggerKey = System.currentTimeMillis()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        LightningScanPulse(
            rect = Rect(0.1f, 0.2f, 0.9f, 0.8f),
            triggerKey = triggerKey,
            direction = PulseDirection.HORIZONTAL_RIGHT,
            pulseColor = LightningYellow
        )
    }
}

@Preview(
    name = "LightningScanPulse - Diagonal",
    showBackground = true,
    backgroundColor = 0xFF050B18
)
@Composable
private fun LightningScanPulseDiagonalPreview() {
    var triggerKey by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            triggerKey = System.currentTimeMillis()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        LightningScanPulse(
            rect = Rect(0.1f, 0.2f, 0.9f, 0.8f),
            triggerKey = triggerKey,
            direction = PulseDirection.DIAGONAL_DOWN_RIGHT,
            pulseColor = LightningYellow
        )
    }
}

// ==================== PriceCountUp Previews ====================

@Preview(
    name = "PriceCountUp - Single Value",
    showBackground = true,
    backgroundColor = 0xFF050B18
)
@Composable
private fun PriceCountUpSinglePreview() {
    var key by remember { mutableStateOf("initial") }

    // Restart animation periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            key = System.currentTimeMillis().toString()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Price Count-Up Demo",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            PriceCountUp(
                targetValue = 25,
                stableKey = key,
                currencySymbol = "€",
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                textColor = Color.White
            )
        }
    }
}

@Preview(
    name = "PriceRangeCountUp - Range",
    showBackground = true,
    backgroundColor = 0xFF050B18
)
@Composable
private fun PriceRangeCountUpPreview() {
    var key by remember { mutableStateOf("initial") }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            key = System.currentTimeMillis().toString()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Price Range Count-Up Demo",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            PriceRangeCountUp(
                lowValue = 10,
                highValue = 45,
                stableKey = key,
                currencySymbol = "€",
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                textColor = Color.White
            )
        }
    }
}

@Preview(
    name = "AnimatedPriceText - Parsed",
    showBackground = true,
    backgroundColor = 0xFF050B18
)
@Composable
private fun AnimatedPriceTextPreview() {
    var key by remember { mutableStateOf("initial") }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            key = System.currentTimeMillis().toString()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Parsed Price Range Demo",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            AnimatedPriceText(
                priceText = "€15–€35",
                stableKey = key,
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                textColor = Color.White
            )
        }
    }
}

// ==================== Combined Motion Showcase ====================

@Preview(
    name = "Motion Showcase - All Components",
    showBackground = true,
    backgroundColor = 0xFF050B18,
    widthDp = 360,
    heightDp = 640
)
@Composable
private fun MotionShowcasePreview() {
    var frameVisible by remember { mutableStateOf(true) }
    var pulseTrigger by remember { mutableLongStateOf(0L) }
    var priceKey by remember { mutableStateOf("initial") }

    // Sequence the animations
    LaunchedEffect(Unit) {
        while (true) {
            // Step 1: Frame appears
            frameVisible = true
            delay(500)

            // Step 2: Pulse triggers
            pulseTrigger = System.currentTimeMillis()
            delay(500)

            // Step 3: Price animates
            priceKey = System.currentTimeMillis().toString()
            delay(2000)

            // Reset
            frameVisible = false
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
    ) {
        // Scan frame
        ScanFrameAppear(
            rect = Rect(0.1f, 0.15f, 0.9f, 0.65f),
            isVisible = frameVisible,
            frameColor = ScaniumBluePrimary
        )

        // Lightning pulse
        if (pulseTrigger > 0) {
            LightningScanPulse(
                rect = Rect(0.1f, 0.15f, 0.9f, 0.65f),
                triggerKey = pulseTrigger,
                direction = PulseDirection.VERTICAL_DOWN,
                pulseColor = LightningYellow
            )
        }

        // Price display at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Vintage Jacket",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            PriceRangeCountUp(
                lowValue = 25,
                highValue = 45,
                stableKey = priceKey,
                currencySymbol = "€",
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                textColor = LightningYellow
            )
        }

        // Title
        Text(
            text = "MOTION SHOWCASE",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )
    }
}
