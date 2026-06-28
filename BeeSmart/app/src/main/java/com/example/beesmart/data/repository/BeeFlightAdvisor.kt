package com.example.beesmart.data.repository

import com.example.beesmart.network.models.WeatherResponse

/**
 * Translates raw weather into a coarse flight-conditions verdict for the colony.
 *
 * Thresholds drawn from common apiculture references:
 * - Bees fly comfortably between ~12°C and ~30°C.
 * - Foragers slow down once sustained wind exceeds ~24 km/h and largely stop above ~32 km/h.
 * - Active rain or snow grounds the colony.
 *
 * This is a deliberate simplification — humidity, daylight, pressure trends, etc. also matter,
 * but the three signals above explain the bulk of day-to-day flight decisions.
 */
object BeeFlightAdvisor {

    enum class Level { OPTIMAL, LIMITED, GROUNDED }

    data class Verdict(
        val level: Level,
        val headline: String,
        val reason: String
    )

    fun evaluate(weather: WeatherResponse): Verdict {
        val tempC = weather.main.temp
        val windKmH = (weather.wind?.speed ?: 0.0) * 3.6   // m/s → km/h
        val raining = (weather.rain?.oneHour ?: 0.0) > 0.0 || (weather.rain?.threeHour ?: 0.0) > 0.0
        val snowing = (weather.snow?.oneHour ?: 0.0) > 0.0 || (weather.snow?.threeHour ?: 0.0) > 0.0
        val conditionId = weather.weather.firstOrNull()?.id ?: 800

        // OWM condition codes 2xx (storm), 3xx (drizzle), 5xx (rain), 6xx (snow), 7xx (fog/dust)
        val activePrecip = raining || snowing || conditionId in 200..531 || conditionId in 600..622

        // Hard stops first.
        when {
            activePrecip -> return Verdict(
                Level.GROUNDED,
                "Albinele rămân în stup",
                "Precipitații active — albinele nu zboară pe ploaie/ninsoare."
            )
            tempC < 10.0 -> return Verdict(
                Level.GROUNDED,
                "Albinele rămân în stup",
                "Temperatură prea scăzută (${"%.0f".format(tempC)}°C) — sub 10°C zborul e oprit."
            )
            tempC > 38.0 -> return Verdict(
                Level.GROUNDED,
                "Albinele rămân în stup",
                "Temperatură excesivă (${"%.0f".format(tempC)}°C) — colonia se concentrează pe răcire."
            )
            windKmH > 32.0 -> return Verdict(
                Level.GROUNDED,
                "Albinele rămân în stup",
                "Vânt puternic (${windKmH.toInt()} km/h) — peste 32 km/h foragerii rămân la stup."
            )
        }

        // Soft thresholds — flight is possible but reduced.
        val limitedReasons = buildList {
            if (tempC in 10.0..11.9) add("temperatură scăzută (${"%.0f".format(tempC)}°C)")
            if (tempC in 30.1..38.0) add("temperatură ridicată (${"%.0f".format(tempC)}°C)")
            if (windKmH in 24.0..32.0) add("vânt moderat (${windKmH.toInt()} km/h)")
            if (conditionId in 800..804 && conditionId >= 803) add("nebulozitate ridicată")
        }
        if (limitedReasons.isNotEmpty()) {
            return Verdict(
                Level.LIMITED,
                "Zbor restricționat",
                "Activitate redusă: ${limitedReasons.joinToString(", ")}."
            )
        }

        return Verdict(
            Level.OPTIMAL,
            "Albinele zboară",
            "Condiții bune pentru cules: ${"%.0f".format(tempC)}°C, vânt ${windKmH.toInt()} km/h."
        )
    }
}
