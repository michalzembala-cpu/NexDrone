package com.nexplay.dronepreflight.copilot

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
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
 * Gemini TTS — generuje audio z API i odtwarza przez AudioTrack.
 * Bardziej naturalny niż wbudowany Android TTS. Używa tego samego klucza co czat.
 *
 * Model gemini-2.5-flash-preview-tts zwraca 16-bit PCM @ 24 kHz mono.
 */
object GeminiTts {

    private const val MODEL = "gemini-2.5-flash-preview-tts"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val SAMPLE_RATE = 24_000

    // Głosy dostępne w Gemini TTS: Puck, Charon, Kore, Fenrir, Aoede, Leda, Orus, Zephyr
    // Kore = męski, spokojny (dobra dla Jarvisa)
    // Puck = męski, energiczny
    // Charon = głęboki męski
    var voiceName: String = "Kore"

    suspend fun synthesize(apiKey: String, text: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = HttpClient(Android) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 20_000
                        connectTimeoutMillis = 5_000
                    }
                }
                try {
                    val body = buildJsonObject {
                        putJsonArray("contents") {
                            addJsonObject {
                                putJsonArray("parts") {
                                    addJsonObject { put("text", text) }
                                }
                            }
                        }
                        putJsonObject("generationConfig") {
                            putJsonArray("responseModalities") { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) }
                            putJsonObject("speechConfig") {
                                putJsonObject("voiceConfig") {
                                    putJsonObject("prebuiltVoiceConfig") {
                                        put("voiceName", voiceName)
                                    }
                                }
                            }
                        }
                    }.toString()

                    val response = client.post(
                        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey",
                    ) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }.bodyAsText()

                    val root = json.parseToJsonElement(response).jsonObject
                    val base64 = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject
                        ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("inlineData")?.jsonObject
                        ?.get("data")?.jsonPrimitive?.content
                        ?: error("Brak audio w response: ${response.take(300)}")

                    Base64.decode(base64, Base64.DEFAULT)
                } finally {
                    client.close()
                }
            }
        }

    /** Odtwarza PCM 16-bit 24kHz mono blocking. */
    fun playPcm(pcmBytes: ByteArray) {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(pcmBytes.size)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(pcmBytes, 0, pcmBytes.size)
            track.play()
            // Poczekaj aż skończy (grubo szacując po długości)
            val durationMs = (pcmBytes.size * 1000L) / (SAMPLE_RATE * 2)  // 16-bit = 2 bytes per sample
            Thread.sleep(durationMs + 100)
        } catch (e: Exception) {
            Log.w("GeminiTts", "playback failed", e)
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
    }
}
