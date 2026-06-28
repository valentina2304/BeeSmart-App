package com.example.beesmart.ui.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.beesmart.R
import com.example.beesmart.ui.components.BeeSectionHeader
import com.example.beesmart.ui.home.ApiaryRadarCard
import com.example.beesmart.ui.home.DeepBeeAdvisorCard
import com.example.beesmart.ui.home.HoneyForecastCard
import com.example.beesmart.ui.home.HomeUiState
import com.example.beesmart.ui.home.HomeViewModel
import com.example.beesmart.ui.theme.BrownPrimary
import com.example.beesmart.ui.theme.GreenSuccess
import com.example.beesmart.ui.theme.PollenAccent
import com.example.beesmart.ui.theme.YellowPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateToApiaries: () -> Unit,
    onNavigateToInspections: () -> Unit,
    onNavigateToTreatments: () -> Unit,
    onNavigateToExtractions: () -> Unit,
    onBack: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val dashboardData by viewModel.dashboardData.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading = uiState is HomeUiState.Loading || uiState is HomeUiState.Idle

    // Refresh whenever the screen returns to the foreground, like Home does.
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
    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.sx_misc_analytics_title), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.sx_misc_analytics_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.sx_misc_analytics_back),
                            tint = BrownPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }

            BeeSectionHeader(
                title = stringResource(R.string.sx_misc_analytics_radar_title),
                subtitle = stringResource(R.string.sx_misc_analytics_radar_subtitle),
                icon = Icons.Default.Visibility,
                accentColor = GreenSuccess,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(
                    animationSpec = tween(400, easing = EaseOutCubic),
                    initialOffsetY = { 30 }
                )
            ) {
                if (isLoading) {
                    AnalyticsLoadingCard()
                } else {
                    ApiaryRadarCard(
                        radar = dashboardData.apiaryRadar,
                        onOpenApiaries = onNavigateToApiaries,
                        onOpenInspections = onNavigateToInspections
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            BeeSectionHeader(
                title = stringResource(R.string.sx_misc_analytics_advisor_title),
                subtitle = stringResource(R.string.sx_misc_analytics_advisor_subtitle),
                icon = Icons.Default.AutoAwesome,
                accentColor = PollenAccent,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(440)) + slideInVertically(
                    animationSpec = tween(440, easing = EaseOutCubic),
                    initialOffsetY = { 30 }
                )
            ) {
                if (isLoading) {
                    AnalyticsLoadingCard()
                } else {
                    DeepBeeAdvisorCard(
                        digest = dashboardData.deepBeeAdvice,
                        onOpenInspections = onNavigateToInspections,
                        onOpenTreatments = onNavigateToTreatments
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            BeeSectionHeader(
                title = stringResource(R.string.sx_misc_analytics_production_title),
                subtitle = stringResource(R.string.sx_misc_analytics_production_subtitle),
                icon = Icons.AutoMirrored.Filled.ShowChart,
                accentColor = YellowPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(450)) + slideInVertically(
                    animationSpec = tween(450, easing = EaseOutCubic),
                    initialOffsetY = { 30 }
                )
            ) {
                if (isLoading) {
                    AnalyticsLoadingCard()
                } else {
                    HoneyForecastCard(
                        analytics = dashboardData.honeyAnalytics,
                        onClick = onNavigateToExtractions
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun AnalyticsLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.height(32.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
