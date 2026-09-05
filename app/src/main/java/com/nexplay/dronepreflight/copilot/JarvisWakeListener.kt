package com.nexplay.dronepreflight.copilot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*

/**
 * Ciągłe nasłuchiwanie w tle na słowo aktywacyjne (domyślnie "jarvis").
 * Uwaga: to jest hacky wake-word na Android SpeechRecognizer, nie prawdziwy Porcupine.
 * Zużywa baterię 2-3x szybciej. Powinno działać gdy apka jest na wierzchu.
 */
class JarvisWakeListener(
    private val context: Context,
    private val wakeWord: String = "jarvis",
    private val onWakeDetected: () -> Unit,
) {

    private var recognizer: SpeechRecognizer? = null
    private var scope: CoroutineScope? = null
    @Volatile private var running = false
    @Volatile private var suspended = false  // gdy inny recognizer używa mikrofonu

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
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope?.launch {
            while (running) {
                if (!suspended) {
                    listenOnce()
                }
                delay(500)  // krótka przerwa między próbami żeby uspokoić mic
            }
        }
    }

    fun stop() {
        running = false
        scope?.cancel()
        scope = null
        recognizer?.destroy()
        recognizer = null
    }

    /** Wstrzymaj chwilowo (gdy inny recognizer używa mikrofonu). */
    fun suspend() { suspended = true; recognizer?.stopListening() }
    fun resume() { suspended = false }

    private suspend fun listenOnce() = withContext(Dispatchers.Main) {
        val done = CompletableDeferred<Unit>()
        recognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        sr.setRecognitionListener(object : RecognitionListener {
            private var triggered = false
            override fun onPartialResults(partialResults: Bundle?) { check(partialResults) }
            override fun onResults(results: Bundle?) {
                check(results)
                if (!done.isCompleted) done.complete(Unit)
            }
            override fun onError(error: Int) {
                if (!done.isCompleted) done.complete(Unit)
            }
            override fun onEndOfSpeech() { /* czekamy na onResults */ }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            private fun check(bundle: Bundle?) {
                if (triggered) return
                val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                for (t in texts) {
                    val lower = t.lowercase()
                    if (lower.contains(wakeWord)) {
                        triggered = true
                        onWakeDetected()
                        sr.stopListening()
                        if (!done.isCompleted) done.complete(Unit)
                        return
                    }
                }
            }
        })

        try {
            sr.startListening(intent)
            withTimeoutOrNull(15_000) { done.await() }
        } catch (e: Exception) {
            Log.w(TAG, "wake listen error", e)
        } finally {
            sr.destroy()
        }
    }

    private fun hasPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "JarvisWake"
    }
}
