package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.ConditionCheck
import com.nexplay.dronepreflight.data.ConfidenceCalculator
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.formatWind
import com.nexplay.dronepreflight.data.tempIn
import com.nexplay.dronepreflight.data.windIn
import com.nexplay.dronepreflight.data.sources.kp.KpReading
import com.nexplay.dronepreflight.ui.AllChecklistIds
import com.nexplay.dronepreflight.ui.BestWindow
import com.nexplay.dronepreflight.ui.HourlyOutlook
import com.nexplay.dronepreflight.ui.UiState
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PulpitScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onDateSelected: (LocalDate) -> Unit = {},
    onStartMonitor: () -> Unit = {},
    onStopMonitor: () -> Unit = {},
    onStartFlight: () -> Unit = {},
    units: com.nexplay.dronepreflight.data.DisplayUnits = com.nexplay.dronepreflight.data.DisplayUnits(),
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LocationCard(
            snapshot = state.snapshot,
            selectedDate = state.selectedDate,
            loading = state.loading,
            onDateClick = { showDatePicker = true },
            onRefresh = onRefresh,
        )

        state.error?.let { ErrorCard(it) }

        if (state.isOfflineData && state.snapshot != null) {
            OfflineBanner(ageMinutes = ageMinutes(state.snapshot.fetchedAt))
        }

        if (state.snapshot != null) {
            WeatherMedianCard(state.snapshot, units)
            KpMedianCard(state.snapshot)
            VerdictCard(state, units)
            if (state.hourlyOutlook.isNotEmpty()) {
                Next3HoursCard(state.hourlyOutlook, units)
            }
            BestTimeCard(state.bestWindow, state.hourlyOutlook)
            if (state.hourlyOutlook.isNotEmpty()) {
                HourlyChartCard(state.hourlyOutlook, state.limits, units)
            }
            ChecklistProgressCard(checkedCount = state.checked.intersect(AllChecklistIds).size)
            CompassCard(windDirectionDeg = state.snapshot.windDir.median)
            MonitoringCard(
                active = state.monitoringActive,
                onStart = onStartMonitor,
                onStop = onStopMonitor,
            )
            StartFlightButton(
                enabled = state.assessment?.overall != Verdict.NO_GO,
                onClick = onStartFlight,
            )
        } else if (!state.loading) {
            EmptyState(onRefresh)
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showDatePicker) {
        DatePickerHost(
            initial = state.selectedDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                showDatePicker = false
                onDateSelected(it)
            },
        )
    }
}

// ── Karta lokalizacji ──

@Composable
private fun LocationCard(
    snapshot: AggregatedSnapshot?,
    selectedDate: LocalDate,
    loading: Boolean,
    onDateClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    val time = snapshot?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.fetchedAt))
    } ?: "—"
    OpsCard {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = OpsColors.Accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    snapshot?.locationName ?: "Lokalizacja",
                    style = MaterialTheme.typography.titleMedium,
                    color = OpsColors.TextPrimary,
                )
                Text(
                    "Aktualizacja: $time · ${formatDatePolish(selectedDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsColors.TextSecondary,
                )
            }
            IconButton(onClick = onDateClick) {
                Icon(Icons.Default.CalendarToday, contentDescription = "Zmień datę", tint = OpsColors.Accent)
            }
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Odśwież", tint = OpsColors.Accent)
                }
            }
        }
    }
}

// ── Karta pogoda-mediana z 5 źródłami ──

