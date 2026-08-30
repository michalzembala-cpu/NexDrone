package com.nexplay.dronepreflight.data

import com.nexplay.dronepreflight.data.sources.SourceReading

/**
 * Szybka ocena pojedynczej godziny z jednego źródła (używane do skanu 48h).
 * NIE stosuje kary za małą liczbę źródeł — służy tylko do zawężenia okna.
 */
object HourlyScorer {
    fun score(reading: SourceReading, limits: DroneLimits, kp: Double?): Verdict {
        var worst = Verdict.GO
        fun bump(v: Verdict) {
            if (v.ordinal > worst.ordinal) worst = v
        }

        val wind = reading.windMs
        when {
            wind == null -> bump(Verdict.CAUTION)
            wind >= limits.maxWindMs -> bump(Verdict.NO_GO)
            wind >= limits.maxWindMs * 0.75 -> bump(Verdict.CAUTION)
        }
        val gust = reading.windGustMs
        if (gust != null && gust >= limits.maxWindMs) bump(Verdict.NO_GO)

        val temp = reading.tempC
        when {
            temp == null -> bump(Verdict.CAUTION)
            temp < limits.minTempC || temp > limits.maxTempC -> bump(Verdict.NO_GO)
            temp < limits.minTempC + 3 || temp > limits.maxTempC - 3 -> bump(Verdict.CAUTION)
        }

        val precip = reading.precipMm ?: 0.0
        when {
            precip <= 0.0 -> {}
            precip < 0.5 -> bump(Verdict.CAUTION)
            else -> bump(Verdict.NO_GO)
        }

        if (reading.isStorm) bump(Verdict.NO_GO)
        if (reading.isFog) bump(Verdict.CAUTION)

        when {
            kp == null -> {}
            kp >= 6 -> bump(Verdict.NO_GO)
            kp >= 5 -> bump(Verdict.CAUTION)
        }

        return worst
    }
}
