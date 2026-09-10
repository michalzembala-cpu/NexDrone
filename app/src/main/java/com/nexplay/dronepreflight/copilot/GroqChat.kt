package com.nexplay.dronepreflight.copilot

import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.FlightAssessment
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
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Groq API — Llama 3.3 70B, ~300 tok/s, DARMOWE bez billingu.
 * OpenAI-compatible chat completions.
 */
object GroqChat {

    private const val MODEL = "openai/gpt-oss-20b"
    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Message(val role: String, val text: String) // "user" | "assistant"

    suspend fun briefing(
        apiKey: String,
        pilotName: String,
        snap: AggregatedSnapshot,
        assessment: FlightAssessment,
        outlook: List<HourlyOutlook>,
        units: DisplayUnits,
        mission: String = "general",
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            call(apiKey, CopilotPrompts.briefing(pilotName, snap, assessment, outlook, units, mission))
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

    suspend fun ask(
        apiKey: String,
        pilotName: String,
        userQuestion: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = if (pilotName.isNotBlank())
                "Imię pilota: $pilotName\nPytanie: $userQuestion"
            else "Pytanie: $userQuestion"
            call(apiKey, prompt)
        }
    }

    suspend fun continueChat(
        apiKey: String,
        systemContext: String,
        history: List<Message>,
        newUserMessage: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val client = HttpClient(Android) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 15_000
                    connectTimeoutMillis = 5_000
                }
            }
            try {
                val body = buildJsonObject {
                    put("model", MODEL)
                    put("max_tokens", 300)
                    put("temperature", 0.7)
                    putJsonArray("messages") {
                        addJsonObject {
                            put("role", "system")
                            put("content", (CopilotPrompts.SYSTEM + "\n\n" + systemContext).trim())
                        }
                        history.forEach { msg ->
                            addJsonObject {
                                put("role", msg.role)
                                put("content", msg.text)
                            }
                        }
                        addJsonObject {
                            put("role", "user")
                            put("content", newUserMessage)
                        }
                    }
                }.toString()

                val response = client.post(ENDPOINT) {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }.bodyAsText()

                extractText(response)
            } finally { client.close() }
        }
    }

    private suspend fun call(apiKey: String, userMessage: String): String {
        val client = HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 5_000
            }
        }
        try {
            val body = buildJsonObject {
                put("model", MODEL)
                put("max_tokens", 300)
                put("temperature", 0.7)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", CopilotPrompts.SYSTEM.trim())
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", userMessage)
                    }
                }
            }.toString()

            val response = client.post(ENDPOINT) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()

            return extractText(response)
        } finally { client.close() }
    }

    private fun extractText(response: String): String {
        val root = json.parseToJsonElement(response).jsonObject
        val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
        if (content != null) return content
        // Wyciągnij czytelny błąd zamiast surowego JSON-a
        val errMsg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        if (errMsg != null) error("Groq: $errMsg")
        error("Groq (nieznana odpowiedź): ${response.take(500)}")
    }
}
