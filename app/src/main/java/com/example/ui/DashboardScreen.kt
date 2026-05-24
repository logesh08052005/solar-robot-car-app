package com.example.ui

import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TelemetryLog
import com.example.data.Waypoint
import com.example.ui.components.BatteryChart
import com.example.ui.components.MPU6050Visualizer
import com.example.ui.components.WaypointRadar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    // Collect ViewModel statuses reactively
    val ipAddress by viewModel.ipAddress.collectAsStateWithLifecycle()
    val port by viewModel.port.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val isSimulationMode by viewModel.isSimulationMode.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // Sensor state readouts
    val voltage by viewModel.voltage.collectAsStateWithLifecycle()
    val currentStr by viewModel.currentStr.collectAsStateWithLifecycle()
    val soc by viewModel.soc.collectAsStateWithLifecycle()
    val temperature by viewModel.temperature.collectAsStateWithLifecycle()
    val yaw by viewModel.yaw.collectAsStateWithLifecycle()
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val roll by viewModel.roll.collectAsStateWithLifecycle()
    val tiltCorrectionStatus by viewModel.tiltCorrectionStatus.collectAsStateWithLifecycle()
    val headingStability by viewModel.headingStability.collectAsStateWithLifecycle()
    val currentCommand by viewModel.currentCommand.collectAsStateWithLifecycle()
    val isAutoMode by viewModel.isAutoMode.collectAsStateWithLifecycle()

    // Simulated coordinates
    val roverX by viewModel.roverX.collectAsStateWithLifecycle()
    val roverY by viewModel.roverY.collectAsStateWithLifecycle()
    val activeWaypointIndex by viewModel.activeWaypointIndex.collectAsStateWithLifecycle()

    // Database flow streams
    val waypoints by viewModel.waypointsList.collectAsStateWithLifecycle()
    val telemetryLogs by viewModel.telemetryLogsList.collectAsStateWithLifecycle()
    val recentTelemetryLogs by viewModel.recentTelemetryForChart.collectAsStateWithLifecycle()

    // UI Expansion and dialog controllers
    var isSettingsExpanded by remember { mutableStateOf(false) }
    var isWaypointsExpanded by remember { mutableStateOf(true) }
    var isLogsExpanded by remember { mutableStateOf(false) }
    var showAddWaypointForm by remember { mutableStateOf(false) }

    // Floating notifications
    LaunchedEffect(statusMessage, errorMessage) {
        if (statusMessage != null || errorMessage != null) {
            delay(4000)
            viewModel.clearStatusMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF0F4F9),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F4F9))
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Top Dashboard Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                DashboardHeader(
                    isConnected = isConnected,
                    currentCommand = currentCommand,
                    isSimulationMode = isSimulationMode,
                    activeWaypointIdx = activeWaypointIndex,
                    isAutoMode = isAutoMode
                )
            }

            // Section 2: Inline Status/Error Notification Messages
            item {
                AnimatedVisibility(
                    visible = statusMessage != null || errorMessage != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    NotificationBanner(
                        statusMessage = statusMessage,
                        errorMessage = errorMessage,
                        onDismiss = { viewModel.clearStatusMessages() }
                    )
                }
            }

            // Section 3: Connection Configurations Toggle Block
            item {
                ConnectionSettingsCard(
                    ipAddress = ipAddress,
                    port = port,
                    isConnected = isConnected,
                    isConnecting = isConnecting,
                    isSimulationMode = isSimulationMode,
                    isExpanded = isSettingsExpanded,
                    onToggleExpand = { isSettingsExpanded = !isSettingsExpanded },
                    onUpdateSettings = { ip, p -> viewModel.updateConnectionSettings(ip, p) },
                    onConnect = { viewModel.connectToHardware() },
                    onDisconnect = { viewModel.disconnectHardware() },
                    onToggleSimulation = { viewModel.toggleSimulationMode(it) }
                )
            }

            // Section 4: Live Core Sensors HUD Readings
            item {
                LiveSensorsHUD(
                    voltage = voltage,
                    currentStr = currentStr,
                    soc = soc,
                    temperature = temperature
                )
            }

            // Section 5: Robotic Drive Joypad Center
            item {
                RoboticControlCenter(
                    currentCommand = currentCommand,
                    isAutoMode = isAutoMode,
                    isSimulationMode = isSimulationMode,
                    headingStability = headingStability,
                    tiltCorrectionStatus = tiltCorrectionStatus,
                    onCommand = { viewModel.executeManualMove(it) },
                    onEmergencyStop = { viewModel.executeEmergencyStop() },
                    onToggleAuto = { viewModel.toggleAutoWaypointMode() },
                    onInclineSimToggle = { viewModel.triggerInclineSimulation(it) },
                    onSnapshotLog = { viewModel.triggerLogSnapshot() }
                )
            }

            // Section 6: Twin Gyroscopic Instruments
            item {
                TwinInstrumentsBlock(
                    yaw = yaw,
                    pitch = pitch,
                    roll = roll,
                    roverX = roverX,
                    roverY = roverY,
                    waypoints = waypoints,
                    activeIdx = activeWaypointIndex
                )
            }

            // Section 7: Waypoint Sequencer Configuration Table
            item {
                WaypointsSequencerBlock(
                    waypoints = waypoints,
                    activeIdx = activeWaypointIndex,
                    isExpanded = isWaypointsExpanded,
                    showAddForm = showAddWaypointForm,
                    onToggleExpand = { isWaypointsExpanded = !isWaypointsExpanded },
                    onToggleAddForm = { showAddWaypointForm = !showAddWaypointForm },
                    onAddWaypoint = { name, x, y, head, type, dur, speed ->
                        viewModel.addWaypointItem(name, x, y, head, type, dur, speed)
                        showAddWaypointForm = false
                    },
                    onDeleteWaypoint = { viewModel.removeWaypointItem(it) },
                    onClearAll = { viewModel.clearAllWaypoints() }
                )
            }

            // Section 8: Offline Telemetry Logs Panel
            item {
                OfflineTelemetryLogsCard(
                    logs = telemetryLogs,
                    recentLogsForChart = recentTelemetryLogs,
                    isExpanded = isLogsExpanded,
                    onToggleExpand = { isLogsExpanded = !isLogsExpanded },
                    onDeleteLog = { viewModel.deleteLog(it) },
                    onClearAll = { viewModel.clearAllOfflineLogs() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// Subcomponents definitions

@Composable
fun DashboardHeader(
    isConnected: Boolean,
    currentCommand: String,
    isSimulationMode: Boolean,
    activeWaypointIdx: Int,
    isAutoMode: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F9)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Rover Terminal",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF001D33)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Text(
                        text = if (isConnected) {
                            if (isSimulationMode) "ESP8266 Connected (SIM)" else "ESP8266 Connected"
                        } else "ESP8266 Disconnected",
                        fontSize = 12.sp,
                        color = if (isConnected) Color(0xFF047857) else Color(0xFFB91C1C),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Right Actions & Signal strength
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Signal",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Text(
                        text = if (isConnected) "-64dBm" else "Offline",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF001D33),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Modern settings circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD1E4FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF001D33),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationBanner(
    statusMessage: String?,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (errorMessage != null) Color(0xFFFFDAD6) else Color(0xFFE8F5E9)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (errorMessage != null) Color(0xFFFFB4AB) else Color(0xFFC8E6C9)
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (errorMessage != null) Icons.Default.Warning else Icons.Default.Info,
                contentDescription = null,
                tint = if (errorMessage != null) Color(0xFF410002) else Color(0xFF1B5E20)
            )
            Text(
                text = errorMessage ?: statusMessage ?: "",
                color = if (errorMessage != null) Color(0xFF410002) else Color(0xFF1B5E20),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = if (errorMessage != null) Color(0xFF410002) else Color(0xFF1B5E20),
                modifier = Modifier.size(16.dp).clickable { onDismiss() }
            )
        }
    }
}

