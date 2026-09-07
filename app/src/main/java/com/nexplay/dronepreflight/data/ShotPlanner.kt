package com.nexplay.dronepreflight.data

/** Katalog typów ujęć + kalkulator "który ma dziś najlepsze warunki". */
object ShotPlanner {

    data class Shot(
        val id: String,
        val emoji: String,
        val name: String,
        val description: String,
        val maxWindMs: Double,      // powyżej: ujęcie odpada
        val idealWindMs: Double,    // poniżej: pełny score
        val needsLowGust: Boolean,  // wrażliwy na porywy
        val needsVisibility: Boolean, // wymaga dobrej widoczności
    )

    val Shots = listOf(
        Shot("orbit", "🌀", "ORBIT",
            "Powolny okrężny lot wokół obiektu — potrzebuje stabilności.",
            maxWindMs = 6.0, idealWindMs = 3.0,
            needsLowGust = true, needsVisibility = false),
        Shot("reveal", "🎬", "REVEAL",
            "Ukrycie kadru za elementem i odkrywanie sceny — dynamiczne.",
            maxWindMs = 8.0, idealWindMs = 4.5,
            needsLowGust = false, needsVisibility = true),
        Shot("tracking", "🎯", "TRACKING",
            "Śledzenie obiektu w ruchu — potrzebuje reakcji na wiatr boczny.",
            maxWindMs = 7.0, idealWindMs = 4.0,
            needsLowGust = false, needsVisibility = true),
        Shot("topdown", "🔽", "TOP-DOWN",
            "Kamera prostopadle w dół — wrażliwe na kołysanie.",
            maxWindMs = 5.0, idealWindMs = 2.5,
            needsLowGust = true, needsVisibility = false),
        Shot("pushin", "▶", "PUSH-IN",
            "Powolne najeżdżanie na obiekt — prawie zawsze działa.",
            maxWindMs = 10.0, idealWindMs = 6.0,
            needsLowGust = false, needsVisibility = false),
        Shot("pullaway", "◀", "PULL-AWAY",
            "Odjeżdżanie od obiektu — ujawnia kontekst, elastyczne.",
            maxWindMs = 10.0, idealWindMs = 6.0,
            needsLowGust = false, needsVisibility = false),
    )

    data class ShotScore(
        val shot: Shot,
        val score: Int,        // 0-100
        val verdict: Verdict,
        val reason: String,
    )

    /** Ocenia każde ujęcie w obecnych warunkach. */
    fun score(snap: AggregatedSnapshot): List<ShotScore> {
        val wind = snap.wind.median ?: 0.0
        val gust = snap.gust.median ?: wind
        val visibility = snap.visibility.median ?: 10_000.0

        return Shots.map { shot ->
            var score = 100
            val reasons = mutableListOf<String>()

            // Wiatr
            when {
                wind > shot.maxWindMs -> {
                    score -= 60
                    reasons += "za silny wiatr (${"%.1f".format(wind)} > ${shot.maxWindMs})"
                }
                wind > shot.idealWindMs -> {
                    val over = wind - shot.idealWindMs
                    val penalty = ((over / (shot.maxWindMs - shot.idealWindMs)) * 30).toInt().coerceIn(0, 30)
                    score -= penalty
                }
            }

            // Porywy (dla wrażliwych)
            if (shot.needsLowGust && gust > shot.idealWindMs * 1.3) {
                val over = gust - shot.idealWindMs * 1.3
                val penalty = (over * 8).toInt().coerceIn(0, 25)
                score -= penalty
                reasons += "porywy ${"%.1f".format(gust)} m/s"
            }

            // Widoczność
            if (shot.needsVisibility && visibility < 5_000) {
                score -= 20
                reasons += "słaba widoczność (%.1f km)".format(visibility / 1000)
            }

            val verdict = when {
                score >= 75 -> Verdict.GO
                score >= 45 -> Verdict.CAUTION
                else -> Verdict.NO_GO
            }

            val reason = when (verdict) {
                Verdict.GO -> if (reasons.isEmpty()) "warunki idealne" else "prawie idealne"
                Verdict.CAUTION -> "problem: ${reasons.joinToString(", ")}"
                Verdict.NO_GO -> "odradzam — ${reasons.joinToString(", ")}"
            }

            ShotScore(shot, score.coerceIn(0, 100), verdict, reason)
        }.sortedByDescending { it.score }
    }
}
