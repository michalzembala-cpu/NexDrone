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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Multi-turn chat z Jarvisem — pamięta historię rozmowy.
 * User może pytać "dlaczego?", "a za godzinę?" bez re-explainowania kontekstu.
 */
object NexAIChat {

    private const val MODEL = "gemini-1.5-flash"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Message(val role: String, val text: String) // role: "user" | "model"

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
                    putJsonObject("systemInstruction") {
                        putJsonArray("parts") {
                            addJsonObject { put("text", (CopilotPrompts.SYSTEM + "\n\n" + systemContext).trim()) }
                        }
                    }
                    putJsonArray("contents") {
                        history.forEach { msg ->
                            addJsonObject {
                                put("role", msg.role)
                                putJsonArray("parts") {
                                    addJsonObject { put("text", msg.text) }
                                }
                            }
                        }
                        addJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                addJsonObject { put("text", newUserMessage) }
                            }
                        }
                    }
                    putJsonObject("generationConfig") {
                        put("maxOutputTokens", 250)
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
                root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content
                    ?: error("Brak odpowiedzi: ${response.take(200)}")
            } finally {
                client.close()
            }
        }
    }
}
