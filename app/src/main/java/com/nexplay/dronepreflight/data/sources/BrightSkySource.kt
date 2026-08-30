package com.nexplay.dronepreflight.data.sources

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class BrightSkySource : WeatherSource {
    override val name = "Bright Sky (DWD)"

    @Serializable
    private data class Resp(val weather: List<Row> = emptyList())

    @Serializable
    private data class Row(
        val timestamp: String? = null,
        val temperature: Double? = null,
        @SerialName("wind_speed") val wind: Double? = null,          // km/h
        @SerialName("wind_direction") val windDir: Double? = null,
        @SerialName("wind_gust_speed") val gust: Double? = null,     // km/h
        val precipitation: Double? = null,                            // mm/h
        @SerialName("cloud_cover") val cloud: Double? = null,
        val visibility: Double? = null,                                // m
        @SerialName("relative_humidity") val humidity: Double? = null,
        val condition: String? = null,
    )

    override suspend fun fetch(client: HttpClient, lat: Double, lon: Double, target: Instant): SourceReading {
        val targetLocal = target.toLocalDateTime(TimeZone.UTC)
        val date = "%04d-%02d-%02d".format(
            java.util.Locale.US,
            targetLocal.year, targetLocal.monthNumber, targetLocal.dayOfMonth,
        )
        val url = "https://api.brightsky.dev/weather" +
                "?lat=%.4f&lon=%.4f&date=%s".format(java.util.Locale.US, lat, lon, date)
        val r: Resp = client.get(url).body()
        val times = r.weather.mapNotNull { it.timestamp?.let(::parseIsoLocalAsUtc) }
        val idx = nearestIndex(times, target) ?: return SourceReading(source = name)
        val w = r.weather[idx]
        val cond = w.condition
        return SourceReading(
            source = name,
            windMs = w.wind?.let { kmhToMs(it) },
            windGustMs = w.gust?.let { kmhToMs(it) },
            windDirDeg = w.windDir,
            tempC = w.temperature,
            precipMm = w.precipitation,
            cloudPct = w.cloud,
            visibilityM = w.visibility,
            humidityPct = w.humidity,
            condition = labelCondition(cond),
            isStorm = cond == "thunderstorm",
            isFog = cond == "fog",
        )
    }

    private fun labelCondition(c: String?): String? = when (c) {
        null, "dry" -> "Sucho"
        "fog" -> "Mgła"
        "rain" -> "Deszcz"
        "sleet" -> "Deszcz ze śniegiem"
        "snow" -> "Śnieg"
        "hail" -> "Grad"
        "thunderstorm" -> "Burza"
        else -> c
    }
}
