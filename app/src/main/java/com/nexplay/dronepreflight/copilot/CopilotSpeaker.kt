package com.nexplay.dronepreflight.copilot

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * TTS wrapper — pręferuje Google TTS + neural/network voice + wolniejsza mowa (bardziej naturalna).
 */
object CopilotSpeaker {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val queue = mutableListOf<String>()
    @Volatile private var lastSpokenAt = 0L
    @Volatile private var lastSpokenHash = 0
    var selectedVoiceName: String? = null

    // Gemini TTS config — set via configureGemini()
    @Volatile private var geminiEnabled = false
    @Volatile private var geminiKey: String = ""
    @Volatile private var geminiCurrentJob: Job? = null
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun configureGemini(enabled: Boolean, apiKey: String, voiceName: String) {
        geminiEnabled = enabled && apiKey.isNotBlank()
        geminiKey = apiKey
        GeminiTts.voiceName = voiceName
    }

    fun init(context: Context) {
        if (tts != null) return
        // Wymuszamy Google TTS engine jeśli dostępny — najlepszy jakościowo na Androidzie
        val googleTts = "com.google.android.tts"
        tts = TextToSpeech(context.applicationContext, { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pl", "PL")
                // Wolniej + niżej = mniej robotyczne
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(0.95f)
                pickBestVoice()
                ready = true
                synchronized(queue) {
                    queue.forEach { speakNow(it) }
                    queue.clear()
                }
            } else {
                Log.w("CopilotSpeaker", "TTS init failed: $status")
            }
        }, googleTts)
    }

    private fun pickBestVoice() {
        val voices = tts?.voices ?: return
        val polishVoices = voices.filter { it.locale.language == "pl" }
        if (polishVoices.isEmpty()) return

        // Preferencja (od najlepszej):
        // 1. Zaznaczony przez usera
        // 2. Nie wymaga sieci + wysoka jakość + nie male-default (bardziej naturalne)
        // 3. Cokolwiek polskiego bez network fallback
        val chosen = selectedVoiceName?.let { name -> polishVoices.firstOrNull { it.name == name } }
            ?: polishVoices.filter { !it.isNetworkConnectionRequired && it.quality >= Voice.QUALITY_HIGH }
                .firstOrNull { it.name.contains("Wavenet", ignoreCase = true) || it.name.contains("neural", ignoreCase = true) }
            ?: polishVoices.filter { !it.isNetworkConnectionRequired }
                .maxByOrNull { it.quality }
            ?: polishVoices.first()
        tts?.voice = chosen
        Log.d("CopilotSpeaker", "Wybrany głos: ${chosen.name} (jakość=${chosen.quality})")
    }

    fun listPolishVoices(): List<Voice> =
        tts?.voices?.filter { it.locale.language == "pl" }?.sortedByDescending { it.quality } ?: emptyList()

    fun setVoice(name: String) {
        selectedVoiceName = name
        val v = tts?.voices?.firstOrNull { it.name == name }
        if (v != null) tts?.voice = v
    }

    /** Powiedz tekst. Deduplikuje — nie powtarza tego samego w ciągu 30s.
     *  Jeśli Gemini TTS włączone → generuje audio z chmury (bardziej naturalne),
     *  fallback do Android TTS gdy błąd. */
    fun say(text: String) {
        val now = System.currentTimeMillis()
        val hash = text.hashCode()
        if (hash == lastSpokenHash && now - lastSpokenAt < 30_000) return
        lastSpokenAt = now
        lastSpokenHash = hash

        if (geminiEnabled) {
            // Anuluj poprzednie odtwarzanie
            geminiCurrentJob?.cancel()
            geminiCurrentJob = bgScope.launch {
                val r = GeminiTts.synthesize(geminiKey, text)
                val audio = r.getOrNull()
                if (audio != null && audio.isNotEmpty()) {
                    GeminiTts.playPcm(audio)
                } else {
                    Log.w("CopilotSpeaker", "Gemini TTS failed, fallback: ${r.exceptionOrNull()?.message}")
                    // Fallback do Android TTS
                    if (ready) speakNow(text)
                    else synchronized(queue) { queue += text }
                }
            }
            return
        }

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
