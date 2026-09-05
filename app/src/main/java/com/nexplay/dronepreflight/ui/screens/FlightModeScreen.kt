package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import com.nexplay.dronepreflight.copilot.AiCopilot
import com.nexplay.dronepreflight.copilot.CopilotSpeaker
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.TempUnit
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.WindUnit
import com.nexplay.dronepreflight.data.tempIn
import com.nexplay.dronepreflight.data.windIn
import com.nexplay.dronepreflight.data.windDirectionCardinal
import kotlinx.coroutines.flow.first
import com.nexplay.dronepreflight.ui.HourlyOutlook
import com.nexplay.dronepreflight.ui.UiState
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import kotlinx.coroutines.delay

@Composable
fun FlightModeScreen(
    state: UiState,
    units: DisplayUnits,
    onDismiss: () -> Unit,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit,
    onRefresh: () -> Unit = {},
    onSaveFlight: (note: String, minutes: Int?) -> Unit = { _, _ -> },
) {
    val startedAt = rememberSaveable { System.currentTimeMillis() }
    var elapsedMs by remember { mutableStateOf(0L) }
    var showSummary by remember { mutableStateOf(false) }

    // Post-flight sampling: co sekundę zliczaj werdykt + trackuj max/min
    var goSecs by remember { mutableStateOf(0) }
    var cautSecs by remember { mutableStateOf(0) }
    var noGoSecs by remember { mutableStateOf(0) }
    var maxWind by remember { mutableStateOf(0.0) }
    var maxGust by remember { mutableStateOf(0.0) }
    var minVis by remember { mutableStateOf(Double.MAX_VALUE) }
    // Historia wiatru dla wykresu — 1 próbka co 10 sek, keep ostatnie 180 = 30 min
    val windHistory = remember { mutableStateListOf<Double>() }

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            elapsedMs = System.currentTimeMillis() - startedAt
            when (state.assessment?.overall) {
                Verdict.GO -> goSecs++
                Verdict.CAUTION -> cautSecs++
                Verdict.NO_GO -> noGoSecs++
                null -> {}
            }
            state.snapshot?.wind?.median?.let { if (it > maxWind) maxWind = it }
            state.snapshot?.gust?.median?.let { if (it > maxGust) maxGust = it }
            state.snapshot?.visibility?.median?.let { if (it < minVis) minVis = it }
            // Wykres — 1 próbka co 10 sek
            if (tick % 10 == 0) {
                windHistory += (state.snapshot?.wind?.median ?: 0.0)
                if (windHistory.size > 180) windHistory.removeAt(0)
            }
            tick++
            delay(1000)
        }
    }

    // Auto-refresh podczas Flight Mode — co 3 min pobierz świeże dane
    LaunchedEffect(Unit) {
        while (true) {
            delay(3 * 60 * 1000L)
            onRefresh()
        }
    }

    if (showSummary) {
        // AI Co-pilot — podsumowanie głosowe (raz, przy pierwszym pokazaniu)
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            val store = SettingsStore(context)
            if (store.assistantEnabled.first()) {
                CopilotSpeaker.init(context)
                val total = (goSecs + cautSecs + noGoSecs).coerceAtLeast(1)
                val msg = AiCopilot.postFlight(
                    pilotName = store.pilotName.first(),
                    elapsedSec = (elapsedMs / 1000).toInt(),
                    maxWindMs = maxWind.takeIf { it > 0.0 },
                    maxGustMs = maxGust.takeIf { it > 0.0 },
                    units = units,
                    goPct = (goSecs * 100 / total),
                    outlook = emptyList<HourlyOutlook>(),
                )
                CopilotSpeaker.say(msg.text)
            }
        }
        FlightSummaryDialog(
            elapsedMs = elapsedMs,
            goSecs = goSecs,
            cautSecs = cautSecs,
            noGoSecs = noGoSecs,
            maxWindMs = maxWind.takeIf { it > 0.0 },
            maxGustMs = maxGust.takeIf { it > 0.0 },
            minVisibilityM = minVis.takeIf { it < Double.MAX_VALUE },
            units = units,
            onSave = { note ->
                val minutes = ((elapsedMs / 1000 / 60).toInt()).coerceAtLeast(1)
                onSaveFlight(note, minutes)
                showSummary = false
                onDismiss()
            },
            onDismiss = {
                showSummary = false
                onDismiss()
            },
        )
        return
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp),
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlightHeader(state, elapsedMs, onDismiss)

                val snap = state.snapshot
                if (snap == null) {
                    Text(
                        "Brak danych — poczekaj na fetch",
                        color = OpsColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 40.dp),
                    )
                    return@Column
                }

                val windUnitStr = units.wind.short
                val tempUnitStr = units.temp.short

                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BigMetric(
                        label = "WIATR",
                        value = snap.wind.median?.let { "%.1f".format(it.windIn(units.wind)) } ?: "—",
                        unit = windUnitStr,
                        sub = windDirectionCardinal(snap.windDir.median),
                        color = windColor(snap.wind.median, state),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    BigMetric(
                        label = "PORYWY",
                        value = snap.gust.median?.let { "%.1f".format(it.windIn(units.wind)) } ?: "—",
                        unit = windUnitStr,
                        sub = "max 3s",
                        color = windColor(snap.gust.median, state),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BigMetric(
                        label = "TEMP",
                        value = snap.temp.median?.let { "%.1f".format(it.tempIn(units.temp)) } ?: "—",
                        unit = tempUnitStr,
                        sub = "med. z ${snap.temp.count} źr.",
                        color = OpsColors.Accent,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    val vis = snap.visibility.median
                    BigMetric(
                        label = "WIDOCZ.",
                        value = vis?.let {
                            if (it >= 10000) "%.0f".format(it / 1000)
                            else "%.1f".format(it / 1000)
                        } ?: "—",
                        unit = "km",
                        sub = "VLOS",
                        color = if ((vis ?: 10_000.0) < 2000) VerdictColors.Caution else OpsColors.Accent,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                MonitorStrip(
                    active = state.monitoringActive,
                    onStart = onStartMonitor,
                    onStop = onStopMonitor,
                )

                if (windHistory.size >= 2) {
                    WindHistoryChart(
                        history = windHistory.toList(),
                        limit = state.limits.maxWindMs.windIn(units.wind),
                        unitLabel = units.wind.short,
                        unitConverter = { it.windIn(units.wind) },
                    )
                }

                Button(
                    onClick = { showSummary = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerdictColors.NoGo),
                ) {
                    Text("ZAKOŃCZ LOT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FlightHeader(state: UiState, elapsedMs: Long, onDismiss: () -> Unit) {
    val overall = state.assessment?.overall
    val (label, color) = when (overall) {
        Verdict.GO -> "GO" to VerdictColors.Go
        Verdict.CAUTION -> "OSTROŻNIE" to VerdictColors.Caution
        Verdict.NO_GO -> "NO-GO" to VerdictColors.NoGo
        null -> "—" to OpsColors.TextSecondary
    }
    val secs = (elapsedMs / 1000).toInt()
    val hh = secs / 3600
    val mm = (secs % 3600) / 60
    val ss = secs % 60
    val elapsedText = if (hh > 0) "%d:%02d:%02d".format(hh, mm, ss) else "%d:%02d".format(mm, ss)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .background(color.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(label, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("CZAS LOTU", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(
                elapsedText,
                color = OpsColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Zamknij", tint = OpsColors.TextSecondary)
        }
    }
}

@Composable
private fun BigMetric(
    label: String,
    value: String,
    unit: String,
    sub: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(OpsColors.BgPanel, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    value,
                    color = color,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(sub, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MonitorStrip(active: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(OpsColors.BgPanel, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(
                    if (active) VerdictColors.Go else OpsColors.TextSecondary,
                    RoundedCornerShape(5.dp),
                )
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (active) "STORM MONITOR AKTYWNY" else "STORM MONITOR WYŁĄCZONY",
            color = if (active) VerdictColors.Go else OpsColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = if (active) onStop else onStart) {
            Text(if (active) "STOP" else "START", color = OpsColors.Accent)
        }
    }
}

@Composable
private fun WindHistoryChart(
    history: List<Double>,
    limit: Double,
    unitLabel: String,
    unitConverter: (Double) -> Double,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(OpsColors.BgPanel, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "WIATR PODCZAS LOTU",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${history.size * 10}s · limit ${"%.1f".format(limit)} $unitLabel",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(80.dp),
        ) {
            if (history.isEmpty()) return@Canvas
            val values = history.map { unitConverter(it) }
            val yMax = maxOf(values.max(), limit) * 1.1
            val yMin = 0.0
            val w = size.width
            val h = size.height
            val stepX = w / (history.size - 1).coerceAtLeast(1)

            // Linia limitu — kreska pozioma
            val yLimit = (h - ((limit - yMin) / (yMax - yMin) * h).toFloat()).coerceIn(0f, h)
            drawLine(
                color = VerdictColors.Caution,
                start = Offset(0f, yLimit),
                end = Offset(w, yLimit),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )

            // Krzywa wiatru
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = i * stepX
                val y = (h - ((v - yMin) / (yMax - yMin) * h).toFloat()).coerceIn(0f, h)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = VerdictColors.Go,
                style = Stroke(width = 3f),
            )
        }
    }
}

private fun windColor(value: Double?, state: UiState): Color {
    if (value == null) return OpsColors.TextSecondary
    val limit = state.limits.maxWindMs
    return when {
        value >= limit -> VerdictColors.NoGo
        value >= limit * 0.75 -> VerdictColors.Caution
        else -> VerdictColors.Go
    }
}

// ── PODSUMOWANIE LOTU ──

@Composable
private fun FlightSummaryDialog(
    elapsedMs: Long,
    goSecs: Int,
    cautSecs: Int,
    noGoSecs: Int,
    maxWindMs: Double?,
    maxGustMs: Double?,
    minVisibilityM: Double?,
    units: DisplayUnits,
    onSave: (note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val secs = (elapsedMs / 1000).toInt()
    val hh = secs / 3600
    val mm = (secs % 3600) / 60
    val ss = secs % 60
    val durText = if (hh > 0) "%d:%02d:%02d".format(hh, mm, ss) else "%d:%02d".format(mm, ss)

    val total = (goSecs + cautSecs + noGoSecs).coerceAtLeast(1)
    val goPct = (goSecs * 100f / total).toInt()
    val cautPct = (cautSecs * 100f / total).toInt()
    val noGoPct = (noGoSecs * 100f / total).toInt()

    var note by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "LOT ZAKOŃCZONY",
                    color = VerdictColors.Go,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    durText,
                    color = OpsColors.TextPrimary,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Czas trwania lotu",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = OpsColors.Grid)
                Spacer(Modifier.height(4.dp))

                // Max/min metryki
                SummaryRow("MAX WIATR", maxWindMs?.let { "%.1f %s".format(it.windIn(units.wind), units.wind.short) } ?: "brak danych")
                SummaryRow("MAX PORYWY", maxGustMs?.let { "%.1f %s".format(it.windIn(units.wind), units.wind.short) } ?: "brak danych")
                SummaryRow(
                    "MIN WIDOCZ.",
                    minVisibilityM?.let {
                        if (it >= 10000) "%.0f km".format(it / 1000) else "%.1f km".format(it / 1000)
                    } ?: "brak danych",
                )

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = OpsColors.Grid)
                Spacer(Modifier.height(4.dp))

                Text(
                    "ROZKŁAD WARUNKÓW",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )

                VerdictBar("GO", goPct, VerdictColors.Go)
                VerdictBar("OSTROŻNIE", cautPct, VerdictColors.Caution)
                VerdictBar("NO-GO", noGoPct, VerdictColors.NoGo)

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = OpsColors.Grid)
                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notatka (opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                )

                Spacer(Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Nie zapisuj") }
                    Button(
                        onClick = { onSave(note) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VerdictColors.Go),
                    ) { Text("Zapisz w historii", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = OpsColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(140.dp),
        )
        Text(
            value,
            color = OpsColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun VerdictBar(label: String, percent: Int, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = OpsColors.TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(100.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(14.dp)
                    .background(OpsColors.BgPanel, RoundedCornerShape(4.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(percent / 100f)
                        .background(color, RoundedCornerShape(4.dp)),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "$percent%",
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
