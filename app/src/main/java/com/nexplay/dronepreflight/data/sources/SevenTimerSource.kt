package com.nexplay.dronepreflight.data.sources

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SevenTimerSource : WeatherSource {
    override val name = "7Timer!"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Serializable
    private data class Resp(
        val init: String? = null,        // "YYYYMMDDHH" UTC
        val dataseries: List<Point> = emptyList(),
    )

    @Serializable
    private data class Point(
        val timepoint: Int? = null,       // godziny od init
        val cloudcover: Int? = null,      // 1–9
        val temp2m: Int? = null,
        val rh2m: String? = null,          // "50%"
        val wind10m: Wind? = null,
        val prec_type: String? = null,
        val prec_amount: Int? = null,
    )

    @Serializable
    private data class Wind(val direction: String? = null, val speed: Int? = null)

    override suspend fun fetch(client: HttpClient, lat: Double, lon: Double, target: Instant): SourceReading {
        val url = "https://www.7timer.info/bin/api.pl" +
                "?lon=%.4f&lat=%.4f&product=civil&output=json".format(java.util.Locale.US, lon, lat)
        val raw: String = client.get(url).body()
        val r: Resp = json.decodeFromString(Resp.serializer(), raw)

        val initInstant = r.init?.let { parseInit(it) } ?: return SourceReading(source = name)
        val targetHoursFromInit = (target.toEpochMilliseconds() - initInstant.toEpochMilliseconds()) / 3_600_000L
        val point = r.dataseries.minByOrNull {
            kotlin.math.abs((it.timepoint ?: 0).toLong() - targetHoursFromInit)
        } ?: return SourceReading(source = name)

        val precAmount = point.prec_amount ?: 0
        val precMm = when (precAmount) {
            0 -> 0.0; 1 -> 0.1; 2 -> 0.5; 3 -> 1.0
            4 -> 2.0; 5 -> 4.0; 6 -> 8.0; 7 -> 15.0
            8 -> 30.0; 9 -> 50.0
            else -> 0.0
        }
        return SourceReading(
            source = name,
            windMs = point.wind10m?.speed?.let { beaufortToMs(it) },
            windGustMs = null,
            windDirDeg = cardinalToDeg(point.wind10m?.direction),
            tempC = point.temp2m?.toDouble(),
            precipMm = precMm,
            cloudPct = point.cloudcover?.let { sevenTimerCloudPct(it) },
            visibilityM = null,
            humidityPct = point.rh2m?.removeSuffix("%")?.trim()?.toDoubleOrNull(),
            condition = point.prec_type?.takeIf { it != "none" },
            isStorm = false,
            isFog = false,
        )
    }

    /** "2026081118" → Instant("2026-08-11T18:00:00Z") */
    private fun parseInit(init: String): Instant? {
        if (init.length < 10) return null
        return runCatching {
            val y = init.substring(0, 4)
            val m = init.substring(4, 6)
            val d = init.substring(6, 8)
            val h = init.substring(8, 10)
            Instant.parse("$y-$m-${d}T$h:00:00Z")
        }.getOrNull()
    }

    private fun cardinalToDeg(dir: String?): Double? = when (dir) {
        "N" -> 0.0
        "NE" -> 45.0
        "E" -> 90.0
        "SE" -> 135.0
        "S" -> 180.0
        "SW" -> 225.0
        "W" -> 270.0
        "NW" -> 315.0
        else -> null
    }
}
