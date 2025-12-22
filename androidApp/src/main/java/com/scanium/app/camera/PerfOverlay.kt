package com.scanium.app.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanium.app.ml.classification.ClassificationMode

@Composable
fun PerfOverlay(
    analysisFps: Double,
    classificationMode: ClassificationMode,
    callsStarted: Int,
    callsCompleted: Int,
    callsFailed: Int,
    lastLatency: Long,
    queueDepth: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp))
            .padding(6.dp)
    ) {
        Text(
            text = "Analysis FPS: %.1f".format(analysisFps),
            color = Color.Green,
            fontSize = 10.sp,
            lineHeight = 12.sp
        )
        Text(
            text = "Mode: ${classificationMode.name}",
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 12.sp
        )
        
        if (classificationMode == ClassificationMode.CLOUD) {
            Text(
                text = "Cloud: $callsCompleted/$callsStarted (Fail: $callsFailed)",
                color = if (callsFailed > 0) Color(0xFFFF6B6B) else Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )
            Text(
                text = "Queue: $queueDepth",
                color = if (queueDepth > 2) Color.Yellow else Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )
            Text(
                text = "Last Latency: ${lastLatency}ms",
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )
        }
    }
}
