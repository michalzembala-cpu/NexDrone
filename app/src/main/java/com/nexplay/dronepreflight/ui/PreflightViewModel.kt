package com.nexplay.dronepreflight.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.DroneLimits
import com.nexplay.dronepreflight.data.DroneProfile
import com.nexplay.dronepreflight.data.TempUnit
import com.nexplay.dronepreflight.data.WindUnit
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.data.FlightAssessor
import com.nexplay.dronepreflight.data.FlightLogEntry
import com.nexplay.dronepreflight.data.HourlyScorer
import com.nexplay.dronepreflight.data.LocationProvider
import com.nexplay.dronepreflight.data.SavedLocation
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.WeatherRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class UiState(
    val loading: Boolean = false,
    val snapshot: AggregatedSnapshot? = null,
    val assessment: FlightAssessment? = null,
    val limits: DroneLimits = DroneLimits(),
    val checked: Set<String> = emptySet(),
    val error: String? = null,
    val selectedDate: LocalDate = today(),
    val selectedHour: Int = defaultHourFor(today()),
    val targetInstant: Instant = Clock.System.now(),
    val bestWindow: BestWindow? = null,
    val hourlyOutlook: List<HourlyOutlook> = emptyList(),
    /** 24 kafelki na wybrany dzień (dane z Open-Meteo, dla szybkiego przeglądu). */
    val dailyTiles: List<HourTile> = emptyList(),
    /** Godzina której kafelek jest rozwinięty w UI (null = wszystkie zwinięte). */
    val expandedHour: Int? = null,
    // Wielokrotne lokalizacje
    val savedLocations: List<SavedLocation> = emptyList(),
    val activeLocationId: String? = null,
    // Historia lotów
    val flightLog: List<FlightLogEntry> = emptyList(),
    // Powiadomienia
    val notificationsEnabled: Boolean = false,
    // Monitoring w trakcie lotu
    val monitoringActive: Boolean = false,
    // Profile BSP
    val droneProfiles: List<DroneProfile> = emptyList(),
    val activeProfileId: String? = null,
    // Offline
    val isOfflineData: Boolean = false,
    // Onboarding
    val onboardingSeen: Boolean = true,   // domyślnie true żeby nie mrugnąć onboardingiem zanim się załaduje flaga
    // Jednostki
    val units: DisplayUnits = DisplayUnits(),
    // Pinezka na mapie — jeśli ustawiona, override na aktywną lokalizację
    val pinnedCoords: Pair<Double, Double>? = null,
    val pinnedName: String? = null,
)

data class BestWindow(
    val startLocal: LocalDateTime,
    val endLocal: LocalDateTime,
    val hours: Int,
)

data class HourlyOutlook(
    val timeLocal: LocalDateTime,
    val verdict: Verdict,
    val tempC: Double? = null,
    val windMs: Double? = null,
)

data class HourTile(
    val hour: Int,               // 0..23
    val tempC: Double?,
    val windMs: Double?,
    val precipMm: Double?,
    val verdict: Verdict,
    val isPast: Boolean,          // godzina już minęła (tylko dla dziś)
)

private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun defaultHourFor(date: LocalDate): Int {
    val nowLocal = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return if (date == nowLocal.date) nowLocal.hour else 12
}

private fun instantFor(date: LocalDate, hour: Int): Instant {
    val tz = TimeZone.currentSystemDefault()
    return LocalDateTime(date, LocalTime(hour.coerceIn(0, 23), 0)).toInstant(tz)
}

class PreflightViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WeatherRepository()
    private val location = LocationProvider(app)
    private val settings = SettingsStore(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.limits.collect { l -> _state.value = _state.value.copy(limits = l) }
        }
        viewModelScope.launch {
            settings.checkedIds.collect { c -> _state.value = _state.value.copy(checked = c) }
        }
        viewModelScope.launch {
            settings.savedLocations.collect { locs ->
                _state.value = _state.value.copy(savedLocations = locs)
            }
        }
        viewModelScope.launch {
            settings.activeLocationId.collect { id ->
                _state.value = _state.value.copy(activeLocationId = id)
            }
        }
        viewModelScope.launch {
            settings.flightLog.collect { list ->
                _state.value = _state.value.copy(flightLog = list)
            }
        }
        viewModelScope.launch {
            settings.notificationsEnabled.collect { on ->
                _state.value = _state.value.copy(notificationsEnabled = on)
            }
        }
        viewModelScope.launch {
            settings.monitoringActive.collect { on ->
                val actuallyRunning = com.nexplay.dronepreflight.monitor.StormMonitorService.isRunning
                if (on && !actuallyRunning) {
                    com.nexplay.dronepreflight.monitor.StormMonitorService.start(getApplication())
                }
                _state.value = _state.value.copy(monitoringActive = on || actuallyRunning)
            }
        }
        viewModelScope.launch {
            settings.droneProfiles.collect { p ->
                _state.value = _state.value.copy(droneProfiles = p)
            }
        }
        viewModelScope.launch {
            settings.activeProfileId.collect { id ->
                _state.value = _state.value.copy(activeProfileId = id)
            }
        }
        viewModelScope.launch {
            settings.onboardingSeen.collect { seen ->
                _state.value = _state.value.copy(onboardingSeen = seen)
            }
        }
        viewModelScope.launch {
            settings.displayUnits.collect { u ->
                _state.value = _state.value.copy(units = u)
            }
        }
    }

    fun markOnboardingDone() {
        viewModelScope.launch { settings.setOnboardingSeen() }
    }

    fun setWindUnit(u: WindUnit) {
        viewModelScope.launch { settings.setWindUnit(u) }
    }

    fun setTempUnit(u: TempUnit) {
        viewModelScope.launch { settings.setTempUnit(u) }
    }

    // ── Pinezka na mapie ──

    fun setPinnedLocation(lat: Double, lon: Double) {
        _state.value = _state.value.copy(
            pinnedCoords = lat to lon,
            pinnedName = "Pinezka %.4f, %.4f".format(lat, lon),
            expandedHour = null,
        )
        refresh()
    }

    fun clearPinnedLocation() {
        _state.value = _state.value.copy(pinnedCoords = null, pinnedName = null)
        refresh()
    }

    // ── Backup / Restore ──

    suspend fun exportBackup(): android.net.Uri =
        com.nexplay.dronepreflight.backup.BackupManager.exportToFile(getApplication(), settings)

    suspend fun importBackup(uri: android.net.Uri): Result<Unit> =
        com.nexplay.dronepreflight.backup.BackupManager.importFromUri(
            getApplication(), uri, settings,
        ).map { }

    fun setSelectedDate(date: LocalDate) {
        val hour = defaultHourFor(date)
        _state.value = _state.value.copy(
            selectedDate = date,
            selectedHour = hour,
            targetInstant = instantFor(date, hour),
            expandedHour = null,   // po zmianie dnia — wszystkie kafelki zwinięte
        )
        refresh()
    }

    fun setSelectedHour(hour: Int) {
        val date = _state.value.selectedDate
        _state.value = _state.value.copy(
            selectedHour = hour,
            targetInstant = instantFor(date, hour),
        )
        refresh()
    }

    /** Klik na kafelek: rozwiń go (lub zwiń jeśli już rozwinięty) i pobierz 5-źródeł dla tej godziny. */
    fun toggleTile(hour: Int) {
        val currentlyExpanded = _state.value.expandedHour
        if (currentlyExpanded == hour) {
            _state.value = _state.value.copy(expandedHour = null)
        } else {
            _state.value = _state.value.copy(expandedHour = hour)
            setSelectedHour(hour)
        }
    }

    fun refresh(fallbackLat: Double = 52.2297, fallbackLon: Double = 21.0122) {
        val date = _state.value.selectedDate
        val hour = _state.value.selectedHour
        val target = instantFor(date, hour)
        _state.value = _state.value.copy(loading = true, error = null, targetInstant = target)
        viewModelScope.launch {
            try {
                // 1) pinezka z mapy  2) aktywna zapisana lokalizacja  3) GPS  4) fallback Warszawa
                val pinned = _state.value.pinnedCoords
                val active = _state.value.savedLocations.firstOrNull {
                    it.id == _state.value.activeLocationId
                }
                val (lat, lon, name) = when {
                    pinned != null -> Triple(
                        pinned.first, pinned.second,
                        _state.value.pinnedName ?: "Pinezka na mapie",
                    )
                    active != null -> Triple(active.lat, active.lon, active.name)
                    else -> {
                        val loc = location.current()
                        if (loc != null) Triple(loc.lat, loc.lon, "Twoja lokalizacja")
                        else Triple(fallbackLat, fallbackLon, "Warszawa (fallback)")
                    }
                }

                val limits = settings.limits.first()

                // Równolegle: pełny fetch 5-źródeł + scan Open-Meteo (godziny + kafelki).
                val (snap, hourly) = coroutineScope {
                    val snapJob = async { repo.fetch(lat, lon, name, target) }
                    val scanJob = async { repo.fetchHourlyScan(lat, lon) }
                    snapJob.await() to scanJob.await()
                }

                val assess = FlightAssessor.assess(snap, limits)
                syncAutoChecks(assess)

                val outlook = buildOutlook(hourly, limits, snap.kpIndex)
                val best = longestGoWindow(outlook, minHours = 2)
                val tiles = buildDailyTiles(hourly, date, limits, snap.kpIndex)

                _state.value = _state.value.copy(
                    loading = false,
                    snapshot = snap,
                    assessment = assess,
                    error = null,
                    hourlyOutlook = outlook,
                    bestWindow = best,
                    dailyTiles = tiles,
                    isOfflineData = false,
                )

                // Zapisz snapshot do cache (offline fallback)
                settings.setCachedSnapshot(snap)

                // Cache dla widgetu + odśwież glance + wyślij na zegarek
                val currentUnits = _state.value.units
                val widgetSnap = SettingsStore.WidgetSnapshot(
                    verdict = assess.overall.name,
                    tempC = com.nexplay.dronepreflight.data.formatTemp(snap.temp.median, currentUnits.temp),
                    windMs = com.nexplay.dronepreflight.data.formatWind(snap.wind.median, currentUnits.wind),
                    kp = snap.kpIndex?.let { "%.1f".format(it) } ?: "—",
                    location = snap.locationName,
                    updatedAt = snap.fetchedAt,
                )
                settings.setWidgetSnapshot(widgetSnap)
                com.nexplay.dronepreflight.widget.NexDroneWidget.updateAll(getApplication())
                com.nexplay.dronepreflight.wear.WearPublisher.publish(getApplication(), widgetSnap)
            } catch (t: Throwable) {
                // Spróbuj załadować cached snapshot (offline mode)
                val cached = runCatching { settings.getCachedSnapshot() }.getOrNull()
                if (cached != null) {
                    val limits = _state.value.limits
                    val assess = FlightAssessor.assess(cached, limits)
                    _state.value = _state.value.copy(
                        loading = false,
                        snapshot = cached,
                        assessment = assess,
                        error = null,
                        isOfflineData = true,
                        hourlyOutlook = emptyList(),
                        bestWindow = null,
                        dailyTiles = emptyList(),
                    )
                } else {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = t.message ?: "Błąd pobierania pogody",
                        isOfflineData = false,
                    )
                }
            }
        }
    }

    private fun buildOutlook(
        hourly: List<Pair<Instant, com.nexplay.dronepreflight.data.sources.SourceReading>>,
        limits: DroneLimits,
        kp: Double?,
    ): List<HourlyOutlook> {
        if (hourly.isEmpty()) return emptyList()
        val tz = TimeZone.currentSystemDefault()
        val nowMs = Clock.System.now().toEpochMilliseconds()
        return hourly.filter { it.first.toEpochMilliseconds() >= nowMs }
            .take(48)
            .map { (time, reading) ->
                HourlyOutlook(
                    timeLocal = time.toLocalDateTime(tz),
                    verdict = HourlyScorer.score(reading, limits, kp),
                    tempC = reading.tempC,
                    windMs = reading.windMs,
                )
            }
    }

    private fun buildDailyTiles(
        hourly: List<Pair<Instant, com.nexplay.dronepreflight.data.sources.SourceReading>>,
        date: LocalDate,
        limits: DroneLimits,
        kp: Double?,
    ): List<HourTile> {
        if (hourly.isEmpty()) return emptyList()
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz)
        // Grupuj po godzinie (lokalnie), wybieramy pierwszy wpis dla każdej godziny wybranego dnia.
        val byHour = hourly
            .map { (t, r) -> t.toLocalDateTime(tz) to r }
            .filter { (dt, _) -> dt.date == date }
            .associateBy { it.first.hour }
        return (0..23).map { h ->
            val entry = byHour[h]
            val reading = entry?.second
            val verdict = if (reading != null) HourlyScorer.score(reading, limits, kp) else Verdict.CAUTION
            val isPast = date < now.date || (date == now.date && h < now.hour)
            HourTile(
                hour = h,
                tempC = reading?.tempC,
                windMs = reading?.windMs,
                precipMm = reading?.precipMm,
                verdict = verdict,
                isPast = isPast,
            )
        }
    }

    private fun longestGoWindow(outlook: List<HourlyOutlook>, minHours: Int): BestWindow? {
        var bestStart = -1
        var bestLen = 0
        var curStart = -1
        var curLen = 0
        outlook.forEachIndexed { i, h ->
            if (h.verdict == Verdict.GO) {
                if (curStart == -1) curStart = i
                curLen++
                if (curLen > bestLen) {
                    bestLen = curLen
                    bestStart = curStart
                }
            } else {
                curStart = -1
                curLen = 0
            }
        }
        if (bestLen < minHours || bestStart < 0) return null
        val startEntry = outlook[bestStart]
        val endEntry = outlook[bestStart + bestLen - 1]
        return BestWindow(startEntry.timeLocal, endEntry.timeLocal, bestLen)
    }

    private suspend fun syncAutoChecks(assess: FlightAssessment) {
        val goodByLabel = assess.checks.associate { it.label to (it.verdict == Verdict.GO) }
        val autoGood = buildSet {
            if (goodByLabel["Wiatr"] == true) add("wind")
            if (goodByLabel["Temperatura"] == true) add("temp")
            if (goodByLabel["KP index"] == true) add("kp")
            if (goodByLabel["Opady"] == true) add("precip")
            if (goodByLabel["Warunki atmosferyczne"] == true) add("storm")
        }
        val manual = _state.value.checked - AutoWeatherIds
        settings.setChecked(manual + autoGood)
    }

    fun toggleChecklistItem(id: String, checked: Boolean) {
        if (id in AutoWeatherIds) return
        viewModelScope.launch {
            val current = _state.value.checked.toMutableSet()
            if (checked) current += id else current -= id
            settings.setChecked(current)
        }
    }

    fun resetChecklist() {
        viewModelScope.launch { settings.setChecked(emptySet()) }
    }

    fun saveLimits(limits: DroneLimits) {
        viewModelScope.launch {
            settings.setLimits(limits)
            _state.value.snapshot?.let { snap ->
                _state.value = _state.value.copy(
                    assessment = FlightAssessor.assess(snap, limits),
                )
            }
        }
    }

    // ── Wielokrotne lokalizacje ──

    fun saveLocation(name: String, lat: Double, lon: Double) {
        val id = "loc_${System.currentTimeMillis()}"
        viewModelScope.launch {
            settings.upsertLocation(SavedLocation(id, name, lat, lon))
        }
    }

    fun deleteLocation(id: String) {
        viewModelScope.launch { settings.deleteLocation(id) }
    }

    fun setActiveLocation(id: String?) {
        viewModelScope.launch {
            settings.setActiveLocation(id)
            refresh()
        }
    }

    // ── Historia lotów ──

    fun saveCurrentFlight(note: String, durationMinutes: Int?) {
        val snap = _state.value.snapshot ?: return
        val assess = _state.value.assessment ?: return
        viewModelScope.launch {
            settings.addFlight(
                FlightLogEntry(
                    id = "flt_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    locationName = snap.locationName,
                    verdict = assess.overall.name,
                    tempC = snap.temp.median,
                    windMs = snap.wind.median,
                    gustMs = snap.gust.median,
                    kpIndex = snap.kpIndex,
                    note = note,
                    durationMinutes = durationMinutes,
                )
            )
        }
    }

    fun updateFlightNote(id: String, note: String) {
        viewModelScope.launch { settings.updateFlightNote(id, note) }
    }

    fun deleteFlight(id: String) {
        viewModelScope.launch { settings.deleteFlight(id) }
    }

    // ── Powiadomienia ──

    fun setNotificationsEnabled(on: Boolean) {
        viewModelScope.launch { settings.setNotificationsEnabled(on) }
    }

    // ── Profile BSP ──

    fun saveProfile(profile: DroneProfile) {
        viewModelScope.launch {
            settings.upsertProfile(profile)
            if (profile.id == _state.value.activeProfileId) {
                _state.value.snapshot?.let { snap ->
                    _state.value = _state.value.copy(
                        assessment = FlightAssessor.assess(snap, profile.toLimits()),
                    )
                }
            }
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch { settings.deleteProfile(id) }
    }

    fun setActiveProfile(id: String?) {
        viewModelScope.launch {
            settings.setActiveProfile(id)
            refresh()
        }
    }
}
