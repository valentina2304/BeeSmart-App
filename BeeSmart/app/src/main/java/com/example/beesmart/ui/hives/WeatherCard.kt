package com.example.beesmart.ui.hives

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Co2
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.beesmart.data.repository.BeeFlightAdvisor
import com.example.beesmart.network.models.AirPollutionResponse
import com.example.beesmart.network.models.ForecastEntry
import com.example.beesmart.network.models.ForecastResponse
import com.example.beesmart.network.models.WeatherResponse
import com.example.beesmart.ui.theme.HoneyGradientEnd
import com.example.beesmart.ui.theme.HoneyGradientMid
import com.example.beesmart.ui.theme.HoneyGradientStart
import com.example.beesmart.ui.theme.NumberStyle
import com.example.beesmart.ui.theme.StatusDanger
import com.example.beesmart.ui.theme.StatusOk
import com.example.beesmart.ui.theme.StatusWatch
import com.example.beesmart.ui.theme.YellowLight
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val honeyGradient = Brush.linearGradient(
    0f to HoneyGradientStart, 0.55f to HoneyGradientMid, 1f to HoneyGradientEnd
)

@Composable
fun WeatherCard(
    state: WeatherUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = HoneyGradientStart),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(honeyGradient)
        ) {
            when (state) {
                WeatherUiState.Idle, WeatherUiState.Loading -> WeatherLoading()
                is WeatherUiState.Unavailable -> WeatherUnavailable(state.reason)
                is WeatherUiState.Success -> WeatherContent(state)
            }
        }
    }
}

@Composable
private fun WeatherLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun WeatherUnavailable(reason: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun WeatherContent(state: WeatherUiState.Success) {
    val w: WeatherResponse = state.bundle.current
    val condition = w.weather.firstOrNull()
    val description = condition?.description?.replaceFirstChar { it.uppercaseChar() } ?: "-"
    val iconUrl = condition?.icon?.let { "https://openweathermap.org/img/wn/${it}@2x.png" }
    val temp = w.main.temp.roundToInt()
    val feelsLike = w.main.feelsLike.roundToInt()
    val humidity = w.main.humidity
    val windKmH = (w.wind?.speed?.times(3.6))?.roundToInt()
    var detailsExpanded by remember(state.bundle.forecast, state.bundle.airPollution) {
        mutableStateOf(false)
    }
    val hasDetails = state.bundle.forecast != null || state.bundle.airPollution?.list?.isNotEmpty() == true

    Column(modifier = Modifier.padding(14.dp)) {
        // Header: location label
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = state.location,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Main weather row: temp + condition icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$temp°C",
                    style = MaterialTheme.typography.headlineMedium.merge(NumberStyle),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Resimțit: $feelsLike°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            iconUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = description,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        // Humidity + wind row
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherStat(icon = Icons.Default.WaterDrop, label = "$humidity%")
            Spacer(modifier = Modifier.width(16.dp))
            if (windKmH != null) {
                WeatherStat(icon = Icons.Default.Air, label = "$windKmH km/h")
            }
            Spacer(modifier = Modifier.width(16.dp))
            SunTimes(w)
        }

        // Bee advisory: the headline beekeeping signal
        Spacer(modifier = Modifier.height(12.dp))
        BeeAdvisoryChip(state.flight)

        if (hasDetails) {
            TextButton(
                onClick = { detailsExpanded = !detailsExpanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = if (detailsExpanded) "Ascunde prognoza" else "Vezi prognoza si aerul",
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            AnimatedVisibility(visible = detailsExpanded) {
                Column {
                    state.bundle.forecast?.let { forecast ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Următoarele 5 zile",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ForecastStrip(forecast)
                    }

                    state.bundle.airPollution?.list?.firstOrNull()?.let { aq ->
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(8.dp))
                        AirQualityRow(aq.main.aqi, aq.components.pm25, aq.components.pm10)
                    }
                }
            }
        }
    }
}

@Composable
private fun BeeAdvisoryChip(verdict: BeeFlightAdvisor.Verdict) {
    val (bg, fg, icon) = when (verdict.level) {
        BeeFlightAdvisor.Level.OPTIMAL -> Triple(
            StatusOk.copy(alpha = 0.15f),
            StatusOk,
            Icons.Default.CheckCircle
        )
        BeeFlightAdvisor.Level.LIMITED -> Triple(
            StatusWatch.copy(alpha = 0.18f),
            StatusWatch,
            Icons.Default.Warning
        )
        BeeFlightAdvisor.Level.GROUNDED -> Triple(
            StatusDanger.copy(alpha = 0.15f),
            StatusDanger,
            Icons.Default.Error
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = verdict.headline,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = fg
            )
            Text(
                text = verdict.reason,
                style = MaterialTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.85f)
            )
        }
    }
}

