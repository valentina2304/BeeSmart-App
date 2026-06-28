package com.example.beesmart.ui.hives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.beesmart.R
import com.example.beesmart.data.repository.BroodAnalyzer
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.InspectionAiAnalysisResponse
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.BrownPrimary
import com.example.beesmart.ui.theme.Gray
import com.example.beesmart.ui.theme.GreenSuccess
import com.example.beesmart.ui.theme.LightGray
import com.example.beesmart.ui.theme.RedError
import com.example.beesmart.ui.theme.StatusOk
import com.example.beesmart.ui.theme.StatusWatch
import com.example.beesmart.ui.theme.YellowPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.Year
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiveStatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: HiveDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sx_hive_stats_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.sx_hive_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.sx_hive_refresh))
                    }
                },
                colors = beeSmartTopAppBarColors()
            )
        }
    ) { padding ->
        when (val state = uiState) {
            HiveDetailUiState.Loading -> StatsLoading(Modifier.padding(padding))
            is HiveDetailUiState.Error -> StatsError(
                message = state.message,
                onRetry = { viewModel.refresh() },
                modifier = Modifier.padding(padding)
            )
            is HiveDetailUiState.Success -> HiveStatsContent(
                hive = state.hive,
                analyses = state.aiAnalyses,
                message = state.statsMessage,
                isStatsLoading = state.isStatsLoading,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun HiveStatsContent(
    hive: HiveResponse,
    analyses: List<InspectionAiAnalysisResponse>,
    message: String?,
    isStatsLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val reportModel by produceState<StatsReportModel?>(initialValue = null, analyses) {
        value = if (analyses.isEmpty()) {
            null
        } else {
            value = null
            withContext(Dispatchers.Default) {
                buildStatsReportModel(analyses)
            }
        }
    }
    var selectedMonth by remember { mutableIntStateOf(OffsetDateTime.now().monthValue) }
    LaunchedEffect(reportModel?.latestPoint?.month) {
        reportModel?.latestPoint?.month?.let { selectedMonth = it }
    }
    val byYearForMonth = remember(reportModel?.points, selectedMonth) {
        reportModel?.points.orEmpty().filter { it.month == selectedMonth }
            .groupBy { it.year }
            .toSortedMap()
            .map { (year, values) ->
                StatsChartPoint(label = year.toString(), value = values.map { it.score }.averageOrNull())
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatsHeader(hive = hive, analysesCount = analyses.size, isStatsLoading = isStatsLoading)

        if (analyses.isEmpty()) {
            if (isStatsLoading) {
                StatsLoadingSummary()
            } else {
                EmptyStats(message = message)
            }
        } else if (reportModel == null) {
            StatsLoadingSummary()
        } else {
            val model = requireNotNull(reportModel)
            val latestAnalysis = model.latestAnalysis?.analysis
            val latestPoint = model.latestPoint
            val trendDelta = model.trendDelta
            val latestYear = model.latestYear

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsMetricTile(stringResource(R.string.sx_hive_stats_metric_cells_analyzed), latestAnalysis?.totalCells?.toString() ?: "-", BrownPrimary, Modifier.weight(1f))
                StatsMetricTile(stringResource(R.string.sx_hive_stats_metric_frame_indicator), latestPoint?.score?.let { "${it.toInt()}%" } ?: "-", latestPoint?.score.healthColor(), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatsMetricTile(stringResource(R.string.sx_hive_stats_metric_brood), latestAnalysis?.broodCells?.toString() ?: "-", GreenSuccess, Modifier.weight(1f))
                StatsMetricTile(stringResource(R.string.sx_hive_stats_metric_stores), latestAnalysis?.storesCells?.toString() ?: "-", YellowPrimary, Modifier.weight(1f))
                StatsMetricTile(stringResource(R.string.sx_hive_stats_metric_trend), trendDelta?.formatDelta() ?: "-", trendDelta.trendColor(), Modifier.weight(1f))
            }

            StatsSection(
                title = "Evoluție lunară $latestYear",
                subtitle = stringResource(R.string.sx_hive_stats_section_monthly_subtitle)
            ) {
                LargeLineChart(points = model.monthly)
            }

            StatsSection(
                title = stringResource(R.string.sx_hive_stats_section_yearly_comparison),
                subtitle = stringResource(R.string.sx_hive_stats_section_yearly_comparison_subtitle)
            ) {
                MonthSelector(selectedMonth = selectedMonth, onMonthSelected = { selectedMonth = it })
                YearBars(points = byYearForMonth)
            }

            model.latestAnalysis?.let { latestSummary ->
                val analysis = latestSummary.analysis
                val spatial = latestSummary.spatial

                StatsSection(
                    title = stringResource(R.string.sx_hive_stats_section_cell_distribution),
                    subtitle = "Ultima analiză salvată: ${analysis.inspectionDate.take(10)}"
                ) {
                    latestSummary.cellStats.forEach { stat ->
                        CellDistributionRow(stat = stat, total = analysis.totalCells)
                    }
                }

                StatsSection(
                    title = stringResource(R.string.sx_hive_stats_section_indicators),
                    subtitle = stringResource(R.string.sx_hive_stats_section_indicators_subtitle)
                ) {
                    IndicatorRow(stringResource(R.string.sx_hive_stats_indicator_brood_density), analysis.broodDensity.asPercent(), stringResource(R.string.sx_hive_stats_indicator_brood_density_note))
                    IndicatorRow(stringResource(R.string.sx_hive_stats_indicator_larvae_capped), analysis.larvaeToCappedRatio.asRatio(), stringResource(R.string.sx_hive_stats_indicator_larvae_capped_note))
                    IndicatorRow(stringResource(R.string.sx_hive_stats_indicator_stores), analysis.storesRatio.asPercent(), stringResource(R.string.sx_hive_stats_indicator_stores_note))
                    if (spatial.hasCoordinates) {
                        IndicatorRow(stringResource(R.string.sx_hive_stats_indicator_brood_compactness), spatial.broodCompactness.asPercent(), stringResource(R.string.sx_hive_stats_indicator_brood_compactness_note))
                        IndicatorRow(stringResource(R.string.sx_hive_stats_indicator_brood_gaps), spatial.broodGapRatio.asPercent(), stringResource(R.string.sx_hive_stats_indicator_brood_gaps_note))
                        IndicatorRow(stringResource(R.string.sx_hive_stats_indicator_stores_edge), spatial.storesEdgeRatio.asPercent(), stringResource(R.string.sx_hive_stats_indicator_stores_edge_note))
                        IndicatorRow(stringResource(R.string.sx_hive_stats_indicator_pollen_near_brood), spatial.pollenNearBroodRatio.asPercent(), stringResource(R.string.sx_hive_stats_indicator_pollen_near_brood_note))
                    }
                }

                StatsSection(
                    title = stringResource(R.string.sx_hive_stats_section_interpretation),
                    subtitle = stringResource(R.string.sx_hive_stats_section_interpretation_subtitle)
                ) {
                    latestSummary.insights.forEach { insight ->
                        Text("• $insight", style = MaterialTheme.typography.bodySmall, color = Color(0xFF3B332B))
                    }
                }
            }

            MethodologyNote()
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun StatsHeader(hive: HiveResponse, analysesCount: Int, isStatsLoading: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = GreenSuccess)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sx_hive_stats_longitudinal_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Text(hive.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Stupină: ${hive.apiaryName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (isStatsLoading && analysesCount == 0) {
                "Se încarcă analizele DeepBee..."
            } else {
                "$analysesCount analize agregate pentru acest stup"
            },
            color = BrownPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatsSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        content()
    }
}

@Composable
private fun StatsMetricTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 78.dp)
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(value, color = accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyStats(message: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(painter = painterResource(id = R.drawable.ic_ai), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
        Text(stringResource(R.string.sx_hive_stats_empty_title), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            message ?: stringResource(R.string.sx_hive_stats_empty_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatsLoadingSummary() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(color = YellowPrimary, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        Text("Pregătim raportul DeepBee...", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            "Încărcăm istoricul și calculăm graficul.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LargeLineChart(points: List<StatsChartPoint>) {
    val valid = points.mapIndexedNotNull { index, point ->
        point.value?.let { index to it.coerceIn(0.0, 100.0) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color(0xFFF8FAF7), RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            val left = 36f
            val top = 18f
            val right = size.width - 12f
            val bottom = size.height - 32f
            val chartWidth = right - left
            val chartHeight = bottom - top

            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { ratio ->
                val y = bottom - chartHeight * ratio
                drawLine(
                    color = Color(0xFFE2E7DB),
                    start = androidx.compose.ui.geometry.Offset(left, y),
                    end = androidx.compose.ui.geometry.Offset(right, y),
                    strokeWidth = 1.2f
                )
            }
            drawLine(Color(0xFFB8BFAF), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left, bottom), 1.5f)
            drawLine(Color(0xFFB8BFAF), androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(right, bottom), 1.5f)

            if (valid.isNotEmpty()) {
                val coordinates = valid.map { (index, value) ->
                    val x = left + chartWidth * (index / 11f)
                    val y = bottom - chartHeight * (value / 100f).toFloat()
                    androidx.compose.ui.geometry.Offset(x, y)
                }
                val path = Path().apply {
                    moveTo(coordinates.first().x, coordinates.first().y)
                    coordinates.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, GreenSuccess, style = Stroke(width = 5f))
                coordinates.forEachIndexed { index, offset ->
                    val value = valid[index].second
                    drawCircle(color = value.healthColor(), radius = 8f, center = offset)
                    drawCircle(color = Color(0xFFF8FAF7), radius = 3.5f, center = offset)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(1, 3, 6, 9, 12).forEach { month ->
                Text(month.shortMonthName(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MonthSelector(selectedMonth: Int, onMonthSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (1..12).forEach { month ->
            AssistChip(
                onClick = { onMonthSelected(month) },
                label = { Text(month.shortMonthName()) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selectedMonth == month) YellowPrimary.copy(alpha = 0.25f) else Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun YearBars(points: List<StatsChartPoint>) {
    if (points.isEmpty()) {
        Text(stringResource(R.string.sx_hive_stats_no_analyses_for_month), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            points.forEach { point ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(point.label, modifier = Modifier.width(52.dp), fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(((point.value ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f))
                                .background(point.value.healthColor(), RoundedCornerShape(7.dp))
                        )
                    }
                    Text(
                        text = point.value?.let { "${it.toInt()}%" } ?: "-",
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CellDistributionRow(stat: StatsCellStat, total: Int) {
    val pct = if (total <= 0) 0.0 else stat.count.toDouble() / total.toDouble()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("${stat.count} (${(pct * 100).toInt()}%)", fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct.toFloat().coerceIn(0f, 1f))
                    .background(stat.color, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun IndicatorRow(label: String, value: String, note: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Text(value, fontWeight = FontWeight.Bold, color = BrownPrimary)
    }
}

@Composable
private fun MethodologyNote() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrownPrimary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(stringResource(R.string.sx_hive_stats_methodology_title), fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.sx_hive_stats_methodology_text),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StatsLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = YellowPrimary)
    }
}

@Composable
private fun StatsError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.sx_hive_stats_retry))
            }
        }
    }
}

private data class StatsHealthPoint(val year: Int, val month: Int, val score: Double)
private data class StatsChartPoint(val label: String, val value: Double?)
private data class StatsCellStat(val label: String, val count: Int, val color: Color)
private data class StatsAnalysisSummary(
    val analysis: InspectionAiAnalysisResponse,
    val timestamp: Long,
    val point: StatsHealthPoint?,
    val spatial: BroodAnalyzer.SpatialMetrics,
    val cellStats: List<StatsCellStat>,
    val insights: List<String>
)
private data class StatsReportModel(
    val points: List<StatsHealthPoint>,
    val latestAnalysis: StatsAnalysisSummary?,
    val latestPoint: StatsHealthPoint?,
    val trendDelta: Double?,
    val latestYear: Int,
    val monthly: List<StatsChartPoint>
)

private fun buildStatsReportModel(analyses: List<InspectionAiAnalysisResponse>): StatsReportModel {
    val summaries = analyses.map { analysis ->
        val spatial = analysis.spatialMetrics()
        StatsAnalysisSummary(
            analysis = analysis,
            timestamp = analysis.inspectionTimestamp(),
            point = analysis.toHealthPoint(spatial),
            spatial = spatial,
            cellStats = analysis.cellStats(),
            insights = analysis.apicultureInsights(spatial)
        )
    }
    val points = summaries.mapNotNull { it.point }
    val sortedPoints = points.sortedWith(compareBy({ it.year }, { it.month }))
    val latestPoint = sortedPoints.lastOrNull()
    val previousPoint = sortedPoints.dropLast(1).lastOrNull()
    val latestYear = points.maxOfOrNull { it.year } ?: Year.now().value
    val monthly = (1..12).map { month ->
        StatsChartPoint(
            label = month.shortMonthName(),
            value = points.filter { it.year == latestYear && it.month == month }
                .map { it.score }
                .averageOrNull()
        )
    }

    return StatsReportModel(
        points = points,
        latestAnalysis = summaries.maxByOrNull { it.timestamp },
        latestPoint = latestPoint,
        trendDelta = latestPoint?.let { latest -> previousPoint?.let { latest.score - it.score } },
        latestYear = latestYear,
        monthly = monthly
    )
}

private fun InspectionAiAnalysisResponse.inspectionTimestamp(): Long =
    runCatching { OffsetDateTime.parse(inspectionDate).toInstant().toEpochMilli() }.getOrDefault(0L)

private fun InspectionAiAnalysisResponse.cellStats(): List<StatsCellStat> = listOf(
    StatsCellStat("Puiet căpăcit", cappedBroodCells, GreenSuccess),
    StatsCellStat("Larve", larvaeCells, StatusOk),
    StatsCellStat("Ouă", eggsCells, Color(0xFF66BB6A)),
    StatsCellStat("Miere", honeyCells, YellowPrimary),
    StatsCellStat("Polen", pollenCells, StatusWatch),
    StatsCellStat("Celule goale", emptyCells, Gray),
    StatsCellStat("Alte celule", otherCells, BrownPrimary)
).filter { it.count > 0 }

private fun InspectionAiAnalysisResponse.apicultureInsights(spatial: BroodAnalyzer.SpatialMetrics): List<String> {
    val insights = mutableListOf<String>()
    val broodPct = if (totalCells > 0) broodCells.toDouble() / totalCells else 0.0
    val storesPct = storesRatio ?: 0.0

    if (broodCells == 0 && totalCells > 0) insights += "Nu s-a detectat puiet pe rama analizată; verifică mai întâi dacă rama aparține cuibului și compară cu ramele vecine."
    if (cappedBroodCells > 0 && larvaeCells == 0) insights += "Există puiet căpăcit, dar nu s-au identificat larve pe această ramă; compară cu ramele vecine și cu evoluția coloniei."
    if (larvaeCells > 0 && cappedBroodCells == 0) insights += "Larve prezente fără puiet căpăcit; colonia poate fi într-o etapă timpurie a ciclului de puiet."
    if (eggsCells > 0 && larvaeCells > 0 && cappedBroodCells > 0) insights += "Sunt prezente ouă, larve și puiet căpăcit; semnal bun pentru continuitatea pontei."
    if (broodPct >= 0.55) insights += "Ponderea puietului pe rama analizată este ridicată; uniformitatea se confirmă prin inspecție vizuală."
    else if (broodPct in 0.01..0.25) insights += "Ponderea puietului pe rama analizată este redusă; compară cu inspecțiile anterioare și cu ramele vecine."
    if (storesPct >= 0.20) insights += "Rezervele de miere și polen sunt consistente pentru rama analizată."
    else if (totalCells > 30) insights += "Rezervele par limitate pe rama analizată; verifică ramele vecine, sezonul și vremea înainte de a decide hrănirea."
    if ((larvaeToCappedRatio ?: 1.0) < 0.20 && cappedBroodCells >= 20) insights += "Raportul larve/căpăcit este scăzut; merită urmărit la următoarea inspecție."

    if (spatial.hasCoordinates) {
        val compactness = spatial.broodCompactness.finiteOrNull()
        val broodGaps = spatial.broodGapRatio.finiteOrNull()
        val storesEdge = spatial.storesEdgeRatio.finiteOrNull()
        val pollenNearBrood = spatial.pollenNearBroodRatio.finiteOrNull()

        if (compactness != null && broodGaps != null) {
            when {
                compactness >= 0.65 && broodGaps <= 0.35 -> {
                    insights += "Coordonatele arata puiet relativ compact si putine goluri in zona cuibului."
                }
                compactness < 0.45 || broodGaps > 0.55 -> {
                    insights += "Coordonatele sugereaza puiet dispersat sau goluri in zona cuibului; verifica uniformitatea pe rama si pe ramele vecine."
                }
            }
        }
        storesEdge?.let {
            when {
                it >= 0.55 -> insights += "Mierea si polenul sunt predominant pe marginea ramei, un pattern compatibil cu rezerve in jurul cuibului."
                it < 0.25 -> insights += "Rezervele apar mai centrale; verifica daca zona de ponta are suficient spatiu liber."
            }
        }
        pollenNearBrood?.let {
            when {
                it >= 0.45 -> insights += "Polenul detectat este aproape de puiet, util pentru cresterea larvelor."
                it < 0.25 -> insights += "Polenul apare departe de puiet; verifica aportul proteic pe ramele alaturate."
            }
        }
    }

    return insights.ifEmpty {
        listOf("Datele nu indică semnale majore; continuă monitorizarea pe inspecțiile următoare.")
    }
}

private fun InspectionAiAnalysisResponse.toHealthPoint(spatial: BroodAnalyzer.SpatialMetrics): StatsHealthPoint? {
    val date = runCatching { OffsetDateTime.parse(inspectionDate) }.getOrNull() ?: return null
    val brood = (broodDensity ?: 0.0).coerceIn(0.0, 1.0)
    val stores = (storesRatio ?: 0.0).coerceIn(0.0, 1.0)
    val larvaeRatio = larvaeToCappedRatio
    val larvaePenalty = if (cappedBroodCells >= 20 && larvaeRatio != null && larvaeRatio < 0.20) 15.0 else 0.0
    val spatialPenalty =
        spatial.broodGapRatio.finiteOrNull()?.let { if (it > 0.55) 18.0 else if (it > 0.40) 8.0 else 0.0 }.orZero() +
            spatial.broodCompactness.finiteOrNull()?.let { if (it < 0.45) 12.0 else if (it < 0.60) 6.0 else 0.0 }.orZero() +
            spatial.storesEdgeRatio.finiteOrNull()?.let {
                if (stores >= 0.05 && it < 0.25) 8.0 else 0.0
            }.orZero() +
            spatial.pollenNearBroodRatio.finiteOrNull()?.let {
                if (pollenCells >= 5 && it < 0.25) 6.0 else 0.0
            }.orZero()
    val spatialBonus =
        spatial.broodCompactness.finiteOrNull()?.let { if (it >= 0.70) 4.0 else 0.0 }.orZero() +
            spatial.storesEdgeRatio.finiteOrNull()?.let { if (stores >= 0.05 && it >= 0.55) 3.0 else 0.0 }.orZero()
    val score = (
        (brood * 70.0) +
            (stores.coerceAtMost(0.35) / 0.35 * 30.0) +
            spatialBonus -
            larvaePenalty -
            spatialPenalty
        )
        .coerceIn(0.0, 100.0)
    return StatsHealthPoint(date.year, date.monthValue, score)
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

private fun Double.formatDelta(): String {
    val sign = if (this > 0) "+" else ""
    return "$sign${toInt()} pp"
}

private fun Double?.asPercent(): String =
    this?.let { "${(it.coerceIn(0.0, 1.0) * 100).toInt()}%" } ?: "-"

private fun Double?.asRatio(): String =
    this?.let { String.format(Locale.US, "%.2f", it) } ?: "-"

private fun InspectionAiAnalysisResponse.spatialMetrics(): BroodAnalyzer.SpatialMetrics =
    BroodAnalyzer.analyze(results, cellDetections).spatial

private fun Double.finiteOrNull(): Double? = takeIf { it.isFinite() }

private fun Double?.orZero(): Double = this ?: 0.0

private fun Double?.trendColor(): Color = when {
    this == null -> Gray
    this >= 4.0 -> GreenSuccess
    this <= -4.0 -> RedError
    else -> StatusWatch
}

private fun Double?.healthColor(): Color = when {
    this == null -> LightGray
    this >= 70.0 -> GreenSuccess
    this >= 45.0 -> StatusWatch
    else -> RedError
}

private fun Int.shortMonthName(): String =
    java.time.Month.of(this).getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ro"))
        .replaceFirstChar { it.uppercase(Locale.forLanguageTag("ro")) }
