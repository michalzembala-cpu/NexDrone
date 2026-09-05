package com.nexplay.dronepreflight.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class VoiceUnavailableException(message: String) : Exception(message)

/**
 * Mikrofon do środka, głośnik na zewnątrz — obie strony na systemowych silnikach Androida.
 *
 * Rozpoznawanie i synteza są tu celowo bez żadnej zewnętrznej biblioteki: telefon ma jedno i
 * drugie po polsku od ręki, a asystent, który ważyłby kilkadziesiąt megabajtów modelu, mijałby
 * się z celem w aplikacji, którą odpala się na łące przed startem drona.
 */
class VoiceIO(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val res = tts?.setLanguage(Locale("pl", "PL"))
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Bez polskiego głosu i tak czytamy — angielski silnik przeczyta to koślawo,
                    // ale to lepsze niż cisza, a odpowiedź jest też widoczna na ekranie.
                    Log.w("VoiceIO", "Polish TTS voice unavailable")
                }
            }
        }
    }

    // ===================== wyjście =====================

    fun speak(text: String) {
        if (text.isBlank() || !ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    // ===================== wejście =====================

    /** Jedna wypowiedź, albo pusty string, gdy nic zrozumiałego nie padło. */
    suspend fun listen(): String = suspendCancellableCoroutine { cont ->
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            cont.resumeWith(
                Result.failure(
                    VoiceUnavailableException(
                        "Na tym telefonie nie ma rozpoznawania mowy. Komendy możesz wpisywać poniżej."
                    )
                )
            )
            return@suspendCancellableCoroutine
        }

        recognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        var settled = false
        fun settle(block: () -> Unit) {
            if (settled) return
            settled = true
            block()
        }

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                settle { cont.resume(text) }
            }

            override fun onError(error: Int) {
                // Cisza i brak dopasowania to nie awaria — to po prostu "nic nie powiedziałeś".
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    -> settle { cont.resume("") }

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> settle {
                        cont.resumeWith(
                            Result.failure(
                                VoiceUnavailableException("Brak zgody na mikrofon. Włącz ją w ustawieniach aplikacji.")
                            )
                        )
                    }

                    else -> settle {
                        cont.resumeWith(
                            Result.failure(VoiceUnavailableException("Nie udało się nagrać (błąd $error)."))
                        )
                    }
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        cont.invokeOnCancellation {
            try {
                sr.cancel()
                sr.destroy()
            } catch (e: Exception) {
                Log.w("VoiceIO", "recognizer cleanup failed", e)
            }
        }

        // Mówienie i słuchanie naraz kończy się tym, że asystent słyszy sam siebie.
        stopSpeaking()
        sr.startListening(intent)
    }

    fun shutdown() {
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w("VoiceIO", "recognizer destroy failed", e)
        }
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
