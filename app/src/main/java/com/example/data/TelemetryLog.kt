package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "telemetry_logs")
data class TelemetryLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val voltage: Float,      // Battery voltage in V (e.g., 11.8)
    val current: Float,      // Current draw in A (e.g., 0.85)
    val soc: Int,            // State of Charge in % (0 - 100)
    val temperature: Float,  // MPU6050/Battery Temperature in °C (e.g., 34.2)
    val yaw: Float,          // MPU6050 Orientation Yaw in degrees (0 - 360)
    val pitch: Float,        // MPU6050 Orientation Pitch in degrees
    val roll: Float,         // MPU6050 Orientation Roll in degrees
    val currentCommand: String, // E.g., FORWARD, REVERSE, STOP, AUTO_WAYPOINT
    val isAutoMode: Boolean  // True if in Waypoint Auto Mode, false if manual
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val formattedDate: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
