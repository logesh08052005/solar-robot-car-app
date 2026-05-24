package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TelemetryLog

@Composable
fun BatteryChart(
    logs: List<TelemetryLog>,
    modifier: Modifier = Modifier
) {
    // We reverse the logs list because the repository returns DESC (recent first)
    // For a line chart we want chronological ASC order left-to-right
    val orderedLogs = logs.sortedBy { it.timestamp }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFF0F4F9))
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "BATTERY DISCHARGE PROFILE (REAL-TIME VOLTAGE LOGS)",
            fontSize = 11.sp,
            color = Color(0xFF005FB0), // High contrast deep blue accent
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF001D33)) // Sleek contrasting dark viewport
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(top = 10.dp, bottom = 10.dp, start = 8.dp, end = 8.dp)
        ) {
            if (orderedLogs.size < 2) {
                // Empty state or insufficient statistics
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = "Collecting telemetry database samples...\n(Minimum 2 entries required)",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height

                    // Max scale configs
                    // Battery voltage maps between 10.0 V (minimum) and 13.0 V (charge peak)
                    val minVal = 10.0f
                    val maxVal = 13.0f
                    val valDelta = maxVal - minVal

                    val chartPoints = orderedLogs.mapIndexed { idx, log ->
                        val x = (idx.toFloat() / (orderedLogs.size - 1)) * width
                        // Normalize voltage to percentage of delta
                        val normalizedVal = ((log.voltage - minVal) / valDelta).coerceIn(0.0f, 1.0f)
                        // Invert Y axis for screen space
                        val y = height - (normalizedVal * height)
                        Offset(x, y)
                    }

                    // Draw reference grid horizon levels
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val gridY = (i.toFloat() / gridSteps) * height
                        val gridVoltVal = maxVal - (i.toFloat() / gridSteps) * valDelta

                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(0f, gridY),
                            end = Offset(width, gridY),
                            strokeWidth = 1f
                        )
                    }

                    // Build path coordinates string
                    val linePath = Path().apply {
                        chartPoints.forEachIndexed { idx, p ->
                            if (idx == 0) {
                                moveTo(p.x, p.y)
                            } else {
                                lineTo(p.x, p.y)
                            }
                        }
                    }

                    // Draw area gradient shading below the line
                    val fillPath = Path().apply {
                        addPath(linePath)
                        // Tie ends back to the floor
                        lineTo(chartPoints.last().x, height)
                        lineTo(chartPoints.first().x, height)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0x2210B981), Color(0x0010B981))
                        )
                    )

                    // Draw the core trend line
                    drawPath(
                        path = linePath,
                        color = Color(0xFF10B981),
                        style = Stroke(width = 3f)
                    )

                    // Highlight the last recorded point with a floating glow indicator
                    val lastP = chartPoints.last()
                    drawCircle(
                        color = Color(0x7710B981),
                        radius = 8f,
                        center = lastP
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = lastP
                    )
                }
            }
        }
    }
}
