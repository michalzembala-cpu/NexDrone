package com.nexplay.dronepreflight.data.sources.kp

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * GFZ Potsdam — oficjalna definicja Kp. Endpoint z parametrami czasu.
 * Zwraca {"metadata": ..., "datetime": [...], "Kp": [...]} albo tablicę.
 */
class GfzNowcastKp : KpSource {
    override val name = "GFZ Potsdam (nowcast)"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetch(client: HttpClient): KpReading {
        val now = Clock.System.now()
        val start = now.minus(6, DateTimeUnit.HOUR, TimeZone.UTC)
        val startStr = start.toString().substring(0, 19) + "Z"
        val endStr = now.toString().substring(0, 19) + "Z"

        val urls = listOf(
            "https://kp.gfz.de/app/json/?start=$startStr&end=$endStr&index=Kp",
            "https://kp.gfz-potsdam.de/app/json/?start=$startStr&end=$endStr&index=Kp",
        )

        var lastErr: String = "brak URL"
        for (url in urls) {
            val result = runCatching { tryFetch(client, url) }
            if (result.isSuccess) return KpReading(name, result.getOrThrow())
            lastErr = result.exceptionOrNull()?.message?.take(80) ?: "unknown"
        }
        error(lastErr)
    }

    private suspend fun tryFetch(client: HttpClient, url: String): Double {
        val resp = client.get(url) { header("User-Agent", "NexDrone/1.0") }
        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}")
        val raw = resp.bodyAsText()
        if (raw.trimStart().startsWith("<")) error("HTML zamiast JSON")
        val root = json.parseToJsonElement(raw)
        return when (root) {
            is JsonObject -> {
                val list = (root["Kp"] as? JsonArray)
                    ?: (root["kp"] as? JsonArray)
                    ?: error("brak pola Kp w obiekcie")
                (list.lastOrNull() as? JsonPrimitive)?.doubleOrNull
                    ?: error("brak wartości Kp na końcu tablicy")
            }
            is JsonArray -> {
                val last = root.lastOrNull() as? JsonArray ?: error("puste dane")
                (last.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: error("brak Kp")
            }
            else -> error("nieznana struktura")
        }
    }
}
