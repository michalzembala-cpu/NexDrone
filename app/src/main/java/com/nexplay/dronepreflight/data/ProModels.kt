package com.nexplay.dronepreflight.data

import kotlinx.serialization.Serializable

@Serializable
data class SavedLocation(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val isDefault: Boolean = false,
    val rating: Int = 0,           // 0-5 gwiazdek, per użytkownika
    val notes: String = "",        // "dobre do zachodu", "wiatr od wschodu" etc.
    val bestConditions: String = "", // "poranek", "wieczór", "słaby wiatr"
)

@Serializable
data class DroneProfile(
    val id: String,
    val name: String,
    val maxWindMs: Double,
    val minTempC: Double,
    val maxTempC: Double,
    // Drone Health — ręcznie wpisywane
    val batteryCycles: Int = 0,
    val propellerStatus: String = "",     // "nowe" / "sprawdzone" / "do wymiany"
    val lastInspection: Long = 0,          // timestamp
    val firmware: String = "",
    val healthNotes: String = "",
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
    val score: Int? = null,          // NexDrone Score 0-100 (opcjonalny — starsze wpisy bez)
    val goPct: Int? = null,          // % czasu w werdykcie GO podczas lotu
)
