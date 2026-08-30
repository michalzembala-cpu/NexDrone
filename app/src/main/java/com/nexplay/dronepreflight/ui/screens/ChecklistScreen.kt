package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.ui.AllChecklistIds
import com.nexplay.dronepreflight.ui.PreflightChecklist
import com.nexplay.dronepreflight.ui.theme.DronePreflightTheme

@Composable
fun ChecklistScreen(
    checked: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val total = AllChecklistIds.size
    val done = checked.intersect(AllChecklistIds).size

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Postęp checklisty", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$done / $total pozycji zaznaczonych",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onReset) { Text("Reset") }
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done / total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreflightChecklist.forEach { section ->
                item(key = "sec_${section.title}") {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(section.items, key = { it.id }) { item ->
                    val isChecked = item.id in checked
                    val onChange: ((Boolean) -> Unit)? =
                        if (item.auto) null else { v -> onToggle(item.id, v) }
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = onChange,
                                enabled = !item.auto,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (item.auto) {
                                        AssistChip(
                                            onClick = {},
                                            enabled = false,
                                            label = { Text("auto") },
                                        )
                                    }
                                }
                                Text(
                                    if (item.auto) "Aktualizowane z Dashboarda po każdym „Odśwież pogodę”. ${item.hint}"
                                    else item.hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Checklista")
@Composable
private fun ChecklistPreview() = DronePreflightTheme {
    ChecklistScreen(
        checked = setOf("route", "battery", "wind"),
        onToggle = { _, _ -> },
        onReset = {},
    )
}

