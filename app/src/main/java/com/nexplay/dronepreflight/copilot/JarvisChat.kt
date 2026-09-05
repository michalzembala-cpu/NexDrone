package com.nexplay.dronepreflight.copilot

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Dowolna komenda głosowa → Gemini → krótka odpowiedź.
 * User mówi "Jarvis", potem np. "jaki jest wiatr" albo "ustaw wysokość 100 metrów".
 */
object JarvisChat {

    private const val MODEL = "gemini-2.0-flash"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val SYSTEM = """
Jesteś Jarvisem — AI asystentem pilota drona. Odpowiadasz krótko (max 2 zdania), po polsku.
Zwracasz się po imieniu jeśli podane. Bez emoji, bez markdownu — będzie czytane głośno.
Jeśli pytanie nie dotyczy drona/pogody — odpowiedz krótko i wróć do tematu.
"""

    suspend fun ask(apiKey: String, pilotName: String, userQuestion: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prompt = buildString {
                    if (pilotName.isNotBlank()) appendLine("Imię pilota: $pilotName")
                    append("Pytanie: ")
                    append(userQuestion)
                }
                call(apiKey, prompt)
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
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject { put("text", SYSTEM.trim()) }
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
                    put("maxOutputTokens", 200)
                    put("temperature", 0.7)
                }
            }.toString()

            val response = client.post(
                "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey",
            ) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()

            val root = json.parseToJsonElement(response).jsonObject
            val text = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("text")?.jsonPrimitive?.content
                ?: error("Brak odpowiedzi: $response")
            return text
        } finally {
            client.close()
        }
    }
}
