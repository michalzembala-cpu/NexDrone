package com.nexplay.dronepreflight.copilot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexplay.dronepreflight.MainActivity
import com.nexplay.dronepreflight.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service nasłuchujący ciągle "jarvis". Trwa nawet gdy apka jest zamknięta —
 * ale wymaga stałej notyfikacji i type=microphone (Android 14+).
 * Battery drain: ~2-3x normal.
 */
class JarvisService : Service() {

    private var wake: JarvisWakeListener? = null
    private var scope: CoroutineScope? = null
    private var speaker: CopilotSpeaker? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        CopilotSpeaker.init(applicationContext)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        wake = JarvisWakeListener(applicationContext, wakeWord = "jarvis") {
            onWakeDetected()
        }
        wake?.start()
        return START_STICKY
    }

    private fun onWakeDetected() {
        Log.d(TAG, "Wake word detected!")
        wake?.suspend()  // zatrzymaj wake żeby nie kolidował z komendą
        CopilotSpeaker.say("Słucham.")
        scope?.launch {
            try {
                val vio = com.nexplay.dronepreflight.assistant.VoiceIO(applicationContext)
                val command = vio.listen()
                if (command.isBlank()) {
                    CopilotSpeaker.say("Nic nie słyszałem.")
                    return@launch
                }
                Log.d(TAG, "Command: $command")
                val store = SettingsStore(applicationContext)
                val provider = store.assistantProvider.first()
                if (provider != "gemini") {
                    CopilotSpeaker.say("Włącz Gemini w ustawieniach żeby móc gadać.")
                    return@launch
                }
                val key = store.assistantGeminiKey.first()
                if (key.isBlank()) {
                    CopilotSpeaker.say("Brak klucza Gemini.")
                    return@launch
                }
                val name = store.pilotName.first()
                val reply = com.nexplay.dronepreflight.copilot.JarvisChat.ask(
                    apiKey = key,
                    pilotName = name,
                    userQuestion = command,
                )
                CopilotSpeaker.say(reply.getOrElse { "Nie udało mi się przetworzyć pytania." })
            } catch (e: Exception) {
                Log.e(TAG, "Command failed", e)
                CopilotSpeaker.say("Wystąpił błąd.")
            } finally {
                wake?.resume()
            }
        }
    }

    private fun startForegroundCompat() {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, JarvisService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("🎤 Jarvis nasłuchuje")
            .setContentText("Powiedz 'Jarvis' żeby aktywować")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "Wyłącz", stop)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ wymaga explicit foregroundServiceType
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Jarvis wake word",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ciągłe nasłuchiwanie słowa aktywacyjnego"
                setSound(null, null)
            }
        )
    }

    override fun onDestroy() {
        isRunning = false
        wake?.stop()
        wake = null
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    companion object {
        @Volatile var isRunning: Boolean = false
            private set

        const val ACTION_STOP = "com.nexplay.dronepreflight.JARVIS_STOP"
        private const val CHANNEL_ID = "jarvis_wake"
        private const val NOTIF_ID = 3001
        private const val TAG = "JarvisService"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, JarvisService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, JarvisService::class.java).setAction(ACTION_STOP))
        }
    }
}
