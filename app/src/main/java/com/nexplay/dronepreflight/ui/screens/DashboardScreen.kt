package com.nexplay.dronepreflight.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.ConditionCheck
import com.nexplay.dronepreflight.data.DroneLimits
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.data.FlightAssessor
import com.nexplay.dronepreflight.data.SourceFailure
import com.nexplay.dronepreflight.data.SourceStat
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.WeatherAggregator
import com.nexplay.dronepreflight.data.sources.SourceReading
import com.nexplay.dronepreflight.data.windDirectionCardinal
import com.nexplay.dronepreflight.ui.BestWindow
import com.nexplay.dronepreflight.ui.HourTile
import com.nexplay.dronepreflight.ui.HourlyOutlook
import com.nexplay.dronepreflight.ui.UiState
import com.nexplay.dronepreflight.ui.theme.DronePreflightTheme
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onDateSelected: (LocalDate) -> Unit = {},
    onHourSelected: (Int) -> Unit = {},
    units: com.nexplay.dronepreflight.data.DisplayUnits = com.nexplay.dronepreflight.data.DisplayUnits(),
) {
    // Jeżeli wybrana godzina — pokazujemy ekran szczegółów. Inaczej lista kafelków.
    if (state.expandedHour != null) {
        HourDetailView(
            state = state,
            hour = state.expandedHour,
            units = units,
            onBack = { onHourSelected(state.expandedHour) },
        )
    } else {
        DashboardListView(
            state = state,
            onRefresh = onRefresh,
            onDateSelected = onDateSelected,
            onHourSelected = onHourSelected,
            units = units,
        )
    }
}

@Composable
private fun DashboardListView(
    state: UiState,
    onRefresh: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onHourSelected: (Int) -> Unit,
    units: com.nexplay.dronepreflight.data.DisplayUnits,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp),
    ) {
        // Nagłówek
        Text(
            "Kalendarz",
            style = MaterialTheme.typography.headlineMedium,
            color = OpsColors.TextPrimary,
        )
        Text(
            "Wybierz dzień",
            style = MaterialTheme.typography.bodyMedium,
            color = OpsColors.TextSecondary,
        )

        Spacer(Modifier.height(12.dp))

        // Chipy dni tygodnia (dzisiaj + 7 kolejnych)
        DaySelector(
            selected = state.selectedDate,
            onSelect = onDateSelected,
        )

        Spacer(Modifier.height(12.dp))

        // Data długim opisem
        Text(
            longPolishDate(state.selectedDate),
            style = MaterialTheme.typography.titleMedium,
            color = OpsColors.TextPrimary,
        )
        Text(
            "Prognoza godzinowa (24h)",
            style = MaterialTheme.typography.bodySmall,
            color = OpsColors.TextSecondary,
        )

        Spacer(Modifier.height(10.dp))

        state.error?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = VerdictColors.NoGo),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Błąd: $it", Modifier.padding(12.dp), color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
        }

        if (state.loading && state.dailyTiles.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Pobieranie…", color = OpsColors.TextSecondary)
                }
            }
        }

        // Grid 4×6 kafelków godzinowych — zajmuje resztę ekranu
        if (state.dailyTiles.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                gridItems(state.dailyTiles) { tile ->
                    HourTileGrid(tile = tile, units = units, onClick = { onHourSelected(tile.hour) })
                }
            }
            Spacer(Modifier.height(8.dp))
            VerdictLegend()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DaySelector(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val days = (0..7).map { today.plus(it, kotlinx.datetime.DateTimeUnit.DAY) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(days) { d ->
            DayChip(
                date = d,
                selected = d == selected,
                onClick = { onSelect(d) },
            )
        }
    }
}

