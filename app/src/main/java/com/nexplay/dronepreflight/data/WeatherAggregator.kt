package com.nexplay.dronepreflight.data

import com.nexplay.dronepreflight.data.sources.SourceReading
import com.nexplay.dronepreflight.data.sources.kp.KpReading
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

@Serializable
data class SourceStat(
    val median: Double?,
    val min: Double?,
    val max: Double?,
    val stddev: Double?,
    val count: Int,
)

@Serializable
data class AggregatedSnapshot(
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val fetchedAt: Long,
    val successfulSources: Int,
    val totalSources: Int,
    val readings: List<SourceReading>,
    val failures: List<SourceFailure>,
    val wind: SourceStat,
    val gust: SourceStat,
    val windDir: SourceStat,
    val temp: SourceStat,
    val precip: SourceStat,
    val cloud: SourceStat,
    val visibility: SourceStat,
    val humidity: SourceStat = SourceStat(null, null, null, null, 0),
    val stormVotes: Int,
    val fogVotes: Int,
    val kpIndex: Double?,             // mediana z odczytów Kp (dla wygody FlightAssessora)
    val kpSource: String?,            // "N/5 źródeł" lub nazwa jedynego, jeśli 1
    val kpReadings: List<KpReading> = emptyList(),
    val kpFailures: List<SourceFailure> = emptyList(),
    val kpTotalSources: Int = 0,
)

@Serializable
data class SourceFailure(val source: String, val reason: String)

object WeatherAggregator {

    fun aggregate(
        locationName: String,
        lat: Double,
        lon: Double,
        readings: List<SourceReading>,
        failures: List<SourceFailure>,
        totalSources: Int,
        kpReadings: List<KpReading>,
        kpFailures: List<SourceFailure>,
        kpTotalSources: Int,
    ): AggregatedSnapshot {
        val kpValues = kpReadings.map { it.value }
        val kpMedian = if (kpValues.isEmpty()) null else stat(kpValues).median
        val kpSourceLabel = when {
            kpReadings.isEmpty() -> null
            kpReadings.size == 1 -> kpReadings.first().source
            else -> "${kpReadings.size}/$kpTotalSources źródeł"
        }
        return AggregatedSnapshot(
            locationName = locationName,
            latitude = lat,
            longitude = lon,
            fetchedAt = System.currentTimeMillis(),
            successfulSources = readings.size,
            totalSources = totalSources,
            readings = readings,
            failures = failures,
            wind = stat(readings.mapNotNull { it.windMs }),
            gust = stat(readings.mapNotNull { it.windGustMs }),
            windDir = statCircular(readings.mapNotNull { it.windDirDeg }),
            temp = stat(readings.mapNotNull { it.tempC }),
            precip = stat(readings.mapNotNull { it.precipMm }),
            cloud = stat(readings.mapNotNull { it.cloudPct }),
            visibility = stat(readings.mapNotNull { it.visibilityM }),
            humidity = stat(readings.mapNotNull { it.humidityPct }),
            stormVotes = readings.count { it.isStorm },
            fogVotes = readings.count { it.isFog },
            kpIndex = kpMedian,
            kpSource = kpSourceLabel,
            kpReadings = kpReadings,
            kpFailures = kpFailures,
            kpTotalSources = kpTotalSources,
        )
    }

    private fun stat(values: List<Double>): SourceStat {
        if (values.isEmpty()) return SourceStat(null, null, null, null, 0)
        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        val mean = sorted.sum() / sorted.size
        val variance = sorted.sumOf { (it - mean) * (it - mean) } / sorted.size
        return SourceStat(
            median = median,
            min = sorted.first(),
            max = sorted.last(),
            stddev = if (sorted.size > 1) sqrt(variance) else 0.0,
            count = sorted.size,
        )
    }

    /** Circular median for wind direction (0–360°). Simple: use median of angular deviations. */
    private fun statCircular(values: List<Double>): SourceStat {
        if (values.isEmpty()) return SourceStat(null, null, null, null, 0)
        if (values.size == 1) return SourceStat(values[0], values[0], values[0], 0.0, 1)
        // Represent each angle as unit vector, average, take atan2.
        val sinSum = values.sumOf { Math.sin(Math.toRadians(it)) }
        val cosSum = values.sumOf { Math.cos(Math.toRadians(it)) }
        var meanDeg = Math.toDegrees(Math.atan2(sinSum, cosSum))
        if (meanDeg < 0) meanDeg += 360.0
        val r = sqrt(sinSum * sinSum + cosSum * cosSum) / values.size
        // Circular std in degrees (approximation)
        val stdDeg = if (r > 0) Math.toDegrees(sqrt(-2 * Math.log(r))) else 180.0
        return SourceStat(
            median = meanDeg,
            min = values.min(),
            max = values.max(),
            stddev = stdDeg,
            count = values.size,
        )
    }
}
