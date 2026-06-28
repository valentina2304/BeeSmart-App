package com.example.beesmart.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.beesmart.R
import com.example.beesmart.data.repository.AdviceCategory
import com.example.beesmart.data.repository.AdvicePriority
import com.example.beesmart.data.repository.ApiaryAdviceDigest
import com.example.beesmart.data.repository.ApiaryRadar
import com.example.beesmart.data.repository.BeekeeperAdvice
import com.example.beesmart.data.repository.ForecastConfidence
import com.example.beesmart.data.repository.HivePriority
import com.example.beesmart.data.repository.HiveRiskLevel
import com.example.beesmart.data.repository.HoneyAnalytics
import com.example.beesmart.data.repository.MonthlyHoneyProduction
import com.example.beesmart.ui.components.BeeSectionHeader
import com.example.beesmart.ui.components.BeeSectionHeaderImage
import com.example.beesmart.ui.theme.BrownPrimary
import com.example.beesmart.ui.theme.GreenSuccess
import com.example.beesmart.ui.theme.HoneyGradientEnd
import com.example.beesmart.ui.theme.HoneyGradientMid
import com.example.beesmart.ui.theme.HoneyGradientStart
import com.example.beesmart.ui.theme.NumberStyle
import com.example.beesmart.ui.theme.PollenAccent
import com.example.beesmart.ui.theme.RedError
import com.example.beesmart.ui.theme.SageSoft
import com.example.beesmart.ui.theme.StatusDanger
import com.example.beesmart.ui.theme.StatusOk
import com.example.beesmart.ui.theme.StatusWatch
import com.example.beesmart.ui.theme.YellowLight
import com.example.beesmart.ui.theme.YellowPrimary
import java.time.Year
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HoneyForecastCard(
    analytics: HoneyAnalytics,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, onClickLabel = "Vezi extracțiile"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prognoză sezon ${Year.now().value}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Calculată local din jurnalul stupinei",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ForecastConfidenceBadge(analytics.confidence)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ForecastMetric(
                    modifier = Modifier.weight(1f),
                    label = "Prognoză sezon",
                    value = analytics.seasonForecastRange(),
                    emphasize = true
                )
                ForecastMetric(
                    modifier = Modifier.weight(1f),
                    label = "Următoarea extracție",
                    value = analytics.nextHarvestPotentialRange()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Producție miere · ultimele 6 luni",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TrendBadge(analytics.trendPercent)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HoneyProductionChart(analytics.monthlyProduction)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ForecastMiniMetric(
                    modifier = Modifier.weight(1f),
                    label = "Recoltat anul acesta",
                    value = "${analytics.currentYearKg.asKg()} kg"
                )
                ForecastMiniMetric(
                    modifier = Modifier.weight(1f),
                    label = "Productivitate",
                    value = "${analytics.kgPerActiveHive.asKg()} kg/stup"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = BrownPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = analytics.forecastExplanation(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ForecastMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false
) {
    Column(
        modifier = modifier
            .background(
                color = if (emphasize) YellowPrimary.copy(alpha = 0.16f) else GreenSuccess.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (emphasize) BrownPrimary else GreenSuccess
        )
    }
}

@Composable
private fun ForecastMiniMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ForecastConfidenceBadge(confidence: ForecastConfidence) {
    val (label, color) = when (confidence) {
        ForecastConfidence.LOW -> "Încredere redusă" to MaterialTheme.colorScheme.error
        ForecastConfidence.MEDIUM -> "Încredere medie" to YellowPrimary
        ForecastConfidence.HIGH -> "Încredere ridicată" to GreenSuccess
    }

    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun TrendBadge(trendPercent: Int?) {
    val trendColor = when {
        trendPercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        trendPercent >= 0 -> GreenSuccess
        else -> MaterialTheme.colorScheme.error
    }
    val text = when {
        trendPercent == null -> "Istoric în formare"
        trendPercent >= 0 -> "+$trendPercent%"
        else -> "$trendPercent%"
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = trendColor
    )
}

@Composable
private fun HoneyProductionChart(points: List<MonthlyHoneyProduction>) {
    if (points.isEmpty()) return
    val maxKg = points.maxOf { it.kilograms }.coerceAtLeast(1.0)
    var chartReady by remember(points) { mutableStateOf(false) }
    LaunchedEffect(points) { chartReady = true }
    val reveal by animateFloatAsState(
        targetValue = if (chartReady) 1f else 0f,
        animationSpec = tween(durationMillis = 760, easing = FastOutSlowInEasing),
        label = "honey_chart_reveal"
    )

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
        ) {
            val step = size.width / points.size
            val barWidth = step * 0.56f
            drawLine(
                color = BrownPrimary.copy(alpha = 0.18f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f
            )
            points.forEachIndexed { index, point ->
                val proportionalHeight = ((point.kilograms / maxKg) * size.height).toFloat()
                val baseHeight = if (point.kilograms > 0.0) proportionalHeight.coerceAtLeast(6f) else 3f
                val barHeight = baseHeight * reveal
                drawRoundRect(
                    color = if (point.kilograms > 0.0) YellowPrimary else YellowPrimary.copy(alpha = 0.22f),
                    topLeft = Offset(index * step + (step - barWidth) / 2, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    text = point.monthLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

private fun HoneyAnalytics.forecastExplanation(): String {
    if (lifetimeKg <= 0.0 && honeyFrames == 0) {
        return "Adaugă extracțiile și actualizează ramele cu miere pentru o prognoză relevantă."
    }
    if (usesSeasonalHistory) {
        val yearsLabel = if (seasonalReferenceYears == 1) "un an anterior" else "$seasonalReferenceYears ani anteriori"
        val frameNote = if (honeyFrames > 0) {
            " Ramele cu miere rămân un semnal de verificare în teren, dar nu înlocuiesc sezonalitatea istorică."
        } else {
            ""
        }
        return "Interval orientativ: producția înregistrată în ${Year.now().value} plus media aceleiași perioade din $yearsLabel. " +
            "Estimarea evită extrapolarea din luna precedentă și scade producția deja extrasă în perioada curentă.$frameNote"
    }
    if (honeyFrames == 0) {
        return "Prognoza include producția înregistrată. Actualizează ramele cu miere pentru estimarea următoarei extracții."
    }
    val frameNote = if (usesFallbackFrameCapacity) {
        " Pentru tipurile fără reper explicit în suport este folosită prudent capacitatea ramei multietajate."
    } else {
        ""
    }
    return "Interval orientativ: producția înregistrată în ${Year.now().value} plus $honeyFrames rame cu miere. " +
        "Calculul folosește echivalentul de ramă complet căpăcită (Dadant 3,6 kg; multietajat 2,3 kg), " +
        "iar extracția se decide numai după verificarea fizică a căpăcirii.$frameNote"
}

private fun HoneyAnalytics.seasonForecastRange(): String =
    kgRange(seasonForecastMinKg, seasonForecastMaxKg)

private fun HoneyAnalytics.nextHarvestPotentialRange(): String =
    kgRange(nextHarvestPotentialMinKg, nextHarvestPotentialMaxKg)

private fun kgRange(minimum: Double, maximum: Double): String =
    if (minimum == maximum) "${minimum.asKg()} kg"
    else "${minimum.asKg()}-${maximum.asKg()} kg"

private fun MonthlyHoneyProduction.monthLabel(): String =
    month.format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("ro-RO")))
        .take(3)
        .replaceFirstChar { it.uppercase() }

private fun Double.asKg(): String =
    if (this >= 100.0) String.format(Locale.forLanguageTag("ro-RO"), "%.0f", this)
    else String.format(Locale.forLanguageTag("ro-RO"), "%.1f", this)
