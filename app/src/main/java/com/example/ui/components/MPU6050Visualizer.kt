package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MPU6050Visualizer(
    yaw: Float,
    pitch: Float,
    roll: Float,
    modifier: Modifier = Modifier
) {
    // Smoothen sensor state readings
    val animatedYaw by animateFloatAsState(targetValue = yaw, label = "yaw")
    val animatedPitch by animateFloatAsState(targetValue = pitch, label = "pitch")
    val animatedRoll by animateFloatAsState(targetValue = roll, label = "roll")

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFF0F4F9)) // Sleek light gray
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "MPU6050 ATTITUDE HEADING REFERENCE SYSTEM",
            fontSize = 11.sp,
            color = Color(0xFF001D33), // Deep Navy title
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AHRS Attitude Sphere (Pitch and Roll)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .border(2.dp, Color(0xFF001D33), RoundedCornerShape(60.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2
                    val center = Offset(size.width / 2, size.height / 2)

                    // Draw Horizon Sky background
                    drawCircle(
                        color = Color(0xFF0284C7), // Sky blue
                        radius = radius,
                        center = center
                    )

                    // Draw Horizon Ground background shifted by Pitch & rotated by Roll
                    rotate(degrees = animatedRoll, pivot = center) {
                        val pitchFactor = (animatedPitch / 45f).coerceIn(-1.0f, 1.0f)
                        val shiftY = center.y + (pitchFactor * radius)

                        val path = Path().apply {
                            moveTo(0f, shiftY)
                            lineTo(size.width, shiftY)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path = path, color = Color(0xFF1E3A8A)) // Deep blue grounding

                        // White horizon line divider
                        drawLine(
                            color = Color.White,
                            start = Offset(0f, shiftY),
                            end = Offset(size.width, shiftY),
                            strokeWidth = 3f
                        )

                        // Pitch graduation ticks
                        for (p in -30..30 step 10) {
                            if (p == 0) continue
                            val tickShiftY = shiftY - (p / 45f * radius)
                            if (tickShiftY in 0f..size.height) {
                                val widthTick = if (p % 20 == 0) 36f else 20f
                                drawLine(
                                    color = Color(0xAAFFFFFF),
                                    start = Offset(center.x - widthTick, tickShiftY),
                                    end = Offset(center.x + widthTick, tickShiftY),
                                    strokeWidth = 2f
                                )
                            }
                        }
                    }

                    // Static Aircraft/Robot symbol in center (Foreground)
                    // Core ring
                    drawCircle(
                        color = Color(0xFFEF4444), // Bright warning red
                        radius = 8f,
                        center = center,
                        style = Stroke(width = 3f)
                    )
                    // Left horizontal wing
                    drawLine(
                        color = Color(0xFFEF4444),
                        start = Offset(center.x - 35f, center.y),
                        end = Offset(center.x - 12f, center.y),
                        strokeWidth = 4f
                    )
                    // Right horizontal wing
                    drawLine(
                        color = Color(0xFFEF4444),
                        start = Offset(center.x + 12f, center.y),
                        end = Offset(center.x + 35f, center.y),
                        strokeWidth = 4f
                    )
                    // Vertical dot
                    drawLine(
                        color = Color(0xFFEF4444),
                        start = Offset(center.x, center.y - 12f),
                        end = Offset(center.x, center.y - 6f),
                        strokeWidth = 4f
                    )

                    // Ring edge overlay
                    drawCircle(
                        color = Color(0xFF334155),
                        radius = radius - 1f,
                        center = center,
                        style = Stroke(width = 3f)
                    )
                }
            }

            // Compass Yaw Dial Visualizer
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .border(2.dp, Color(0xFF001D33), RoundedCornerShape(60.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2
                    val center = Offset(size.width / 2, size.height / 2)

                    // Standard backdrop
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF001D33), Color(0xFF000E1C))
                        ),
                        radius = radius,
                        center = center
                    )

                    // Rotate entire dial relative to current heading (Yaw)
                    rotate(degrees = -animatedYaw, pivot = center) {
                        for (deg in 0 until 360 step 30) {
                            val rad = Math.toRadians(deg.toDouble())
                            val innerRad = radius - 12f
                            val outerRad = radius - 4f

                            val x1 = (center.x + cos(rad) * innerRad).toFloat()
                            val y1 = (center.y + sin(rad) * innerRad).toFloat()
                            val x2 = (center.x + cos(rad) * outerRad).toFloat()
                            val y2 = (center.y + sin(rad) * outerRad).toFloat()

                            // Draw degree tick
                            drawLine(
                                color = if (deg % 90 == 0) Color(0xFF38BDF8) else Color(0xFF64748B),
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = if (deg % 90 == 0) 3f else 1.5f
                            )
                        }

                        // Cardinal Directions
                        val textInset = radius - 26f
                        val cardinals = listOf("E" to 0, "S" to 90, "W" to 180, "N" to 270) // ESP8266 coordinate axes mapped E to 0 deg
                        cardinals.forEach { (label, deg) ->
                            val r = Math.toRadians(deg.toDouble())
                            val tx = (center.x + cos(r) * textInset).toFloat()
                            val ty = (center.y + sin(r) * textInset).toFloat()

                            // Draw text letter indicator dynamically in the rotation scope
                            rotate(degrees = -(-animatedYaw + deg), pivot = Offset(tx, ty)) {
                                // Draw simple cross marks for N, S, W, E
                            }
                        }
                    }

                    // Static Pointer Needle pointing straight up (0 degrees orientation of client device)
                    val needlePath = Path().apply {
                        moveTo(center.x, center.y - radius + 15f)
                        lineTo(center.x - 6f, center.y - radius + 2f)
                        lineTo(center.x + 6f, center.y - radius + 2f)
                        close()
                    }
                    drawPath(path = needlePath, color = Color(0xFFFF007F)) // Fuchsia pointer
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dynamic readout telemetry tags
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("YAW", fontSize = 9.sp, color = Color(0xFF475569), fontFamily = FontFamily.Monospace)
                Text(
                    text = String.format(locale = java.util.Locale.US, "%.1f°", animatedYaw),
                    fontSize = 15.sp,
                    color = Color(0xFF1A1C1E),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PITCH (TILT)", fontSize = 9.sp, color = Color(0xFF475569), fontFamily = FontFamily.Monospace)
                Text(
                    text = String.format(locale = java.util.Locale.US, "%.1f°", animatedPitch),
                    fontSize = 15.sp,
                    color = if (Math.abs(animatedPitch) > 12f) Color(0xFFB45309) else Color(0xFF1A1C1E), // Warn on incline
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ROLL", fontSize = 9.sp, color = Color(0xFF475569), fontFamily = FontFamily.Monospace)
                Text(
                    text = String.format(locale = java.util.Locale.US, "%.1f°", animatedRoll),
                    fontSize = 15.sp,
                    color = if (Math.abs(animatedRoll) > 12f) Color(0xFFB45309) else Color(0xFF1A1C1E),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
