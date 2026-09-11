package com.nexplay.dronepreflight.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.*

/**
 * Pobiera pobliskie miejscowości (miasta, wioski) w promieniu ~20 km z Nominatim (OpenStreetMap).
 * Darmowe, bez klucza. Wymaga User-Agent (polityka OSM).
 */
object NearbyPlacesFetcher {

    data class Place(
        val name: String,
        val lat: Double,
        val lon: Double,
        val distanceKm: Double,
        val kind: String, // city, town, village, hamlet
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val UA = "NexDrone/1.0 (drone preflight app; blazeyzem@gmail.com)"

    suspend fun findNearby(
        lat: Double,
        lon: Double,
        radiusKm: Double = 20.0,
        maxResults: Int = 8,
    ): Result<List<Place>> = withContext(Dispatchers.IO) {
        runCatching {
            // Bounding box ~radiusKm dookoła
            val dLat = radiusKm / 111.0
            val dLon = radiusKm / (111.0 * cos(Math.toRadians(lat)))
            val south = lat - dLat
            val north = lat + dLat
            val west = lon - dLon
            val east = lon + dLon

            // Overpass API — bardziej niezawodny do miejscowości niż Nominatim
            // node[place~"city|town|village"](south,west,north,east);
            val query = """
                [out:json][timeout:15];
                (
                  node["place"~"city|town|village|hamlet"](${south},${west},${north},${east});
                );
                out body;
            """.trimIndent()
            val url = "https://overpass-api.de/api/interpreter?data=" +
                java.net.URLEncoder.encode(query, "UTF-8")

            val client = HttpClient(Android) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 15_000
                    connectTimeoutMillis = 5_000
                }
            }
            try {
                val body = client.get(url) {
                    header("User-Agent", UA)
                }.bodyAsText()

                val root = json.parseToJsonElement(body).jsonObject
                val elements = root["elements"]?.jsonArray ?: return@runCatching emptyList()

                elements.mapNotNull { el ->
                    val obj = el.jsonObject
                    val eLat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    val eLon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    val tags = obj["tags"]?.jsonObject ?: return@mapNotNull null
                    val name = tags["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val kind = tags["place"]?.jsonPrimitive?.content ?: "unknown"
                    val d = haversineKm(lat, lon, eLat, eLon)
                    if (d > radiusKm) null
                    else Place(name = name, lat = eLat, lon = eLon, distanceKm = d, kind = kind)
                }.sortedBy { it.distanceKm }
                    .take(maxResults)
            } finally { client.close() }
        }
    }

    private fun haversineKm(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(la2 - la1)
        val dLon = Math.toRadians(lo2 - lo1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(la1)) * cos(Math.toRadians(la2)) * sin(dLon / 2).pow(2.0)
        return 2 * R * asin(sqrt(a))
    }
}