@Composable
private fun WeatherMedianCard(
    snap: AggregatedSnapshot,
    units: com.nexplay.dronepreflight.data.DisplayUnits,
) {
    val temp = snap.temp.median
    val cloud = snap.cloud.median
    val disagreement = snap.temp.stddev ?: 0.0
    val (badge, badgeColor) = when {
        disagreement < 1.0 -> "ROZBIEŻNOŚĆ NISKA" to VerdictColors.Go
        disagreement < 2.5 -> "ROZBIEŻNOŚĆ ŚREDNIA" to VerdictColors.Caution
        else -> "ROZBIEŻNOŚĆ WYSOKA" to VerdictColors.NoGo
    }
    OpsCard {
        Column(Modifier.padding(16.dp)) {
            CardHeader("POGODA — MEDIANA (${snap.successfulSources}/${snap.totalSources} ŹRÓDEŁ)")
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        com.nexplay.dronepreflight.data.formatTemp(temp, units.temp),
                        style = MaterialTheme.typography.displaySmall,
                        color = OpsColors.TextPrimary,
                    )
                    Text(
                        buildString {
                            append("Zachmurzenie ")
                            append(cloud?.let { "%.0f%%".format(it) } ?: "—")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OpsColors.TextSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    snap.readings.forEach { r ->
                        SourceLineTemp(r.source, r.tempC, units.temp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            WeatherMetricsRow(snap)
            Spacer(Modifier.height(10.dp))
            Badge(badge, badgeColor)
        }
    }
}

@Composable
private fun WeatherMetricsRow(snap: AggregatedSnapshot) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricMini(
            icon = Icons.Default.Cloud,
            label = "CHMURY",
            value = snap.cloud.median?.let { "%.0f%%".format(it) } ?: "—",
            modifier = Modifier.weight(1f),
        )
        MetricMini(
            icon = Icons.Default.WaterDrop,
            label = "WILGOTNOŚĆ",
            value = snap.humidity.median?.let { "%.0f%%".format(it) } ?: "—",
            modifier = Modifier.weight(1f),
        )
        MetricMini(
            icon = Icons.Default.Visibility,
            label = "WIDOCZ.",
            value = snap.visibility.median?.let {
                if (it >= 10000) "%.0f km".format(it / 1000)
                else "%.1f km".format(it / 1000)
            } ?: "—",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricMini(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanelRaised),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = OpsColors.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(value, color = OpsColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SourceLineTemp(
    source: String,
    value: Double?,
    unit: com.nexplay.dronepreflight.data.TempUnit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            shortSource(source),
            color = OpsColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            com.nexplay.dronepreflight.data.formatTemp(value, unit),
            color = OpsColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun shortSource(s: String): String = when {
    s.startsWith("Open-Meteo") -> "Open-Meteo"
    s.startsWith("MET") -> "MET Norway"
    s.startsWith("Bright Sky") -> "Bright Sky"
    s.startsWith("7Timer") -> "7Timer"
    s.startsWith("wttr") -> "wttr.in"
    s.startsWith("NOAA SWPC (planetary)") -> "NOAA planetary"
    s.startsWith("NOAA SWPC (forecast)") -> "NOAA forecast"
    s.startsWith("NOAA SWPC (1-min") -> "NOAA 1-min"
    s.startsWith("NOAA SWPC (G-scale)") -> "NOAA G-scale"
    s.startsWith("GFZ") -> "GFZ Potsdam"
    else -> s
}

// ── Karta KP z półkolistym gauge ──

@Composable
private fun KpMedianCard(snap: AggregatedSnapshot) {
    val kp = snap.kpIndex
    val kpLevel = when {
        kp == null -> "BRAK DANYCH" to OpsColors.TextSecondary
        kp < 4 -> "AKTYWNOŚĆ NISKA" to VerdictColors.Go
        kp < 5 -> "AKTYWNOŚĆ ŚREDNIA" to VerdictColors.Caution
        else -> "AKTYWNOŚĆ WYSOKA" to VerdictColors.NoGo
    }
    OpsCard {
        Column(Modifier.padding(16.dp)) {
            CardHeader("KP INDEX — MEDIANA (${snap.kpReadings.size}/${snap.kpTotalSources} ŹRÓDEŁ)")
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    KpGauge(kp ?: 0.0, modifier = Modifier.size(width = 140.dp, height = 90.dp))
                    Text(
                        kp?.let { "%.2f".format(it).replace('.', ',') } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        color = OpsColors.TextPrimary,
                    )
                    Text(
                        kpLevel.first,
                        color = kpLevel.second,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    snap.kpReadings.forEach { r -> KpSourceLine(r) }
                    snap.kpFailures.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(shortSource(f.source), color = OpsColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(8.dp))
                            Text("—", color = OpsColors.Danger, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(8.dp))
                            Dot(OpsColors.Danger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpSourceLine(r: KpReading) {
    val color = when {
        r.value < 4 -> VerdictColors.Go
        r.value < 5 -> VerdictColors.Caution
        else -> VerdictColors.NoGo
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(shortSource(r.source), color = OpsColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Text("%.1f".format(r.value).replace('.', ','), color = OpsColors.TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Dot(color)
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
}

/** Półkolisty wskaźnik KP 0-9. */
@Composable
private fun KpGauge(value: Double, modifier: Modifier = Modifier) {
    val clamped = value.coerceIn(0.0, 9.0)
    val fraction = clamped / 9.0
    Canvas(modifier) {
        val stroke = 12f
        val w = size.width
        val h = size.height
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(w - stroke, (h - stroke / 2) * 2)
        // Tło łuku
        drawArc(
            color = OpsColors.Grid,
            startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Kolor wypełnienia zależny od wartości
        val fillColor = when {
            clamped < 4 -> VerdictColors.Go
            clamped < 5 -> VerdictColors.Caution
            else -> VerdictColors.NoGo
        }
        drawArc(
            color = fillColor,
            startAngle = 180f,
            sweepAngle = (180f * fraction).toFloat(),
            useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

// ── Karta OCENA (rozbudowana: WHY? + Confidence) ──

@Composable
private fun VerdictCard(state: UiState, units: DisplayUnits) {
    val assessment = state.assessment ?: return
    val overall = assessment.overall
    val (label, color, sub) = when (overall) {
        Verdict.GO -> Triple("GO", VerdictColors.Go, "Warunki dobre do lotu")
        Verdict.CAUTION -> Triple("OSTROŻNIE", VerdictColors.Caution, "Warunki graniczne — sprawdź szczegóły")
        Verdict.NO_GO -> Triple("NO-GO", VerdictColors.NoGo, "Nie lataj — warunki poza limitami")
    }
    val confidence = state.snapshot?.let { ConfidenceCalculator.calculate(it) }
    val problems = assessment.checks.filter { it.verdict != Verdict.GO }
    val allOk = assessment.checks.filter { it.verdict == Verdict.GO }

    OpsCard {
        Column(Modifier.padding(16.dp)) {
            CardHeader("OCENA DLA TWOJEGO BSP")
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = color, style = MaterialTheme.typography.displaySmall)
                    Text(sub, color = OpsColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                confidence?.let { c ->
                    ConfidenceBadge(c)
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = OpsColors.Grid)
            Spacer(Modifier.height(12.dp))

            Text(
                "DLACZEGO ${label}?",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(8.dp))

            if (problems.isNotEmpty()) {
                problems.forEach { CheckDetailRow(it) }
                if (allOk.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "✓ Bez zastrzeżeń: " + allOk.joinToString(", ") { it.label.lowercase() },
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else {
                allOk.forEach { CheckDetailRow(it) }
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(c: ConfidenceCalculator.Confidence) {
    val col = when {
        c.percent >= 85 -> VerdictColors.Go
        c.percent >= 60 -> VerdictColors.Caution
        else -> VerdictColors.NoGo
    }
    Column(horizontalAlignment = Alignment.End) {
        Text("PEWNOŚĆ", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(
            "${c.percent}%",
            color = col,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Box(
            Modifier
                .background(col.copy(alpha = 0.18f), MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(c.label, color = col, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CheckDetailRow(check: ConditionCheck) {
    val rowColor = when (check.verdict) {
        Verdict.GO -> VerdictColors.Go
        Verdict.CAUTION -> VerdictColors.Caution
        Verdict.NO_GO -> VerdictColors.NoGo
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(rowColor, RoundedCornerShape(5.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    check.label,
                    color = OpsColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    check.value,
                    color = rowColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            check.note?.let {
                Text(it, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── Karta NAJBLIŻSZE 3H ──

@Composable
private fun Next3HoursCard(outlook: List<HourlyOutlook>, units: DisplayUnits) {
    val next = outlook.take(4)
    if (next.isEmpty()) return
    OpsCard {
        Column(Modifier.padding(16.dp)) {
            CardHeader("NAJBLIŻSZE GODZINY")
            Spacer(Modifier.height(10.dp))
            next.forEachIndexed { i, hour ->
                val vColor = when (hour.verdict) {
                    Verdict.GO -> VerdictColors.Go
                    Verdict.CAUTION -> VerdictColors.Caution
                    Verdict.NO_GO -> VerdictColors.NoGo
                }
                val vLabel = when (hour.verdict) {
                    Verdict.GO -> "GO"
                    Verdict.CAUTION -> "OSTROŻNIE"
                    Verdict.NO_GO -> "NO-GO"
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (i == 0) "TERAZ" else "+${i}h",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(
                        "%02d:00".format(hour.timeLocal.hour),
                        color = OpsColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(56.dp),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .background(vColor.copy(alpha = 0.18f), MaterialTheme.shapes.small)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            vLabel,
                            color = vColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        hour.windMs?.let { formatWind(it, units.wind) } ?: "—",
                        color = OpsColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(70.dp),
                    )
                }
            }
        }
    }
}

// ── Karta najlepsza pora ──

@Composable
private fun BestTimeCard(best: BestWindow?, outlook: List<HourlyOutlook>) {
    OpsCard {
        Column(Modifier.padding(16.dp)) {
            CardHeader("NAJLEPSZA PORA (KOLEJNE 48H)")
            Spacer(Modifier.height(10.dp))
            if (best != null) {
                Text(
                    "%s %02d:00 – %02d:00".format(
                        if (best.startLocal.date == outlook.firstOrNull()?.timeLocal?.date) "Dzisiaj" else "Jutro",
                        best.startLocal.hour,
                        (best.endLocal.hour + 1) % 24,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = VerdictColors.Go,
                )
                Text(
                    "Stabilne warunki, ${best.hours} h okna GO",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Brak okna ≥2 h GO w kolejnych 48h",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            HourStrip(outlook)
        }
    }
}

@Composable
private fun HourStrip(outlook: List<HourlyOutlook>) {
    if (outlook.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(20.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        outlook.forEach { h ->
            val c = when (h.verdict) {
                Verdict.GO -> VerdictColors.Go
                Verdict.CAUTION -> VerdictColors.Caution
                Verdict.NO_GO -> VerdictColors.NoGo
            }
            Box(Modifier.weight(1f).fillMaxHeight().background(c, RoundedCornerShape(2.dp)))
        }
    }
}

// ── Karta checklista progress ──

@Composable
private fun ChecklistProgressCard(checkedCount: Int) {
    val total = AllChecklistIds.size
    val fraction = if (total == 0) 0f else checkedCount / total.toFloat()
    OpsCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CHECKLISTA PANSA (${total})",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$checkedCount / $total",
                    color = OpsColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = VerdictColors.Go,
                trackColor = OpsColors.Grid,
            )
        }
    }
}

@Composable
private fun HourlyChartCard(
    outlook: List<com.nexplay.dronepreflight.ui.HourlyOutlook>,
    limits: com.nexplay.dronepreflight.data.DroneLimits,
    units: com.nexplay.dronepreflight.data.DisplayUnits,
) {
    OpsCard {
        Column(Modifier.padding(16.dp)) {
            CardHeader("WYKRES 48H · WIATR I TEMP")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(Color(0xFF34D399))
                Spacer(Modifier.width(4.dp))
                Text("wiatr ${units.wind.short}", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(14.dp))
                LegendDot(Color(0xFFF59E0B))
                Spacer(Modifier.width(4.dp))
                Text("temp ${units.temp.short}", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(10.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                if (outlook.isEmpty()) return@Canvas
                val padL = 30f
                val padR = 30f
                val padT = 8f
                val padB = 18f
                val plotW = size.width - padL - padR
                val plotH = size.height - padT - padB

                // Osie — konwersja do wybranych jednostek
                val winds = outlook.mapNotNull { it.windMs?.windIn(units.wind) }
                val temps = outlook.mapNotNull { it.tempC?.tempIn(units.temp) }
                val windMax = maxOf(
                    winds.maxOrNull() ?: 10.0,
                    limits.maxWindMs.windIn(units.wind),
                    5.0,
                )
                val windMin = 0.0
                val tempMax = temps.maxOrNull() ?: 25.0
                val tempMin = temps.minOrNull() ?: 0.0
                val tempRange = (tempMax - tempMin).takeIf { it > 1.0 } ?: 5.0
                val limitWindConverted = limits.maxWindMs.windIn(units.wind)

                // Kolorowe strefy tła — pod krzywą wg verdict
                val cellW = plotW / outlook.size
                outlook.forEachIndexed { i, h ->
                    val color = when (h.verdict) {
                        com.nexplay.dronepreflight.data.Verdict.GO -> Color(0xFF14532D)
                        com.nexplay.dronepreflight.data.Verdict.CAUTION -> Color(0xFF854D0E)
                        com.nexplay.dronepreflight.data.Verdict.NO_GO -> Color(0xFF7F1D1D)
                    }
                    drawRect(
                        color = color.copy(alpha = 0.25f),
                        topLeft = androidx.compose.ui.geometry.Offset(padL + i * cellW, padT),
                        size = androidx.compose.ui.geometry.Size(cellW + 0.5f, plotH),
                    )
                }

                // Linia limitu wiatru
                val yLimitWind = padT + plotH - ((limitWindConverted - windMin) / (windMax - windMin) * plotH).toFloat()
                drawLine(
                    color = Color(0xFF34D399).copy(alpha = 0.4f),
                    start = androidx.compose.ui.geometry.Offset(padL, yLimitWind),
                    end = androidx.compose.ui.geometry.Offset(padL + plotW, yLimitWind),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )

                // Krzywa wiatru
                val windPath = androidx.compose.ui.graphics.Path()
                var firstWind = true
                outlook.forEachIndexed { i, h ->
                    val w = h.windMs?.windIn(units.wind)
                        ?: return@forEachIndexed
                    val x = padL + i * cellW + cellW / 2f
                    val y = padT + plotH - ((w - windMin) / (windMax - windMin) * plotH).toFloat()
                    if (firstWind) { windPath.moveTo(x, y); firstWind = false }
                    else windPath.lineTo(x, y)
                }
                drawPath(
                    path = windPath,
                    color = Color(0xFF34D399),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )

                // Krzywa temperatury
                val tempPath = androidx.compose.ui.graphics.Path()
                var firstTemp = true
                outlook.forEachIndexed { i, h ->
                    val t = h.tempC?.tempIn(units.temp)
                        ?: return@forEachIndexed
                    val x = padL + i * cellW + cellW / 2f
                    val y = padT + plotH - ((t - tempMin) / tempRange * plotH).toFloat()
                    if (firstTemp) { tempPath.moveTo(x, y); firstTemp = false }
                    else tempPath.lineTo(x, y)
                }
                drawPath(
                    path = tempPath,
                    color = Color(0xFFF59E0B),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )

                // Etykiety osi X (co 12h)
                val labelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#94A3B8")
                    textSize = 22f
                    isAntiAlias = true
                }
                val hoursLabels = listOf(0, 12, 24, 36, 47)
                hoursLabels.forEach { idx ->
                    if (idx >= outlook.size) return@forEach
                    val h = outlook[idx]
                    val x = padL + idx * cellW + cellW / 2f
                    val txt = "%02d".format(h.timeLocal.hour)
                    drawContext.canvas.nativeCanvas.drawText(txt, x - 12f, size.height - 4f, labelPaint)
                }

                // Etykiety Y (wiatr lewa, temp prawa)
                val leftPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#34D399")
                    textSize = 22f; isAntiAlias = true
                }
                val rightPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#F59E0B")
                    textSize = 22f; isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f".format(windMax), 2f, padT + 12f, leftPaint,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f".format(tempMax), size.width - padR + 4f, padT + 12f, rightPaint,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f".format(tempMin), size.width - padR + 4f, size.height - padB, rightPaint,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .background(color, MaterialTheme.shapes.small)
    )
}

@Composable
private fun MonitoringCard(active: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    OpsCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            if (active) VerdictColors.Go else OpsColors.TextSecondary,
                            RoundedCornerShape(5.dp),
                        )
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (active) "MONITORING AKTYWNY" else "MONITORING W TRAKCIE LOTU",
                        color = if (active) VerdictColors.Go else OpsColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        if (active)
                            "Sprawdzam warunki co 5 min. Alarm gdy pojawi się burza lub silne porywy."
                        else
                            "Włącz przed lotem. Trwałe powiadomienie + alarm gdy warunki się pogorszą.",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (active) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = VerdictColors.NoGo),
                ) { Text("Zakończ monitoring") }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = VerdictColors.Go),
                ) { Text("Rozpocznij monitoring") }
            }
        }
    }
}

@Composable
private fun StartFlightButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VerdictColors.Go,
            disabledContainerColor = OpsColors.BgPanel,
            disabledContentColor = OpsColors.TextSecondary,
        ),
    ) {
        Text(
            if (enabled) "START LOTU" else "NO-GO — nie lataj",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun OfflineBanner(ageMinutes: Long) {
    val ageStr = when {
        ageMinutes < 60 -> "${ageMinutes} min temu"
        ageMinutes < 60 * 24 -> "${ageMinutes / 60} h temu"
        else -> "${ageMinutes / (60 * 24)} d temu"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.Amber.copy(alpha = 0.18f)),
        border = BorderStroke(1.dp, OpsColors.Amber),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⚠", color = OpsColors.Amber, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "TRYB OFFLINE",
                    color = OpsColors.Amber,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    "Brak połączenia — pokazuję dane sprzed $ageStr. Kalendarz i wykres godzinowy niedostępne.",
                    color = OpsColors.TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun ageMinutes(timestamp: Long): Long {
    val diffMs = (System.currentTimeMillis() - timestamp).coerceAtLeast(0)
    return diffMs / 60_000
}

// ── Helpers ──

@Composable
private fun OpsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun CardHeader(text: String) {
    Text(
        text,
        color = OpsColors.TextSecondary,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.18f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ErrorCard(msg: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = VerdictColors.NoGo.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, VerdictColors.NoGo),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Błąd: $msg",
            modifier = Modifier.padding(16.dp),
            color = VerdictColors.NoGo,
        )
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    OpsCard {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Brak danych pogodowych",
                style = MaterialTheme.typography.titleMedium,
                color = OpsColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Kliknij poniżej, żeby pobrać z 5 źródeł.",
                style = MaterialTheme.typography.bodyMedium,
                color = OpsColors.TextSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Odśwież pogodę")
            }
        }
    }
}

// ── Data picker (reuse Material 3) ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerHost(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val startOfDayMs = kotlinx.datetime.LocalDateTime(initial, kotlinx.datetime.LocalTime(0, 0))
        .toInstant(kotlinx.datetime.TimeZone.UTC)
        .toEpochMilliseconds()
    val state = rememberDatePickerState(initialSelectedDateMillis = startOfDayMs)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) {
                    val d = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
                        .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
                    onConfirm(d)
                } else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    ) { DatePicker(state = state) }
}

private fun formatDatePolish(date: LocalDate): String {
    val today = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    val diff = date.toEpochDays() - today.toEpochDays()
    val label = when (diff) {
        0 -> "Dzisiaj"
        1 -> "Jutro"
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