@Composable
private fun DayChip(date: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) VerdictColors.Go else OpsColors.BgPanel
    val fg = if (selected) OpsColors.BgBase else OpsColors.TextPrimary
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, if (selected) VerdictColors.Go else OpsColors.Grid),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .width(48.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                dayOfWeekShort(date),
                style = MaterialTheme.typography.labelSmall,
                color = fg,
            )
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.titleMedium,
                color = fg,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HourTileGrid(
    tile: HourTile,
    units: com.nexplay.dronepreflight.data.DisplayUnits,
    onClick: () -> Unit,
) {
    val isNight = tile.hour < 6 || tile.hour >= 21
    val bg = when {
        tile.isPast -> Color(0xFF1A2333)
        isNight -> Color(0xFF1E2A44) // NOC — ciemny navy
        tile.verdict == Verdict.GO -> Color(0xFF14532D)      // green-800
        tile.verdict == Verdict.CAUTION -> Color(0xFF854D0E) // amber-800
        tile.verdict == Verdict.NO_GO -> Color(0xFF7F1D1D)   // red-800
        else -> OpsColors.BgPanel
    }
    val border = when {
        tile.isPast -> OpsColors.Grid
        isNight -> Color(0xFF3B4B70)
        tile.verdict == Verdict.GO -> VerdictColors.Go
        tile.verdict == Verdict.CAUTION -> VerdictColors.Caution
        tile.verdict == Verdict.NO_GO -> VerdictColors.NoGo
        else -> OpsColors.Grid
    }
    val alpha = if (tile.isPast) 0.5f else 1f
    val temp = tile.tempC?.let {
        com.nexplay.dronepreflight.data.formatTemp(it, units.temp, decimals = 0)
    } ?: "—"
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, border.copy(alpha = 0.7f * alpha)),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .height(96.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "%02d:00".format(tile.hour),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = alpha),
            )
            Text(
                weatherEmoji(tile),
                fontSize = 24.sp,
            )
            Text(
                temp,
                color = Color.White.copy(alpha = alpha),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun VerdictLegend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendItem(Color(0xFF14532D), "GO", Modifier.weight(1f))
        LegendItem(Color(0xFF854D0E), "OSTROŻNIE", Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendItem(Color(0xFF7F1D1D), "NO-GO", Modifier.weight(1f))
        LegendItem(Color(0xFF1E2A44), "NOC", Modifier.weight(1f))
    }
}

@Composable
private fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

private fun dayOfWeekShort(date: LocalDate): String {
    return when (date.dayOfWeek) {
        kotlinx.datetime.DayOfWeek.MONDAY -> "Pn"
        kotlinx.datetime.DayOfWeek.TUESDAY -> "Wt"
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Śr"
        kotlinx.datetime.DayOfWeek.THURSDAY -> "Czw"
        kotlinx.datetime.DayOfWeek.FRIDAY -> "Pt"
        kotlinx.datetime.DayOfWeek.SATURDAY -> "Sob"
        kotlinx.datetime.DayOfWeek.SUNDAY -> "Ndz"
        else -> "?"
    }
}

private fun longPolishDate(date: LocalDate): String {
    val dow = when (date.dayOfWeek) {
        kotlinx.datetime.DayOfWeek.MONDAY -> "Poniedziałek"
        kotlinx.datetime.DayOfWeek.TUESDAY -> "Wtorek"
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> "Środa"
        kotlinx.datetime.DayOfWeek.THURSDAY -> "Czwartek"
        kotlinx.datetime.DayOfWeek.FRIDAY -> "Piątek"
        kotlinx.datetime.DayOfWeek.SATURDAY -> "Sobota"
        kotlinx.datetime.DayOfWeek.SUNDAY -> "Niedziela"
        else -> ""
    }
    val month = when (date.monthNumber) {
        1 -> "stycznia"; 2 -> "lutego"; 3 -> "marca"; 4 -> "kwietnia"
        5 -> "maja"; 6 -> "czerwca"; 7 -> "lipca"; 8 -> "sierpnia"
        9 -> "września"; 10 -> "października"; 11 -> "listopada"; 12 -> "grudnia"
        else -> ""
    }
    return "$dow, ${date.dayOfMonth} $month ${date.year}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HourDetailView(
    state: UiState,
    hour: Int,
    units: com.nexplay.dronepreflight.data.DisplayUnits,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Szczegóły godziny") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                }
            },
        )
        if (state.loading && state.snapshot == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Pobieranie z 5 źródeł…")
                }
            }
            return@Column
        }
        val snap = state.snapshot ?: return@Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HourWeatherCard(snap, hour, state.selectedDate, units)
            HourWindCard(snap, units)
            HourKpCard(snap)
            HourAssessmentCard(state.assessment, state.limits, snap, units)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Karta 1: godzina + pogoda ──

