package com.nexplay.dronepreflight.hub

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Klient NexHub backendu — Cloudflare Worker współdzielony z NexPlay.
 * URL i token trzymane w SettingsStore (assistantHubUrl, assistantHubToken).
 */
object NexHubClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Profile(
        val rl: Map<String, String> = emptyMap(),
        val drone: Map<String, String> = emptyMap(),
        val meta: Map<String, String> = emptyMap(),
    )

    private fun client() = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

    /** Utwórz nowy profil, zwraca token. */
    suspend fun createProfile(baseUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = client()
            try {
                val response = c.post("$baseUrl/profile").bodyAsText()
                json.parseToJsonElement(response).jsonObject["token"]?.jsonPrimitive?.content
                    ?: error("Brak tokena w odpowiedzi: $response")
            } finally { c.close() }
        }
    }

    /** Pobierz cały profil. */
    suspend fun fetchProfile(baseUrl: String, token: String): Result<Profile> = withContext(Dispatchers.IO) {
        runCatching {
            val c = client()
            try {
                val response = c.get("$baseUrl/profile") {
                    header("x-token", token)
                }.bodyAsText()
                val root = json.parseToJsonElement(response).jsonObject
                Profile(
                    rl = flatMap(root["rl"]),
                    drone = flatMap(root["drone"]),
                    meta = flatMap(root["meta"]),
                )
            } finally { c.close() }
        }
    }

    /** Zapisz sekcję drona (najczęściej po locie: max wiatr, czas, verdict). */
    suspend fun putDrone(baseUrl: String, token: String, data: Map<String, Any>): Result<Unit> =
        put(baseUrl, token, "/profile/drone", data)

    suspend fun putMeta(baseUrl: String, token: String, data: Map<String, Any>): Result<Unit> =
        put(baseUrl, token, "/profile/meta", data)

    private suspend fun put(baseUrl: String, token: String, path: String, data: Map<String, Any>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val c = client()
                try {
                    val body = buildString {
                        append("{")
                        data.entries.forEachIndexed { i, (k, v) ->
                            if (i > 0) append(",")
                            append("\"").append(k).append("\":")
                            when (v) {
                                is Number -> append(v)
                                is Boolean -> append(v)
                                else -> append("\"").append(v.toString().replace("\"", "\\\"")).append("\"")
                            }
                        }
                        append("}")
                    }
                    c.put("$baseUrl$path") {
                        header("x-token", token)
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }.bodyAsText()
                    Unit
                } finally { c.close() }
            }
        }

    private fun flatMap(el: JsonElement?): Map<String, String> {
        val obj = el?.jsonObject ?: return emptyMap()
        return obj.mapValues { (_, v) ->
            runCatching { v.jsonPrimitive.content }.getOrDefault(v.toString())
        }
    }
}
