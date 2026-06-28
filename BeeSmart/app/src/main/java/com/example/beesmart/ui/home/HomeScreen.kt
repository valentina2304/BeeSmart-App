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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToApiaries: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToInspections: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToQrScanner: () -> Unit,
    onNavigateToTreatments: () -> Unit,
    onNavigateToExtractions: () -> Unit,
    onNavigateToCreateInspection: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboardData by viewModel.dashboardData.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var hasSeenInitialResume = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!hasSeenInitialResume) hasSeenInitialResume = true
                else viewModel.loadHomeData(forceRefresh = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Load data when screen is first displayed
    LaunchedEffect(Unit) {
        viewModel.loadHomeData()
    }

    // Handle UI state changes
    LaunchedEffect(uiState) {
        when (uiState) {
            is HomeUiState.LoggedOut -> {
                snackbarHostState.showSnackbar(
                    message = "Delogare reușită!",
                    duration = SnackbarDuration.Short
                )
                onLogout()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // Show a snackbar when a manual sync finishes (transition isSyncing true → false).
    var wasSyncing by remember { mutableStateOf(false) }
    LaunchedEffect(isSyncing, pendingSyncCount) {
        if (wasSyncing && !isSyncing) {
            val msg = if (pendingSyncCount == 0) "Sincronizare finalizată"
                      else "Sincronizare incompletă — $pendingSyncCount operații rămase"
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        }
        wasSyncing = isSyncing
    }

    val userName = when (val state = uiState) {
        is HomeUiState.Success -> state.userName
        else -> "Apicultor"
    }

    val hasDashboardData = dashboardData.userName.isNotBlank()
    val isInitialDashboardLoad = uiState is HomeUiState.Loading && !hasDashboardData
    val hivesCount = if (hasDashboardData) dashboardData.hivesCount else "..."

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BeeSmart", fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.sx_aph_home_app_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = stringResource(R.string.sx_aph_home_notifications_cd),
                            tint = BrownPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = stringResource(R.string.sx_aph_home_profile_cd),
                            tint = BrownPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
            // Welcome Card with Animation
            var welcomeVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                welcomeVisible = true
            }

            AnimatedVisibility(
                visible = welcomeVisible,
                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                    animationSpec = tween(400, easing = EaseOutCubic),
                    initialOffsetY = { -30 }
                )
            ) {
                WelcomeCard(
                    userName = userName,
                    isLoading = uiState is HomeUiState.Loading,
                    isOffline = isOffline,
                    pendingSyncCount = pendingSyncCount,
                    isSyncing = isSyncing,
                    onSyncClick = viewModel::triggerManualSync,
                    onProfileClick = onNavigateToProfile
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stats Section
            BeeSectionHeaderImage(
                title = stringResource(R.string.sx_aph_home_my_apiaries_title),
                subtitle = stringResource(R.string.sx_aph_home_my_apiaries_subtitle),
                painter = painterResource(id = R.drawable.ic_hive_box),
                accentColor = YellowPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Stats Cards with Animation
            var statsVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(100)
                statsVisible = true
            }

            AnimatedVisibility(
                visible = statsVisible,
                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                    animationSpec = tween(400, easing = EaseOutCubic),
                    initialOffsetY = { 30 }
                )
            ) {
                Column {
                    LargeStatCard(
                        title = stringResource(R.string.sx_aph_home_total_hives_title),
                        value = hivesCount,
                        iconRes = R.drawable.ic_hive_box,
                        description = stringResource(R.string.sx_aph_home_total_hives_desc),
                        onClick = onNavigateToApiaries
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OperationalOverviewRow(
                        activeHives = dashboardData.activeHivesCount,
                        pendingTasks = dashboardData.pendingTasksCount,
                        overdueTasks = dashboardData.overdueTasksCount,
                        inspections = dashboardData.inspectionsCount,
                        isLoading = isInitialDashboardLoad,
                        onApiariesClick = onNavigateToApiaries,
                        onTasksClick = onNavigateToTasks,
                        onInspectionsClick = onNavigateToInspections
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Analytics & forecast — summary that opens the dedicated screen
            BeeSectionHeader(
                title = stringResource(R.string.sx_aph_home_analytics_title),
                subtitle = stringResource(R.string.sx_aph_home_analytics_subtitle),
                icon = Icons.AutoMirrored.Filled.ShowChart,
                accentColor = GreenSuccess,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            AnimatedVisibility(
                visible = statsVisible,
                enter = fadeIn(animationSpec = tween(425)) + slideInVertically(
                    animationSpec = tween(425, easing = EaseOutCubic),
                    initialOffsetY = { 30 }
                )
            ) {
                AnalyticsTeaserCard(
                    healthScore = dashboardData.apiaryRadar.healthScore,
                    monitoredHives = dashboardData.apiaryRadar.monitoredHivesCount,
                    urgentCount = dashboardData.deepBeeAdvice.urgentCount,
                    recommendationCount = dashboardData.deepBeeAdvice.advice.size,
                    onClick = onNavigateToAnalytics
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // AI Section
            BeeSectionHeader(
                title = stringResource(R.string.sx_aph_home_ai_title),
                subtitle = stringResource(R.string.sx_aph_home_ai_subtitle),
                icon = Icons.Default.CameraAlt,
                accentColor = GreenSuccess,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            var aiVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(150)
                aiVisible = true
            }

            AnimatedVisibility(
                visible = aiVisible,
                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                    animationSpec = tween(400, easing = EaseOutCubic),
                    initialOffsetY = { 30 }
                )
            ) {
                FeaturedActionCard(
                    title = stringResource(R.string.sx_aph_home_ai_card_title),
                    subtitle = stringResource(R.string.sx_aph_home_ai_card_subtitle),
                    iconRes = R.drawable.ic_ai,
                    onClick = onNavigateToCreateInspection
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dashboard Section
            BeeSectionHeader(
                title = stringResource(R.string.sx_aph_home_quick_access_title),
                subtitle = stringResource(R.string.sx_aph_home_quick_access_subtitle),
                icon = Icons.Default.Dashboard,
                accentColor = BrownPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            var dashboardVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(200)
                dashboardVisible = true
            }

            AnimatedVisibility(
                visible = dashboardVisible,
                enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
                    animationSpec = tween(400, easing = EaseOutCubic),
                    initialOffsetY = { 30 }
                )
            ) {
                DashboardGrid(
                    items = listOf(
                        DashboardItem(stringResource(R.string.sx_aph_home_grid_apiaries_title), stringResource(R.string.sx_aph_home_grid_apiaries_subtitle), iconRes = R.mipmap.ic_launcher_foreground, iconTint = Color.Unspecified) {
                            onNavigateToApiaries()
                        },
                        DashboardItem(stringResource(R.string.sx_aph_home_grid_tasks_title), stringResource(R.string.sx_aph_home_grid_tasks_subtitle), iconRes = R.drawable.ic_tasks, iconTint = GreenSuccess) {
                            onNavigateToTasks()
                        },
                        DashboardItem(stringResource(R.string.sx_aph_home_grid_inspections_title), stringResource(R.string.sx_aph_home_grid_inspections_subtitle), iconRes = R.drawable.ic_inspection, iconTint = BrownPrimary) {
                            onNavigateToInspections()
                        },
                        DashboardItem(stringResource(R.string.sx_aph_home_grid_qr_title), stringResource(R.string.sx_aph_home_grid_qr_subtitle), iconRes = R.drawable.ic_qr, iconTint = PollenAccent) {
                            onNavigateToQrScanner()
                        },
                        DashboardItem(stringResource(R.string.sx_aph_home_grid_treatments_title), stringResource(R.string.sx_aph_home_grid_treatments_subtitle), iconRes = R.drawable.ic_shield_check, iconTint = BrownPrimary) {
                            onNavigateToTreatments()
                        },
                        DashboardItem(stringResource(R.string.sx_aph_home_grid_extractions_title), stringResource(R.string.sx_aph_home_grid_extractions_subtitle), iconRes = R.drawable.ic_honey, iconTint = GreenSuccess) {
                            onNavigateToExtractions()
                        }
                    )
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
internal fun DeepBeeAdvisorCard(
    digest: ApiaryAdviceDigest,
    onOpenInspections: () -> Unit,
    onOpenTreatments: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(PollenAccent.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai),
                        contentDescription = null,
                        tint = BrownPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = digest.advisorHeadline(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.sx_aph_home_advisor_correlate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdvisorMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.sx_aph_home_advisor_urgent),
                    value = digest.urgentCount,
                    color = RedError
                )
                AdvisorMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.sx_aph_home_advisor_important),
                    value = digest.importantCount,
                    color = YellowPrimary
                )
                AdvisorMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.sx_aph_home_advisor_opportunities),
                    value = digest.opportunityCount,
                    color = GreenSuccess
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(modifier = Modifier.height(12.dp))

            if (digest.advice.isEmpty()) {
                RadarEmptyState(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.sx_aph_home_advisor_waiting_title),
                    subtitle = stringResource(R.string.sx_aph_home_advisor_waiting_subtitle)
                )
            } else {
                Text(
                    text = stringResource(R.string.sx_aph_home_advisor_prioritized),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                digest.advice.take(4).forEachIndexed { index, advice ->
                    if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                    DeepBeeAdviceRow(advice)
                }
                if (digest.advice.size > 4) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "+ ${digest.advice.size - 4} recomandări disponibile după actualizarea jurnalului",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.sx_aph_home_advisor_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onOpenTreatments) {
                    Text(stringResource(R.string.sx_aph_home_advisor_treatments))
                }
                TextButton(onClick = onOpenInspections) {
                    Text(stringResource(R.string.sx_aph_home_advisor_inspections))
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvisorMetric(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun DeepBeeAdviceRow(advice: BeekeeperAdvice) {
    val color = advice.priority.adviceColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = advice.priority.adviceIcon(),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = advice.priority.adviceLabel(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = advice.category.categoryLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = "${advice.hiveName}: ${advice.title}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = advice.action,
            style = MaterialTheme.typography.bodySmall
        )
        if (advice.evidence.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = advice.evidence.take(2).joinToString(" \u00b7 "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (advice.veterinaryReviewRecommended) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Confirmă fizic; solicită evaluare de specialitate dacă semnalul persistă.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

private fun ApiaryAdviceDigest.advisorHeadline(): String = when {
    urgentCount > 0 -> "$urgentCount recomandări urgente"
    importantCount > 0 -> "$importantCount recomandări importante"
    opportunityCount > 0 -> "$opportunityCount oportunități identificate"
    advice.isNotEmpty() -> "Plan de monitorizare pregătit"
    else -> "Consilier pregătit pentru analiză"
}

private fun AdvicePriority.adviceLabel(): String = when (this) {
    AdvicePriority.URGENT -> "URGENT"
    AdvicePriority.IMPORTANT -> "IMPORTANT"
    AdvicePriority.WATCH -> "DE URMARIT"
    AdvicePriority.OPPORTUNITY -> "OPORTUNITATE"
}

private fun AdvicePriority.adviceColor(): Color = when (this) {
    AdvicePriority.URGENT -> StatusDanger
    AdvicePriority.IMPORTANT -> StatusWatch
    AdvicePriority.WATCH -> BrownPrimary
    AdvicePriority.OPPORTUNITY -> StatusOk
}

private fun AdvicePriority.adviceIcon(): ImageVector = when (this) {
    AdvicePriority.URGENT -> Icons.Default.Warning
    AdvicePriority.IMPORTANT -> Icons.Default.PriorityHigh
    AdvicePriority.WATCH -> Icons.Default.Visibility
    AdvicePriority.OPPORTUNITY -> Icons.Default.CheckCircle
}

private fun AdviceCategory.categoryLabel(): String = when (this) {
    AdviceCategory.QUEEN -> "regina"
    AdviceCategory.BROOD -> "puiet"
    AdviceCategory.SWARM -> "roire"
    AdviceCategory.NUTRITION -> "hrana"
    AdviceCategory.TREATMENT -> "tratament"
    AdviceCategory.PRODUCTION -> "productie"
    AdviceCategory.MONITORING -> "monitorizare"
    AdviceCategory.DATA_QUALITY -> "calitatea datelor"
}

@Composable
internal fun ApiaryRadarCard(
    radar: ApiaryRadar,
    onOpenApiaries: () -> Unit,
    onOpenInspections: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HealthScoreGauge(score = radar.healthScore)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = radar.radarHeadline(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scor explicabil din jurnal, task-uri și semnale DeepBee",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadarMetric(
                    modifier = Modifier.weight(1f),
                    label = "Stabili",
                    value = radar.stableHivesCount,
                    color = GreenSuccess
                )
                RadarMetric(
                    modifier = Modifier.weight(1f),
                    label = "De urmărit",
                    value = radar.watchHivesCount,
                    color = StatusWatch
                )
                RadarMetric(
                    modifier = Modifier.weight(1f),
                    label = "Critici",
                    value = radar.criticalHivesCount,
                    color = RedError
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FactCheck,
                    contentDescription = null,
                    tint = BrownPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "Acoperire inspecții ${radar.inspectionCoveragePercent}% · DeepBee pe ${radar.deepBeeHivesCount} stupi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(modifier = Modifier.height(12.dp))

            when {
                radar.monitoredHivesCount == 0 -> {
                    RadarEmptyState(
                        icon = Icons.Default.Hive,
                        title = "Radarul așteaptă primul stup",
                        subtitle = "Adaugă stupii pentru a calcula prioritățile zilnice."
                    )
                }
                radar.priorities.isEmpty() -> {
                    RadarEmptyState(
                        icon = Icons.Default.Verified,
                        title = "Nicio intervenție urgentă",
                        subtitle = "Datele disponibile indică o stupină stabilă. Continuă monitorizarea."
                    )
                }
                else -> {
                    Text(
                        text = "Plan recomandat pentru azi",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    radar.priorities.take(3).forEachIndexed { index, priority ->
                        if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                        RadarPriorityRow(priority)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onOpenApiaries) {
                    Text("Vezi stupinele")
                }
                TextButton(onClick = onOpenInspections) {
                    Text("Deschide jurnalul", maxLines = 1, softWrap = false)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthScoreGauge(score: Int) {
    val color = score.healthColor()
    val animatedSweep by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) * 3.6f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "health_score_sweep"
    )
    Box(
        modifier = Modifier.size(92.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = BrownPrimary.copy(alpha = 0.12f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 12f, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = animatedSweep,
                useCenter = false,
                style = Stroke(width = 12f, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.titleLarge.merge(NumberStyle),
                color = color
            )
            Text(
                text = "/ 100",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RadarMetric(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RadarPriorityRow(priority: HivePriority) {
    val color = priority.level.riskColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.09f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (priority.level == HiveRiskLevel.CRITICAL) Icons.Default.Warning else Icons.Default.Visibility,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = priority.hiveName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${priority.healthScore}/100",
                    style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                    color = color
                )
            }
            Text(
                text = priority.action,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = priority.reasons.take(2).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RadarEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GreenSuccess,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ApiaryRadar.radarHeadline(): String = when {
    monitoredHivesCount == 0 -> "Radar pregătit pentru date"
    criticalHivesCount > 0 -> "$criticalHivesCount stupi cer intervenție rapidă"
    watchHivesCount > 0 -> "$watchHivesCount stupi merită urmăriți"
    else -> "Stupina este stabilă"
}

private fun Int.healthColor(): Color = when {
    this >= 80 -> StatusOk
    this >= 50 -> StatusWatch
    else -> StatusDanger
}

private fun HiveRiskLevel.riskColor(): Color = when (this) {
    HiveRiskLevel.STABLE -> StatusOk
    HiveRiskLevel.WATCH -> StatusWatch
    HiveRiskLevel.CRITICAL -> StatusDanger
}

@Composable
private fun OperationalOverviewRow(
    activeHives: Int,
    pendingTasks: Int,
    overdueTasks: Int,
    inspections: Int,
    isLoading: Boolean,
    onApiariesClick: () -> Unit,
    onTasksClick: () -> Unit,
    onInspectionsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OverviewStatCard(
            modifier = Modifier.weight(1f),
            title = "Activi",
            value = if (isLoading) "..." else activeHives.toString(),
            helper = if (isLoading) "se încarcă" else "stupi",
            icon = Icons.Default.CheckCircle,
            tint = GreenSuccess,
            onClick = onApiariesClick
        )
        OverviewStatCard(
            modifier = Modifier.weight(1f),
            title = "Task-uri",
            value = if (isLoading) "..." else pendingTasks.toString(),
            helper = if (overdueTasks > 0) "$overdueTasks întârziate" else "de rezolvat",
            icon = Icons.AutoMirrored.Filled.Assignment,
            tint = PollenAccent,
            onClick = onTasksClick
        )
        OverviewStatCard(
            modifier = Modifier.weight(1f),
            title = "Inspecții",
            value = if (isLoading) "..." else inspections.toString(),
            helper = "în jurnal",
            icon = Icons.AutoMirrored.Filled.FactCheck,
            tint = BrownPrimary,
            onClick = onInspectionsClick
        )
    }
}

@Composable
private fun OverviewStatCard(
    title: String,
    value: String,
    helper: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.merge(NumberStyle)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (value == "...") "se încarcă" else helper,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class DashboardItem(
    val title: String,
    val subtitle: String,
    @DrawableRes val iconRes: Int,
    val iconTint: Color,
    val onClick: () -> Unit
)

@Composable
private fun DashboardGrid(items: List<DashboardItem>) {
    val rows = items.chunked(2)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    DashboardCard(
                        modifier = Modifier.weight(1f),
                        item = item
                    )
                }

                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    item: DashboardItem
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val preserveIconColors = item.iconTint == Color.Unspecified

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dashboard_card_scale"
    )

    Card(
        modifier = modifier
            .height(150.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(),
                onClick = item.onClick
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 2.dp else 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (preserveIconColors) YellowPrimary.copy(alpha = 0.14f) else item.iconTint.copy(alpha = 0.14f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (preserveIconColors) {
                    Image(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = item.title,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = item.title,
                        tint = item.iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeaturedActionCard(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "featured_card_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                onClick = {
                    pressed = true
                    onClick()
                },
                onClickLabel = "Vezi $title"
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = SageSoft),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(GreenSuccess.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    tint = GreenSuccess,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = BrownPrimary.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(100)
            pressed = false
        }
    }
}

@Composable
private fun AnalyticsTeaserCard(
    healthScore: Int,
    monitoredHives: Int,
    urgentCount: Int,
    recommendationCount: Int,
    onClick: () -> Unit
) {
    val scoreColor = when {
        healthScore >= 80 -> StatusOk
        healthScore >= 50 -> StatusWatch
        else -> StatusDanger
    }
    val hasData = monitoredHives > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, onClickLabel = "Vezi analizele"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(scoreColor.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasData) {
                    Text(
                        text = healthScore.toString(),
                        style = MaterialTheme.typography.titleLarge.merge(NumberStyle),
                        color = scoreColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = scoreColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasData) "Scor sănătate $healthScore/100" else "Analize detaliate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (hasData) {
                        "$urgentCount urgente · $recommendationCount recomandări DeepBee"
                    } else {
                        "Radar, consilier DeepBee și prognoza producției"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = BrownPrimary.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun WelcomeCard(
    userName: String,
    isLoading: Boolean,
    isOffline: Boolean,
    pendingSyncCount: Int,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bee_float")
    val beeOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bee_offset"
    )

    // Box without clipToBounds so the bee can overflow the card boundary
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onProfileClick),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = HoneyGradientStart),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            0f to HoneyGradientStart, 0.55f to HoneyGradientMid, 1f to HoneyGradientEnd
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bine ai venit",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isOffline) "Offline" else "Online",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                                modifier = Modifier.padding(start = 8.dp)
                            )

                            if (pendingSyncCount > 0 || isSyncing) {
                                Spacer(modifier = Modifier.width(8.dp))
                                BadgedBox(
                                    badge = {
                                        if (pendingSyncCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ) {
                                                Text(text = pendingSyncCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    IconButton(
                                        onClick = onSyncClick,
                                        enabled = !isSyncing && !isOffline,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        if (isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Sync,
                                                contentDescription = "Sincronizează acum",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Spacer so the card body doesn't overlap with the bee
                    Spacer(modifier = Modifier.width(88.dp))
                }
            }
        }

        // Bee rendered outside the Card so it's not clipped
        Image(
            painter = painterResource(id = R.drawable.bee),
            contentDescription = "Bee",
            modifier = Modifier
                .size(88.dp)
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .offset(y = beeOffset.dp)
        )
    }
}

@Composable
private fun LargeStatCard(
    title: String,
    value: String,
    @DrawableRes iconRes: Int,
    description: String,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "large_stat_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                onClick = {
                    pressed = true
                    onClick()
                },
                onClickLabel = "Vezi $title"
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = value,
                    style = MaterialTheme.typography.displayMedium.merge(NumberStyle),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (value == "...") "Se încarcă datele exploatației" else description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(YellowPrimary.copy(alpha = 0.16f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(100)
            pressed = false
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "stat_scale"
    )

    Card(
        modifier = modifier
            .height(120.dp)
            .scale(scale)
            .clickable(
                onClick = {
                    pressed = true
                    onClick()
                },
                onClickLabel = "Vezi $title"
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = iconTint
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(100)
            pressed = false
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
