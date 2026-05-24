package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RoverDao {

    // Telemetry logs
    @Query("SELECT * FROM telemetry_logs ORDER BY timestamp DESC")
    fun getAllTelemetryLogs(): Flow<List<TelemetryLog>>

    @Query("SELECT * FROM telemetry_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTelemetryLogs(limit: Int): Flow<List<TelemetryLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetryLog(log: TelemetryLog)

    @Query("DELETE FROM telemetry_logs")
    suspend fun clearAllTelemetryLogs()

    @Query("DELETE FROM telemetry_logs WHERE id = :id")
    suspend fun deleteTelemetryLogById(id: Long)

    // Waypoints config
    @Query("SELECT * FROM waypoints ORDER BY orderIndex ASC")
    fun getAllWaypoints(): Flow<List<Waypoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: Waypoint)

    @Update
    suspend fun updateWaypoint(waypoint: Waypoint)

    @Delete
    suspend fun deleteWaypoint(waypoint: Waypoint)

    @Query("DELETE FROM waypoints")
    suspend fun clearAllWaypoints()
}
