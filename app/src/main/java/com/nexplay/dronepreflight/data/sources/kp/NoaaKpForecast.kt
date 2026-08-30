package com.nexplay.dronepreflight.data.sources.kp

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/** NOAA — prognoza planetary Kp. Bierzemy pierwszy wiersz danych (najbliższy "teraz"). */
class NoaaKpForecast : KpSource {
    override val name = "NOAA SWPC (forecast)"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetch(client: HttpClient): KpReading {
        val url = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json"
        val resp = client.get(url) { header("User-Agent", "NexDrone/1.0") }
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}")
        val raw = resp.bodyAsText()
        val root = runCatching { json.parseToJsonElement(raw).jsonArray }
            .getOrNull() ?: error("nie tablica JSON")

        // Bierzemy PIERWSZY wiersz z liczbowym Kp (najbliższy teraz).
        for (i in 0 until root.size) {
            val kp = kpFromRow(root[i]) ?: continue
            if (kp in 0.0..9.5) return KpReading(name, kp)
        }
        error("brak liczbowego Kp w ${root.size} wierszach")
    }

    private fun kpFromRow(el: JsonElement): Double? {
        if (el is JsonArray) {
            return (el.getOrNull(1) as? JsonPrimitive)?.content?.toDoubleOrNull()
        }
        if (el is JsonObject) {
            val candidates = listOf("kp_index", "kp", "predicted_kp", "Kp")
            for (key in candidates) {
                val v = (el[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
                if (v != null) return v
            }
        }
        return null
    }
}
