package com.example.beesmart.network.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response from OpenWeatherMap's Geocoding API:
 * GET /geo/1.0/direct?q={location}&limit={n}&appid={key}
 */
@JsonClass(generateAdapter = true)
data class GeocodeResult(
    @Json(name = "name") val name: String,
    @Json(name = "lat") val lat: Double,
    @Json(name = "lon") val lon: Double,
    @Json(name = "country") val country: String? = null,
    @Json(name = "state") val state: String? = null
)

/**
 * Response from OpenWeatherMap's Current Weather API:
 * GET /data/2.5/weather?lat={}&lon={}&units=metric&lang=ro&appid={key}
 */
@JsonClass(generateAdapter = true)
data class WeatherResponse(
    @Json(name = "weather") val weather: List<WeatherCondition>,
    @Json(name = "main") val main: WeatherMain,
    @Json(name = "wind") val wind: Wind? = null,
    @Json(name = "clouds") val clouds: Clouds? = null,
    @Json(name = "rain") val rain: Precipitation? = null,
    @Json(name = "snow") val snow: Precipitation? = null,
    @Json(name = "dt") val dt: Long,
    @Json(name = "sys") val sys: SystemInfo? = null,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class WeatherCondition(
    @Json(name = "id") val id: Int,
    @Json(name = "main") val main: String,
    @Json(name = "description") val description: String,
    @Json(name = "icon") val icon: String
)

@JsonClass(generateAdapter = true)
data class WeatherMain(
    @Json(name = "temp") val temp: Double,
    @Json(name = "feels_like") val feelsLike: Double,
    @Json(name = "temp_min") val tempMin: Double,
    @Json(name = "temp_max") val tempMax: Double,
    @Json(name = "pressure") val pressure: Int,
    @Json(name = "humidity") val humidity: Int
)

@JsonClass(generateAdapter = true)
data class Wind(
    @Json(name = "speed") val speed: Double,
    @Json(name = "deg") val deg: Int? = null,
    @Json(name = "gust") val gust: Double? = null
)

@JsonClass(generateAdapter = true)
data class Clouds(
    @Json(name = "all") val all: Int
)

@JsonClass(generateAdapter = true)
data class Precipitation(
    @Json(name = "1h") val oneHour: Double? = null,
    @Json(name = "3h") val threeHour: Double? = null
)

@JsonClass(generateAdapter = true)
data class SystemInfo(
    @Json(name = "country") val country: String? = null,
    @Json(name = "sunrise") val sunrise: Long? = null,
    @Json(name = "sunset") val sunset: Long? = null
)

/**
 * Response from OpenWeatherMap's 5-day / 3-hour forecast API:
 * GET /data/2.5/forecast?lat={}&lon={}&units=metric&lang=ro&appid={key}
 */
@JsonClass(generateAdapter = true)
data class ForecastResponse(
    @Json(name = "list") val list: List<ForecastEntry>,
    @Json(name = "city") val city: ForecastCity? = null
)

@JsonClass(generateAdapter = true)
data class ForecastEntry(
    @Json(name = "dt") val dt: Long,                    // unix seconds
    @Json(name = "main") val main: WeatherMain,
    @Json(name = "weather") val weather: List<WeatherCondition>,
    @Json(name = "wind") val wind: Wind? = null,
    @Json(name = "pop") val precipitationProbability: Double? = null,  // 0..1
    @Json(name = "rain") val rain: Precipitation? = null,
    @Json(name = "snow") val snow: Precipitation? = null,
    @Json(name = "dt_txt") val dtTxt: String? = null
)

@JsonClass(generateAdapter = true)
data class ForecastCity(
    @Json(name = "name") val name: String? = null,
    @Json(name = "country") val country: String? = null,
    @Json(name = "sunrise") val sunrise: Long? = null,
    @Json(name = "sunset") val sunset: Long? = null,
    @Json(name = "timezone") val timezoneOffset: Int? = null
)

/**
 * Response from OpenWeatherMap's Air Pollution API:
 * GET /data/2.5/air_pollution?lat={}&lon={}&appid={key}
 *
 * AQI scale: 1 Good · 2 Fair · 3 Moderate · 4 Poor · 5 Very Poor
 */
@JsonClass(generateAdapter = true)
data class AirPollutionResponse(
    @Json(name = "list") val list: List<AirPollutionEntry>
)

@JsonClass(generateAdapter = true)
data class AirPollutionEntry(
    @Json(name = "dt") val dt: Long,
    @Json(name = "main") val main: AirQualityIndex,
    @Json(name = "components") val components: AirComponents
)

@JsonClass(generateAdapter = true)
data class AirQualityIndex(
    @Json(name = "aqi") val aqi: Int  // 1..5
)

@JsonClass(generateAdapter = true)
data class AirComponents(
    @Json(name = "co") val co: Double? = null,
    @Json(name = "no") val no: Double? = null,
    @Json(name = "no2") val no2: Double? = null,
    @Json(name = "o3") val o3: Double? = null,
    @Json(name = "so2") val so2: Double? = null,
    @Json(name = "pm2_5") val pm25: Double? = null,
    @Json(name = "pm10") val pm10: Double? = null,
    @Json(name = "nh3") val nh3: Double? = null
)
