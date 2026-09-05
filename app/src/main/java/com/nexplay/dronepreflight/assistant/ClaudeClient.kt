package com.nexplay.dronepreflight.assistant

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

sealed class LlmOutcome {
    data class Answer(val text: String) : LlmOutcome()
    data class NeedsConfirmation(val tool: AssistantTool, val args: Map<String, String>) : LlmOutcome()
}

/**
 * Opcjonalna połowa mózgu. Odzywa się tylko wtedy, gdy dopasowanie offline nic nie znalazło,
 * a user wkleił własny klucz w Ustawieniach — bez obu tych rzeczy z telefonu nic nie wychodzi.
 *
 * Model dostaje ten sam katalog narzędzi co tryb offline i nic poza nim: do stanu aplikacji
 * może sięgnąć wyłącznie przez narzędzie, a narzędzia typu WRITE nie są tu w ogóle wykonywane —
 * pętla zatrzymuje się i oddaje je do głosowego potwierdzenia.
 *
 * Rozmowa jedzie po surowym JSON-ie, a nie przez SDK: cała reszta sieci w tej aplikacji stoi na
 * ktorze, a bloki odpowiedzi (łącznie z blokami myślenia i ich podpisami) odsyłamy z powrotem
 * dosłownie tak, jak przyszły — co przy typowanym mapowaniu byłoby łatwe do zepsucia.
 */
class ClaudeClient(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) {
    companion object {
        const val DEFAULT_MODEL = "claude-opus-5"
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun toolSchema(tools: AssistantTools): JsonArray = buildJsonArray {
        for (t in tools.all) {
            add(
                buildJsonObject {
                    put("name", t.name)
                    put("description", t.description)
                    putJsonObject("input_schema") {
                        put("type", "object")
                        putJsonObject("properties") {
                            for (p in t.params) {
                                putJsonObject(p.name) {
                                    put("type", "string")
                                    put("description", p.description)
                                    // Ograniczony parametr jedzie jako enum, żeby model nie mógł
                                    // wymyślić wartości; i tak jest walidowany po powrocie.
                                    val allowed = p.allowed.filter { it.isNotEmpty() }
                                    if (allowed.isNotEmpty()) {
                                        putJsonArray("enum") { allowed.forEach { add(it) } }
                                    }
                                }
                            }
                        }
                        putJsonArray("required") {
                            t.params.filter { it.required }.forEach { add(it.name) }
                        }
                    }
                }
            )
        }
    }

    private fun argsFrom(input: JsonObject?): Map<String, String> {
        if (input == null) return emptyMap()
        // Model bywa różny co do typów — liczba w polu "hour" przyjdzie jako liczba, nie string.
        // Spłaszczenie do stringów sprawia, że obie połowy mózgu wołają narzędzia identycznie.
        return input.mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: v.toString() }
    }

    suspend fun ask(utterance: String, tools: AssistantTools, system: String): LlmOutcome {
        val client = HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
            }
        }

        try {
            val messages = mutableListOf<JsonObject>(
                buildJsonObject {
                    put("role", "user")
                    put("content", utterance)
                }
            )

            // Cztery rundy to znacznie więcej, niż którekolwiek z tych pytań potrzebuje,
            // a jednocześnie twardy limit na wypadek zapętlenia.
            repeat(4) {
                val body = buildJsonObject {
                    put("model", model)
                    put("max_tokens", 1024)
                    put("system", system)
                    // Router komend to proste zadanie, a to ścieżka głosowa, gdzie opóźnienie
                    // czuć wprost — niski effort załatwia jedno i drugie.
                    putJsonObject("output_config") { put("effort", "low") }
                    put("tools", toolSchema(tools))
                    putJsonArray("messages") { messages.forEach { add(it) } }
                }

                val raw = client.post(ENDPOINT) {
                    header("x-api-key", apiKey)
                    header("anthropic-version", API_VERSION)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(JsonObject.serializer(), body))
                }.bodyAsText()

                val root = json.parseToJsonElement(raw).jsonObject
                root["error"]?.let { err ->
                    val msg = err.jsonObject["message"]?.jsonPrimitive?.content ?: "nieznany błąd"
                    throw IllegalStateException(msg)
                }

                val content = root["content"]?.jsonArray ?: JsonArray(emptyList())
                val spoken = StringBuilder()
                val toolResults = mutableListOf<JsonObject>()

                for (block in content) {
                    val obj = block.jsonObject
                    when (obj["type"]?.jsonPrimitive?.content) {
                        "text" -> obj["text"]?.jsonPrimitive?.content?.let {
                            if (it.isNotBlank()) spoken.append(it.trim()).append(' ')
                        }

                        "tool_use" -> {
                            val id = obj["id"]?.jsonPrimitive?.content ?: continue
                            val name = obj["name"]?.jsonPrimitive?.content ?: continue
                            val tool = tools.find(name)
                            if (tool == null) {
                                toolResults += buildJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", id)
                                    put("content", "Nie ma takiego narzędzia.")
                                }
                                continue
                            }

                            val args = tools.sanitize(tool, argsFrom(obj["input"]?.jsonObject))

                            // Jedyna rzecz, której model nie może zrobić sam.
                            if (tool.kind == ToolKind.WRITE) {
                                return LlmOutcome.NeedsConfirmation(tool, args)
                            }

                            toolResults += buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", id)
                                put("content", tool.run(args))
                            }
                        }
                    }
                }

                if (toolResults.isEmpty()) {
                    val answer = spoken.toString().trim()
                    return LlmOutcome.Answer(answer.ifEmpty { "Nie mam na to odpowiedzi." })
                }

                // Bloki odpowiedzi wracają dosłownie — podpisy bloków myślenia muszą przetrwać
                // nietknięte, inaczej API odrzuci kolejną turę.
                messages += buildJsonObject {
                    put("role", "assistant")
                    put("content", content)
                }
                messages += buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") { toolResults.forEach { add(it) } }
                }
            }

            return LlmOutcome.Answer("Zgubiłem się przy tym pytaniu.")
        } finally {
            client.close()
        }
    }
}
