package com.example.beesmart.network

import com.example.beesmart.network.models.AirPollutionResponse
import com.example.beesmart.network.models.ForecastResponse
import com.example.beesmart.network.models.GeocodeResult
import com.example.beesmart.network.models.WeatherResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenWeatherMap public REST API. Auth is via the `appid` query parameter,
 * so this client doesn't go through AuthInterceptor.
 *
 * Free tier limits: 1000 calls/day, 60/min — both endpoints share that quota.
 */
interface WeatherApi {

    /** Convert a location string ("Cluj-Napoca, RO") to lat/lon. */
    @GET("geo/1.0/direct")
    suspend fun geocode(
        @Query("q") query: String,
        @Query("limit") limit: Int = 1,
        @Query("appid") apiKey: String
    ): Response<List<GeocodeResult>>

    /** Current weather at the given coordinates. `units=metric` → °C; `lang=ro` → Romanian descriptions. */
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ro",
        @Query("appid") apiKey: String
    ): Response<WeatherResponse>

    /** 5-day / 3-hour forecast. Returns up to 40 entries (5 days × 8 slots/day). */
    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ro",
        @Query("appid") apiKey: String
    ): Response<ForecastResponse>

    /** Current air-pollution data (AQI 1..5 plus components like PM2.5/PM10). */
    @GET("data/2.5/air_pollution")
    suspend fun getAirPollution(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String
    ): Response<AirPollutionResponse>
}
