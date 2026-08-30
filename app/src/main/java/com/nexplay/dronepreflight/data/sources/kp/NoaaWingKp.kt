package com.nexplay.dronepreflight.data.sources.kp

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * NOAA SWPC — estymowany planetary Kp co ~1 min z wiatru słonecznego (ACE/DSCOVR).
 * Zwraca listę obiektów {time_tag, kp_index, ...}. Bierzemy ostatni.
 */
class NoaaWingKp : KpSource {
    override val name = "NOAA SWPC (1-min estimate)"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetch(client: HttpClient): KpReading {
        val url = "https://services.swpc.noaa.gov/json/planetary_k_index_1m.json"
        val resp = client.get(url) { header("User-Agent", "NexDrone/1.0") }
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}")
        val raw = resp.bodyAsText()
        val arr = runCatching { json.parseToJsonElement(raw).jsonArray }
            .getOrNull() ?: error("nie tablica JSON")
        // Szukaj od końca po obiekcie z polem kp_index / estimated_kp / kp.
        for (i in arr.size - 1 downTo 0) {
            val obj = arr[i] as? JsonObject ?: continue
            val v = (obj["kp_index"] as? JsonPrimitive
                ?: obj["estimated_kp"] as? JsonPrimitive
                ?: obj["kp"] as? JsonPrimitive)
                ?.jsonPrimitive?.content?.toDoubleOrNull()
            if (v != null && v in 0.0..9.5) return KpReading(name, v)
        }
        error("brak liczbowego kp_index w ${arr.size} wpisach")
    }
}
