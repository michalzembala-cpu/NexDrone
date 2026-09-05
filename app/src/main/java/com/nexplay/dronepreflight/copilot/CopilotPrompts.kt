package com.nexplay.dronepreflight.copilot

import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.formatTemp
import com.nexplay.dronepreflight.data.formatWind
import com.nexplay.dronepreflight.ui.HourlyOutlook

/** Wspólne prompty dla wszystkich AI-providerów (Gemini, ...). Ton = Jarvis. */
object CopilotPrompts {

    // Jedna osobowość: Jarvis z Iron Mana. Personality parameter ignorowane.
    val SYSTEM: String get() = SYSTEM_JARVIS
    fun systemFor(personality: String): String = SYSTEM_JARVIS

    private const val SYSTEM_JARVIS = """
Jesteś JARVIS — sztuczna inteligencja obsługująca operatora BSP.
Grasz DOKŁADNIE tę samą postać co Jarvis z filmów Iron Man.
Nie mówisz że jesteś modelem językowym. Nie mówisz że jesteś "AI Co-pilotem apki NexDrone".
Jesteś Jarvisem.

TON — kluczowe:
- Angielsko-formalny, ale ciepły. Zwracasz się "sir" (po polsku: "proszę Pana" tylko przy poważnych sprawach, częściej po prostu bez zwrotu).
- Suchy brytyjski humor. Sarkazm w rękawiczkach. "Poziom porywów sugeruje że dziś może niekoniecznie."
- Ekstremalnie kompetentny. Nigdy nie brzmisz niepewnie — nawet gdy dane są niepełne.
- Anticipujesz — nie tylko odpowiadasz, ale i sugerujesz sensowny następny krok.
- Konkretne liczby zawsze. Wiatr, porywy, KP — mówisz cyfry, nie ogólniki.
- Zwięzły: 1-2 zdania. Max 3 przy briefingach.

FRAZY W STYLU JARVISA (używaj takich, nie wprost kopiuj):
- "Warunki wyglądają zadowalająco. Wiatr 7 m/s, porywy 9."
- "Muszę zauważyć, że porywy właśnie osiągnęły 14 m/s. Sugerowałbym rozważyć powrót."
- "Jeśli mogę zasugerować — okno GO kończy się za około 35 minut."
- "Odczyty są jednoznaczne. Nie polecałbym startu."
- "Uwaga, wykryłem znaczące pogorszenie. Zalecam natychmiastowe lądowanie."

CZEGO NIE ROBIĆ:
- Nie mów "z 5 źródeł", "mediana", "confidence score".
- Nie tłumacz oczywistego.
- Bez markdownu i emoji — to TTS.
- Nigdy nie wychodź z roli Jarvisa. Jeśli user zapyta "jakim jesteś modelem" — odpowiedz "Jestem Jarvis. Do usług."
- Bez współczesnego slangu ("spoko", "kurczę"). Ty jesteś BRYTYJSKI dżentelmen-AI.
- Bez wyrazów jak "kurwa" nawet gdy user tak mówi.
"""

    fun briefing(
        pilotName: String,
        snap: AggregatedSnapshot,
        assessment: FlightAssessment,
        outlook: List<HourlyOutlook>,
        units: DisplayUnits,
    ): String = buildString {
        appendLine("Wygeneruj krótki pre-flight briefing.")
        appendLine()
        if (pilotName.isNotBlank()) appendLine("Pilot: $pilotName")
        appendLine("Werdykt: ${verdictLabel(assessment.overall)}")
        appendLine("Wiatr: ${snap.wind.median?.let { formatWind(it, units.wind) } ?: "brak"}")
        appendLine("Porywy: ${snap.gust.median?.let { formatWind(it, units.wind) } ?: "brak"}")
        appendLine("Kierunek: ${com.nexplay.dronepreflight.data.windDirectionCardinal(snap.windDir.median)}")
        appendLine("Temperatura: ${formatTemp(snap.temp.median, units.temp)}")
        snap.kpIndex?.let { appendLine("KP index: %.1f".format(it)) }
        appendLine("Zachmurzenie: ${snap.cloud.median?.let { "%.0f%%".format(it) } ?: "brak"}")
        appendLine("Widoczność: ${snap.visibility.median?.let { "%.1f km".format(it / 1000) } ?: "brak"}")
        val problems = assessment.checks.filter { it.verdict != Verdict.GO }
        if (problems.isNotEmpty()) {
            appendLine("Problemy:")
            problems.forEach { appendLine("- ${it.label}: ${it.value} (${it.note ?: "poza limitem"})") }
        }
        val next3 = outlook.take(3).mapNotNull { it.windMs }
        if (next3.isNotEmpty()) {
            val trend = when {
                next3.last() > next3.first() + 2.0 -> "rosnący"
                next3.first() > next3.last() + 2.0 -> "malejący"
                else -> "stabilny"
            }
            appendLine("Trend wiatru (3h): $trend, wartości ${next3.joinToString(", ") { "%.1f".format(it) }} m/s")
        }
    }

    fun postFlight(
        pilotName: String,
        elapsedSec: Int,
        maxWindMs: Double?,
        maxGustMs: Double?,
        units: DisplayUnits,
        goPct: Int,
        outlook: List<HourlyOutlook>,
    ): String = buildString {
        appendLine("Wygeneruj krótkie podsumowanie po locie.")
        appendLine()
        if (pilotName.isNotBlank()) appendLine("Pilot: $pilotName")
        val mm = elapsedSec / 60
        val ss = elapsedSec % 60
        appendLine("Czas lotu: %d:%02d".format(mm, ss))
        maxWindMs?.let { appendLine("Max wiatr: ${formatWind(it, units.wind)}") }
        maxGustMs?.let { appendLine("Max porywy: ${formatWind(it, units.wind)}") }
        appendLine("Rozkład warunków: $goPct% czasu w GO")
        val nextGo = outlook.take(24).indexOfFirst { it.verdict == Verdict.GO }
        if (nextGo > 0) appendLine("Następne GO za $nextGo godzin")
    }

    private fun verdictLabel(v: Verdict): String = when (v) {
        Verdict.GO -> "GO (można latać)"
        Verdict.CAUTION -> "CAUTION (warunki graniczne)"
        Verdict.NO_GO -> "NO-GO (nie latać)"
    }
}
