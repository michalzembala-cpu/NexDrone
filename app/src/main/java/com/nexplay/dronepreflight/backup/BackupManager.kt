package com.nexplay.dronepreflight.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.nexplay.dronepreflight.data.DroneLimits
import com.nexplay.dronepreflight.data.DroneProfile
import com.nexplay.dronepreflight.data.FlightLogEntry
import com.nexplay.dronepreflight.data.SavedLocation
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.TempUnit
import com.nexplay.dronepreflight.data.WindUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class BackupData(
    val version: Int = 1,
    val generatedAt: Long,
    val maxWindMs: Double,
    val minTempC: Double,
    val maxTempC: Double,
    val checkedIds: List<String>,
    val savedLocations: List<SavedLocation>,
    val activeLocationId: String?,
    val droneProfiles: List<DroneProfile>,
    val activeProfileId: String?,
    val flightLog: List<FlightLogEntry>,
    val windUnit: String,   // WindUnit.name
    val tempUnit: String,   // TempUnit.name
    val notificationsEnabled: Boolean,
)

object BackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun exportToFile(context: Context, settings: SettingsStore): Uri = withContext(Dispatchers.IO) {
        val data = BackupData(
            generatedAt = System.currentTimeMillis(),
            maxWindMs = settings.limits.first().maxWindMs,
            minTempC = settings.limits.first().minTempC,
            maxTempC = settings.limits.first().maxTempC,
            checkedIds = settings.checkedIds.first().toList(),
            savedLocations = settings.savedLocations.first(),
            activeLocationId = settings.activeLocationId.first(),
            droneProfiles = settings.droneProfiles.first(),
            activeProfileId = settings.activeProfileId.first(),
            flightLog = settings.flightLog.first(),
            windUnit = settings.displayUnits.first().wind.name,
            tempUnit = settings.displayUnits.first().temp.name,
            notificationsEnabled = settings.notificationsEnabled.first(),
        )
        val raw = json.encodeToString(BackupData.serializer(), data)
        val outDir = File(context.getExternalFilesDir(null), "backups").apply { mkdirs() }
        val filename = "nexdrone-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json"
        val file = File(outDir, filename)
        file.writeText(raw)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    suspend fun importFromUri(context: Context, uri: Uri, settings: SettingsStore): Result<BackupData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)!!.use {
                    it.bufferedReader().readText()
                }
                val data = json.decodeFromString(BackupData.serializer(), raw)
                applyBackup(settings, data)
                data
            }
        }

    private suspend fun applyBackup(settings: SettingsStore, data: BackupData) {
        settings.setLimits(DroneLimits(data.maxWindMs, data.minTempC, data.maxTempC))
        settings.setChecked(data.checkedIds.toSet())
        // Locations — zastąp wszystkie
        settings.savedLocations.first().forEach { settings.deleteLocation(it.id) }
        data.savedLocations.forEach { settings.upsertLocation(it) }
        settings.setActiveLocation(data.activeLocationId)
        // Profile
        settings.droneProfiles.first().forEach { settings.deleteProfile(it.id) }
        data.droneProfiles.forEach { settings.upsertProfile(it) }
        settings.setActiveProfile(data.activeProfileId)
        // Historia — zastąp wszystkie
        settings.flightLog.first().forEach { settings.deleteFlight(it.id) }
        data.flightLog.forEach { settings.addFlight(it) }
        // Jednostki
        runCatching { WindUnit.valueOf(data.windUnit) }.getOrNull()?.let { settings.setWindUnit(it) }
        runCatching { TempUnit.valueOf(data.tempUnit) }.getOrNull()?.let { settings.setTempUnit(it) }
        // Notyfikacje
        settings.setNotificationsEnabled(data.notificationsEnabled)
    }
}
