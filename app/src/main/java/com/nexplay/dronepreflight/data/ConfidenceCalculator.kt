package com.nexplay.dronepreflight.data

/** Liczy pewność prognozy (0-100%) z:
 *  - dostępności źródeł pogody (40%)
 *  - dostępności źródeł KP (15%)
 *  - zgodności modeli pogodowych (25%) — im mniejszy stddev wiatru, tym pewniej
 *  - świeżości danych (20%) — im starsze, tym mniej pewnie
 */
object ConfidenceCalculator {

    data class Confidence(
        val percent: Int,
        val label: String,
        val details: List<String>,
    )

    fun calculate(snap: AggregatedSnapshot): Confidence {
        val srcTotal = snap.totalSources.coerceAtLeast(1)
        val srcCov = snap.successfulSources.toDouble() / srcTotal

        val kpCov = if (snap.kpTotalSources > 0)
            snap.kpReadings.size.toDouble() / snap.kpTotalSources else 0.5

        val windStd = snap.wind.stddev ?: 0.0
        val agreement = (1.0 - (windStd / 5.0).coerceIn(0.0, 1.0))

        val ageMin = (System.currentTimeMillis() - snap.fetchedAt) / 60_000.0
        val freshness = when {
            ageMin < 5 -> 1.0
            ageMin < 30 -> 1.0 - (ageMin - 5) / 25.0
            ageMin < 60 -> 0.5 - (ageMin - 30) / 60.0
            else -> 0.0
        }.coerceIn(0.0, 1.0)

        val total = srcCov * 0.40 + kpCov * 0.15 + agreement * 0.25 + freshness * 0.20
        val percent = (total * 100).toInt().coerceIn(0, 100)

        val label = when {
            percent >= 85 -> "HIGH"
            percent >= 60 -> "MEDIUM"
            else -> "LOW"
        }

        val details = buildList {
            add("Źródła pogody: ${snap.successfulSources}/$srcTotal")
            if (snap.kpTotalSources > 0) add("Źródła KP: ${snap.kpReadings.size}/${snap.kpTotalSources}")
            add("Zgodność wiatru: ±%.1f m/s".format(windStd))
            add("Dane sprzed ${ageMin.toInt()} min")
        }

        return Confidence(percent, label, details)
    }
}
