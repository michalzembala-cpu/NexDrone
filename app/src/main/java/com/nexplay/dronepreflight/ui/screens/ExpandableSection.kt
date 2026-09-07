package com.nexplay.dronepreflight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.ui.theme.OpsColors

/**
 * Rozwijana sekcja z headerem. Klik toggle. Stan zapisywany między sesjami przez sectionKey.
 */
@Composable
fun ExpandableSection(
    label: String,
    sectionKey: String,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(sectionKey) { mutableStateOf(defaultExpanded) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OpsColors.BgPanel, RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "▲" else "▼",
                color = OpsColors.Accent,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) { content() }
        }
    }
}
