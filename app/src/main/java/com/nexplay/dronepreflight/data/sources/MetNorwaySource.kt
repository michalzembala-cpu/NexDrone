package com.nexplay.dronepreflight.data.sources

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MetNorwaySource : WeatherSource {
    override val name = "MET Norway"

    @Serializable
    private data class Resp(val properties: Props? = null)

    @Serializable
    private data class Props(val timeseries: List<Series> = emptyList())

    @Serializable
    private data class Series(val time: String? = null, val data: SeriesData? = null)

    @Serializable
    private data class SeriesData(
        val instant: Instant2? = null,
        @SerialName("next_1_hours") val next1h: Next? = null,
        @SerialName("next_6_hours") val next6h: Next? = null,
    )

    @Serializable
    private data class Instant2(val details: Details? = null)

    @Serializable
    private data class Details(
        @SerialName("air_temperature") val temp: Double? = null,
        @SerialName("wind_speed") val wind: Double? = null,
        @SerialName("wind_speed_of_gust") val gust: Double? = null,
        @SerialName("wind_from_direction") val windDir: Double? = null,
        @SerialName("cloud_area_fraction") val cloud: Double? = null,
        @SerialName("fog_area_fraction") val fog: Double? = null,
        @SerialName("relative_humidity") val humidity: Double? = null,
    )

    @Serializable
    private data class Next(val details: NextDetails? = null, val summary: Summary? = null)

    @Serializable
    private data class NextDetails(
        @SerialName("precipitation_amount") val precip: Double? = null,
    )

    @Serializable
    private data class Summary(@SerialName("symbol_code") val symbol: String? = null)

    override suspend fun fetch(client: HttpClient, lat: Double, lon: Double, target: Instant): SourceReading {
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
                "?lat=%.4f&lon=%.4f".format(java.util.Locale.US, lat, lon)
        val r: Resp = client.get(url) {
            header("User-Agent", "NexDrone/1.0 (github.com/nexplay/nexdrone)")
        }.body()
        val series = r.properties?.timeseries.orEmpty()
        val instants = series.mapNotNull { it.time?.let(::parseIsoLocalAsUtc) }
        val idx = nearestIndex(instants, target) ?: return SourceReading(source = name)
        val entry = series[idx]
        val d = entry.data?.instant?.details
        val n = entry.data?.next1h ?: entry.data?.next6h
        val sym = n?.summary?.symbol.orEmpty()
        return SourceReading(
            source = name,
            windMs = d?.wind,
            windGustMs = d?.gust,
            windDirDeg = d?.windDir,
            tempC = d?.temp,
            precipMm = n?.details?.precip,
            cloudPct = d?.cloud,
            visibilityM = null,
            humidityPct = d?.humidity,
            condition = labelSymbol(sym),
            isStorm = sym.contains("thunder"),
            isFog = sym.startsWith("fog") || (d?.fog ?: 0.0) > 50.0,
        )
    }

    private fun labelSymbol(sym: String): String? {
        if (sym.isBlank()) return null
        return when {
            sym.startsWith("clearsky") -> "Bezchmurnie"
            sym.startsWith("fair") -> "Przeważnie słonecznie"
            sym.startsWith("partlycloudy") -> "Częściowe zachmurzenie"
            sym.startsWith("cloudy") -> "Pochmurno"
            sym.startsWith("fog") -> "Mgła"
            sym.contains("thunder") -> "Burza"
            sym.contains("sleet") -> "Deszcz ze śniegiem"
            sym.contains("snow") -> "Śnieg"
            sym.contains("rain") -> "Deszcz"
            else -> sym.replace('_', ' ')
        }
    }
}
