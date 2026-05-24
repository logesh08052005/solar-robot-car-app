package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.RoverRepository
import com.example.data.TelemetryLog
import com.example.data.Waypoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = RoverRepository(database.roverDao())

    // Connection configuration states
    private val _ipAddress = MutableStateFlow("192.168.4.1")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _port = MutableStateFlow(80)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isSimulationMode = MutableStateFlow(true) // Default to true for out-of-the-box rich demo
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Real-Time Sensor telemetry states
    private val _voltage = MutableStateFlow(12.4f)
    val voltage: StateFlow<Float> = _voltage.asStateFlow()

    private val _currentStr = MutableStateFlow(0.40f)
    val currentStr: StateFlow<Float> = _currentStr.asStateFlow()

    private val _soc = MutableStateFlow(92)
    val soc: StateFlow<Int> = _soc.asStateFlow()

    private val _temperature = MutableStateFlow(29.5f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    // Orientation tracking from MPU6050
    private val _yaw = MutableStateFlow(0.0f) // Heading: 0 to 360 degrees
    val yaw: StateFlow<Float> = _yaw.asStateFlow()

    private val _pitch = MutableStateFlow(0.0f) // Tilt: pitch up/down
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _roll = MutableStateFlow(0.0f) // Roll left/right
    val roll: StateFlow<Float> = _roll.asStateFlow()

    private val _tiltCorrectionStatus = MutableStateFlow("Neutral terrain: Ideal voltage output")
    val tiltCorrectionStatus: StateFlow<String> = _tiltCorrectionStatus.asStateFlow()

    private val _headingStability = MutableStateFlow("MPU6050 feedback online - Yaw stability active")
    val headingStability: StateFlow<String> = _headingStability.asStateFlow()

    private val _currentCommand = MutableStateFlow("STOP")
    val currentCommand: StateFlow<String> = _currentCommand.asStateFlow()

    private val _isAutoMode = MutableStateFlow(false)
    val isAutoMode: StateFlow<Boolean> = _isAutoMode.asStateFlow()

    // Simulated coordinates of the robot on the coordinate axes (meters)
    private val _roverX = MutableStateFlow(0.0f)
    val roverX: StateFlow<Float> = _roverX.asStateFlow()

    private val _roverY = MutableStateFlow(0.0f)
    val roverY: StateFlow<Float> = _roverY.asStateFlow()

    private val _activeWaypointIndex = MutableStateFlow(-1)
    val activeWaypointIndex: StateFlow<Int> = _activeWaypointIndex.asStateFlow()

    // Auto loop internal management
    private val _isAutoLoopRunning = MutableStateFlow(false)
    val isAutoLoopRunning: StateFlow<Boolean> = _isAutoLoopRunning.asStateFlow()

    // Database flow bindings
    val waypointsList: StateFlow<List<Waypoint>> = repository.allWaypoints.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val telemetryLogsList: StateFlow<List<TelemetryLog>> = repository.allLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentTelemetryForChart: StateFlow<List<TelemetryLog>> = repository.recentLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Job handles
    private var telemetryPollingJob: Job? = null
    private var autoNavigationJob: Job? = null
    private var isSimulatingIncline = false

    init {
        // Start continuous background loop
        startSensorMonitoringLoop()
        
        // Seed default sample waypoints if none exist
        viewModelScope.launch {
            delay(1000)
            if (waypointsList.value.isEmpty()) {
                repository.addWaypoint(Waypoint(name = "A - Cargo Loading", x = 1.8f, y = 2.0f, targetHeading = 45f, turnType = "MPU_YAW", turnDurationMs = 0, targetSpeed = 0.5f, orderIndex = 0))
                repository.addWaypoint(Waypoint(name = "B - Ramp Elevation", x = 3.5f, y = -1.5f, targetHeading = 180f, turnType = "TIMED", turnDurationMs = 2000, targetSpeed = 0.8f, orderIndex = 1))
                repository.addWaypoint(Waypoint(name = "C - Depot Return", x = -1.0f, y = -3.0f, targetHeading = 270f, turnType = "MPU_YAW", turnDurationMs = 0, targetSpeed = 0.4f, orderIndex = 2))
                showStatus("Seeded 3 standard navigation waypoints")
            }
        }
    }

    fun updateConnectionSettings(newIp: String, newPort: Int) {
        _ipAddress.value = newIp
        _port.value = newPort
    }

    fun toggleSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
        if (enabled) {
            _isConnected.value = true
            showStatus("Simulation mode activated. Sensors streaming virtual data.")
        } else {
            _isConnected.value = false
            showStatus("Hardware interface active. Tap Connect to sync with ESP8266.")
        }
    }

    fun connectToHardware() {
        if (_isSimulationMode.value) {
            _isConnected.value = true
            showStatus("Connected to virtual simulator")
            return
        }

        viewModelScope.launch {
            _isConnecting.value = true
            _statusMessage.value = "Attempting handshake with ESP8266..."
            _errorMessage.value = null

            repository.fetchSensorPayload(_ipAddress.value, _port.value).fold(
                onSuccess = { logVal ->
                    _isConnected.value = true
                    _isConnecting.value = false
                    updateSensorState(logVal)
                    showStatus("Established connection to controller!")
                },
                onFailure = { err ->
                    _isConnecting.value = false
                    _isConnected.value = false
                    _errorMessage.value = "Could not reach ESP8266 at ${_ipAddress.value}:${_port.value}. Details: ${err.localizedMessage}"
                }
            )
        }
    }

    fun disconnectHardware() {
        _isConnected.value = false
        showStatus("Connection closed")
    }

    // Action Triggers from Manual Buttons
    fun executeManualMove(command: String) {
        if (_isAutoMode.value) {
            showError("Cannot steer manually while Autonomous Navigation is active! Disengage Auto mode first.")
            return
        }

        _currentCommand.value = command.uppercase()

        // Apply visual feedback in simulator
        if (_isSimulationMode.value) {
            simulateManualMovement(command)
        } else {
            // Physical Robot Web Request
            viewModelScope.launch {
                repository.sendControlCommand(_ipAddress.value, _port.value, command).fold(
                    onSuccess = { resp -> showStatus("Command '$command': $resp") },
                    onFailure = { err -> showError("Hardware transmission failed: ${err.localizedMessage}") }
                )
            }
        }
    }

    fun executeEmergencyStop() {
        _currentCommand.value = "EMERGENCY_STOP"
        _isAutoMode.value = false
        _isAutoLoopRunning.value = false
        autoNavigationJob?.cancel()

        // Apply instant stopped values
        _currentStr.value = 0.0f
        
        if (_isSimulationMode.value) {
            showStatus("EMERGENCY ALL MOTORS BRAKED - SIMULATOR HALTED")
        } else {
            viewModelScope.launch {
                repository.sendControlCommand(_ipAddress.value, _port.value, "STOP").fold(
                    onSuccess = { showStatus("EMERGENCY BRAKE ACKNOWLEDGED BY HARDWARE") },
                    onFailure = { err -> showError("CRITICAL: EMERGENCY SIGNAL FAILED! ${err.localizedMessage}") }
                )
            }
        }
    }

    fun toggleAutoWaypointMode() {
        val nextMode = !_isAutoMode.value
        _isAutoMode.value = nextMode
        
        if (nextMode) {
            _currentCommand.value = "AUTO_NAV"
            startWaypointExecution()
        } else {
            _currentCommand.value = "STOP"
            _isAutoLoopRunning.value = false
            autoNavigationJob?.cancel()
            _activeWaypointIndex.value = -1
            showStatus("Autonomous Mode Deactivated. Handed control to pilot.")
        }
    }

    // Waypoint management functions
    fun addWaypointItem(name: String, x: Float, y: Float, heading: Float, turnType: String, durationMs: Long, speed: Float) {
        viewModelScope.launch {
            val order = waypointsList.value.size
            val waypoint = Waypoint(
                name = name.ifBlank { "WP #${order + 1}" },
                x = x,
                y = y,
                targetHeading = heading.coerceIn(0f, 359f),
                turnType = turnType,
                turnDurationMs = durationMs,
                targetSpeed = speed.coerceIn(0.1f, 1.0f),
                orderIndex = order
            )
            repository.addWaypoint(waypoint)
            showStatus("Waypoint '${waypoint.name}' saved to Room Database")
            
            // Sync with physical robot if connected and not simulated
            if (!_isSimulationMode.value && _isConnected.value) {
                syncWaypointsToHardware()
            }
        }
    }

    fun removeWaypointItem(wp: Waypoint) {
        viewModelScope.launch {
            repository.deleteWaypoint(wp)
            showStatus("Removed waypoint: ${wp.name}")
        }
    }

    fun clearAllWaypoints() {
        viewModelScope.launch {
            repository.clearWaypoints()
            showStatus("Cleared all waypoints")
            if (_isAutoMode.value) {
                executeEmergencyStop()
            }
        }
    }

    fun triggerInclineSimulation(enable: Boolean) {
        isSimulatingIncline = enable
        if (enable) {
            _pitch.value = 18.5f    // Pitch up on elevation ramp
            _roll.value = -4.2f     // Slight left incline tilt
            _tiltCorrectionStatus.value = "Active Tilt Correction: Motor compensation +30% power"
            showStatus("Incline Sim ON: Pitch ${_pitch.value}° - Rover compensation engaged")
        } else {
            _pitch.value = 0.0f
            _roll.value = 0.0f
            _tiltCorrectionStatus.value = "Neutral terrain: Ideal voltage output"
            showStatus("Incline Sim OFF: Level terrain restored")
        }
    }

    fun triggerLogSnapshot() {
        viewModelScope.launch {
            saveCurrentStateLogToDatabase()
            showStatus("Battery telemetry database log recorded!")
        }
    }

    fun clearAllOfflineLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            showStatus("All offline battery logs deleted")
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            repository.deleteLogById(id)
            showStatus("Deleted log entry #$id")
        }
    }

    private fun syncWaypointsToHardware() {
        viewModelScope.launch {
            val wps = waypointsList.value
            repository.uploadWaypointsToRobot(_ipAddress.value, _port.value, wps).fold(
                onSuccess = { resp -> showStatus("Sync success: $resp") },
                onFailure = { err -> showError("Failed to sync waypoints to ESP8266: ${err.localizedMessage}") }
            )
        }
    }

    // Main polling and logging background job
    private fun startSensorMonitoringLoop() {
        telemetryPollingJob?.cancel()
        telemetryPollingJob = viewModelScope.launch {
            var dbLogCounter = 0
            while (true) {
                if (_isConnected.value) {
                    if (_isSimulationMode.value) {
                        // Gather and fluctuate values in pure simulation
                        runSimulatedSensorFluctuations()
                    } else {
                        // Gather physical value packages
                        repository.fetchSensorPayload(_ipAddress.value, _port.value).fold(
                            onSuccess = { payload -> updateSensorState(payload) },
                            onFailure = { err -> 
                                Log.w("DashboardViewModel", "Hardware polling failed: ${err.message}")
                                // Don't snap-disconnect immediately, allow temporary signal drops, but notify
                            }
                        )
                    }

                    // Periodic auto database logging for offline battery telemetry logs (every 3 seconds)
                    dbLogCounter++
                    if (dbLogCounter >= 3) {
                        dbLogCounter = 0
                        saveCurrentStateLogToDatabase()
                    }
                } else if (_isSimulationMode.value) {
                    // Even if disconnected, simulation mode starts connected for easier UX
                    _isConnected.value = true
                }
                delay(1000)
            }
        }
    }

    private suspend fun saveCurrentStateLogToDatabase() {
        val entry = TelemetryLog(
            voltage = _voltage.value,
            current = _currentStr.value,
            soc = _soc.value,
            temperature = _temperature.value,
            yaw = _yaw.value,
            pitch = _pitch.value,
            roll = _roll.value,
            currentCommand = _currentCommand.value,
            isAutoMode = _isAutoMode.value
        )
        repository.saveTelemetry(entry)
    }

    private fun updateSensorState(payload: TelemetryLog) {
        _voltage.value = payload.voltage
        _currentStr.value = payload.current
        _soc.value = payload.soc
        _temperature.value = payload.temperature
        _yaw.value = payload.yaw
        _pitch.value = payload.pitch
        _roll.value = payload.roll
        _currentCommand.value = payload.currentCommand
        _isAutoMode.value = payload.isAutoMode
    }

    // Auto Mode navigation algorithms
    private fun startWaypointExecution() {
        autoNavigationJob?.cancel()
        val list = waypointsList.value
        if (list.isEmpty()) {
            showError("No waypoints loaded! Please configured targets first.")
            _isAutoMode.value = false
            _currentCommand.value = "STOP"
            return
        }

        _isAutoLoopRunning.value = true
        autoNavigationJob = viewModelScope.launch {
            showStatus("Auto piloting started. Target coordinates sync active.")
            
            // Start from nearest or order indexed 0
            for (idx in list.indices) {
                val target = list[idx]
                _activeWaypointIndex.value = idx
                showStatus("Slewing to Waypoint #${idx+1}: '${target.name}'")

                // Step A: Orientation Turn logic driven by MPU6050 feedback
                if (target.turnType == "MPU_YAW") {
                    _headingStability.value = "Aligning to Target Heading: ${target.targetHeading}°"
                    alignHeadingUsingMpu(target.targetHeading)
                } else {
                    _headingStability.value = "Timed Pivot in progress: ${target.turnDurationMs}ms"
                    executeSimpleTimedTurn(target.turnDurationMs, target.targetHeading)
                }

                // Step B: Drive straight toward coordinate (x,y)
                driveToCoordinate(target.x, target.y, target.targetSpeed)

                // Mark completed in database
                val completedWp = target.copy(isCompleted = true)
                repository.updateWaypoint(completedWp)
                showStatus("Arrived at Waypoint #${idx+1}: '${target.name}'")
                delay(1500) // Pause for loading/sampling at waypoints
            }

            // Waypoints finished
            _activeWaypointIndex.value = -1
            _isAutoMode.value = false
            _isAutoLoopRunning.value = false
            _currentCommand.value = "STOP"
            _headingStability.value = "Mission complete - Standard yaw hold engaged"
            showStatus("Mission Accomplished! All coordinates cleared.")
            
            // Mark all waypoints as uncompleted so they can be run again
            list.forEach { wp ->
                repository.updateWaypoint(wp.copy(isCompleted = false))
            }
        }
    }

    private suspend fun alignHeadingUsingMpu(targetAngle: Float) {
        _currentCommand.value = "TURN"
        _currentStr.value = 1.1f // Increased current draw during turn
        var diff = targetAngle - _yaw.value
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f

        while (Math.abs(diff) > 3.0f) {
            val step = if (diff > 0) 10f else -10f
            _yaw.value = (_yaw.value + step + 360f) % 360f
            
            diff = targetAngle - _yaw.value
            if (diff > 180f) diff -= 360f
            if (diff < -180f) diff += 360f
            
            delay(150) // Simulation step rate
        }
        _yaw.value = targetAngle // lock precisely
        _currentCommand.value = "ALIGN_OK"
        delay(300)
    }

    private suspend fun executeSimpleTimedTurn(durationMs: Long, targetAngle: Float) {
        _currentCommand.value = "TIMED_TURN"
        _currentStr.value = 0.9f
        var elapsed = 0L
        val step = 200L
        val totalSteps = durationMs / step

        for (i in 0 until totalSteps) {
            val progress = (i.toFloat() / totalSteps)
            _yaw.value = (_yaw.value + (targetAngle - _yaw.value) * progress) % 360f
            delay(step)
            elapsed += step
        }
        _yaw.value = targetAngle
        _currentCommand.value = "TURN_DONE"
        delay(300)
    }

    private suspend fun driveToCoordinate(tx: Float, ty: Float, speed: Float) {
        _currentCommand.value = "FORWARD"
        _currentStr.value = 0.5f + (speed * 0.8f) // Speed maps to current consumption

        var dx = tx - _roverX.value
        var dy = ty - _roverY.value
        var distance = sqrt(dx * dx + dy * dy)

        while (distance > 0.15f) {
            _currentCommand.value = "FORWARD"
            
            // Real-time MPU6050 angle closed loop drift correction
            val targetHeading = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat().let {
                if (it < 0) it + 360f else it
            }
            
            // Slowly match current heading to targetheading to simulate drift correction
            var yawDiff = targetHeading - _yaw.value
            if (yawDiff > 180f) yawDiff -= 360f
            if (yawDiff < -180f) yawDiff += 360f
            _yaw.value = (_yaw.value + yawDiff * 0.4f + 360f) % 360f

            // Tilt stability test: adjust current consumption and status if pitch is detected
            if (isSimulatingIncline) {
                // Adjust motor compensation
                _currentStr.value = (0.5f + (speed * 0.8f)) * 1.35f // Compensing incline
                _tiltCorrectionStatus.value = "Pitch ${_pitch.value}°: compensating motor thrust +35%"
            } else {
                _currentStr.value = 0.5f + (speed * 0.8f)
                _tiltCorrectionStatus.value = "Neutral terrain: Ideal voltage output"
            }

            // Propagate position straight
            val yawRad = Math.toRadians(_yaw.value.toDouble())
            val moveDistance = 0.25f * speed
            _roverX.value += (cos(yawRad) * moveDistance).toFloat()
            _roverY.value += (sin(yawRad) * moveDistance).toFloat()

            dx = tx - _roverX.value
            dy = ty - _roverY.value
            distance = sqrt(dx * dx + dy * dy)

            delay(300) // Travel tick
        }

        // Snap precisely
        _roverX.value = tx
        _roverY.value = ty
        _currentCommand.value = "STANDBY"
    }

    private fun simulateManualMovement(cmd: String) {
        _currentCommand.value = cmd.uppercase()
        val thrust = 0.45f
        
        when (cmd.uppercase()) {
            "FORWARD" -> {
                _currentStr.value = 0.75f
                val r = Math.toRadians(_yaw.value.toDouble())
                _roverX.value += (cos(r) * thrust).toFloat()
                _roverY.value += (sin(r) * thrust).toFloat()
            }
            "REVERSE" -> {
                _currentStr.value = 0.80f
                val r = Math.toRadians(_yaw.value.toDouble())
                _roverX.value -= (cos(r) * thrust).toFloat()
                _roverY.value -= (sin(r) * thrust).toFloat()
            }
            "LEFT" -> {
                _currentStr.value = 0.95f
                _yaw.value = (_yaw.value - 15f + 360f) % 360f
            }
            "RIGHT" -> {
                _currentStr.value = 0.95f
                _yaw.value = (_yaw.value + 15f) % 360f
            }
            "STOP" -> {
                _currentStr.value = 0.05f
            }
        }
    }

    private fun runSimulatedSensorFluctuations() {
        // High fidelity small vibrations in battery draw and angle readings
        val noiseVolt = ((-100..100).random() / 1000f)
        val noiseCurr = ((-50..50).random() / 1000f)
        val noiseTemp = ((-20..20).random() / 100f)

        // Slow battery discharge when drawing current
        if (_currentStr.value > 0.01f) {
            val dischargeRate = _currentStr.value * 0.01f
            _voltage.value = (_voltage.value - dischargeRate + noiseVolt).coerceIn(10.0f, 12.6f)
            
            // Map voltage roughly to SOC (%)
            _soc.value = (((_voltage.value - 10.0f) / 2.6f) * 100).toInt().coerceIn(1, 100)
        } else {
            // Charging recovery or standby fluctuation
            _voltage.value = (12.4f + noiseVolt).coerceIn(10.0f, 12.6f)
        }

        _currentStr.value = (_currentStr.value + noiseCurr).coerceAtLeast(0.0f)
        _temperature.value = (29.5f + noiseTemp).coerceIn(20.0f, 50.0f)

        // Small gyro drift noise of orientation sensors
        if (_currentCommand.value == "STOP" || _currentCommand.value == "STANDBY") {
            _yaw.value = (_yaw.value + ((-5..5).random() / 10f) + 360f) % 360f
            if (!isSimulatingIncline) {
                _pitch.value = ((-3..3).random() / 10f).coerceIn(-10f, 10f)
                _roll.value = ((-3..3).random() / 10f).coerceIn(-10f, 10f)
            }
        }
    }

    // Diagnostic Helpers
    private fun showStatus(text: String) {
        _statusMessage.value = text
        _errorMessage.value = null
    }

    private fun showError(text: String) {
        _errorMessage.value = text
        _statusMessage.value = null
    }

    fun clearStatusMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }
}
