package com.nexplay.dronepreflight.wear

import android.content.Context
import android.content.SharedPreferences

/** Minimalny cache dla danych z telefonu — SharedPreferences (bo wear nie ma DataStore setup). */
class WearSnapshotCache(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wear_cache", Context.MODE_PRIVATE)

    data class Snapshot(
        val verdict: String,
        val tempC: String,
        val windMs: String,
        val kp: String,
        val location: String,
        val updatedAt: Long,
    )

    fun read(): Snapshot = Snapshot(
        verdict = prefs.getString("verdict", "—") ?: "—",
        tempC = prefs.getString("temp", "—") ?: "—",
        windMs = prefs.getString("wind", "—") ?: "—",
        kp = prefs.getString("kp", "—") ?: "—",
        location = prefs.getString("location", "Brak danych") ?: "Brak danych",
        updatedAt = prefs.getLong("updated_at", 0L),
    )

    fun write(snap: Snapshot) {
        prefs.edit()
            .putString("verdict", snap.verdict)
            .putString("temp", snap.tempC)
            .putString("wind", snap.windMs)
            .putString("kp", snap.kp)
            .putString("location", snap.location)
            .putLong("updated_at", snap.updatedAt)
            .apply()
    }
}
