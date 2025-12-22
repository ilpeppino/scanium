package com.scanium.app.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
        // Add more metrics here as they become available via StateFlows
    }
}
