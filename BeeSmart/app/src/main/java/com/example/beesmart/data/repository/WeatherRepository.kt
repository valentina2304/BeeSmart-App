package com.example.beesmart.data.repository

import com.example.beesmart.BuildConfig
import com.example.beesmart.network.WeatherApi
import com.example.beesmart.network.models.AirPollutionResponse
import com.example.beesmart.network.models.ForecastResponse
import com.example.beesmart.network.models.WeatherResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps OpenWeatherMap's geocoding, current-weather, 5-day forecast and
 * air-pollution endpoints.
 *
 * Geocode results are cached forever (a city's coords don't change).
 * Forecast/weather/air-pollution responses are cached for [TTL_MS] per
 * coord pair to stay well within the free tier's 1000-call/day limit
 * even with several apiaries open.
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val weatherApi: WeatherApi
) {
    /** Bundle returned to the UI: any of the three slots may be null if that call failed. */
    data class WeatherBundle(
        val current: WeatherResponse,
        val forecast: ForecastResponse? = null,
        val airPollution: AirPollutionResponse? = null
    )

    private data class CachedWeather(val response: WeatherResponse, val timestamp: Long)
    private data class CachedForecast(val response: ForecastResponse, val timestamp: Long)
    private data class CachedAir(val response: AirPollutionResponse, val timestamp: Long)
    private data class Coords(val lat: Double, val lon: Double)

    private val apiKey: String = BuildConfig.OPENWEATHER_API_KEY

    private val geocodeCache = ConcurrentHashMap<String, Coords>()
    private val weatherCache = ConcurrentHashMap<String, CachedWeather>()
    private val forecastCache = ConcurrentHashMap<String, CachedForecast>()
    private val airCache = ConcurrentHashMap<String, CachedAir>()

    /** Convenience: same as before — just current weather. */
    suspend fun getWeatherForLocation(location: String): Result<WeatherResponse> = withContext(Dispatchers.IO) {
        val coords = try {
            resolveCoords(location)
        } catch (e: IOException) {
            return@withContext Result.Error("Vremea nu este disponibilă fără conexiune la internet", null, e)
        } ?: return@withContext Result.Error("Locația \"$location\" nu a fost găsită")
        if (apiKey.isBlank()) {
            return@withContext Result.Error("Cheia OpenWeatherMap nu este configurată în local.properties")
        }
        runCatching { fetchCurrent(coords) }
            .fold(
                onSuccess = { it?.let { Result.Success(it) } ?: Result.Error("Nu s-au putut obține datele meteo") },
                onFailure = { Result.Error(it.message ?: "Eroare la obținerea vremii", null, it as? Exception) }
            )
    }

    /** Fetches all three endpoints in parallel; current weather is the only required one. */
    suspend fun getBundleForLocation(location: String): Result<WeatherBundle> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.Error("Cheia OpenWeatherMap nu este configurată în local.properties")
        }
        val trimmed = location.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.Error("Locația stupinei nu este completată")
        }
        val coords = try {
            resolveCoords(trimmed)
        } catch (e: IOException) {
            // Serve stale cached data if available; otherwise surface a friendly offline message.
            val staleCoords = geocodeCache[trimmed]
            if (staleCoords != null) {
                val key = "${staleCoords.lat},${staleCoords.lon}"
                val cached = weatherCache[key]?.response
                if (cached != null) {
                    return@withContext Result.Success(
                        WeatherBundle(cached, forecastCache[key]?.response, airCache[key]?.response)
                    )
                }
            }
            return@withContext Result.Error("Vremea nu este disponibilă fără conexiune la internet", null, e)
        } ?: return@withContext Result.Error("Locația \"$trimmed\" nu a fost găsită")

        try {
            coroutineScope {
                val currentDeferred = async { runCatching { fetchCurrent(coords) }.getOrNull() }
                val forecastDeferred = async { runCatching { fetchForecast(coords) }.getOrNull() }
                val airDeferred = async { runCatching { fetchAirPollution(coords) }.getOrNull() }
                val current = currentDeferred.await()
                val forecast = forecastDeferred.await()
                val air = airDeferred.await()
                if (current != null) {
                    Result.Success(WeatherBundle(current, forecast, air))
                } else {
                    Result.Error("Nu s-au putut obține datele meteo curente")
                }
            }
        } catch (e: IOException) {
            // Final fallback: serve any stale cached data we still have so the UI isn't empty.
            val key = "${coords.lat},${coords.lon}"
            val cached = weatherCache[key]?.response
            cached?.let {
                Result.Success(
                    WeatherBundle(
                        it,
                        forecastCache[key]?.response,
                        airCache[key]?.response
                    )
                )
            } ?: Result.Error("Vremea necesită conexiune la internet", null, e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare la obținerea vremii", null, e)
        }
    }

    // ==================== private helpers ====================

    /**
     * Returns null only when the location genuinely isn't found (empty geocode response).
     * IOExceptions propagate so callers can distinguish "offline" from "not found".
     */
    private suspend fun resolveCoords(location: String): Coords? {
        val trimmed = location.trim()
        if (trimmed.isEmpty()) return null
        geocodeCache[trimmed]?.let { return it }
        // Let IOException propagate — caller will show the right message
        val resp = weatherApi.geocode(trimmed, limit = 1, apiKey = apiKey)
        if (!resp.isSuccessful) return null
        val first = resp.body()?.firstOrNull() ?: return null
        return Coords(first.lat, first.lon).also { geocodeCache[trimmed] = it }
    }

    private suspend fun fetchCurrent(coords: Coords): WeatherResponse? {
        val key = "${coords.lat},${coords.lon}"
        val now = System.currentTimeMillis()
        weatherCache[key]?.let { if (now - it.timestamp < TTL_MS) return it.response }

        val resp = weatherApi.getCurrentWeather(coords.lat, coords.lon, apiKey = apiKey)
        return if (resp.isSuccessful && resp.body() != null) {
            resp.body()!!.also { weatherCache[key] = CachedWeather(it, now) }
        } else {
            weatherCache[key]?.response  // stale beats nothing
        }
    }

    private suspend fun fetchForecast(coords: Coords): ForecastResponse? {
        val key = "${coords.lat},${coords.lon}"
        val now = System.currentTimeMillis()
        forecastCache[key]?.let { if (now - it.timestamp < TTL_MS) return it.response }

        val resp = weatherApi.getForecast(coords.lat, coords.lon, apiKey = apiKey)
        return if (resp.isSuccessful && resp.body() != null) {
            resp.body()!!.also { forecastCache[key] = CachedForecast(it, now) }
        } else {
            forecastCache[key]?.response
        }
    }

    private suspend fun fetchAirPollution(coords: Coords): AirPollutionResponse? {
        val key = "${coords.lat},${coords.lon}"
        val now = System.currentTimeMillis()
        airCache[key]?.let { if (now - it.timestamp < TTL_MS) return it.response }

        val resp = weatherApi.getAirPollution(coords.lat, coords.lon, apiKey)
        return if (resp.isSuccessful && resp.body() != null) {
            resp.body()!!.also { airCache[key] = CachedAir(it, now) }
        } else {
            airCache[key]?.response
        }
    }

    companion object {
        private const val TTL_MS: Long = 30 * 60 * 1000L  // 30 minutes
    }
}
