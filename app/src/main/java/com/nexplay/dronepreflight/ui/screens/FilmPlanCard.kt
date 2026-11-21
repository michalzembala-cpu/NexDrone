package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.DroneCatalog
import com.nexplay.dronepreflight.data.DroneProfile
import com.nexplay.dronepreflight.data.FilmPlanner
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.ui.UiState
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors

/**
 * Wybór modelu drona: aktywuje istniejący profil tego modelu, dopisuje model
 * do aktywnego profilu bez modelu albo tworzy nowy profil z limitami z katalogu.
 */
suspend fun selectDroneModel(store: SettingsStore, state: UiState, model: DroneCatalog.Model) {
    val profiles = state.droneProfiles
    val existing = profiles.firstOrNull { DroneCatalog.forProfile(it)?.id == model.id }
    val active = profiles.firstOrNull { it.id == state.activeProfileId }
    when {
        existing != null -> {
            if (existing.modelId != model.id) store.upsertProfile(existing.copy(modelId = model.id))
            store.setActiveProfile(existing.id)
        }
        active != null && DroneCatalog.forProfile(active) == null -> {
            store.upsertProfile(active.copy(modelId = model.id))
        }
        else -> {
            val p = DroneProfile(
                id = "prof_${System.currentTimeMillis()}",
                name = model.name,
                maxWindMs = model.maxWindMs,
                minTempC = model.minTempC,
                maxTempC = model.maxTempC,
                modelId = model.id,
            )
            store.upsertProfile(p)
            store.setActiveProfile(p.id)
        }
    }
}

private fun verdictColor(v: Verdict): Color = when (v) {
    Verdict.GO -> VerdictColors.Go
    Verdict.CAUTION -> VerdictColors.Caution
    Verdict.NO_GO -> VerdictColors.NoGo
}

@Composable
fun FilmPlanCard(
    snap: AggregatedSnapshot,
    profiles: List<DroneProfile>,
    activeProfileId: String?,
    onPickModel: (DroneCatalog.Model) -> Unit,
) {
    val active = profiles.firstOrNull { it.id == activeProfileId }
    val model = DroneCatalog.forProfile(active)
    var showPicker by remember { mutableStateOf(false) }

    // ── Wybór drona ──
    Panel {
        Text("TWÓJ DRON", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(OpsColors.BgPanelRaised, RoundedCornerShape(8.dp))
                .clickable { showPicker = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🛸", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model?.name ?: "Wybierz drona",
                    color = if (model != null) OpsColors.TextPrimary else OpsColors.Accent,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (model != null) "${model.weightG} g · wiatr do %.1f m/s · %s".format(model.maxWindMs, model.obstacleSensing.let { "czujniki: $it" })
                    else "Plan zależy od modelu — tryby, wysokości, ustawienia kamery",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text("ZMIEŃ ▾", color = OpsColors.Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Który dron?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DroneCatalog.Models.forEach { m ->
                        val sel = m.id == model?.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(
                                    if (sel) VerdictColors.Go.copy(alpha = 0.15f) else OpsColors.BgPanelRaised,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    showPicker = false
                                    onPickModel(m)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, color = OpsColors.TextPrimary, fontWeight = FontWeight.Bold)
                                Text(
                                    if (m.id == "other") "ostrożne założenia" else "${m.weightG} g · ${m.video}",
                                    color = OpsColors.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (sel) Text("✓", color = VerdictColors.Go, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Zamknij") } },
        )
    }

    if (model == null) return

    val plan = remember(snap.fetchedAt, model.id) { FilmPlanner.plan(model, snap) }
    val col = verdictColor(plan.overall)

    // ── Werdykt + kiedy/gdzie ──
    Panel(border = col) {
        Text(
            when (plan.overall) { Verdict.GO -> "✅ LECISZ"; Verdict.CAUTION -> "⚠️ OSTROŻNIE"; Verdict.NO_GO -> "⛔ ODPUŚĆ" },
            color = col, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        )
        Text(plan.headline, color = OpsColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
        plan.warnings.forEach {
            Spacer(Modifier.height(4.dp))
            Text("• $it", color = VerdictColors.Caution, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        InfoRow("🕓", "KIEDY", plan.whenText)
        InfoRow("📍", "GDZIE", plan.whereText)
        InfoRow("☀️", "SŁOŃCE", plan.sunDirText)
        InfoRow("💨", "WIATR", plan.windText)
        InfoRow("🔋", "BATERIE", plan.batteriesText)
    }

    // ── Ujęcia ──
    Text(
        "UJĘCIA · ${plan.shots.size} klipów ≈ ${plan.shots.sumOf { it.clipSec }} s rolki",
        color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
    plan.shots.forEach { ShotCard(it) }

    // ── Kamera ──
    Panel {
        Text("USTAWIENIA KAMERY · ${model.name}", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        plan.cameraSettings.forEach { (k, v) -> KeyValue(k, v) }
    }

    // ── Przepisy ──
    Panel {
        Text("PRZEPISY", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        plan.rules.forEach {
            Text("• $it", color = OpsColors.TextPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
private fun ShotCard(s: FilmPlanner.ShotPlan) {
    var open by remember { mutableStateOf(s.num == 1) }
    val col = verdictColor(s.verdict)
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, if (open) col else OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable { open = !open }.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${s.num}. ${s.title}", color = OpsColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .background((if (s.auto) OpsColors.Accent else OpsColors.Amber).copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (s.auto) "AUTO" else "RĘCZNIE",
                                color = if (s.auto) OpsColors.Accent else OpsColors.Amber,
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("${s.clipSec} s · ${s.height}", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Box(Modifier.size(12.dp).background(col, RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(8.dp))
                Text(if (open) "▲" else "▼", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                Text(s.purpose, color = OpsColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text("Warunki: ${s.verdictNote}", color = col, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                KeyValue("Tryb", s.mode)
                KeyValue("Wysokość", s.height)
                KeyValue("Odległość", s.distance)
                KeyValue("Prędkość", s.speed)
                KeyValue("Gimbal", s.gimbal)
                Spacer(Modifier.height(8.dp))
                Text("GDZIE", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(s.where, color = OpsColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("JAK", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                s.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", color = OpsColors.TextPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 1.dp))
                }
                s.tips.forEach {
                    Text("💡 $it", color = VerdictColors.Caution, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Panel(border: Color = OpsColors.Grid, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, border),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun InfoRow(emoji: String, label: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(emoji, fontSize = 16.sp, modifier = Modifier.width(26.dp))
        Column {
            Text(label, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(text, color = OpsColors.TextPrimary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun KeyValue(k: String, v: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(k, color = OpsColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
        Text(v, color = OpsColors.TextPrimary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}
