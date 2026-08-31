package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.TempUnit
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.WindUnit
import com.nexplay.dronepreflight.data.tempIn
import com.nexplay.dronepreflight.data.windIn
import com.nexplay.dronepreflight.data.windDirectionCardinal
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
) {
    val startedAt = rememberSaveable { System.currentTimeMillis() }
    var elapsedMs by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            elapsedMs = System.currentTimeMillis() - startedAt
            delay(1000)
        }
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

                Button(
                    onClick = onDismiss,
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

private fun windColor(value: Double?, state: UiState): Color {
    if (value == null) return OpsColors.TextSecondary
    val limit = state.limits.maxWindMs
    return when {
        value >= limit -> VerdictColors.NoGo
        value >= limit * 0.75 -> VerdictColors.Caution
        else -> VerdictColors.Go
    }
}
