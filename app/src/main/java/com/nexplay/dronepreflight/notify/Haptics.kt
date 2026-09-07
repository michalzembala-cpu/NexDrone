package com.nexplay.dronepreflight.notify

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Wibracje przy ważnych alertach — silniejsze przy krytycznych, delikatne przy info. */
object Haptics {

    enum class Kind { INFO, WARN, ALERT }

    fun vibrate(context: Context, kind: Kind) {
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        val pattern = when (kind) {
            Kind.INFO -> longArrayOf(0, 40)                        // krótki puls
            Kind.WARN -> longArrayOf(0, 100, 50, 100)               // dwa krótkie
            Kind.ALERT -> longArrayOf(0, 200, 100, 200, 100, 200)   // trzy mocne
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = when (kind) {
                Kind.INFO -> intArrayOf(0, 80)
                Kind.WARN -> intArrayOf(0, 180, 0, 180)
                Kind.ALERT -> intArrayOf(0, 255, 0, 255, 0, 255)
            }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amp, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
