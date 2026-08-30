package com.nexplay.dronepreflight.data

import kotlinx.serialization.Serializable

@Serializable
data class SavedLocation(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val isDefault: Boolean = false,
)

@Serializable
data class DroneProfile(
    val id: String,
    val name: String,
    val maxWindMs: Double,
    val minTempC: Double,
    val maxTempC: Double,
) {
    fun toLimits(): DroneLimits = DroneLimits(
        maxWindMs = maxWindMs,
        minTempC = minTempC,
        maxTempC = maxTempC,
    )
}

@Serializable
data class FlightLogEntry(
    val id: String,
    val timestamp: Long,
    val locationName: String,
    val verdict: String,           // GO / CAUTION / NO_GO
    val tempC: Double? = null,
    val windMs: Double? = null,
    val gustMs: Double? = null,
    val kpIndex: Double? = null,
    val note: String = "",
    val durationMinutes: Int? = null,
)
