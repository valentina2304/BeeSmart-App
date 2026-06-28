package com.example.beesmart.data.repository

import com.example.beesmart.network.WeatherApi
import com.example.beesmart.network.models.AirPollutionResponse
import com.example.beesmart.network.models.ForecastResponse
import com.example.beesmart.network.models.GeocodeResult
import com.example.beesmart.network.models.WeatherCondition
import com.example.beesmart.network.models.WeatherMain
import com.example.beesmart.network.models.WeatherResponse
import com.example.beesmart.network.models.Wind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class WeatherRepositoryTest {

    private lateinit var api: WeatherApi
    private lateinit var repository: WeatherRepository

    @Before
    fun setUp() {
        api = mockk()
        repository = WeatherRepository(api)
    }

    @Test
    fun `blank location for bundle returns validation error without api call`() = runTest {
        val result = repository.getBundleForLocation("   ")

        assertTrue(result is Result.Error)
        coVerify(exactly = 0) { api.geocode(any(), any(), any()) }
        coVerify(exactly = 0) { api.getCurrentWeather(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `missing geocode result returns not found error`() = runTest {
        coEvery { api.geocode("Unknown", 1, any()) } returns Response.success(emptyList())

        val result = repository.getWeatherForLocation("Unknown")

        assertTrue(result is Result.Error)
        coVerify(exactly = 1) { api.geocode("Unknown", 1, any()) }
        coVerify(exactly = 0) { api.getCurrentWeather(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `current weather success returns response and caches geocode and weather`() = runTest {
        coEvery { api.geocode("Cluj", 1, any()) } returns Response.success(listOf(geocode()))
        coEvery { api.getCurrentWeather(46.77, 23.59, "metric", "ro", any()) } returns
            Response.success(weather(temp = 21.0, city = "Cluj"))

        val first = repository.getWeatherForLocation("Cluj")
        val second = repository.getWeatherForLocation("Cluj")

        assertTrue(first is Result.Success)
        assertTrue(second is Result.Success)
        assertEquals("Cluj", (first as Result.Success).data.name)
        assertEquals(21.0, (second as Result.Success).data.main.temp, 0.001)
        coVerify(exactly = 1) { api.geocode("Cluj", 1, any()) }
        coVerify(exactly = 1) { api.getCurrentWeather(46.77, 23.59, "metric", "ro", any()) }
    }

    @Test
    fun `bundle succeeds when current weather succeeds but optional calls fail`() = runTest {
        coEvery { api.geocode("Cluj", 1, any()) } returns Response.success(listOf(geocode()))
        coEvery { api.getCurrentWeather(46.77, 23.59, "metric", "ro", any()) } returns
            Response.success(weather(temp = 23.0, city = "Cluj"))
        coEvery { api.getForecast(46.77, 23.59, "metric", "ro", any()) } returns
            Response.error(500, "forecast down".toResponseBody("text/plain".toMediaType()))
        coEvery { api.getAirPollution(46.77, 23.59, any()) } throws IOException("air down")

        val result = repository.getBundleForLocation("Cluj")

        assertTrue(result is Result.Success)
        val bundle = (result as Result.Success).data
        assertEquals("Cluj", bundle.current.name)
        assertNull(bundle.forecast)
        assertNull(bundle.airPollution)
    }

    @Test
    fun `bundle returns error when current weather cannot be loaded`() = runTest {
        coEvery { api.geocode("Cluj", 1, any()) } returns Response.success(listOf(geocode()))
        coEvery { api.getCurrentWeather(46.77, 23.59, "metric", "ro", any()) } returns
            Response.error(500, "weather down".toResponseBody("text/plain".toMediaType()))
        coEvery { api.getForecast(46.77, 23.59, "metric", "ro", any()) } returns
            Response.success(ForecastResponse(emptyList()))
        coEvery { api.getAirPollution(46.77, 23.59, any()) } returns
            Response.success(AirPollutionResponse(emptyList()))

        val result = repository.getBundleForLocation("Cluj")

        assertTrue(result is Result.Error)
    }

    private fun geocode() = GeocodeResult(
        name = "Cluj-Napoca",
        lat = 46.77,
        lon = 23.59,
        country = "RO"
    )

    private fun weather(temp: Double, city: String) = WeatherResponse(
        weather = listOf(WeatherCondition(800, "Clear", "clear sky", "01d")),
        main = WeatherMain(
            temp = temp,
            feelsLike = temp,
            tempMin = temp,
            tempMax = temp,
            pressure = 1012,
            humidity = 55
        ),
        wind = Wind(speed = 2.0),
        dt = 1_700_000_000L,
        name = city
    )
}
