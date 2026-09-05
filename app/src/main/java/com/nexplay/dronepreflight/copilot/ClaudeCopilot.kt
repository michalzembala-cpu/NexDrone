package com.nexplay.dronepreflight.copilot

import android.util.Log
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.formatTemp
import com.nexplay.dronepreflight.data.formatWind
import com.nexplay.dronepreflight.ui.HourlyOutlook
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.addJsonObject

/**
 * Claude API co-pilot. Używa Haiku 4.5 — najszybszy i najtańszy model, idealny do
 * krótkich briefów gdzie liczy się czas odpowiedzi.
 */
object ClaudeCopilot {

    private const val MODEL = "claude-haiku-4-5-20251001"
    private const val API_URL = "https://api.anthropic.com/v1/messages"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val SYSTEM_PROMPT = """
Jesteś AI co-pilotem drona — w stylu Jarvisa z Iron Mana. Mówisz po polsku.

ZASADY:
- Zwięźle: maksymalnie 2-3 zdania.
- Grzecznie, ale nie przesadnie formalnie. Zwracasz się po imieniu jeśli podane.
- Konkret: mówisz LICZBY (wiatr, porywy). Nie owijasz w bawełnę.
- Jesteś rzeczowy — nie robisz smalltalku, nie tłumaczysz oczywistego.
- W briefingu: powitanie + stan warunków + KLUCZOWA obserwacja (np. nadchodzące pogorszenie).
- W podsumowaniu lotu: czas, max wiatr, ogólna ocena, ewentualna sugestia na następny raz.
- NIE mów "z 5 źródeł", "mediana" itp. — mów jak człowiek do człowieka.
- NIE dodawaj emoji ani markdownu — to jest wypowiedziane głośno przez TTS.
"""

    suspend fun briefing(
        apiKey: String,
        pilotName: String,
        snap: AggregatedSnapshot,
        assessment: FlightAssessment,
        outlook: List<HourlyOutlook>,
        units: DisplayUnits,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildBriefingPrompt(pilotName, snap, assessment, outlook, units)
            call(apiKey, prompt)
        }
    }

    suspend fun postFlight(
        apiKey: String,
        pilotName: String,
        elapsedSec: Int,
        maxWindMs: Double?,
        maxGustMs: Double?,
        units: DisplayUnits,
        goPct: Int,
        outlook: List<HourlyOutlook>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = buildPostFlightPrompt(pilotName, elapsedSec, maxWindMs, maxGustMs, units, goPct, outlook)
            call(apiKey, prompt)
        }
    }

    private suspend fun call(apiKey: String, userMessage: String): String {
        val client = HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 5_000
            }
        }
        try {
            val body = buildJsonObject {
                put("model", MODEL)
                put("max_tokens", 300)
                put("system", SYSTEM_PROMPT.trim())
                put("messages", buildJsonArray {
                    addJsonObject {
                        put("role", "user")
                        put("content", userMessage)
                    }
                })
            }.toString()

            val response = client.post(API_URL) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()

            val root = json.parseToJsonElement(response).jsonObject
            val content = root["content"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error("Brak content w odpowiedzi: $response")
            return content["text"]?.jsonPrimitive?.content
                ?: error("Brak text w content: $response")
        } finally {
            client.close()
        }
    }

    private fun buildBriefingPrompt(
        pilotName: String,
        snap: AggregatedSnapshot,
        assessment: FlightAssessment,
        outlook: List<HourlyOutlook>,
        units: DisplayUnits,
    ): String = buildString {
        appendLine("Wygeneruj krótki pre-flight briefing.")
        appendLine()
        if (pilotName.isNotBlank()) appendLine("Pilot: $pilotName")
        appendLine("Werdykt: ${verdictLabel(assessment.overall)}")
        appendLine("Wiatr: ${snap.wind.median?.let { formatWind(it, units.wind) } ?: "brak"}")
        appendLine("Porywy: ${snap.gust.median?.let { formatWind(it, units.wind) } ?: "brak"}")
        appendLine("Kierunek wiatru: ${cardinal(snap.windDir.median)}")
        appendLine("Temperatura: ${formatTemp(snap.temp.median, units.temp)}")
        snap.kpIndex?.let { appendLine("KP index: %.1f".format(it)) }
        appendLine("Zachmurzenie: ${snap.cloud.median?.let { "%.0f%%".format(it) } ?: "brak"}")
        appendLine("Widoczność: ${snap.visibility.median?.let { "%.1f km".format(it / 1000) } ?: "brak"}")
        val problems = assessment.checks.filter { it.verdict != Verdict.GO }
        if (problems.isNotEmpty()) {
            appendLine("Problemy:")
            problems.forEach { appendLine("- ${it.label}: ${it.value} (${it.note ?: "poza limitem"})") }
        }
        val next3 = outlook.take(3).mapNotNull { it.windMs }
        if (next3.isNotEmpty()) {
            val trend = if (next3.last() > next3.first() + 2.0) "rosnący" else if (next3.first() > next3.last() + 2.0) "malejący" else "stabilny"
            appendLine("Trend wiatru (3h): $trend, wartości ${next3.joinToString(", ") { "%.1f".format(it) }} m/s")
        }
    }

    private fun buildPostFlightPrompt(
        pilotName: String,
        elapsedSec: Int,
        maxWindMs: Double?,
        maxGustMs: Double?,
        units: DisplayUnits,
        goPct: Int,
        outlook: List<HourlyOutlook>,
    ): String = buildString {
        appendLine("Wygeneruj krótkie podsumowanie po locie.")
        appendLine()
        if (pilotName.isNotBlank()) appendLine("Pilot: $pilotName")
        val mm = elapsedSec / 60
        val ss = elapsedSec % 60
        appendLine("Czas lotu: %d:%02d".format(mm, ss))
        maxWindMs?.let { appendLine("Max wiatr: ${formatWind(it, units.wind)}") }
        maxGustMs?.let { appendLine("Max porywy: ${formatWind(it, units.wind)}") }
        appendLine("Rozkład warunków: $goPct% czasu w GO")
        val nextGo = outlook.take(24).indexOfFirst { it.verdict == Verdict.GO }
        if (nextGo > 0) appendLine("Następne GO za $nextGo godzin")
    }

    private fun verdictLabel(v: Verdict): String = when (v) {
        Verdict.GO -> "GO (można latać)"
        Verdict.CAUTION -> "CAUTION (warunki graniczne)"
        Verdict.NO_GO -> "NO-GO (nie latać)"
    }

    private fun cardinal(deg: Double?): String = com.nexplay.dronepreflight.data.windDirectionCardinal(deg)
}
