package com.nexplay.dronepreflight.data.sources

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class WttrInSource : WeatherSource {
    override val name = "wttr.in"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Serializable
    private data class Resp(
        val weather: List<Day> = emptyList(),
    )

    @Serializable
    private data class Day(
        val date: String? = null,        // "YYYY-MM-DD"
        val hourly: List<Hourly> = emptyList(),
    )

    @Serializable
    private data class Hourly(
        val time: String? = null,           // "0", "300", "600", ... (HHMM w formacie int)
        @SerialName("tempC") val tempC: String? = null,
        @SerialName("windspeedKmph") val windKmph: String? = null,
        @SerialName("winddirDegree") val windDir: String? = null,
        @SerialName("precipMM") val precipMm: String? = null,
        val cloudcover: String? = null,
        val visibility: String? = null,
        val humidity: String? = null,
        val weatherDesc: List<Desc> = emptyList(),
    )

    @Serializable
    private data class Desc(val value: String)

    override suspend fun fetch(client: HttpClient, lat: Double, lon: Double, target: Instant): SourceReading {
        val url = "https://wttr.in/%.4f,%.4f?format=j1".format(java.util.Locale.US, lat, lon)
        val raw: String = client.get(url) {
            header("Accept", "application/json")
            header("User-Agent", "curl/8.0")
        }.body()
        val r: Resp = json.decodeFromString(Resp.serializer(), raw)
        if (r.weather.isEmpty()) return SourceReading(source = name)

        // Data lokalna (bez strefy, ale przybliżenie wystarczy — wttr używa daty lokalnej lokalizacji).
        val targetLocal = target.toLocalDateTime(TimeZone.UTC)
        val targetDate = "%04d-%02d-%02d".format(
            java.util.Locale.US,
            targetLocal.year, targetLocal.monthNumber, targetLocal.dayOfMonth,
        )
        val day = r.weather.firstOrNull { it.date == targetDate } ?: r.weather.first()
        val targetHhmm = targetLocal.hour * 100
        val hourly = day.hourly.minByOrNull {
            val h = it.time?.toIntOrNull() ?: 0
            kotlin.math.abs(h - targetHhmm)
        } ?: return SourceReading(source = name)

        val desc = hourly.weatherDesc.firstOrNull()?.value
        val descLower = desc?.lowercase().orEmpty()
        return SourceReading(
            source = name,
            windMs = hourly.windKmph?.toDoubleOrNull()?.let { kmhToMs(it) },
            windGustMs = null,
            windDirDeg = hourly.windDir?.toDoubleOrNull(),
            tempC = hourly.tempC?.toDoubleOrNull(),
            precipMm = hourly.precipMm?.toDoubleOrNull(),
            cloudPct = hourly.cloudcover?.toDoubleOrNull(),
            visibilityM = hourly.visibility?.toDoubleOrNull()?.let { it * 1000.0 },
            humidityPct = hourly.humidity?.toDoubleOrNull(),
            condition = desc,
            isStorm = "thunder" in descLower || "storm" in descLower,
            isFog = "fog" in descLower || "mist" in descLower,
        )
    }
}
