package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RoverRepository(private val roverDao: RoverDao) {

    private val tag = "RoverRepository"

    // OkHttp Client configured with quick timeouts for responsive robotic feedback
    private val client = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(1500, TimeUnit.MILLISECONDS)
        .writeTimeout(1500, TimeUnit.MILLISECONDS)
        .build()

    // Database flow streams
    val allLogs: Flow<List<TelemetryLog>> = roverDao.getAllTelemetryLogs()
    val recentLogs: Flow<List<TelemetryLog>> = roverDao.getRecentTelemetryLogs(60) // Fetch latest 60 logs for charts
    val allWaypoints: Flow<List<Waypoint>> = roverDao.getAllWaypoints()

    // Database suspend operations
    suspend fun saveTelemetry(log: TelemetryLog) = withContext(Dispatchers.IO) {
        roverDao.insertTelemetryLog(log)
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        roverDao.clearAllTelemetryLogs()
    }

    suspend fun deleteLogById(id: Long) = withContext(Dispatchers.IO) {
        roverDao.deleteTelemetryLogById(id)
    }

    suspend fun addWaypoint(waypoint: Waypoint) = withContext(Dispatchers.IO) {
        roverDao.insertWaypoint(waypoint)
    }

    suspend fun updateWaypoint(wp: Waypoint) = withContext(Dispatchers.IO) {
        roverDao.updateWaypoint(wp)
    }

    suspend fun deleteWaypoint(wp: Waypoint) = withContext(Dispatchers.IO) {
        roverDao.deleteWaypoint(wp)
    }

    suspend fun clearWaypoints() = withContext(Dispatchers.IO) {
        roverDao.clearAllWaypoints()
    }

    // ESP8266 REST API client integrations
    // Sends manual drive actions: FORWARD, REVERSE, LEFT, RIGHT, STOP, AUTO_ON, AUTO_OFF
    suspend fun sendControlCommand(ip: String, port: Int, command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formattedIp = formatIpAddress(ip)
            // REST endpoint standard e.g. http://192.168.4.1:80/control?cmd=FORWARD
            val url = "http://$formattedIp:$port/control?cmd=${command.uppercase()}"
            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    Result.success(bodyString.ifEmpty { "Command $command acknowledged" })
                } else {
                    Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to send command $command to $ip: ${e.message}")
            Result.failure(e)
        }
    }

    // Sends waypoints list payload so the ESP8266 system can cache the targets
    suspend fun uploadWaypointsToRobot(ip: String, port: Int, waypoints: List<Waypoint>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formattedIp = formatIpAddress(ip)
            val jsonArray = StringBuilder("[").apply {
                waypoints.forEachIndexed { i, wp ->
                    append(
                        """{"id":${wp.id},"name":"${wp.name}","x":${wp.x},"y":${wp.y},"heading":${wp.targetHeading},"turnType":"${wp.turnType}","duration":${wp.turnDurationMs},"speed":${wp.targetSpeed}}"""
                    )
                    if (i < waypoints.lastIndex) append(",")
                }
                append("]")
            }

            // Real web API endpoints usually use a POST to /waypoints
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = jsonArray.toString().toRequestBody(mediaType)
            val url = "http://$formattedIp:$port/waypoints"
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(response.body?.string() ?: "Waypoints synchronization successful")
                } else {
                    Result.failure(Exception("Server returned ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload waypoints list: ${e.message}")
            Result.failure(e)
        }
    }

    // Fetches live sensor data stream from ESP8266 returning the parsed package
    suspend fun fetchSensorPayload(ip: String, port: Int): Result<TelemetryLog> = withContext(Dispatchers.IO) {
        try {
            val formattedIp = formatIpAddress(ip)
            val url = "http://$formattedIp:$port/sensors"
            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: throw Exception("Empty sensors response")
                    val jsonObj = JSONObject(jsonStr)

                    val logEntry = TelemetryLog(
                        voltage = jsonObj.optDouble("voltage", 12.0).toFloat(),
                        current = jsonObj.optDouble("current", 0.0).toFloat(),
                        soc = jsonObj.optInt("soc", 100),
                        temperature = jsonObj.optDouble("temp", 25.0).toFloat(),
                        yaw = jsonObj.optDouble("yaw", 0.0).toFloat(),
                        pitch = jsonObj.optDouble("pitch", 0.0).toFloat(),
                        roll = jsonObj.optDouble("roll", 0.0).toFloat(),
                        currentCommand = jsonObj.optString("command", "IDLE"),
                        isAutoMode = jsonObj.optBoolean("isAuto", false)
                    )
                    Result.success(logEntry)
                } else {
                    Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatIpAddress(ip: String): String {
        var clean = ip.trim()
        clean = clean.replace("http://", "")
        clean = clean.replace("https://", "")
        if (clean.endsWith("/")) {
            clean = clean.dropLast(1)
        }
        return clean.ifEmpty { "192.168.4.1" }
    }
}