@Composable
fun ConnectionSettingsCard(
    ipAddress: String,
    port: Int,
    isConnected: Boolean,
    isConnecting: Boolean,
    isSimulationMode: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onUpdateSettings: (String, Int) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleSimulation: (Boolean) -> Unit
) {
    var inputIp by remember(ipAddress) { mutableStateOf(ipAddress) }
    var inputPort by remember(port) { mutableStateOf(port.toString()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F9)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isSimulationMode) Icons.Default.Computer else Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFF005FB0)
                    )
                    Column {
                        Text(
                            text = "COMMUNICATION LINK CONFIG",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF001D33)
                        )
                        Text(
                            text = if (isSimulationMode) "Simulating virtual robot on-device" else "Target: $ipAddress:$port",
                            fontSize = 10.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF001D33)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Divider(color = Color(0xFFE2E8F0))

                    // Mode toggler
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Simulate ESP8266 Interface",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF001D33)
                            )
                            Text(
                                "Enables offline high-g telemetry testing",
                                fontSize = 10.sp,
                                color = Color(0xFF475569)
                            )
                        }
                        Switch(
                            checked = isSimulationMode,
                            onCheckedChange = onToggleSimulation,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF005FB0),
                                checkedTrackColor = Color(0xFFD1E4FF)
                            )
                        )
                    }

                    // Input fields IP/Port
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputIp,
                            onValueChange = { inputIp = it },
                            label = { Text("IP Address", fontSize = 11.sp, color = Color(0xFF001D33)) },
                            enabled = !isSimulationMode,
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1A1C1E),
                                unfocusedTextColor = Color(0xFF1A1C1E),
                                focusedBorderColor = Color(0xFF005FB0),
                                unfocusedBorderColor = Color(0xFFCCD6E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            modifier = Modifier.weight(2f)
                        )

                        OutlinedTextField(
                            value = inputPort,
                            onValueChange = { inputPort = it },
                            label = { Text("Port", fontSize = 11.sp, color = Color(0xFF001D33)) },
                            enabled = !isSimulationMode,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1A1C1E),
                                unfocusedTextColor = Color(0xFF1A1C1E),
                                focusedBorderColor = Color(0xFF005FB0),
                                unfocusedBorderColor = Color(0xFFCCD6E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Connect/Disconnect buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isSimulationMode) {
                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    onUpdateSettings(inputIp, inputPort.toIntOrNull() ?: 80)
                                    if (isConnected) onDisconnect() else onConnect()
                                },
                                enabled = !isConnecting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isConnected) Color(0xFFEF4444) else Color(0xFF005FB0)
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isConnected) Icons.Default.WifiOff else Icons.Default.Wifi,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isConnecting) "LINKING..." else if (isConnected) "DISCONNECT" else "CONNECT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        } else {
                            // Info badge for simulator
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFD1E4FF), RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🚀 DIRECT WORKSPACE EMULATOR ONLINE",
                                    fontSize = 10.sp,
                                    color = Color(0xFF001D33),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveSensorsHUD(
    voltage: Float,
    currentStr: Float,
    soc: Int,
    temperature: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Battery Telemetry with SOC progress bar
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F9)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .weight(1f)
                .height(115.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = Color(0xFF005FB0),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "TELEMETRY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF005FB0)
                    )
                }

                Column {
                    Text(
                        text = String.format(locale = java.util.Locale.US, "%.1fV", voltage),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1C1E)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Progress bar representing state of charge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (soc / 100f).coerceIn(0f, 1f))
                                .background(Color(0xFF10B981))
                        )
                    }
                }
            }
        }

        // Card 2: Engine Motor Load and Temp HUD
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F9)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .weight(1f)
                .height(115.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color(0xFF005FB0),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "LOAD/TEMP",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF005FB0)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "LOAD",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                        Text(
                            text = String.format(locale = java.util.Locale.US, "%.1fA", currentStr),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1C1E)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "CHIP",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                        Text(
                            text = String.format(locale = java.util.Locale.US, "%.1f°C", temperature),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1C1E)
                        )
                    }
                }
            }
        }
    }
}

