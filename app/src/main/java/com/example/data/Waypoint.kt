package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waypoints")
data class Waypoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val x: Float,                 // X coordinate in meters relative to origin (e.g., 2.5)
    val y: Float,                 // Y coordinate in meters relative to origin (e.g., -4.0)
    val targetHeading: Float,     // Target alignment heading in degrees (0 - 359)
    val turnType: String,         // "MPU_YAW" (MPU6050 feedback turn) or "TIMED" (duration-based)
    val turnDurationMs: Long,     // Duration of timed turn if turnType is "TIMED"
    val targetSpeed: Float,       // Thrust or speed factor 0.0 to 1.0 (e.g. 0.6)
    val isCompleted: Boolean = false,
    val orderIndex: Int           // Traversal sequence ordering
)
