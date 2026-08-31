package com.nexplay.dronepreflight.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nexplay.dronepreflight.MainActivity
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.DroneLimits
import com.nexplay.dronepreflight.data.HourlyScorer
import com.nexplay.dronepreflight.data.LocationProvider
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.WeatherRepository
import com.nexplay.dronepreflight.data.formatTemp
import com.nexplay.dronepreflight.data.formatWind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime

/**
 * Foreground service pilnujący pogody w trakcie lotu.
 * Co 5 minut sprawdza warunki i alarmuje przy burzy / mocnych porywach / spadku werdyktu poniżej GO.
 */
class StormMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var lastVerdict: Verdict = Verdict.GO
    private var endingSoonNotified: Boolean = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        scope.launch { SettingsStore(applicationContext).setMonitoringActive(true) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForegroundCompat()
        if (loop == null) {
            loop = scope.launch {
                while (isActive) {
                    runCatching { checkOnce() }
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        ensureChannels()
        val n = buildOngoingNotification("Monitoring warunków lotu", "Sprawdzam co 5 minut…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID_ONGOING, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIF_ID_ONGOING, n)
        }
    }

    private suspend fun checkOnce() {
        val settings = SettingsStore(applicationContext)
        val limits = settings.limits.first()
        val units = settings.displayUnits.first()
        val active = settings.savedLocations.first().firstOrNull {
            it.id == settings.activeLocationId.first()
        }
        val (lat, lon) = if (active != null) active.lat to active.lon
        else {
            val loc = LocationProvider(applicationContext).current()
            if (loc != null) loc.lat to loc.lon
            else 52.2297 to 21.0122
        }

        val repo = WeatherRepository()
        val hourly = repo.fetchHourlyScan(lat, lon)
        if (hourly.isEmpty()) return

        val now = Clock.System.now().toEpochMilliseconds()
        // Rozważamy najbliższą godzinę + następną
        val nextTwo = hourly.filter {
            val ms = it.first.toEpochMilliseconds()
            ms in (now - 3600_000)..(now + 2 * 3600_000)
        }
        if (nextTwo.isEmpty()) return

        // Reprezentatywna godzina — najbliższa teraz
        val current = nextTwo.minByOrNull { kotlin.math.abs(it.first.toEpochMilliseconds() - now) }!!
        val reading = current.second

        val verdict = HourlyScorer.score(reading, limits, null)
        val alerts = mutableListOf<String>()
        if (reading.isStorm) alerts += "BURZA w prognozie."
        val gust = reading.windGustMs
        if (gust != null && gust >= limits.maxWindMs) {
            alerts += "Porywy ${formatWind(gust, units.wind)} ≥ limit ${formatWind(limits.maxWindMs, units.wind)}."
        }
        if (verdict != Verdict.GO && lastVerdict == Verdict.GO) {
            alerts += "Werdykt spadł z GO na ${verdictLabel(verdict)}."
        }
        lastVerdict = verdict

        // KONIEC OKNA GO — jeśli teraz GO, ale w ciągu ~30-60 min prognoza pogorszy się
        if (verdict == Verdict.GO && !endingSoonNotified) {
            val firstBad = hourly.firstOrNull { (t, r) ->
                val ms = t.toEpochMilliseconds()
                ms > now && ms - now <= 90 * 60 * 1000L &&
                    HourlyScorer.score(r, limits, null) != Verdict.GO
            }
            if (firstBad != null) {
                val minutesLeft = ((firstBad.first.toEpochMilliseconds() - now) / 60_000).toInt()
                if (minutesLeft <= 60) {
                    alerts += "Twoje okno GO kończy się za $minutesLeft min (o ${
                        firstBad.first.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).run {
                            "%02d:%02d".format(hour, minute)
                        }
                    })."
                    endingSoonNotified = true
                }
            }
        }
        // Reset flag gdy GO wróci lub minie już alarmowany moment
        if (verdict != Verdict.GO) endingSoonNotified = false

        // Aktualizuj on-going z krótkim status-em
        updateOngoing(
            title = "Monitoring warunków · ${verdictLabel(verdict)}",
            text = buildStatusLine(reading, units),
        )

        if (alerts.isNotEmpty()) {
            fireAlert(alerts.joinToString(" · "))
        }
    }

    private fun buildStatusLine(
        reading: com.nexplay.dronepreflight.data.sources.SourceReading,
        units: DisplayUnits,
    ): String {
        val bits = buildList {
            reading.windMs?.let { add("wiatr ${formatWind(it, units.wind)}") }
            reading.windGustMs?.let { add("porywy ${formatWind(it, units.wind)}") }
            reading.tempC?.let { add(formatTemp(it, units.temp)) }
            if (reading.isStorm) add("BURZA")
        }
        return bits.joinToString(" · ")
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING, "Monitoring lotu",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Trwałe powiadomienie w trakcie monitoringu warunków"
                setSound(null, null)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT, "Alarmy pogodowe",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerty gdy pogoda zagraża lotowi"
                enableVibration(true)
            }
        )
    }

    private fun buildOngoingNotification(title: String, text: String): android.app.Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, StormMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "Zakończ", stopPi)
            .build()
    }

    private fun updateOngoing(title: String, text: String) {
        val nm = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        nm.notify(NOTIF_ID_ONGOING, buildOngoingNotification(title, text))
    }

    private fun fireAlert(text: String) {
        val nm = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠ Alarm pogodowy")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_SOUND)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(NOTIF_ID_ALERT, n)
    }

    override fun onDestroy() {
        isRunning = false
        loop?.cancel()
        // Zapisz stan SYNC-owo (żeby zdążyć przed zabiciem procesu)
        runBlocking {
            SettingsStore(applicationContext).setMonitoringActive(false)
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun verdictLabel(v: Verdict): String = when (v) {
        Verdict.GO -> "GO"
        Verdict.CAUTION -> "OSTROŻNIE"
        Verdict.NO_GO -> "NO-GO"
    }

    companion object {
        @Volatile var isRunning: Boolean = false
            private set

        const val ACTION_STOP = "com.nexplay.dronepreflight.STOP_MONITOR"
        private const val CHANNEL_ONGOING = "storm_monitor_ongoing"
        private const val CHANNEL_ALERT = "storm_monitor_alert"
        private const val NOTIF_ID_ONGOING = 2001
        private const val NOTIF_ID_ALERT = 2002
        private const val CHECK_INTERVAL_MS = 5 * 60 * 1000L  // 5 minut

        fun start(context: Context) {
            val intent = Intent(context, StormMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StormMonitorService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