@Composable
private fun HourWeatherCard(
    snap: AggregatedSnapshot,
    hour: Int,
    date: LocalDate,
    units: com.nexplay.dronepreflight.data.DisplayUnits,
) {
    OpsDetailCard {
        Row(Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "%02d:00".format(hour),
                    style = MaterialTheme.typography.titleLarge,
                    color = OpsColors.TextPrimary,
                )
                Text(
                    longPolishDate(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(weatherEmojiForHour(snap, hour), fontSize = 44.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        com.nexplay.dronepreflight.data.formatTemp(snap.temp.median, units.temp, decimals = 0),
                        style = MaterialTheme.typography.displaySmall,
                        color = OpsColors.TextPrimary,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Zachmurzenie ${snap.cloud.median?.let { "%.0f%%".format(it) } ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsColors.TextSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                snap.readings.forEach { r ->
                    Text(
                        shortSourceName(r.source),
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        com.nexplay.dronepreflight.data.formatTemp(r.tempC, units.temp),
                        color = OpsColors.TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        MedianRow(
            label = "MEDIANA",
            value = com.nexplay.dronepreflight.data.formatTemp(snap.temp.median, units.temp),
            spread = agreementBadge(snap.temp.stddev, hot = 2.5),
        )
    }
}

// ── Karta 2: wiatr ──

@Composable
private fun HourWindCard(
    snap: AggregatedSnapshot,
    units: com.nexplay.dronepreflight.data.DisplayUnits,
) {
    OpsDetailCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                "WIATR",
                style = MaterialTheme.typography.labelMedium,
                color = OpsColors.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.Air,
                        contentDescription = null,
                        tint = OpsColors.Accent,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        com.nexplay.dronepreflight.data.formatWind(snap.wind.median, units.wind),
                        style = MaterialTheme.typography.headlineMedium,
                        color = OpsColors.TextPrimary,
                    )
                    val dir = snap.windDir.median
                    Text(
                        dir?.let {
                            "%s (%.0f°)".format(windDirectionCardinal(it), it)
                        } ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OpsColors.TextSecondary,
                    )
                    snap.gust.median?.let {
                        Text(
                            "Porywy " + com.nexplay.dronepreflight.data.formatWind(it, units.wind),
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsColors.TextSecondary,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    snap.readings.forEach { r ->
                        Text(
                            shortSourceName(r.source),
                            color = OpsColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            com.nexplay.dronepreflight.data.formatWind(r.windMs, units.wind),
                            color = OpsColors.TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        MedianRow(
            label = "MEDIANA",
            value = com.nexplay.dronepreflight.data.formatWind(snap.wind.median, units.wind),
            spread = agreementBadge(snap.wind.stddev, hot = 2.5),
        )
    }
}

// ── Karta 3: KP INDEX ──

@Composable
private fun HourKpCard(snap: AggregatedSnapshot) {
    val kp = snap.kpIndex
    val level = when {
        kp == null -> "BRAK DANYCH" to OpsColors.TextSecondary
        kp < 4 -> "AKTYWNOŚĆ NISKA" to VerdictColors.Go
        kp < 5 -> "AKTYWNOŚĆ ŚREDNIA" to VerdictColors.Caution
        else -> "AKTYWNOŚĆ WYSOKA" to VerdictColors.NoGo
    }
    OpsDetailCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                "KP INDEX",
                style = MaterialTheme.typography.labelMedium,
                color = OpsColors.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    KpMiniGauge(kp ?: 0.0)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        kp?.let { "%.2f".format(it).replace('.', ',') } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        color = OpsColors.TextPrimary,
                    )
                    Text(
                        level.first,
                        color = level.second,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    snap.kpReadings.forEach { r ->
                        val c = when {
                            r.value < 4 -> VerdictColors.Go
                            r.value < 5 -> VerdictColors.Caution
                            else -> VerdictColors.NoGo
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                shortSourceName(r.source),
                                color = OpsColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "%.1f".format(r.value).replace('.', ','),
                                color = OpsColors.TextPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(8.dp).background(c, RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
        }
        MedianRow(
            label = "MEDIANA",
            value = kp?.let { "%.2f".format(it).replace('.', ',') } ?: "—",
            spread = null,
        )
    }
}

@Composable
private fun KpMiniGauge(value: Double) {
    val clamped = value.coerceIn(0.0, 9.0)
    val fraction = clamped / 9.0
    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(width = 120.dp, height = 60.dp),
    ) {
        val stroke = 10f
        val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, (size.height - stroke / 2) * 2)
        drawArc(
            color = OpsColors.Grid,
            startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        val fill = when {
            clamped < 4 -> VerdictColors.Go
            clamped < 5 -> VerdictColors.Caution
            else -> VerdictColors.NoGo
        }
        drawArc(
            color = fill,
            startAngle = 180f, sweepAngle = (180f * fraction).toFloat(), useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

// ── Karta 4: ocena z listą warunków ──

@Composable
private fun HourAssessmentCard(
    assessment: FlightAssessment?,
    limits: DroneLimits,
    snap: AggregatedSnapshot,
    units: com.nexplay.dronepreflight.data.DisplayUnits,
) {
    val (label, color) = when (assessment?.overall) {
        Verdict.GO -> "GO" to VerdictColors.Go
        Verdict.CAUTION -> "OSTROŻNIE" to VerdictColors.Caution
        Verdict.NO_GO -> "NO-GO" to VerdictColors.NoGo
        null -> "—" to OpsColors.TextSecondary
    }
    OpsDetailCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                "OCENA DLA TWOJEGO BSP",
                style = MaterialTheme.typography.labelMedium,
                color = OpsColors.TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                label,
                color = color,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(12.dp))
            // Pokazujemy WSZYSTKIE warunki które assessor sprawdził — inaczej user widzi
            // 4 zielone ✓ i NO-GO obok, bo brakuje burza/mgła/widzialność.
            assessment?.checks?.forEach { check ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ckColor = when (check.verdict) {
                        Verdict.GO -> VerdictColors.Go
                        Verdict.CAUTION -> VerdictColors.Caution
                        Verdict.NO_GO -> VerdictColors.NoGo
                    }
                    Icon(
                        when (check.verdict) {
                            Verdict.GO -> androidx.compose.material.icons.Icons.Default.CheckCircle
                            else -> androidx.compose.material.icons.Icons.Default.Cancel
                        },
                        contentDescription = null,
                        tint = ckColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            check.label,
                            color = OpsColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            check.value,
                            color = ckColor,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers dla detail view ──

@Composable
private fun OpsDetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun MedianRow(label: String, value: String, spread: Pair<String, Color>?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = OpsColors.TextSecondary)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = OpsColors.TextPrimary)
        Spacer(Modifier.weight(1f))
        spread?.let { (label, color) ->
            Text("ROZBIEŻNOŚĆ", style = MaterialTheme.typography.labelSmall, color = OpsColors.TextSecondary)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

private fun agreementBadge(stddev: Double?, hot: Double): Pair<String, Color>? {
    if (stddev == null) return null
    return when {
        stddev < hot * 0.3 -> "NISKA" to VerdictColors.Go
        stddev < hot -> "ŚREDNIA" to VerdictColors.Caution
        else -> "WYSOKA" to VerdictColors.NoGo
    }
}

private fun shortSourceName(source: String): String = when {
    source.startsWith("Open-Meteo") -> "Open-Meteo"
    source.startsWith("MET") -> "MET Norway"
    source.startsWith("Bright Sky") -> "Bright Sky"
    source.startsWith("7Timer") -> "7Timer"
    source.startsWith("wttr") -> "wttr.in"
    source.startsWith("NOAA SWPC (planetary)") -> "NOAA planetary"
    source.startsWith("NOAA SWPC (forecast)") -> "NOAA forecast"
    source.startsWith("NOAA SWPC (1-min") -> "NOAA 1-min"
    source.startsWith("NOAA SWPC (G-scale)") -> "NOAA G-scale"
    source.startsWith("GFZ") -> "GFZ Potsdam"
    else -> source
}

private fun weatherEmojiForHour(snap: AggregatedSnapshot, hour: Int): String {
    val precip = snap.precip.median ?: 0.0
    val stormAlert = snap.stormVotes > 0
    val fog = snap.fogVotes > 0
    val isNight = hour < 6 || hour >= 21
    return when {
        stormAlert -> "⛈"
        precip > 5 -> "🌧"
        precip > 0.5 -> "🌦"
        fog -> "🌫"
        isNight -> "🌙"
        (snap.cloud.median ?: 0.0) > 75 -> "☁"
        (snap.cloud.median ?: 0.0) > 30 -> "⛅"
        else -> "☀"
    }
}

@Composable
private fun HourTileRow(
    tile: HourTile,
    onClick: () -> Unit,
) {
    val verdictColor = when (tile.verdict) {
        Verdict.GO -> VerdictColors.Go
        Verdict.CAUTION -> VerdictColors.Caution
        Verdict.NO_GO -> VerdictColors.NoGo
    }
    val temp = tile.tempC?.let { "%.1f°C".format(it) } ?: "—"
    val wind = tile.windMs?.let { "%.1f m/s".format(it) } ?: "—"
    val alpha = if (tile.isPast) 0.45f else 1.0f
    Card(
        colors = CardDefaults.cardColors(
            containerColor = OpsColors.BgPanel.copy(alpha = if (tile.isPast) 0.6f else 1f),
        ),
        border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.5f * alpha)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Weather emoji (statusowy)
            Text(
                weatherEmoji(tile),
                fontSize = 22.sp,
                modifier = Modifier.width(36.dp),
            )
            // Godzina — monospace
            Text(
                "%02d:00".format(tile.hour),
                color = OpsColors.TextPrimary.copy(alpha = alpha),
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 20.sp,
                modifier = Modifier.width(72.dp),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricBlock("TEMP", temp, alpha)
                    Spacer(Modifier.width(12.dp))
                    MetricBlock("WIATR", wind, alpha)
                }
                if ((tile.precipMm ?: 0.0) > 0) {
                    Text(
                        "opad %.1f mm".format(tile.precipMm),
                        color = OpsColors.Amber.copy(alpha = alpha),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // Werdykt — pigułka koloru
            Box(
                Modifier
                    .background(verdictColor.copy(alpha = alpha), MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    if (tile.isPast) "—" else verdictLabel(tile.verdict),
                    color = OpsColors.BgBase,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, alpha: Float = 1f) {
    Column {
        Text(
            label,
            color = OpsColors.TextSecondary.copy(alpha = alpha),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            value,
            color = OpsColors.TextPrimary.copy(alpha = alpha),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun weatherEmoji(tile: HourTile): String {
    val precip = tile.precipMm ?: 0.0
    val hour = tile.hour
    val isNight = hour < 6 || hour >= 21
    return when {
        precip > 5 -> "⛈"
        precip > 0.5 -> "🌧"
        precip > 0 -> "🌦"
        isNight -> "🌙"
        else -> "☀"
    }
}

private fun verdictLabel(v: Verdict): String = when (v) {
    Verdict.GO -> "GO"
    Verdict.CAUTION -> "OSTROŻNIE"
    Verdict.NO_GO -> "NO-GO"
}


@Composable
private fun VerdictHeader(
    assessment: FlightAssessment?,
    snap: AggregatedSnapshot?,
    selectedDate: LocalDate,
) {
    val (label, color) = when (assessment?.overall) {
        Verdict.GO -> "GO · warunki OK" to VerdictColors.Go
        Verdict.CAUTION -> "OSTROŻNIE · zwróć uwagę" to VerdictColors.Caution
        Verdict.NO_GO -> "NO-GO · nie lataj" to VerdictColors.NoGo
        null -> "Brak danych" to MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanelRaised),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(color, RoundedCornerShape(7.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "OCENA DLA TWOJEGO BSP",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                color = color,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Dla: ${formatDate(selectedDate)}",
                color = OpsColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            snap?.let {
                Text(
                    "${it.successfulSources}/${it.totalSources} źródeł odpowiedziało",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun BestWindowCard(best: BestWindow?, outlook: List<HourlyOutlook>) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = VerdictColors.Go)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Najlepsza pora (48 h)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (best != null) {
                            "%s %02d:00 – %02d:00 (%d h GO)".format(
                                dayShort(best.startLocal.date),
                                best.startLocal.hour,
                                (best.endLocal.hour + 1) % 24,
                                best.hours,
                            )
                        } else "Brak okna ≥2 h z werdyktem GO w najbliższych 48 h",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            HourlyStrip(outlook)
        }
    }
}

@Composable
private fun HourlyStrip(outlook: List<HourlyOutlook>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        outlook.forEach { h ->
            val c = when (h.verdict) {
                Verdict.GO -> VerdictColors.Go
                Verdict.CAUTION -> VerdictColors.Caution
                Verdict.NO_GO -> VerdictColors.NoGo
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(c, RoundedCornerShape(2.dp))
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val first = outlook.firstOrNull()?.timeLocal
        val last = outlook.lastOrNull()?.timeLocal
        if (first != null && last != null) {
            Text(
                "%s %02d:00".format(dayShort(first.date), first.hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "%s %02d:00".format(dayShort(last.date), last.hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun dayShort(date: LocalDate): String {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return when (date.toEpochDays() - today.toEpochDays()) {
        0 -> "Dziś"
        1 -> "Jutro"
        2 -> "Pjut"
        else -> "%d.%d".format(date.dayOfMonth, date.monthNumber)
    }
}

@Composable
private fun DatePickerCard(
    selectedDate: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanelRaised),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = OpsColors.Accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("DATA LOTU", style = MaterialTheme.typography.labelSmall, color = OpsColors.TextSecondary)
                Text(
                    formatDate(selectedDate),
                    style = MaterialTheme.typography.titleMedium,
                    color = OpsColors.TextPrimary,
                )
            }
            Text("Zmień", color = OpsColors.Accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    // Konwersja LocalDate → millis (00:00 UTC) bez java.time (min-SDK 24).
    val startOfDayMs = kotlinx.datetime.LocalDateTime(initial, kotlinx.datetime.LocalTime(0, 0))
        .toInstant(TimeZone.UTC)
        .toEpochMilliseconds()
    val state = rememberDatePickerState(initialSelectedDateMillis = startOfDayMs)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) {
                    val d = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
                        .toLocalDateTime(TimeZone.UTC).date
                    onConfirm(d)
                } else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    ) {
        DatePicker(state = state)
    }
}

private fun formatDate(date: LocalDate): String {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val diff = date.toEpochDays() - today.toEpochDays()
    val label = when (diff) {
        0 -> "Dzisiaj"
        1 -> "Jutro"
        2 -> "Pojutrze"
        -1 -> "Wczoraj"
        else -> null
    }
    val month = when (date.monthNumber) {
        1 -> "sty"; 2 -> "lut"; 3 -> "mar"; 4 -> "kwi"
        5 -> "maj"; 6 -> "cze"; 7 -> "lip"; 8 -> "sie"
        9 -> "wrz"; 10 -> "paź"; 11 -> "lis"; 12 -> "gru"
        else -> ""
    }
    val d = "${date.dayOfMonth} $month ${date.year}"
    return if (label != null) "$label · $d" else d
}

@Composable
private fun LocationInfoCard(s: AggregatedSnapshot) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(s.fetchedAt))
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(s.locationName, style = MaterialTheme.typography.titleMedium)
            Text(
                "%.4f, %.4f · pobrano o %s".format(s.latitude, s.longitude, time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CheckRow(check: ConditionCheck) {
    val color = when (check.verdict) {
        Verdict.GO -> VerdictColors.Go
        Verdict.CAUTION -> VerdictColors.Caution
        Verdict.NO_GO -> VerdictColors.NoGo
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(check.label, style = MaterialTheme.typography.titleSmall)
                Text(check.value, style = MaterialTheme.typography.bodyMedium)
                check.note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                check.agreement?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcesSection(snap: AggregatedSnapshot) {
    var expanded by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Źródła danych", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${snap.successfulSources}/${snap.totalSources} odpowiedziało" +
                            if (snap.failures.isNotEmpty()) " · ${snap.failures.size} błąd" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
                HorizontalDivider(Modifier.padding(bottom = 8.dp))
                Text(
                    "Pogoda (${snap.successfulSources}/${snap.totalSources})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                snap.readings.forEach { r -> SourceRow(r) }
                snap.failures.forEach { f -> SourceFailureRow(f) }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(Modifier.padding(bottom = 8.dp))
                Text(
                    "KP index (${snap.kpReadings.size}/${snap.kpTotalSources})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                snap.kpReadings.forEach { k ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("✓ ", color = VerdictColors.Go)
                        Column {
                            Text(k.source, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Kp = %.1f".format(k.value),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                snap.kpFailures.forEach { f -> SourceFailureRow(f) }
            }
        }
    }
}

@Composable
private fun SourceFailureRow(f: SourceFailure) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("✗ ", color = VerdictColors.NoGo)
        Column {
            Text(f.source, style = MaterialTheme.typography.labelMedium)
            Text(
                f.reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceRow(r: SourceReading) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓ ", color = VerdictColors.Go)
            Text(r.source, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        val bits = buildList {
            r.windMs?.let { add("wiatr %.1f m/s".format(it)) }
            r.windDirDeg?.let { add(windDirectionCardinal(it)) }
            r.tempC?.let { add("%.1f°C".format(it)) }
            r.precipMm?.let { if (it > 0) add("opad %.1f mm".format(it)) }
            r.cloudPct?.let { add("chmury %.0f%%".format(it)) }
            r.visibilityM?.let { add("widz. %.0f m".format(it)) }
            r.condition?.let { add(it) }
            if (r.isStorm) add("BURZA")
            if (r.isFog) add("mgła")
        }
        Text(
            bits.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Podglądy ──

private fun previewSnapshot(overrides: Map<String, SourceReading> = emptyMap()): AggregatedSnapshot {
    val base = listOf(
        SourceReading("Open-Meteo (ICON/GFS)", 6.2, 9.1, 245.0, 18.4, 0.0, 40.0, 15000.0, condition = "Częściowe zachmurzenie"),
        SourceReading("MET Norway", 6.5, 9.5, 250.0, 18.1, 0.0, 45.0, null, condition = "Częściowe zachmurzenie"),
        SourceReading("wttr.in", 5.8, null, 240.0, 18.0, 0.0, 38.0, 10000.0, condition = "Partly cloudy"),
        SourceReading("Bright Sky (DWD)", 6.0, 8.8, 248.0, 18.6, 0.0, 42.0, 20000.0, condition = "Sucho"),
        SourceReading("7Timer!", 6.7, null, 225.0, 18.0, 0.0, 37.0, null, condition = null),
    ).map { overrides[it.source] ?: it }
    return WeatherAggregator.aggregate(
        locationName = "Warszawa (podgląd)",
        lat = 52.2297, lon = 21.0122,
        readings = base,
        failures = emptyList(),
        totalSources = 5,
        kpReadings = listOf(
            com.nexplay.dronepreflight.data.sources.kp.KpReading("NOAA SWPC (planetary)", 3.0),
            com.nexplay.dronepreflight.data.sources.kp.KpReading("GFZ Potsdam (nowcast)", 2.7),
            com.nexplay.dronepreflight.data.sources.kp.KpReading("NOAA SWPC (forecast)", 3.3),
        ),
        kpFailures = emptyList(),
        kpTotalSources = 5,
    )
}

@Preview(showBackground = true, name = "Dashboard – GO (5/5)")
@Composable
private fun DashboardPreviewGo() = DronePreflightTheme {
    val snap = previewSnapshot()
    DashboardScreen(
        state = UiState(
            snapshot = snap,
            assessment = FlightAssessor.assess(snap, DroneLimits()),
            limits = DroneLimits(),
        ),
        onRefresh = {},
    )
}

@Preview(showBackground = true, name = "Dashboard – NO-GO (burza)")
@Composable
private fun DashboardPreviewNoGo() = DronePreflightTheme {
    val stormy = mapOf(
        "Open-Meteo (ICON/GFS)" to SourceReading("Open-Meteo (ICON/GFS)", 14.0, 20.0, 200.0, 18.0, 3.0, 90.0, 4000.0, condition = "Burza", isStorm = true),
        "MET Norway" to SourceReading("MET Norway", 15.0, 22.0, 205.0, 17.5, 4.0, 95.0, null, condition = "Burza", isStorm = true),
    )
    val snap = previewSnapshot(stormy).copy(
        failures = listOf(SourceFailure("Bright Sky (DWD)", "Timeout after 10s")),
    )
    DashboardScreen(
        state = UiState(
            snapshot = snap,
            assessment = FlightAssessor.assess(snap, DroneLimits()),
            limits = DroneLimits(),
        ),
        onRefresh = {},
    )
}
