package com.nexplay.dronepreflight.data

import org.shredzone.commons.suncalc.SunTimes
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/** Wschód / zachód / golden hour / blue hour dla lokalizacji i dnia. */
object SunLight {

    data class Times(
        val sunrise: LocalTime?,
        val sunset: LocalTime?,
        val solarNoon: LocalTime?,
        val goldenHourMorningStart: LocalTime?,
        val goldenHourMorningEnd: LocalTime?,
        val goldenHourEveningStart: LocalTime?,
        val goldenHourEveningEnd: LocalTime?,
        val blueHourMorning: LocalTime?,   // 30 min przed sunrise
        val blueHourEvening: LocalTime?,   // 30 min po sunset
        val dayLengthMin: Int,
    )

    fun compute(lat: Double, lon: Double, dateMillis: Long = System.currentTimeMillis()): Times {
        val date = Date(dateMillis)
        val zone = ZoneId.systemDefault()

        val sunTimes = SunTimes.compute()
            .on(date)
            .at(lat, lon)
            .timezone(zone)
            .execute()

        // commons-suncalc 3.x zwraca ZonedDateTime (może być null gdy słońce nie wschodzi/zachodzi)
        val sunrise = sunTimes.rise?.toLocalTime()
        val sunset = sunTimes.set?.toLocalTime()
        val noon = sunTimes.noon?.toLocalTime()

        // Golden hour: 1h przed sunset i 1h po sunrise (uproszczenie)
        val goldenMorningStart = sunrise
        val goldenMorningEnd = sunrise?.plusHours(1)
        val goldenEveningStart = sunset?.minusHours(1)
        val goldenEveningEnd = sunset

        // Blue hour: ~30 min przed sunrise i po sunset
        val blueMorning = sunrise?.minusMinutes(30)
        val blueEvening = sunset?.plusMinutes(30)

        val dayLen = if (sunrise != null && sunset != null)
            java.time.Duration.between(sunrise, sunset).toMinutes().toInt() else 0

        return Times(
            sunrise = sunrise,
            sunset = sunset,
            solarNoon = noon,
            goldenHourMorningStart = goldenMorningStart,
            goldenHourMorningEnd = goldenMorningEnd,
            goldenHourEveningStart = goldenEveningStart,
            goldenHourEveningEnd = goldenEveningEnd,
            blueHourMorning = blueMorning,
            blueHourEvening = blueEvening,
            dayLengthMin = dayLen,
        )
    }

}
