package com.nexplay.dronepreflight.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

data class WindPoint(
    val lat: Double,
    val lon: Double,
    val windMs: Double,
    val gustMs: Double?,
    val dirDeg: Double,
)

/**
 * Pobiera siatkę wiatru wokół zadanego punktu z Open-Meteo (jednym request-em multi-lokacyjnym).
 * Domyślnie 3×3 = 9 punktów co ~5 km. Zwraca listę z aktualną prędkością i kierunkiem.
 */
object WindGridFetcher {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(
        centerLat: Double,
        centerLon: Double,
        spacingDeg: Double = 0.05,   // ~5 km na średnich szerokościach
        size: Int = 3,               // 3x3 = 9 punktów
    ): List<WindPoint> = withContext(Dispatchers.IO) {
        val half = size / 2
        val points = buildList {
            for (dy in -half..half) {
                for (dx in -half..half) {
                    add(centerLat + dy * spacingDeg to centerLon + dx * spacingDeg)
                }
            }
        }
        val lats = points.joinToString(",") { String.format(Locale.US, "%.4f", it.first) }
        val lons = points.joinToString(",") { String.format(Locale.US, "%.4f", it.second) }
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lats&longitude=$lons" +
            "&current=wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&wind_speed_unit=ms&timezone=UTC"

        val client = HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 5_000
            }
        }
        val body = client.get(url).bodyAsText()
        client.close()

        // Multi-location response: JSON może być tablicą (obiektów per lokacja) albo jednym obiektem.
        val root = json.parseToJsonElement(body)
        val arr = if (root.jsonObject.containsKey("latitude")) listOf(root) else root.jsonArray

        arr.mapIndexedNotNull { i, elem ->
            runCatching {
                val obj = elem.jsonObject
                val lat = obj["latitude"]!!.jsonPrimitive.content.toDouble()
                val lon = obj["longitude"]!!.jsonPrimitive.content.toDouble()
                val current = obj["current"]!!.jsonObject
                val wind = current["wind_speed_10m"]!!.jsonPrimitive.content.toDouble()
                val dir = current["wind_direction_10m"]!!.jsonPrimitive.content.toDouble()
                val gust = current["wind_gusts_10m"]?.jsonPrimitive?.content?.toDoubleOrNull()
                WindPoint(lat, lon, wind, gust, dir)
            }.getOrNull().also { if (it == null) android.util.Log.w("WindGrid", "Skipped point $i") }
        }
    }
}
