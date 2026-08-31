package com.nexplay.dronepreflight.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexplay.dronepreflight.MainActivity
import com.nexplay.dronepreflight.data.FlightAssessor
import com.nexplay.dronepreflight.data.HourlyScorer
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.WeatherRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.max

/** Skanuje pogodę godzinowo Open-Meteo, powiadamia gdy pojawi się okno GO w kolejnych 24h. */
class GoWindowWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(context)
        if (!settings.notificationsEnabled.first()) return Result.success()

        val limits = settings.limits.first()
        val active = settings.savedLocations.first()
            .firstOrNull { it.id == settings.activeLocationId.first() }
        val (lat, lon) = if (active != null) active.lat to active.lon
        else 52.2297 to 21.0122

        val repo = WeatherRepository()
        val hourly = repo.fetchHourlyScan(lat, lon)
        if (hourly.isEmpty()) return Result.retry()

        val nowMs = Clock.System.now().toEpochMilliseconds()
        val next24h = hourly.filter {
            val ms = it.first.toEpochMilliseconds()
            ms in nowMs..(nowMs + 24 * 3600 * 1000)
        }
        // Znajdź najdłuższe okno GO
        var bestStart = -1
        var bestLen = 0
        var curStart = -1
        var curLen = 0
        next24h.forEachIndexed { i, (_, reading) ->
            val v = HourlyScorer.score(reading, limits, null)
            if (v == Verdict.GO) {
                if (curStart == -1) curStart = i
                curLen++
                if (curLen > bestLen) { bestLen = curLen; bestStart = curStart }
            } else { curStart = -1; curLen = 0 }
        }
        if (bestLen >= 2 && bestStart >= 0) {
            val start = next24h[bestStart].first.toLocalDateTime(TimeZone.currentSystemDefault())
            val end = next24h[bestStart + bestLen - 1].first.toLocalDateTime(TimeZone.currentSystemDefault())
            notify(
                title = "Okno GO w najbliższych 24h",
                text = "%02d:00 – %02d:00 (%d h). Warunki spełniają Twoje limity BSP.".format(
                    start.hour, (end.hour + 1) % 24, bestLen,
                ),
            )
        }

        // Alert "GO kończy się za X min" — gdy obecna godzina jest GO, a w ciągu 90 min pogorszenie
        val currentVerdict = next24h.firstOrNull { it.first.toEpochMilliseconds() >= nowMs - 30 * 60 * 1000L }
            ?.let { HourlyScorer.score(it.second, limits, null) }
        if (currentVerdict == Verdict.GO) {
            val firstBad = next24h.firstOrNull { (t, r) ->
                val ms = t.toEpochMilliseconds()
                ms > nowMs && ms - nowMs <= 90 * 60 * 1000L &&
                    HourlyScorer.score(r, limits, null) != Verdict.GO
            }
            if (firstBad != null) {
                val minutesLeft = max(0, ((firstBad.first.toEpochMilliseconds() - nowMs) / 60_000).toInt())
                val badLocal = firstBad.first.toLocalDateTime(TimeZone.currentSystemDefault())
                notify(
                    channelId = "go_ending",
                    channelName = "Koniec okna GO",
                    notifId = 1002,
                    title = "⏰ Okno GO kończy się za $minutesLeft min",
                    text = "O ${"%02d:%02d".format(badLocal.hour, badLocal.minute)} warunki przestaną spełniać limity Twojego BSP.",
                )
            }
        }

        return Result.success()
    }

    private fun notify(
        title: String,
        text: String,
        channelId: String = CHANNEL_ID,
        channelName: String = "Okna GO",
        notifId: Int = NOTIF_ID,
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Powiadamia o oknach dobrych warunków do lotu" }
            nm.createNotificationChannel(channel)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, n)
    }

    companion object {
        const val WORK_NAME = "go_window_scan"
        private const val CHANNEL_ID = "go_windows"
        private const val NOTIF_ID = 1001

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<GoWindowWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
