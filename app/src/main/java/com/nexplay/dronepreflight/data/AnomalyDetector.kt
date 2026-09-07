package com.nexplay.dronepreflight.data

import kotlin.math.abs

/** Wykrywa outlier'ów pomiędzy źródłami. Jeśli 1 źródło mocno odjeżdża, powiadom. */
object AnomalyDetector {

    data class Anomaly(
        val parameter: String,   // np. "Wiatr", "Temperatura"
        val outlierSource: String,
        val outlierValue: Double,
        val medianValue: Double,
        val unit: String,
    )

    fun detect(snap: AggregatedSnapshot): List<Anomaly> {
        val out = mutableListOf<Anomaly>()

        // Wiatr — jeśli 1 źródło > 3 m/s od mediany → outlier
        checkParameter(
            snap.readings.map { it.source to it.windMs }.filter { it.second != null }
                .map { it.first to it.second!! },
            median = snap.wind.median,
            threshold = 3.0,
            label = "Wiatr",
            unit = "m/s",
        )?.let { out += it }

        // Temperatura — >4°C od mediany
        checkParameter(
            snap.readings.map { it.source to it.tempC }.filter { it.second != null }
                .map { it.first to it.second!! },
            median = snap.temp.median,
            threshold = 4.0,
            label = "Temperatura",
            unit = "°C",
        )?.let { out += it }

        // Widoczność — >5km różnicy
        checkParameter(
            snap.readings.map { it.source to it.visibilityM }.filter { it.second != null }
                .map { it.first to it.second!! },
            median = snap.visibility.median,
            threshold = 5000.0,
            label = "Widoczność",
            unit = "m",
        )?.let { out += it }

        return out
    }

    private fun checkParameter(
        readings: List<Pair<String, Double>>,
        median: Double?,
        threshold: Double,
        label: String,
        unit: String,
    ): Anomaly? {
        if (median == null || readings.size < 3) return null
        val outlier = readings.maxByOrNull { abs(it.second - median) } ?: return null
        return if (abs(outlier.second - median) > threshold) {
            Anomaly(
                parameter = label,
                outlierSource = outlier.first,
                outlierValue = outlier.second,
                medianValue = median,
                unit = unit,
            )
        } else null
    }
}
