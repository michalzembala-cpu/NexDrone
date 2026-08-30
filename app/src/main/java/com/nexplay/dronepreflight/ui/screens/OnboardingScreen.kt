package com.nexplay.dronepreflight.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import kotlinx.coroutines.launch

private data class Slide(
    val emoji: String,
    val title: String,
    val body: String,
    val accent: Color,
)

private val SLIDES = listOf(
    Slide(
        emoji = "🛰",
        title = "5 niezależnych źródeł pogody",
        body = "Pobieramy prognozę z Open-Meteo, MET Norway, wttr.in, Bright Sky (DWD) i 7Timer. Pokazujemy medianę i wykrywamy rozbieżności — nie ryzykujesz podejmując decyzję na podstawie jednego, potencjalnie błędnego serwisu.",
        accent = OpsColors.Accent,
    ),
    Slide(
        emoji = "✅",
        title = "Automatyczna ocena GO / OSTROŻNIE / NO-GO",
        body = "Wprowadzasz limity Twojego BSP (maks wiatr, zakres temperatury) w Ustawieniach. Aplikacja ocenia każdą godzinę wybranego dnia i alarmuje gdy warunki przekraczają Twoje limity albo grozi burza.",
        accent = VerdictColors.Go,
    ),
    Slide(
        emoji = "📋",
        title = "Checklista PANSA i historia lotów",
        body = "19-punktowa checklista przed lotem oparta o wytyczne PANSA — część zaznacza się automatycznie na podstawie pogody. Po locie zapisujesz go w historii z warunkami i notatką.",
        accent = OpsColors.Amber,
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { SLIDES.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == SLIDES.size - 1

    Column(
        Modifier
            .fillMaxSize()
            .background(OpsColors.BgBase)
            .padding(16.dp),
    ) {
        // Skip u góry po prawej
        Row(Modifier.fillMaxWidth()) {
            Text(
                "NexDrone",
                color = OpsColors.Accent,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
            )
            if (!isLast) {
                TextButton(onClick = onDone) {
                    Text("Pomiń", color = OpsColors.TextSecondary)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            SlideContent(SLIDES[page])
        }

        // Kropki
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(SLIDES.size) { i ->
                val active = i == pagerState.currentPage
                val alpha by animateFloatAsState(if (active) 1f else 0.3f, label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .background(OpsColors.Accent.copy(alpha = alpha), RoundedCornerShape(5.dp))
                )
            }
        }

        // Przycisk główny
        Button(
            onClick = {
                if (isLast) onDone()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isLast) "Rozpocznij" else "Dalej")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SlideContent(slide: Slide) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(slide.emoji, fontSize = 80.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            slide.title,
            color = slide.accent,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            slide.body,
            color = OpsColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
