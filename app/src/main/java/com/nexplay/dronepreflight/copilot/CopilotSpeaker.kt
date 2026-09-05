package com.nexplay.dronepreflight.copilot

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TTS wrapper — proces głos się inicjalizuje asynchronicznie. Kolejkujemy jak jeszcze nie gotowy.
 * Jedna instancja per apka.
 */
object CopilotSpeaker {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val queue = mutableListOf<String>()
    @Volatile private var lastSpokenAt = 0L
    @Volatile private var lastSpokenHash = 0

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pl", "PL")
                ready = true
                synchronized(queue) {
                    queue.forEach { speakNow(it) }
                    queue.clear()
                }
            } else {
                Log.w("CopilotSpeaker", "TTS init failed: $status")
            }
        }
    }

    /** Powiedz tekst. Deduplikuje — nie powtarza tego samego w ciągu 30s. */
    fun say(text: String) {
        val now = System.currentTimeMillis()
        val hash = text.hashCode()
        if (hash == lastSpokenHash && now - lastSpokenAt < 30_000) return
        lastSpokenAt = now
        lastSpokenHash = hash

        if (!ready) {
            synchronized(queue) { queue += text }
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "copilot-${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
