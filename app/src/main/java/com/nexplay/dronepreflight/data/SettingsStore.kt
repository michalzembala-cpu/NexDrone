package com.nexplay.dronepreflight.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "drone_preflight")

class SettingsStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private object Keys {
        val MaxWind = doublePreferencesKey("max_wind")
        val MinTemp = doublePreferencesKey("min_temp")
        val MaxTemp = doublePreferencesKey("max_temp")
        val ChecklistDone = stringPreferencesKey("checklist_done")
        val SavedLocations = stringPreferencesKey("saved_locations")   // JSON List<SavedLocation>
        val ActiveLocationId = stringPreferencesKey("active_location_id")
        val FlightLog = stringPreferencesKey("flight_log")             // JSON List<FlightLogEntry>
        val NotificationsEnabled = stringPreferencesKey("notif_enabled")
        val MonitoringActive = stringPreferencesKey("monitoring_active")
        val DroneProfiles = stringPreferencesKey("drone_profiles")           // JSON List<DroneProfile>
        val ActiveProfileId = stringPreferencesKey("active_profile_id")
        val CachedSnapshot = stringPreferencesKey("cached_snapshot")         // JSON AggregatedSnapshot (offline)
        val OnboardingSeen = stringPreferencesKey("onboarding_seen")
        val WindUnitKey = stringPreferencesKey("unit_wind")   // MS / KMH / KTS
        val TempUnitKey = stringPreferencesKey("unit_temp")   // C / F
        // Cache dla widgetu — trzymamy prosty snapshot ostatniej oceny.
        val WVerdict = stringPreferencesKey("w_verdict")           // GO/CAUTION/NO_GO/—
        val WTempC = stringPreferencesKey("w_temp")                 // "18.5" lub "—"
        val WWindMs = stringPreferencesKey("w_wind")                // "6.2" lub "—"
        val WKp = stringPreferencesKey("w_kp")                      // "3.0" lub "—"
        val WLocation = stringPreferencesKey("w_location")
        val WUpdatedAt = longPreferencesKey("w_updated_at")
        // Auto-update dialog throttle + dismiss-memory
        val LastUpdateCheckAt = longPreferencesKey("update_check_at")
        val DismissedUpdateVersion = stringPreferencesKey("update_dismissed_ver")
        // Asystent głosowy
        val AssistantEnabled = stringPreferencesKey("assistant_enabled")
        val AssistantSpeak = stringPreferencesKey("assistant_speak")
        val AssistantUseLlm = stringPreferencesKey("assistant_use_llm")
        val AssistantApiKey = stringPreferencesKey("assistant_api_key")
        val PilotName = stringPreferencesKey("pilot_name")
    }

    val pilotName: Flow<String> = context.dataStore.data.map { it[Keys.PilotName] ?: "" }
    suspend fun setPilotName(name: String) {
        context.dataStore.edit { it[Keys.PilotName] = name.trim() }
    }

    // ── Asystent głosowy ──
    //
    // Dwa osobne zgody. Pierwsza włącza lokalny, offline'owy zestaw komend i mikrofon; druga —
    // i tylko ona — wysyła cokolwiek na zewnątrz, i wymaga własnego klucza użytkownika.

    val assistantEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AssistantEnabled] == "1" }
    suspend fun setAssistantEnabled(on: Boolean) {
        context.dataStore.edit { it[Keys.AssistantEnabled] = if (on) "1" else "0" }
    }

    // Domyślnie włączone: przy dronie w powietrzu ręce są zajęte, a czytana odpowiedź jest
    // jedynym sensownym wyjściem.
    val assistantSpeak: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AssistantSpeak] != "0" }
    suspend fun setAssistantSpeak(on: Boolean) {
        context.dataStore.edit { it[Keys.AssistantSpeak] = if (on) "1" else "0" }
    }

    val assistantUseLlm: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AssistantUseLlm] == "1" }
    suspend fun setAssistantUseLlm(on: Boolean) {
        context.dataStore.edit { it[Keys.AssistantUseLlm] = if (on) "1" else "0" }
    }

    val assistantApiKey: Flow<String> =
        context.dataStore.data.map { it[Keys.AssistantApiKey] ?: "" }
    suspend fun setAssistantApiKey(key: String) {
        context.dataStore.edit { it[Keys.AssistantApiKey] = key }
    }

    // ── Auto-update dialog ──

    val lastUpdateCheckAt: Flow<Long> = context.dataStore.data.map { it[Keys.LastUpdateCheckAt] ?: 0L }
    suspend fun setLastUpdateCheckAt(ms: Long) {
        context.dataStore.edit { it[Keys.LastUpdateCheckAt] = ms }
    }

    val dismissedUpdateVersion: Flow<String> = context.dataStore.data.map { it[Keys.DismissedUpdateVersion] ?: "" }
    suspend fun setDismissedUpdateVersion(v: String) {
        context.dataStore.edit { it[Keys.DismissedUpdateVersion] = v }
    }

    /**
     * Efektywne limity BSP — aktywny profil jeśli ustawiony,
     * inaczej stare klucze (backward-compat po pierwszej instalacji).
     */
    val limits: Flow<DroneLimits> = context.dataStore.data.map { p ->
        val activeId = p[Keys.ActiveProfileId]
        val profilesRaw = p[Keys.DroneProfiles]
        val profiles = if (profilesRaw.isNullOrBlank()) emptyList()
        else runCatching {
            json.decodeFromString(ListSerializer(DroneProfile.serializer()), profilesRaw)
        }.getOrDefault(emptyList())
        val active = profiles.firstOrNull { it.id == activeId }
        active?.toLimits() ?: DroneLimits(
            maxWindMs = p[Keys.MaxWind] ?: 10.0,
            minTempC = p[Keys.MinTemp] ?: 0.0,
            maxTempC = p[Keys.MaxTemp] ?: 40.0,
        )
    }

    suspend fun setLimits(l: DroneLimits) {
        context.dataStore.edit {
            it[Keys.MaxWind] = l.maxWindMs
            it[Keys.MinTemp] = l.minTempC
            it[Keys.MaxTemp] = l.maxTempC
        }
    }

    val checkedIds: Flow<Set<String>> = context.dataStore.data.map { p ->
        val raw = p[Keys.ChecklistDone] ?: return@map emptySet<String>()
        if (raw.isBlank()) emptySet() else raw.split('|').toSet()
    }

    suspend fun setChecked(ids: Set<String>) {
        context.dataStore.edit {
            it[Keys.ChecklistDone] = ids.joinToString("|")
        }
    }

    // ── Wielokrotne lokalizacje ──

    val savedLocations: Flow<List<SavedLocation>> = context.dataStore.data.map { p ->
        val raw = p[Keys.SavedLocations] ?: return@map emptyList<SavedLocation>()
        if (raw.isBlank()) emptyList() else runCatching {
            json.decodeFromString(ListSerializer(SavedLocation.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    val activeLocationId: Flow<String?> = context.dataStore.data.map { p ->
        p[Keys.ActiveLocationId]?.takeIf { it.isNotBlank() }
    }

    suspend fun upsertLocation(loc: SavedLocation) {
        val existing = readLocationsBlocking()
        val next = if (existing.any { it.id == loc.id }) {
            existing.map { if (it.id == loc.id) loc else it }
        } else existing + loc
        writeLocations(next)
    }

    suspend fun deleteLocation(id: String) {
        writeLocations(readLocationsBlocking().filterNot { it.id == id })
        // Jeśli usuwana była aktywna — zeruj wybór.
        context.dataStore.edit { prefs ->
            if (prefs[Keys.ActiveLocationId] == id) prefs.remove(Keys.ActiveLocationId)
        }
    }

    suspend fun setActiveLocation(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.ActiveLocationId)
            else prefs[Keys.ActiveLocationId] = id
        }
    }

    private suspend fun readLocationsBlocking(): List<SavedLocation> = savedLocations.first()

    private suspend fun writeLocations(list: List<SavedLocation>) {
        val raw = json.encodeToString(ListSerializer(SavedLocation.serializer()), list)
        context.dataStore.edit { it[Keys.SavedLocations] = raw }
    }

    // ── Historia lotów ──

    val flightLog: Flow<List<FlightLogEntry>> = context.dataStore.data.map { p ->
        val raw = p[Keys.FlightLog] ?: return@map emptyList<FlightLogEntry>()
        if (raw.isBlank()) emptyList() else runCatching {
            json.decodeFromString(ListSerializer(FlightLogEntry.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun addFlight(entry: FlightLogEntry) {
        val list = readFlightsBlocking()
        writeFlights(listOf(entry) + list) // najnowszy na górze
    }

    suspend fun updateFlightNote(id: String, note: String) {
        val list = readFlightsBlocking().map {
            if (it.id == id) it.copy(note = note) else it
        }
        writeFlights(list)
    }

    suspend fun deleteFlight(id: String) {
        writeFlights(readFlightsBlocking().filterNot { it.id == id })
    }

    private suspend fun readFlightsBlocking(): List<FlightLogEntry> = flightLog.first()

    private suspend fun writeFlights(list: List<FlightLogEntry>) {
        val raw = json.encodeToString(ListSerializer(FlightLogEntry.serializer()), list)
        context.dataStore.edit { it[Keys.FlightLog] = raw }
    }

    // ── Powiadomienia ──

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.NotificationsEnabled] == "1"
    }

    suspend fun setNotificationsEnabled(on: Boolean) {
        context.dataStore.edit { it[Keys.NotificationsEnabled] = if (on) "1" else "0" }
    }

    // ── Profile BSP ──

    val droneProfiles: Flow<List<DroneProfile>> = context.dataStore.data.map { p ->
        val raw = p[Keys.DroneProfiles] ?: return@map emptyList<DroneProfile>()
        if (raw.isBlank()) emptyList() else runCatching {
            json.decodeFromString(ListSerializer(DroneProfile.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    val activeProfileId: Flow<String?> = context.dataStore.data.map { p ->
        p[Keys.ActiveProfileId]?.takeIf { it.isNotBlank() }
    }

    suspend fun upsertProfile(profile: DroneProfile) {
        val existing = droneProfiles.first()
        val next = if (existing.any { it.id == profile.id }) {
            existing.map { if (it.id == profile.id) profile else it }
        } else existing + profile
        writeProfiles(next)
    }

    suspend fun deleteProfile(id: String) {
        writeProfiles(droneProfiles.first().filterNot { it.id == id })
        context.dataStore.edit { prefs ->
            if (prefs[Keys.ActiveProfileId] == id) prefs.remove(Keys.ActiveProfileId)
        }
    }

    suspend fun setActiveProfile(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.ActiveProfileId)
            else prefs[Keys.ActiveProfileId] = id
        }
    }

    private suspend fun writeProfiles(list: List<DroneProfile>) {
        val raw = json.encodeToString(ListSerializer(DroneProfile.serializer()), list)
        context.dataStore.edit { it[Keys.DroneProfiles] = raw }
    }

    // ── Monitoring (foreground service) ──

    val monitoringActive: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.MonitoringActive] == "1"
    }

    suspend fun setMonitoringActive(on: Boolean) {
        context.dataStore.edit { it[Keys.MonitoringActive] = if (on) "1" else "0" }
    }

    // ── Jednostki ──

    val displayUnits: Flow<DisplayUnits> = context.dataStore.data.map { p ->
        DisplayUnits(
            wind = runCatching { WindUnit.valueOf(p[Keys.WindUnitKey] ?: "MS") }.getOrDefault(WindUnit.MS),
            temp = runCatching { TempUnit.valueOf(p[Keys.TempUnitKey] ?: "C") }.getOrDefault(TempUnit.C),
        )
    }

    suspend fun setWindUnit(u: WindUnit) {
        context.dataStore.edit { it[Keys.WindUnitKey] = u.name }
    }

    suspend fun setTempUnit(u: TempUnit) {
        context.dataStore.edit { it[Keys.TempUnitKey] = u.name }
    }

    // ── Onboarding ──

    val onboardingSeen: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.OnboardingSeen] == "1"
    }

    suspend fun setOnboardingSeen() {
        context.dataStore.edit { it[Keys.OnboardingSeen] = "1" }
    }

    // ── Offline snapshot cache ──

    suspend fun getCachedSnapshot(): AggregatedSnapshot? {
        val raw = context.dataStore.data.first()[Keys.CachedSnapshot]
        if (raw.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(AggregatedSnapshot.serializer(), raw)
        }.getOrNull()
    }

    suspend fun setCachedSnapshot(snap: AggregatedSnapshot) {
        val raw = json.encodeToString(AggregatedSnapshot.serializer(), snap)
        context.dataStore.edit { it[Keys.CachedSnapshot] = raw }
    }

    // ── Widget cache ──

    data class WidgetSnapshot(
        val verdict: String,
        val tempC: String,
        val windMs: String,
        val kp: String,
        val location: String,
        val updatedAt: Long,
    )

    val widgetSnapshot: Flow<WidgetSnapshot> = context.dataStore.data.map { p ->
        WidgetSnapshot(
            verdict = p[Keys.WVerdict] ?: "—",
            tempC = p[Keys.WTempC] ?: "—",
            windMs = p[Keys.WWindMs] ?: "—",
            kp = p[Keys.WKp] ?: "—",
            location = p[Keys.WLocation] ?: "Brak danych",
            updatedAt = p[Keys.WUpdatedAt] ?: 0L,
        )
    }

    suspend fun setWidgetSnapshot(snap: WidgetSnapshot) {
        context.dataStore.edit {
            it[Keys.WVerdict] = snap.verdict
            it[Keys.WTempC] = snap.tempC
            it[Keys.WWindMs] = snap.windMs
            it[Keys.WKp] = snap.kp
            it[Keys.WLocation] = snap.location
            it[Keys.WUpdatedAt] = snap.updatedAt
        }
    }
}
