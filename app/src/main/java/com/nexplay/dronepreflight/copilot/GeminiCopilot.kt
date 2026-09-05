package com.nexplay.dronepreflight.copilot

import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.ui.HourlyOutlook
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Google Gemini 2.0 Flash — DARMOWY co-pilot AI.
 * Free tier: 15 requestów/min, 1500 requestów/dzień, 1M tokenów/dzień.
 * Klucz: https://aistudio.google.com/app/apikey (za darmo, wystarczy konto Google).
 */
object GeminiCopilot {

    private const val MODEL = "gemini-2.0-flash"
    private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun briefing(
        apiKey: String,
        pilotName: String,
        snap: AggregatedSnapshot,
        assessment: FlightAssessment,
        outlook: List<HourlyOutlook>,
        units: DisplayUnits,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            call(apiKey, CopilotPrompts.briefing(pilotName, snap, assessment, outlook, units))
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
            call(apiKey, CopilotPrompts.postFlight(pilotName, elapsedSec, maxWindMs, maxGustMs, units, goPct, outlook))
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
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", CopilotPrompts.SYSTEM.trim()) }
                    }
                }
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject { put("text", userMessage) }
                        }
                    }
                }
                putJsonObject("generationConfig") {
                    put("maxOutputTokens", 300)
                    put("temperature", 0.7)
                }
            }.toString()

            val response = client.post("$API_BASE/$MODEL:generateContent?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()

            val root = json.parseToJsonElement(response).jsonObject
            val candidates = root["candidates"]?.jsonArray
                ?: error("Brak candidates: $response")
            val text = candidates.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.content
                ?: error("Brak text w odpowiedzi: $response")
            return text
        } finally {
            client.close()
        }
    }
}
