package com.nexplay.dronepreflight.data.sources

import io.ktor.client.HttpClient
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SourceReading(
    val source: String,
    val windMs: Double? = null,
    val windGustMs: Double? = null,
    val windDirDeg: Double? = null,
    val tempC: Double? = null,
    val precipMm: Double? = null,
    val cloudPct: Double? = null,
    val visibilityM: Double? = null,
    val humidityPct: Double? = null,
    val condition: String? = null,
    val isStorm: Boolean = false,
    val isFog: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis(),
)

interface WeatherSource {
    val name: String
    suspend fun fetch(
        client: HttpClient,
        lat: Double,
        lon: Double,
        target: Instant,
    ): SourceReading
}

internal fun kmhToMs(kmh: Double): Double = kmh / 3.6

/** Beaufort scale (1–12) → m/s midpoint, per WMO table. */
internal fun beaufortToMs(bft: Int): Double = when (bft) {
    0 -> 0.15
    1 -> 0.9
    2 -> 2.5
    3 -> 4.4
    4 -> 6.7
    5 -> 9.3
    6 -> 12.3
    7 -> 15.5
    8 -> 18.9
    9 -> 22.6
    10 -> 26.4
    11 -> 30.5
    12 -> 34.0
    else -> 0.0
}

/** Znajdź index [times] o czasie najbliższym [target]. */
internal fun nearestIndex(times: List<Instant>, target: Instant): Int? {
    if (times.isEmpty()) return null
    var bestIdx = 0
    var bestDiff = Long.MAX_VALUE
    times.forEachIndexed { i, t ->
        val diff = kotlin.math.abs(t.toEpochMilliseconds() - target.toEpochMilliseconds())
        if (diff < bestDiff) { bestDiff = diff; bestIdx = i }
    }
    return bestIdx
}

/** Bezpieczne parsowanie ISO-8601 z tolerancją na "YYYY-MM-DDTHH:mm" (bez sekund/strefy). */
internal fun parseIsoLocalAsUtc(iso: String): Instant? = runCatching {
    // Strefa? "Z" na końcu, "+HH:mm" albo "-HH:mm" po 10. znaku (za YYYY-MM-DD).
    val hasTz = iso.endsWith("Z") ||
            iso.indexOf('+', startIndex = 10) >= 0 ||
            iso.indexOf('-', startIndex = 10) >= 0
    val normalized = when {
        hasTz -> iso
        iso.length == 16 -> "${iso}:00Z"     // YYYY-MM-DDTHH:mm → +Z
        iso.length == 19 -> "${iso}Z"        // YYYY-MM-DDTHH:mm:ss → +Z
        else -> iso
    }
    Instant.parse(normalized)
}.getOrNull()

/** 7Timer cloud cover code (1–9) → percent midpoint. */
internal fun sevenTimerCloudPct(code: Int): Double = when (code) {
    1 -> 3.0
    2 -> 12.0
    3 -> 25.0
    4 -> 37.0
    5 -> 50.0
    6 -> 62.0
    7 -> 75.0
    8 -> 87.0
    9 -> 97.0
    else -> 0.0
}
