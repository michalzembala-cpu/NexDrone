package com.nexplay.dronepreflight.data.sources.kp

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * NOAA G-scale (storm scale, aktualizowany co godzinę).
 * G-scale wynika bezpośrednio z Kp: G0<5, G1=5, G2=6, G3=7, G4=8, G5=9.
 * Konwertujemy G z powrotem do przybliżonego Kp (dolny próg).
 */
class NoaaBoulderK : KpSource {
    override val name = "NOAA SWPC (G-scale)"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetch(client: HttpClient): KpReading {
        val url = "https://services.swpc.noaa.gov/products/noaa-scales.json"
        val resp = client.get(url) { header("User-Agent", "NexDrone/1.0") }
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}")
        val raw = resp.bodyAsText()
        val root = runCatching {
            json.parseToJsonElement(raw) as? JsonObject
        }.getOrNull() ?: error("nie obiekt JSON")

        // Struktura: {"0": {...bieżąca aktywność...}, "1": {...ostatnia godzina...}, ...}
        // Bierzemy pole "0" (aktualne).
        val current = root["0"] as? JsonObject ?: error("brak sekcji 0")
        val g = current["G"] as? JsonObject ?: error("brak G-scale")
        val scale = (g["Scale"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: error("brak G-scale value")
        val kp = when (scale) {
            0 -> 4.0
            1 -> 5.0
            2 -> 6.0
            3 -> 7.0
            4 -> 8.0
            5 -> 9.0
            else -> error("nieznana wartość G=$scale")
        }
        return KpReading(name, kp)
    }
}