// Keep a stub for compatibility
@Composable
fun HUDCard(
    title: String,
    value: String,
    subtext: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
}

@Composable
fun RoboticControlCenter(
    currentCommand: String,
    isAutoMode: Boolean,
    isSimulationMode: Boolean,
    headingStability: String,
    tiltCorrectionStatus: String,
    onCommand: (String) -> Unit,
    onEmergencyStop: () -> Unit,
    onToggleAuto: () -> Unit,
    onInclineSimToggle: (Boolean) -> Unit,
    onSnapshotLog: () -> Unit
) {
    var tiltTestChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // NAVY AUTOPILOT MODE BANNER
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF001D33)),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Navigation Mode",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF93C5FD), // sky-300
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (isAutoMode) "Auto-Waypoint Mode" else "Manual Steer Active",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isAutoMode) "WP DRIVER RUNNING" else "STEER READY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF93C5FD)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current command
                    Column(
                        modifier = Modifier
                            .weight(1.5f)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "COMMAND FEEDBACK",
                            fontSize = 9.sp,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (currentCommand.isEmpty()) "IDLE STANDBY" else currentCommand,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Thrust or speed mapping
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "SPEED THRUST",
                            fontSize = 9.sp,
                            color = Color.LightGray,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isAutoMode) "0.85 m/s" else "Manual",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8) // sky-400
                        )
                    }
                }
            }
        }

        // PHYSICAL DIRECT STEER CONTROLLER JOYPAD
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F9)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "CRITICAL COCKPIT CONTROLS",
                    color = Color(0xFF001D33),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Start)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0E8F3).copy(alpha = 0.4f))
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Forward
                        ControlButton(
                            icon = Icons.Default.KeyboardArrowUp,
                            label = "FORWARD",
                            isActive = currentCommand == "FORWARD",
                            enabled = !isAutoMode,
                            onClick = { onCommand("FORWARD") }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(22.dp)
                        ) {
                            // Left
                            ControlButton(
                                icon = Icons.Default.KeyboardArrowLeft,
                                label = "LEFT",
                                isActive = currentCommand == "LEFT",
                                enabled = !isAutoMode,
                                onClick = { onCommand("LEFT") }
                            )

                            // STOP CRITICAL Center button
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFDAD6))
                                    .border(4.dp, Color(0xFFFFB4AB), CircleShape)
                                    .clickable { onEmergencyStop() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "STOP\nCRITICAL",
                                    color = Color(0xFF410002),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 13.sp
                                )
                            }

                            // Right
                            ControlButton(
                                icon = Icons.Default.KeyboardArrowRight,
                                label = "RIGHT",
                                isActive = currentCommand == "RIGHT",
                                enabled = !isAutoMode,
                                onClick = { onCommand("RIGHT") }
                            )
                        }

                        // Reverse
                        ControlButton(
                            icon = Icons.Default.KeyboardArrowDown,
                            label = "REVERSE",
                            isActive = currentCommand == "REVERSE",
                            enabled = !isAutoMode,
                            onClick = { onCommand("REVERSE") }
                        )
                    }
                }

                // Autopilot footer buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onToggleAuto,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAutoMode) Color(0xFFEF4444) else Color(0xFF005FB0)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isAutoMode) Icons.Default.CancelPresentation else Icons.Default.Navigation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isAutoMode) "STOP VEHICLE AUTO" else "AUTO WAYPOINT MODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSnapshotLog,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F766E)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0F766E)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("SAVE TELEMETRY", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // MPU6050 feedback footer
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F9)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "MPU6050 IMU HEADING ALIGNMENT RECONSTRUCTION",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF001D33),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF005FB0),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = headingStability,
                            fontSize = 11.sp,
                            color = Color(0xFF1A1C1E),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (tiltTestChecked) Icons.Default.TrendingUp else Icons.Default.TrendingFlat,
                            contentDescription = null,
                            tint = if (tiltTestChecked) Color(0xFFEF4444) else Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = tiltCorrectionStatus,
                            fontSize = 11.sp,
                            color = Color(0xFF1A1C1E),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (isSimulationMode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Divider(color = Color(0xFFE2E8F0))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Simulate Elevator Incline (Ramp)",
                                fontSize = 11.sp,
                                color = Color(0xFF1A1C1E)
                            )
                            Checkbox(
                                checked = tiltTestChecked,
                                onCheckedChange = {
                                    tiltTestChecked = it
                                    onInclineSimToggle(it)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF005FB0),
                                    uncheckedColor = Color(0xFFCCD6E0)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) Color(0xFFD1E4FF) else Color.White)
            .border(
                width = 1.dp,
                color = if (isActive) Color(0xFF005FB0) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color(0xFF001D33) else if (enabled) Color(0xFF001D33) else Color(0xFF94A3B8),
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun TwinInstrumentsBlock(
    yaw: Float,
    pitch: Float,
    roll: Float,
    roverX: Float,
    roverY: Float,
    waypoints: List<Waypoint>,
    activeIdx: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Gyro Scope Attitude Model
        MPU6050Visualizer(
            yaw = yaw,
            pitch = pitch,
            roll = roll,
            modifier = Modifier.fillMaxWidth()
        )

        // Coordinates Radar plotting map
        WaypointRadar(
            roverX = roverX,
            roverY = roverY,
            yaw = yaw,
            waypoints = waypoints,
            activeIdx = activeIdx,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun WaypointsSequencerBlock(
    waypoints: List<Waypoint>,
    activeIdx: Int,
    isExpanded: Boolean,
    showAddForm: Boolean,
    onToggleExpand: () -> Unit,
    onToggleAddForm: () -> Unit,
    onAddWaypoint: (String, Float, Float, Float, String, Long, Float) -> Unit,
    onDeleteWaypoint: (Waypoint) -> Unit,
    onClearAll: () -> Unit
) {
    // Add Waypoint Inputs
    var name by remember { mutableStateOf("") }
    var xStr by remember { mutableStateOf("") }
    var yStr by remember { mutableStateOf("") }
    var headingStr by remember { mutableStateOf("") }
    var turnType by remember { mutableStateOf("MPU_YAW") }
    var durationStr by remember { mutableStateOf("1500") }
    var speedStr by remember { mutableStateOf("0.5") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F9)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = Color(0xFF005FB0))
                    Column {
                        Text(
                            text = "WAYPOINTS DIRECTORY QUEUE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF001D33)
                        )
                        Text(
                            text = "${waypoints.size} coordinates set under routing",
                            fontSize = 10.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF001D33)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Insert option trigger
                        OutlinedButton(
                            onClick = onToggleAddForm,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF005FB0)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF005FB0))
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    if (showAddForm) "CANCEL" else "ADD COORDINATE WP",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (waypoints.isNotEmpty()) {
                            TextButton(onClick = onClearAll) {
                                Text("Purge Queue", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Waypoint submission form
                    AnimatedVisibility(visible = showAddForm) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "CONFIGURE TARGET COORDINATES",
                                fontSize = 10.sp,
                                color = Color(0xFF005FB0),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Waypoint Name", color = Color(0xFF001D33)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF1A1C1E),
                                    unfocusedTextColor = Color(0xFF1A1C1E),
                                    focusedBorderColor = Color(0xFF005FB0),
                                    unfocusedBorderColor = Color(0xFFCCD6E0),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = xStr,
                                    onValueChange = { xStr = it },
                                    label = { Text("X-Coord (m)", color = Color(0xFF001D33)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF1A1C1E), unfocusedTextColor = Color(0xFF1A1C1E),
                                        focusedBorderColor = Color(0xFF005FB0), unfocusedBorderColor = Color(0xFFCCD6E0),
                                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = yStr,
                                    onValueChange = { yStr = it },
                                    label = { Text("Y-Coord (m)", color = Color(0xFF001D33)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF1A1C1E), unfocusedTextColor = Color(0xFF1A1C1E),
                                        focusedBorderColor = Color(0xFF005FB0), unfocusedBorderColor = Color(0xFFCCD6E0),
                                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = headingStr,
                                    onValueChange = { headingStr = it },
                                    label = { Text("Yaw Head (°)", color = Color(0xFF001D33)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF1A1C1E), unfocusedTextColor = Color(0xFF1A1C1E),
                                        focusedBorderColor = Color(0xFF005FB0), unfocusedBorderColor = Color(0xFFCCD6E0),
                                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = speedStr,
                                    onValueChange = { speedStr = it },
                                    label = { Text("Speed (0.1 - 1.0)", color = Color(0xFF001D33)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF1A1C1E), unfocusedTextColor = Color(0xFF1A1C1E),
                                        focusedBorderColor = Color(0xFF005FB0), unfocusedBorderColor = Color(0xFFCCD6E0),
                                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Turn mechanism selector
                            Column {
                                Text("TURN MECHANISM TYPE:", fontSize = 9.sp, color = Color(0xFF475569), fontFamily = FontFamily.Monospace)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { turnType = "MPU_YAW" }
                                    ) {
                                        RadioButton(
                                            selected = turnType == "MPU_YAW",
                                            onClick = { turnType = "MPU_YAW" },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF005FB0))
                                        )
                                        Text("MPU6050 Yaw Turn", fontSize = 11.sp, color = Color(0xFF1A1C1E))
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { turnType = "TIMED" }
                                    ) {
                                        RadioButton(
                                            selected = turnType == "TIMED",
                                            onClick = { turnType = "TIMED" },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF005FB0))
                                        )
                                        Text("Timed Motor Turn", fontSize = 11.sp, color = Color(0xFF1A1C1E))
                                    }
                                }
                            }

                            if (turnType == "TIMED") {
                                OutlinedTextField(
                                    value = durationStr,
                                    onValueChange = { durationStr = it },
                                    label = { Text("Turn Duration (ms)", color = Color(0xFF001D33)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF1A1C1E), unfocusedTextColor = Color(0xFF1A1C1E),
                                        focusedBorderColor = Color(0xFF005FB0), unfocusedBorderColor = Color(0xFFCCD6E0),
                                        focusedContainerColor = Color.White, unfocusedContainerColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Confirm submit
                            Button(
                                onClick = {
                                    val x = xStr.toFloatOrNull() ?: 0.0f
                                    val y = yStr.toFloatOrNull() ?: 0.0f
                                    val head = headingStr.toFloatOrNull() ?: 0.0f
                                    val speed = speedStr.toFloatOrNull() ?: 0.5f
                                    val duration = durationStr.toLongOrNull() ?: 1500L

                                    onAddWaypoint(name, x, y, head, turnType, duration, speed)

                                    name = ""
                                    xStr = ""
                                    yStr = ""
                                    headingStr = ""
                                    turnType = "MPU_YAW"
                                    durationStr = "1500"
                                    speedStr = "0.5"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005FB0)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("APPEND TO WAYPOINT SEQUENCE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Waypoint items list table
                    if (waypoints.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Empty sequence queue. Seed standard routes or add coordinate nodes to begin.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            waypoints.forEachIndexed { i, wp ->
                                val isActive = i == activeIdx
                                val cardBgColor = when {
                                    isActive -> Color(0xFFD1E4FF) // Glowing active light blue
                                    wp.isCompleted -> Color(0xFFE8F5E9) // Cool light green completed
                                    else -> Color.White // Standard clear white card
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(cardBgColor)
                                        .border(
                                            1.dp,
                                            if (isActive) Color(0xFF005FB0) else Color(0xFFE2E8F0),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${wp.orderIndex + 1}. ${wp.name}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1A1C1E)
                                            )
                                            if (isActive) {
                                                Text(
                                                    "NAVIGATING",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .background(Color(0xFF005FB0), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            } else if (wp.isCompleted) {
                                                Text(
                                                    "ARRIVED",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .background(Color(0xFF10B981), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                "Target: [x:${wp.x}m, y:${wp.y}m]",
                                                fontSize = 10.sp,
                                                color = Color(0xFF005FB0),
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                "Yaw: ${wp.targetHeading}°",
                                                fontSize = 10.sp,
                                                color = Color(0xFF475569),
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                "Thrust: ${(wp.targetSpeed * 100).toInt()}%",
                                                fontSize = 10.sp,
                                                color = Color(0xFF7C3AED),
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Text(
                                            text = if (wp.turnType == "MPU_YAW") "Turn style: MPU6050 alignment" else "Turn style: Pivot timed ${wp.turnDurationMs}ms",
                                            fontSize = 9.sp,
                                            color = Color(0xFF64748B),
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteWaypoint(wp) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove Node",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineTelemetryLogsCard(
    logs: List<TelemetryLog>,
    recentLogsForChart: List<TelemetryLog>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDeleteLog: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F9)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF10B981))
                    Column {
                        Text(
                            text = "OFFLINE TELEMETRY FLIGHT BLACKBOX",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF001D33)
                        )
                        Text(
                            text = "${logs.size} telemetry records stored in SQLite",
                            fontSize = 10.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF001D33)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Dynamic battery chart
                    BatteryChart(
                        logs = recentLogsForChart,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "HISTORICAL FLIGHT RECORDS",
                            fontSize = 10.sp,
                            color = Color(0xFF001D33),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        if (logs.isNotEmpty()) {
                            TextButton(onClick = onClearAll) {
                                Text("Format Disk / Clear Logs", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No telemetry files recorded. Start manual driver session or run auto-missions to generate offline charts.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        val visibleLogs = logs.take(15)
                        
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            visibleLogs.forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = log.formattedTime,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1A1C1E),
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = if (log.isAutoMode) "AUTO_NAV" else "MANUAL",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier
                                                    .background(
                                                        if (log.isAutoMode) Color(0xFF005FB0) else Color(0xFF94A3B8),
                                                        RoundedCornerShape(3.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                            Text(
                                                text = "CMD: ${log.currentCommand}",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB45309),
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                "${log.voltage}V",
                                                fontSize = 10.sp,
                                                color = Color(0xFF047857),
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                "${log.current}A",
                                                fontSize = 10.sp,
                                                color = Color(0xFF7C3AED),
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                "Status: ${log.soc}%",
                                                fontSize = 10.sp,
                                                color = Color(0xFF475569),
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                "${log.temperature}°C",
                                                fontSize = 10.sp,
                                                color = Color(0xFF0284C7),
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Text(
                                            "MPU6050 Orientation: [Y: ${String.format(locale = java.util.Locale.US, "%.1f", log.yaw)}°, P: ${String.format(locale = java.util.Locale.US, "%.1f", log.pitch)}°, R: ${String.format(locale = java.util.Locale.US, "%.1f", log.roll)}°]",
                                            fontSize = 9.sp,
                                            color = Color(0xFF64748B),
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteLog(log.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Burn entry",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            if (logs.size > 15) {
                                Text(
                                    text = "Showing recent 15 records of ${logs.size} database items...",
                                    fontSize = 9.sp,
                                    color = Color(0xFF475569),
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
