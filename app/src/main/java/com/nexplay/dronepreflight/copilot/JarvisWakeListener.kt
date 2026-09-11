package com.nexplay.dronepreflight.copilot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat

/**
 * Ciągłe nasłuchiwanie w tle na słowo aktywacyjne (domyślnie "jarvis").
 *
 * Android SpeechRecognizer zawsze kończy sesję po ciszy — nie da się utrzymać jednej długiej.
 * Więc reużywamy JEDEN recognizer i restartujemy go natychmiast po zakończeniu (bez destroy/create),
 * co znacząco skraca "mrugnięcie" ikonki mikrofonu i redukuje zużycie CPU.
 */
class JarvisWakeListener(
    private val context: Context,
    private val wakeWord: String = "jarvis",
    private val onWakeDetected: () -> Unit,
) {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var running = false
    @Volatile private var suspended = false

    private val intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Wydłuż okno mowy żeby nie ucinał co 2s
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10_000L)
        }
    }

    private val listener = object : RecognitionListener {
        private var triggered = false
        override fun onReadyForSpeech(params: Bundle?) { triggered = false }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) { checkResults(partialResults) }

        override fun onResults(results: Bundle?) {
            checkResults(results)
            scheduleRestart(80)
        }

        override fun onError(error: Int) {
            // NO_MATCH / SPEECH_TIMEOUT są normalne — restart natychmiast
            val fast = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            scheduleRestart(if (fast) 80 else 500)
        }

        private fun checkResults(bundle: Bundle?) {
            if (triggered) return
            val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
            for (t in texts) {
                if (t.lowercase().contains(wakeWord)) {
                    triggered = true
                    try { recognizer?.stopListening() } catch (_: Exception) {}
                    onWakeDetected()
                    return
                }
            }
        }
    }

    fun start() {
        if (running) return
        if (!hasPermission()) {
            Log.w(TAG, "Brak RECORD_AUDIO — wake word wyłączony")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Brak SpeechRecognizer na urządzeniu")
            return
        }
        running = true
        main.post { createAndStart() }
    }

    fun stop() {
        running = false
        main.post {
            try { recognizer?.destroy() } catch (_: Exception) {}
            recognizer = null
        }
    }

    fun suspend() {
        suspended = true
        main.post { try { recognizer?.stopListening() } catch (_: Exception) {} }
    }

    fun resume() {
        if (!running) return
        suspended = false
        scheduleRestart(80)
    }

    private fun createAndStart() {
        if (!running || suspended) return
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "startListening failed", e)
            scheduleRestart(1000)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        main.removeCallbacks(restartRunnable)
        main.postDelayed(restartRunnable, delayMs)
    }

    private val restartRunnable = Runnable { createAndStart() }

    private fun hasPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "JarvisWake"
    }
}