/** One row in the forecast strip, aggregated from all 3-hour slots of a single day. */
private data class DailyForecast(
    val date: LocalDate,
    val label: String,
    val iconCode: String?,
    val description: String,
    val minC: Int,
    val maxC: Int,
    val popPercent: Int
)

@Composable
private fun ForecastStrip(forecast: ForecastResponse) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val days = remember(forecast) {
        // OWM 5-day/3-hour returns up to 40 entries (8 slots x 5 days). Group them by
        // local date, then aggregate per day:
        //  - min/max temp from `temp` across ALL slots of the day (each slot's own
        //    temp_min/temp_max only describes a 3-hour window so they collapse together)
        //  - max precipitation probability across the day
        //  - icon + description from the slot closest to noon (most representative)
        forecast.list
            .map { entry ->
                val ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(entry.dt), zone)
                Triple(ldt.toLocalDate(), ldt, entry)
            }
            .groupBy({ it.first }, { it.second to it.third })
            .toSortedMap()
            .map { (date, slots) ->
                val noonSlot = slots.minByOrNull { (ldt, _) ->
                    kotlin.math.abs(ldt.toLocalTime().toSecondOfDay() - LocalTime.NOON.toSecondOfDay())
                }?.second ?: return@map null
                val temps = slots.map { (_, e) -> e.main.temp }
                val popMax = slots.mapNotNull { (_, e) -> e.precipitationProbability }.maxOrNull() ?: 0.0
                DailyForecast(
                    date = date,
                    label = if (date == today) "Azi"
                            else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ro-RO")),
                    iconCode = noonSlot.weather.firstOrNull()?.icon,
                    description = noonSlot.weather.firstOrNull()?.description.orEmpty(),
                    minC = temps.min().roundToInt(),
                    maxC = temps.max().roundToInt(),
                    popPercent = (popMax * 100).roundToInt()
                )
            }
            .filterNotNull()
            .take(5)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        days.forEach { day -> ForecastDayItem(day) }
    }
}

@Composable
private fun ForecastDayItem(day: DailyForecast) {
    val iconUrl = day.iconCode?.let { "https://openweathermap.org/img/wn/${it}.png" }
    Column(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.label.replaceFirstChar { it.uppercaseChar() },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        iconUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = day.description,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(
            text = "${day.maxC}° / ${day.minC}°",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        if (day.popPercent > 0) {
            Text(
                text = "${day.popPercent}% precip.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SunTimes(w: WeatherResponse) {
    val zone = ZoneId.systemDefault()
    val sunrise = w.sys?.sunrise?.let {
        LocalDateTime.ofInstant(Instant.ofEpochSecond(it), zone).format(HOUR_FMT)
    }
    val sunset = w.sys?.sunset?.let {
        LocalDateTime.ofInstant(Instant.ofEpochSecond(it), zone).format(HOUR_FMT)
    }
    if (sunrise == null && sunset == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        sunrise?.let {
            Icon(
                imageVector = Icons.Default.WbSunny,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        sunset?.let {
            Icon(
                imageVector = Icons.Outlined.NightsStay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun AirQualityRow(aqi: Int, pm25: Double?, pm10: Double?) {
    val (label, color) = when (aqi) {
        1 -> "Aer curat" to Color(0xFF2E7D32)
        2 -> "Aer bun" to Color(0xFF689F38)
        3 -> "Aer moderat" to Color(0xFFF9A825)
        4 -> "Aer poluat" to Color(0xFFEF6C00)
        else -> "Aer foarte poluat" to Color(0xFFC62828)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Co2,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            val pmParts = listOfNotNull(
                pm25?.let { "PM2.5: ${it.roundToInt()}" },
                pm10?.let { "PM10: ${it.roundToInt()}" }
            )
            if (pmParts.isNotEmpty()) {
                Text(
                    text = pmParts.joinToString(" · ") + " µg/m³",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun WeatherStat(
    icon: ImageVector,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

private val HOUR_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
