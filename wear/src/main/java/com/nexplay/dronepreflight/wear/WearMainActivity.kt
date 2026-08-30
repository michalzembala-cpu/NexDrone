package com.nexplay.dronepreflight.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearApp()
            }
        }
    }
}

@Composable
private fun WearApp() {
    val context = LocalContext.current
    val cache = remember { WearSnapshotCache(context) }
    var snap by remember { mutableStateOf(cache.read()) }

    // Polluj cache co 2s (na wypadek gdyby listener zapisał nową wartość).
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                snap = cache.read()
            }
            delay(2000)
        }
    }

    val (label, color) = when (snap.verdict) {
        "GO" -> "GO" to Color(0xFF22C55E)
        "CAUTION" -> "OSTROŻNIE" to Color(0xFFF59E0B)
        "NO_GO" -> "NO-GO" to Color(0xFFEF4444)
        else -> "—" to Color(0xFF94A3B8)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1220))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "NEXDRONE",
            color = Color(0xFF34D399),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = color,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${snap.tempC} · ${snap.windMs}",
            color = Color.White,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Kp ${snap.kp}",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            snap.location,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
        )
        if (snap.updatedAt == 0L) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Uruchom apkę na telefonie",
                color = Color(0xFF94A3B8),
                fontSize = 9.sp,
            )
        }
    }
}
