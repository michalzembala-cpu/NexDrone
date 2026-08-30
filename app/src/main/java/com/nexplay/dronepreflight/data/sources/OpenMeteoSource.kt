package com.nexplay.dronepreflight.data.sources

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class OpenMeteoSource : WeatherSource {
    override val name = "Open-Meteo (ICON/GFS)"

    @Serializable
    private data class Resp(val hourly: Hourly? = null)

    @Serializable
    private data class Hourly(
        val time: List<String> = emptyList(),
        @SerialName("temperature_2m") val temp: List<Double?> = emptyList(),
        @SerialName("relative_humidity_2m") val humidity: List<Double?> = emptyList(),
        @SerialName("wind_speed_10m") val wind: List<Double?> = emptyList(),
        @SerialName("wind_direction_10m") val windDir: List<Double?> = emptyList(),
        @SerialName("wind_gusts_10m") val gust: List<Double?> = emptyList(),
        val precipitation: List<Double?> = emptyList(),
        @SerialName("cloud_cover") val cloud: List<Double?> = emptyList(),
        val visibility: List<Double?> = emptyList(),
        @SerialName("weather_code") val code: List<Int?> = emptyList(),
    )

    override suspend fun fetch(client: HttpClient, lat: Double, lon: Double, target: Instant): SourceReading {
        val all = fetchAllHours(client, lat, lon)
        if (all.isEmpty()) return SourceReading(source = name)
        val times = all.map { it.first }
        val idx = nearestIndex(times, target) ?: return SourceReading(source = name)
        return all[idx].second
    }

    /** Pełna godzinowa prognoza (8 dni) — używane w skanie najlepszej pory. */
    suspend fun fetchAllHours(client: HttpClient, lat: Double, lon: Double): List<Pair<Instant, SourceReading>> {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m,wind_direction_10m," +
                "wind_gusts_10m,precipitation,cloud_cover,visibility,weather_code" +
                "&wind_speed_unit=ms&timezone=UTC&forecast_days=8"
        val r: Resp = client.get(url).body()
        val h = r.hourly ?: return emptyList()
        return h.time.mapIndexedNotNull { i, ts ->
            val t = parseIsoLocalAsUtc(ts) ?: return@mapIndexedNotNull null
            val code = h.code.getOrNull(i)
            val reading = SourceReading(
                source = name,
                windMs = h.wind.getOrNull(i),
                windGustMs = h.gust.getOrNull(i),
                windDirDeg = h.windDir.getOrNull(i),
                tempC = h.temp.getOrNull(i),
                precipMm = h.precipitation.getOrNull(i),
                cloudPct = h.cloud.getOrNull(i),
                visibilityM = h.visibility.getOrNull(i),
                humidityPct = h.humidity.getOrNull(i),
                condition = wmoLabel(code),
                isStorm = code in listOf(95, 96, 99),
                isFog = code in listOf(45, 48),
            )
            t to reading
        }
    }

    private fun wmoLabel(code: Int?): String? = when (code) {
        null -> null
        0 -> "Bezchmurnie"
        1, 2 -> "Częściowe zachmurzenie"
        3 -> "Pochmurno"
        45, 48 -> "Mgła"
        51, 53, 55, 56, 57 -> "Mżawka"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Deszcz"
        71, 73, 75, 77, 85, 86 -> "Śnieg"
        95, 96, 99 -> "Burza"
        else -> "Zmienna"
    }
}
