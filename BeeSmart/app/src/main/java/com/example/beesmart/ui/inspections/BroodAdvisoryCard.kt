package com.example.beesmart.ui.inspections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.beesmart.data.repository.BroodAnalyzer
import com.example.beesmart.network.models.CellDetection
import com.example.beesmart.ui.theme.BrownPrimary
import com.example.beesmart.ui.theme.GreenSuccess
import com.example.beesmart.ui.theme.StatusDanger
import com.example.beesmart.ui.theme.StatusOk
import com.example.beesmart.ui.theme.StatusWatch
import com.example.beesmart.ui.theme.YellowPrimary
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun BroodAdvisoryCard(
    state: AnalyzeCellsUiState.Success,
    modifier: Modifier = Modifier
) {
    val report = state.report
    val metrics = report.metrics
    val spatial = report.spatial
    val verdict = report.verdict
    val (verdictBg, verdictFg, verdictIcon) = verdictColors(verdict.level)
    val meanConfidence = state.cellDetections
        .map { it.confidence }
        .filter { it.isFinite() }
        .takeIf { it.isNotEmpty() }
        ?.average()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ResultHeader(
                totalCells = metrics.total,
                detectedCoordinates = spatial.analyzedCells,
                meanConfidence = meanConfidence,
                status = state.status
            )

            VerdictPanel(
                headline = verdict.headline,
                background = verdictBg,
                foreground = verdictFg,
                icon = verdictIcon
            )

            KeyMetricsRow(metrics = metrics, spatial = spatial)

            if (metrics.total > 0) {
                SectionLabel(
                    icon = Icons.Default.Analytics,
                    title = "Compoziția ramei",
                    subtitle = "Distribuția claselor detectate"
                )
                CompositionBar(metrics)
            }

            if (state.cellDetections.isNotEmpty()) {
                SectionLabel(
                    icon = Icons.Default.PinDrop,
                    title = "Harta celulelor",
                    subtitle = "Poziția fiecărei celule detectate pe fotografie"
                )
                CellMapPreview(cellDetections = state.cellDetections)
                CellMapLegend()
            }

            SectionLabel(
                icon = Icons.Default.GridView,
                title = "Indicatori apicoli",
                subtitle = "Numărători, ritm de puiet și rezerve"
            )
            MetricsGrid(metrics)

            SpatialMetricsPanel(spatial = spatial)

            InsightSections(verdict = verdict)

            state.message?.let { msg ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultHeader(
    totalCells: Int,
    detectedCoordinates: Int,
    meanConfidence: Double?,
    status: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Insights,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Analiză AI a ramei",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = buildList {
                    add("$totalCells celule")
                    if (detectedCoordinates > 0) add("$detectedCoordinates cu coordonate")
                    meanConfidence?.let { add("încredere ${it.asPercent()}") }
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusPill(status = status)
    }
}

@Composable
private fun StatusPill(status: String) {
    val normalized = status.lowercase(Locale.ROOT)
    val color = when {
        normalized.contains("demo") -> StatusWatch
        normalized.contains("success") -> StatusOk
        normalized.contains("uncertain") -> StatusWatch
        else -> MaterialTheme.colorScheme.primary
    }
    Text(
        text = when {
            normalized.contains("demo") -> "demo"
            normalized.contains("uncertain") -> "verifică"
            normalized.contains("success") -> "salvat"
            else -> "AI"
        },
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun VerdictPanel(
    headline: String,
    background: Color,
    foreground: Color,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = foreground, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = foreground
        )
    }
}

@Composable
private fun KeyMetricsRow(metrics: BroodAnalyzer.Metrics, spatial: BroodAnalyzer.SpatialMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KeyMetricTile(
            label = "Puiet",
            value = metrics.broodTotal.toString(),
            accent = GreenSuccess,
            modifier = Modifier.weight(1f)
        )
        KeyMetricTile(
            label = "Rezerve",
            value = (metrics.totals[BroodAnalyzer.Category.HONEY].orZero() +
                metrics.totals[BroodAnalyzer.Category.POLLEN].orZero()).toString(),
            accent = YellowPrimary,
            modifier = Modifier.weight(1f)
        )
        KeyMetricTile(
            label = "Goluri",
            value = metrics.totals[BroodAnalyzer.Category.EMPTY].orZero().toString(),
            accent = StatusWatch,
            modifier = Modifier.weight(1f)
        )
        KeyMetricTile(
            label = "Spațial",
            value = if (spatial.hasCoordinates) "da" else "-",
            accent = BrownPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun KeyMetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

@Composable
private fun SectionLabel(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = BrownPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(7.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CompositionBar(metrics: BroodAnalyzer.Metrics) {
    val total = metrics.total.coerceAtLeast(1)
    val segments = cellSegments(metrics).filter { it.count > 0 }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
    ) {
        segments.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight((segment.count.toFloat() / total).coerceAtLeast(0.001f))
                    .fillMaxSize()
                    .background(segment.color)
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { segment ->
                    LegendChip(segment = segment, total = total, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegendChip(segment: CellSegment, total: Int, modifier: Modifier = Modifier) {
    val pct = if (total > 0) ((segment.count.toFloat() / total) * 100).roundToInt() else 0
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(segment.color, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "${segment.label} · ${segment.count} ($pct%)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CellMapPreview(cellDetections: List<CellDetection>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.62f)
            .background(Color(0xFFF8FAF7), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val radiusBase = min(w, h)
            cellDetections.forEach { cell ->
                val x = cell.normalizedX
                val y = cell.normalizedY
                if (x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0) {
                    drawCircle(
                        color = cellColor(cell.className).copy(alpha = confidenceAlpha(cell.confidence)),
                        radius = (cell.normalizedRadius * radiusBase).toFloat().coerceIn(1.7f, 4.2f),
                        center = Offset((x * w).toFloat(), (y * h).toFloat())
                    )
                }
            }
        }
    }
}

@Composable
private fun CellMapLegend() {
    val legend = listOf(
        "Puiet" to GreenSuccess,
        "Miere" to YellowPrimary,
        "Polen" to Color(0xFFFF9800),
        "Goale" to Color(0xFF90A4AE)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        legend.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MetricsGrid(metrics: BroodAnalyzer.Metrics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricRow("Densitate puiet", metrics.broodDensity, "puiet / celule ocupate", asPercent = true)
        MetricRow("Larve din total", metrics.larvaeDensity, "puiet deschis detectat", asPercent = true)
        MetricRow("Ouă din puiet", metrics.eggsRatio, "continuitatea pontei", asPercent = true)
        MetricRow("Larve / căpăcit", metrics.larvaeToCappedRatio, "ritm relativ al pontei", asPercent = false)
        MetricRow("Celule goale", metrics.emptyRatio, "spațiu sau goluri de verificat", asPercent = true)
        MetricRow("Rezerve", metrics.storesRatio, "miere + polen / total", asPercent = true)
    }
}

@Composable
private fun SpatialMetricsPanel(spatial: BroodAnalyzer.SpatialMetrics) {
    SectionLabel(
        icon = Icons.Default.PinDrop,
        title = "Pattern spațial",
        subtitle = if (spatial.hasCoordinates) {
            "Coordonatele ajută la citirea cuibului, rezervelor și polenului"
        } else {
            "Analiza salvată nu include coordonate pentru celule"
        }
    )

    if (!spatial.hasCoordinates) {
        Text(
            text = "Refă analiza cu fluxul DeepBee curent pentru a vedea compactitatea, golurile și poziția rezervelor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpatialMetricRow(
            label = "Compactitate puiet",
            value = spatial.broodCompactness,
            note = "zona conectată principală",
            color = spatial.broodCompactness.semanticColor(goodHigh = true, warning = 0.45, healthy = 0.65)
        )
        SpatialMetricRow(
            label = "Goluri în cuib",
            value = spatial.broodGapRatio,
            note = "non-puiet în aria puietului",
            color = spatial.broodGapRatio.semanticColor(goodHigh = false, warning = 0.55, healthy = 0.35)
        )
        SpatialMetricRow(
            label = "Rezerve pe margini",
            value = spatial.storesEdgeRatio,
            note = "miere/polen pe coroană și margini",
            color = spatial.storesEdgeRatio.semanticColor(goodHigh = true, warning = 0.25, healthy = 0.55)
        )
        SpatialMetricRow(
            label = "Polen lângă puiet",
            value = spatial.pollenNearBroodRatio,
            note = "suport proteic aproape de larve",
            color = spatial.pollenNearBroodRatio.semanticColor(goodHigh = true, warning = 0.25, healthy = 0.45)
        )
    }
}

@Composable
private fun MetricRow(label: String, value: Double, note: String, asPercent: Boolean) {
    val displayValue = value.formatMetric(asPercent)
    val barFraction = if (value.isFinite()) value.coerceIn(0.0, 1.0).toFloat() else 0f
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text(text = note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = displayValue, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        if (asPercent) {
            LinearProgressIndicator(
                progress = { barFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun SpatialMetricRow(label: String, value: Double, note: String, color: Color) {
    MetricRowContainer {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text(text = note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = value.formatMetric(asPercent = true), color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricRowContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        content()
    }
}

@Composable
private fun InsightSections(verdict: BroodAnalyzer.Verdict) {
    if (verdict.highlights.isNotEmpty()) {
        BulletSection(
            title = "Aspecte pozitive",
            icon = Icons.Default.CheckCircle,
            color = StatusOk,
            items = verdict.highlights
        )
    }
    if (verdict.concerns.isNotEmpty()) {
        BulletSection(
            title = "Atenție",
            icon = Icons.Default.Warning,
            color = StatusWatch,
            items = verdict.concerns
        )
    }
    if (verdict.recommendations.isNotEmpty()) {
        BulletSection(
            title = "Pași recomandați",
            icon = Icons.Default.Lightbulb,
            color = MaterialTheme.colorScheme.primary,
            items = verdict.recommendations
        )
    }
}

@Composable
private fun BulletSection(
    title: String,
    icon: ImageVector,
    color: Color,
    items: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
        items.forEach { item ->
            Row(modifier = Modifier.padding(start = 24.dp)) {
                Text(text = "• ", style = MaterialTheme.typography.bodySmall)
                Text(text = item, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private data class CellSegment(
    val label: String,
    val count: Int,
    val color: Color
)

private fun cellSegments(metrics: BroodAnalyzer.Metrics) = listOf(
    CellSegment("Căpăcit", metrics.totals[BroodAnalyzer.Category.CAPPED_BROOD].orZero(), Color(0xFF6D4C41)),
    CellSegment("Larve", metrics.totals[BroodAnalyzer.Category.LARVAE].orZero(), Color(0xFFFFA726)),
    CellSegment("Ouă", metrics.totals[BroodAnalyzer.Category.EGGS].orZero(), Color(0xFFFFEB3B)),
    CellSegment("Miere", metrics.totals[BroodAnalyzer.Category.HONEY].orZero(), YellowPrimary),
    CellSegment("Polen", metrics.totals[BroodAnalyzer.Category.POLLEN].orZero(), Color(0xFFFF9800)),
    CellSegment("Goale", metrics.totals[BroodAnalyzer.Category.EMPTY].orZero(), Color(0xFF90A4AE)),
    CellSegment("Altele", metrics.totals[BroodAnalyzer.Category.OTHER].orZero(), BrownPrimary)
)

private fun cellColor(className: String): Color {
    val normalized = Normalizer.normalize(className.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
    return when {
        normalized.contains("honey") || normalized.contains("miere") || normalized.contains("nectar") -> YellowPrimary
        normalized.contains("pollen") || normalized.contains("polen") -> Color(0xFFFF9800)
        normalized.contains("empty") || normalized.contains("goale") || normalized.contains("goala") -> Color(0xFF90A4AE)
        normalized.contains("larv") -> Color(0xFFFFA726)
        normalized.contains("egg") || normalized.contains("ou") -> Color(0xFFFFEB3B)
        normalized.contains("capped") || normalized.contains("capac") || normalized.contains("brood") -> GreenSuccess
        else -> BrownPrimary
    }
}

private fun confidenceAlpha(confidence: Double): Float =
    when {
        !confidence.isFinite() -> 0.58f
        confidence >= 0.90 -> 0.88f
        confidence >= 0.70 -> 0.72f
        else -> 0.50f
    }

private fun Double.formatMetric(asPercent: Boolean): String =
    when {
        !isFinite() -> "-"
        asPercent -> "${(coerceIn(0.0, 1.0) * 100).roundToInt()}%"
        else -> String.format(Locale.US, "%.2f", this)
    }

private fun Double.asPercent(): String =
    if (isFinite()) "${(coerceIn(0.0, 1.0) * 100).roundToInt()}%" else "-"

private fun Double.semanticColor(goodHigh: Boolean, warning: Double, healthy: Double): Color {
    if (!isFinite()) return MaterialThemeUnavailableColor
    return if (goodHigh) {
        when {
            this >= healthy -> StatusOk
            this <= warning -> StatusWatch
            else -> YellowPrimary
        }
    } else {
        when {
            this <= healthy -> StatusOk
            this >= warning -> StatusWatch
            else -> YellowPrimary
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0

private val MaterialThemeUnavailableColor = Color(0xFF9E9E9E)

private fun verdictColors(level: BroodAnalyzer.Level): Triple<Color, Color, ImageVector> = when (level) {
    BroodAnalyzer.Level.HEALTHY -> Triple(
        StatusOk.copy(alpha = 0.15f),
        StatusOk,
        Icons.Default.CheckCircle
    )
    BroodAnalyzer.Level.ATTENTION -> Triple(
        StatusWatch.copy(alpha = 0.18f),
        StatusWatch,
        Icons.Default.Bolt
    )
    BroodAnalyzer.Level.WARNING -> Triple(
        StatusDanger.copy(alpha = 0.15f),
        StatusDanger,
        Icons.Default.Error
    )
}
