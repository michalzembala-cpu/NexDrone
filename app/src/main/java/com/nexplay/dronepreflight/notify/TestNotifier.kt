package com.nexplay.dronepreflight.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

/** Wysyła testowe powiadomienie i zwraca stan: czy się udało + powód porażki. */
object TestNotifier {

    sealed class Result {
        object Success : Result()
        object PermissionMissing : Result()
        object ChannelDisabled : Result()
        data class Error(val message: String) : Result()
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun send(context: Context): Result {
        if (!hasPermission(context)) return Result.PermissionMissing
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "test_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId, "Test", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Testowe powiadomienia NexDrone" }
            nm.createNotificationChannel(ch)
            if (!nm.areNotificationsEnabled()) return Result.ChannelDisabled
        }

        return try {
            val n = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("NexDrone — test powiadomień")
                .setContentText("Jeśli to widzisz, powiadomienia działają.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "Jeśli to widzisz, powiadomienia działają — GO window worker będzie mógł ostrzegać o oknach dobrej pogody."
                ))
                .setAutoCancel(true)
                .build()
            nm.notify(9999, n)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e.message ?: "unknown")
        }
    }
}
