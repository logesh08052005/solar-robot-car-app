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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Waypoint
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WaypointRadar(
    roverX: Float,
    roverY: Float,
    yaw: Float,
    waypoints: List<Waypoint>,
    activeIdx: Int,
    modifier: Modifier = Modifier
) {
    val animX by animateFloatAsState(targetValue = roverX, label = "roverX")
    val animY by animateFloatAsState(targetValue = roverY, label = "roverY")
    val animYaw by animateFloatAsState(targetValue = yaw, label = "yaw")

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFF0F4F9))
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COORDINATE WAYPOINT RADAR MAP (-5m to +5m)",
                fontSize = 11.sp,
                color = Color(0xFF001D33), // Deep Navy
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            // Dynamic location indicator tag
            Text(
                text = String.format(locale = java.util.Locale.US, "X: %.2f | Y: %.2f", animX, animY),
                fontSize = 10.sp,
                color = Color(0xFF001D33),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF001D33)) // Sleek deep navy
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                
                // Mapped scale: 1 meter = 18 dp/pixel scale factor.
                // Center represents (X=0, Y=0) of coordinate plane
                // Let's calculate pixels per meter based on radar canvas dimension
                val pixelsPerMeter = size.minDimension / 10f // 10 meters coverage total (-5m to +5m)

                // 1. Draw concentric distance range rings (1m, 22.5m, 3m, 4m, 5m)
                for (r in 1..5) {
                    drawCircle(
                        color = Color(0xFF1E293B),
                        radius = r * pixelsPerMeter,
                        center = center,
                        style = Stroke(
                            width = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    )
                }

                // 2. Draw crosshair axes
                // Horizontal axis
                drawLine(
                    color = Color(0xFF1F2937),
                    start = Offset(0f, center.y),
                    end = Offset(size.width, center.y),
                    strokeWidth = 1f
                )
                // Vertical axis
                drawLine(
                    color = Color(0xFF1F2937),
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, size.height),
                    strokeWidth = 1f
                )

                // 3. Draw waypoint route line paths
                if (waypoints.isNotEmpty()) {
                    val path = Path()
                    waypoints.forEachIndexed { i, wp ->
                        // Convert waypoint X, Y (Meter) to local screen pixel offsets
                        // Coordinate conventions: standard XY plot, +x right, +y up. Screen coordinates are +x right, +y down.
                        // So local screen coordinate is x = center.x + wp.x * pixelsPerMeter, y = center.y - wp.y * pixelsPerMeter
                        val pX = center.x + (wp.x * pixelsPerMeter)
                        val pY = center.y - (wp.y * pixelsPerMeter)

                        if (i == 0) {
                            path.moveTo(pX, pY)
                        } else {
                            path.lineTo(pX, pY)
                        }
                    }

                    // Stroke route path
                    drawPath(
                        path = path,
                        color = Color(0x66A855F7),
                        style = Stroke(
                            width = 2.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    )
                }

                // 4. Plot individual waypoint coordinate points
                waypoints.forEachIndexed { idx, wp ->
                    val pX = center.x + (wp.x * pixelsPerMeter)
                    val pY = center.y - (wp.y * pixelsPerMeter)

                    val isActive = idx == activeIdx
                    val isCompleted = wp.isCompleted

                    // Glow circle for active target
                    if (isActive) {
                        drawCircle(
                            color = Color(0x55F0ABFC), // Pulsing purple halo
                            radius = 16f,
                            center = Offset(pX, pY)
                        )
                    }

                    // Main dot
                    drawCircle(
                        color = when {
                            isActive -> Color(0xFFE879F9)    // Active purple highlight
                            isCompleted -> Color(0xFF10B981) // Arrived Green
                            else -> Color(0xFF3B82F6)        // Standby Blue
                        },
                        radius = if (isActive) 8f else 6f,
                        center = Offset(pX, pY)
                    )

                    // Order Index text or marker
                    drawCircle(
                        color = Color.White,
                        radius = 2f,
                        center = Offset(pX, pY)
                    )
                }

                // 5. Draw the Rover/Robot Node representing live position
                val rX = center.x + (animX * pixelsPerMeter)
                val rY = center.y - (animY * pixelsPerMeter)

                // Check boundary limitations
                if (rX in 0f..size.width && rY in 0f..size.height) {
                    // Outer signal radiation halo
                    drawCircle(
                        color = Color(0x33FBBF24), // Simulated radar beacon ring
                        radius = 20f,
                        center = Offset(rX, rY)
                    )

                    // Heading arrow representation
                    rotate(degrees = animYaw, pivot = Offset(rX, rY)) {
                        val arrowPath = Path().apply {
                            moveTo(rX, rY - 14f)     // Nose pointing up
                            lineTo(rX - 8f, rY + 8f)  // Back-left wing
                            lineTo(rX, rY + 3f)      // Tail notch
                            lineTo(rX + 8f, rY + 8f)  // Back-right wing
                            close()
                        }
                        // Fill rover arrow
                        drawPath(path = arrowPath, color = Color(0xFFF59E0B)) // Golden Rover Indicator

                        // Core hinge circle
                        drawCircle(color = Color.Black, radius = 2.5f, center = Offset(rX, rY))
                    }
                }
            }
        }
    }
}
