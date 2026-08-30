package com.nexplay.dronepreflight.data

enum class WindUnit(val short: String) {
    MS("m/s"),
    KMH("km/h"),
    KTS("kn"),
}

enum class TempUnit(val short: String) {
    C("°C"),
    F("°F"),
}

data class DisplayUnits(
    val wind: WindUnit = WindUnit.MS,
    val temp: TempUnit = TempUnit.C,
)

/** Konwertuje m/s do wybranej jednostki. */
fun Double.windIn(unit: WindUnit): Double = when (unit) {
    WindUnit.MS -> this
    WindUnit.KMH -> this * 3.6
    WindUnit.KTS -> this * 1.94384
}

/** Konwertuje wartość w wybranej jednostce z powrotem do m/s. */
fun Double.windToMs(unit: WindUnit): Double = when (unit) {
    WindUnit.MS -> this
    WindUnit.KMH -> this / 3.6
    WindUnit.KTS -> this / 1.94384
}

/** Konwertuje °C do wybranej jednostki. */
fun Double.tempIn(unit: TempUnit): Double = when (unit) {
    TempUnit.C -> this
    TempUnit.F -> this * 9.0 / 5.0 + 32.0
}

/** Konwertuje wartość w wybranej jednostce z powrotem do °C. */
fun Double.tempToC(unit: TempUnit): Double = when (unit) {
    TempUnit.C -> this
    TempUnit.F -> (this - 32.0) * 5.0 / 9.0
}

/** Sformatuj wiatr (podajesz w m/s). */
fun formatWind(ms: Double?, unit: WindUnit, decimals: Int = 1): String {
    if (ms == null) return "—"
    val v = ms.windIn(unit)
    return "%.${decimals}f %s".format(v, unit.short)
}

/** Sformatuj temperaturę (podajesz w °C). */
fun formatTemp(c: Double?, unit: TempUnit, decimals: Int = 1): String {
    if (c == null) return "—"
    val v = c.tempIn(unit)
    return "%.${decimals}f%s".format(v, unit.short)
}
