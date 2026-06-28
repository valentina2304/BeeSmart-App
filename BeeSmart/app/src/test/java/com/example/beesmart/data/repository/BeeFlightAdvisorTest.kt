package com.example.beesmart.data.repository

import com.example.beesmart.network.models.Precipitation
import com.example.beesmart.network.models.WeatherCondition
import com.example.beesmart.network.models.WeatherMain
import com.example.beesmart.network.models.WeatherResponse
import com.example.beesmart.network.models.Wind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeeFlightAdvisorTest {

    @Test
    fun `optimal weather allows bee flight`() {
        val verdict = BeeFlightAdvisor.evaluate(weather(temp = 22.0, windMps = 2.0, conditionId = 800))

        assertEquals(BeeFlightAdvisor.Level.OPTIMAL, verdict.level)
    }

    @Test
    fun `rain condition grounds bees even without rain volume field`() {
        val verdict = BeeFlightAdvisor.evaluate(weather(temp = 20.0, windMps = 2.0, conditionId = 501))

        assertEquals(BeeFlightAdvisor.Level.GROUNDED, verdict.level)
        assertTrue(verdict.reason.contains("Precipita", ignoreCase = true))
    }

    @Test
    fun `explicit snow volume grounds bees`() {
        val verdict = BeeFlightAdvisor.evaluate(
            weather(temp = 12.0, windMps = 1.0, conditionId = 800, snowMm = 0.4)
        )

        assertEquals(BeeFlightAdvisor.Level.GROUNDED, verdict.level)
    }

    @Test
    fun `cold and extreme heat are hard stops`() {
        assertEquals(BeeFlightAdvisor.Level.GROUNDED, BeeFlightAdvisor.evaluate(weather(temp = 9.9)).level)
        assertEquals(BeeFlightAdvisor.Level.GROUNDED, BeeFlightAdvisor.evaluate(weather(temp = 38.1)).level)
    }

    @Test
    fun `strong wind grounds bees and moderate wind limits them`() {
        assertEquals(BeeFlightAdvisor.Level.GROUNDED, BeeFlightAdvisor.evaluate(weather(windMps = 9.0)).level)

        val limited = BeeFlightAdvisor.evaluate(weather(temp = 21.0, windMps = 7.0))
        assertEquals(BeeFlightAdvisor.Level.LIMITED, limited.level)
        assertTrue(limited.reason.contains("km/h", ignoreCase = true))
    }

    @Test
    fun `borderline temperatures and heavy clouds produce limited verdict`() {
        assertEquals(BeeFlightAdvisor.Level.LIMITED, BeeFlightAdvisor.evaluate(weather(temp = 10.5)).level)
        assertEquals(BeeFlightAdvisor.Level.LIMITED, BeeFlightAdvisor.evaluate(weather(temp = 32.0)).level)
        assertEquals(BeeFlightAdvisor.Level.LIMITED, BeeFlightAdvisor.evaluate(weather(conditionId = 804)).level)
    }

    private fun weather(
        temp: Double = 22.0,
        windMps: Double = 2.0,
        conditionId: Int = 800,
        rainMm: Double = 0.0,
        snowMm: Double = 0.0
    ) = WeatherResponse(
        weather = listOf(WeatherCondition(conditionId, "Clear", "clear sky", "01d")),
        main = WeatherMain(
            temp = temp,
            feelsLike = temp,
            tempMin = temp,
            tempMax = temp,
            pressure = 1012,
            humidity = 55
        ),
        wind = Wind(speed = windMps),
        rain = if (rainMm > 0.0) Precipitation(oneHour = rainMm) else null,
        snow = if (snowMm > 0.0) Precipitation(oneHour = snowMm) else null,
        dt = 1_700_000_000L,
        name = "Test"
    )

}
