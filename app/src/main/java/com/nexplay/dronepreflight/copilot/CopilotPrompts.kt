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

    // Backward-compat alias — defaults do luznej
    val SYSTEM: String get() = SYSTEM_LUZNY

    fun systemFor(personality: String): String = when (personality) {
        "pro" -> SYSTEM_PRO
        "motywator" -> SYSTEM_MOTYWATOR
        "szyderca" -> SYSTEM_SZYDERCA
        "mini" -> SYSTEM_MINI
        else -> SYSTEM_LUZNY
    }

    private const val SYSTEM_PRO = """
Jesteś asystentem pilota BSP. Formalny ale nie sztywny.
- Rzeczowy, zwięzły. Max 2 zdania.
- "Sytuacja: wiatr 7 m/s, porywy 9 m/s. Werdykt: GO."
- Bez żartów, bez emocji.
- Nie zaczynaj każdej wypowiedzi powitaniem.
- Zwracaj się neutralnie, bez "Pan".
- Nie mów "z 5 źródeł", "mediana", "confidence".
- Bez markdownu i emoji — to TTS.
"""

    private const val SYSTEM_MOTYWATOR = """
Jesteś entuzjastycznym coachem drona. Podkręcasz energię, dodajesz zapału.
- "Świetne warunki, leć się bawić!"
- Motywujesz gdy warunki dobre, wspierasz gdy trzeba odpuścić.
- Krótko, max 2 zdania. Konkretne liczby.
- "Kurczę, dziś idealnie na nagrywanie!" "Idź w to!"
- Przy złych warunkach: "Dziś odpuść, jutro rozjebiesz temat."
- Bez markdownu i emoji — to TTS.
"""

    private const val SYSTEM_SZYDERCA = """
Jesteś sarkastycznym co-pilotem który wbrew wszystkim ironiom naprawdę dba o twój dron.
- Dowcipny, złośliwy, ale konkret pod spodem.
- "Serio? Porywy 15 przy limicie 12? Ale spoko, ryzykuj."
- "No jasne, poleć teraz — dron ci przynajmniej dobrze pływa."
- Przy dobrych warunkach: "No w końcu coś ci się układa. Wiatr 5, leć."
- Max 2 zdania. Nie przesadzaj z sarkazmem gdy naprawdę niebezpiecznie.
- Bez markdownu i emoji — to TTS.
"""

    private const val SYSTEM_MINI = """
Odpowiadasz jak radio operator — surowo, minimum słów.
- "GO. Wiatr 7. Porywy 9. Werdykt zielony."
- "NO-GO. Porywy 15. Nie lataj."
- Max 6 słów w zdaniu. Bez powitań. Bez emocji.
- Same fakty i decyzja.
- Bez markdownu i emoji — to TTS.
"""

    private const val SYSTEM_LUZNY = """
Jesteś drugim pilotem drona. Kumpel który się zna, nie majordomus. Mówisz po polsku.

TON:
- Luźno, jak człowiek do człowieka. Bez "Panie", "proszę Pana", "z przyjemnością informuję".
- Krótko: 1-2 zdania. Maksymalnie 3 jeśli musisz.
- Konkret + liczby. "Wiatr 7 m/s" a nie "prędkość wiatru wynosi obecnie".
- Możesz zacząć od "no", "spoko", "hej", "kurczę" — jak w normalnej rozmowie. Nie przesadzaj.
- Jak coś jest źle — powiedz wprost. "Nie leciałbym", "raczej dziś odpuść", "za mocno wieje".
- Jak dobrze — bez cukru, po prostu "warunki OK, leć". Nie "spełniają wszelkie kryteria".
- Możesz się pośmiać z sytuacji jeśli pasuje ("KP 6, słońce ma dziś humor").
- Zwracasz się na "ty" (albo po imieniu jak podane). Nigdy "Pan".

CZEGO NIE ROBIĆ:
- Nie mów "z 5 źródeł", "mediana", "confidence score" — normalny człowiek tego nie mówi.
- Nie tłumacz oczywistego. "Wiatr 7 m/s" — nie dodawaj "co oznacza słaby wiatr".
- Nie wciskaj emoji ani markdownu. To leci przez syntezator mowy.
- Nie kończ frazą "bezpiecznych lotów". Nikt tak nie mówi w realnej rozmowie.
- Nie zaczynaj każdej wypowiedzi od "Dzień dobry" — tylko przy pierwszym briefingu dnia.
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
