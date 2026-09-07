package com.nexplay.dronepreflight.data

/**
 * NexDrone Score — 0-100 punktów za lot.
 *
 * Składniki:
 *  30 pkt — pogoda przy starcie (verdict + confidence)
 *  20 pkt — przygotowanie (checklist)
 *  30 pkt — warunki podczas lotu (% czasu w GO)
 *  10 pkt — długość lotu (5-30 min = pełne, ekstrema karane)
 *  10 pkt — pewność danych (confidence)
 */
object FlightScorer {

    data class Score(
        val total: Int,
        val weather: Int,   // 0-30
        val prep: Int,      // 0-20
        val flight: Int,    // 0-30
        val duration: Int,  // 0-10
        val confidence: Int, // 0-10
        val label: String,
    )

    fun compute(
        startVerdict: Verdict,
        confidencePct: Int,
        checklistDone: Int,
        checklistTotal: Int,
        goPctDuringFlight: Int,
        durationSec: Int,
    ): Score {
        // POGODA — 30 pkt
        val weather = when (startVerdict) {
            Verdict.GO -> 30
            Verdict.CAUTION -> 18
            Verdict.NO_GO -> 6
        }

        // PRZYGOTOWANIE — 20 pkt
        val prep = if (checklistTotal > 0)
            (checklistDone * 20 / checklistTotal).coerceIn(0, 20)
        else 10

        // WARUNKI PODCZAS LOTU — 30 pkt
        val flight = (goPctDuringFlight * 30 / 100).coerceIn(0, 30)

        // DŁUGOŚĆ — 10 pkt (bell curve: 5-30 min sweet spot)
        val minutes = durationSec / 60
        val duration = when {
            minutes < 2 -> 3    // za krótki lot
            minutes < 5 -> 7
            minutes in 5..30 -> 10
            minutes <= 45 -> 8
            else -> 5           // baterie prawdopodobnie na wykończeniu
        }

        // PEWNOŚĆ DANYCH — 10 pkt
        val conf = (confidencePct * 10 / 100).coerceIn(0, 10)

        val total = weather + prep + flight + duration + conf

        val label = when {
            total >= 90 -> "PERFEKCYJNY"
            total >= 75 -> "BARDZO DOBRY"
            total >= 60 -> "DOBRY"
            total >= 40 -> "PRZECIĘTNY"
            else -> "SŁABY"
        }

        return Score(
            total = total.coerceIn(0, 100),
            weather = weather,
            prep = prep,
            flight = flight,
            duration = duration,
            confidence = conf,
            label = label,
        )
    }
}
