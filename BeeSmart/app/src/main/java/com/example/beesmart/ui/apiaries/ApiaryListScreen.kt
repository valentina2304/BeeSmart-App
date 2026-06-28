package com.example.beesmart.ui.apiaries

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.beesmart.R
import com.example.beesmart.network.models.ApiaryResponse
import com.example.beesmart.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiaryListScreen(
    onNavigateBack: () -> Unit,
    onApiaryClick: (String, String) -> Unit, // apiaryId, apiaryName
    onCreateApiary: () -> Unit,
    onEditApiary: (String) -> Unit, // apiaryId
    viewModel: ApiaryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val weatherByApiaryId by viewModel.weatherByApiaryId.collectAsState()

    var apiaryToDelete by remember { mutableStateOf<ApiaryResponse?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var hasSeenInitialResume = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!hasSeenInitialResume) hasSeenInitialResume = true
                else viewModel.loadApiaries(forceRefresh = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle state changes
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is ApiaryListUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
            }
            is ApiaryListUiState.DeleteSuccess -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Short
                )
            }
            else -> {}
        }
    }

    // Delete confirmation dialog
    apiaryToDelete?.let { apiary ->
        AlertDialog(
            onDismissRequest = { apiaryToDelete = null },
            title = { Text(stringResource(R.string.sx_aph_apiary_delete_title)) },
            text = {
                Text("Ești sigur că vrei să ștergi stupina ${apiary.name}? Aceasta va șterge și toți stupii asociați.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteApiary(apiary.id)
                        apiaryToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.sx_aph_apiary_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { apiaryToDelete = null }) {
                    Text(stringResource(R.string.sx_aph_apiary_cancel))
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateApiary,
                icon = { Icon(Icons.Default.Add, stringResource(R.string.sx_aph_apiary_add_cd)) },
                text = { Text(stringResource(R.string.sx_aph_apiary_new_fab), fontWeight = FontWeight.SemiBold) },
                containerColor = YellowPrimary,
                contentColor = BrownPrimary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = BrownPrimary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sx_aph_apiary_back))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sx_aph_apiary_my_apiaries),
                            fontWeight = FontWeight.Bold,
                            color = BrownPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.sx_aph_apiary_header_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { viewModel.loadApiaries(forceRefresh = true) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = BrownPrimary)
                    ) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.sx_aph_apiary_refresh))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val state = uiState) {
                    is ApiaryListUiState.Success -> {
                        if (state.apiaries.isEmpty()) {
                            // Empty state
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.sx_aph_apiary_empty_title),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.sx_aph_apiary_empty_subtitle),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // Apiaries list
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    top = 16.dp,
                                    end = 16.dp,
                                    bottom = 88.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = state.apiaries,
                                    key = { it.id }
                                ) { apiary ->
                                    ApiaryCard(
                                        apiary = apiary,
                                        weatherState = weatherByApiaryId[apiary.id],
                                        onClick = { onApiaryClick(apiary.id, apiary.name) },
                                        onEdit = { onEditApiary(apiary.id) },
                                        onDelete = { apiaryToDelete = apiary }
                                    )
                                }
                            }
                        }
                    }
                    is ApiaryListUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = YellowPrimary)
                        }
                    }
                    is ApiaryListUiState.Error -> {
                        // Error is shown via snackbar, show retry option
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.sx_aph_apiary_load_error),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadApiaries(forceRefresh = true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = YellowPrimary
                                )
                            ) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.sx_aph_apiary_retry))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.sx_aph_apiary_retry))
                            }
                        }
                    }
                    is ApiaryListUiState.DeleteSuccess -> {
                        // Success is shown via snackbar
                    }
                }
            }
        }
    }
}

@Composable
fun ApiaryCard(
    apiary: ApiaryResponse,
    weatherState: ApiaryWeatherUiState?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with name and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apiary.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Hive count
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_hive_box),
                            contentDescription = stringResource(R.string.sx_aph_apiary_hives_cd),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = getHiveCountText(apiary.hiveCount),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action buttons
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.sx_aph_apiary_edit_cd),
                            tint = BrownPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.sx_aph_apiary_delete_cd),
                            tint = RedError,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (!apiary.location.isNullOrEmpty() || weatherState is ApiaryWeatherUiState.Success) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Location (if available)
            if (!apiary.location.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = apiary.location,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (weatherState is ApiaryWeatherUiState.Success) {
                Spacer(modifier = Modifier.height(10.dp))
                ApiaryWeatherStrip(state = weatherState)
            }
        }
    }
}

@Composable
private fun ApiaryWeatherStrip(state: ApiaryWeatherUiState) {
    when (state) {
        ApiaryWeatherUiState.Loading,
        ApiaryWeatherUiState.Unavailable -> Unit
        is ApiaryWeatherUiState.Success -> ApiaryWeatherContent(state.summary)
    }
}

@Composable
private fun ApiaryWeatherContent(summary: ApiaryWeatherSummary) {
    val (flightBg, flightFg, flightIcon) = apiaryFlightStyle(summary.flight.level)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        YellowPrimary.copy(alpha = 0.18f),
                        SageSoft.copy(alpha = 0.72f)
                    )
                ),
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = weatherIconFor(summary.condition),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = YellowPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${summary.tempC}\u00B0C",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrownPrimary
                    )
                    Text(
                        text = summary.condition,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                WeatherMiniStat(Icons.Default.WaterDrop, "${summary.humidity}%")
                Spacer(modifier = Modifier.width(10.dp))
                WeatherMiniStat(Icons.Default.Air, "${summary.windKmH} km/h")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(flightBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = flightIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = flightFg
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = summary.flight.headline,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = flightFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "res. ${summary.feelsLikeC}\u00B0C",
                    fontSize = 11.sp,
                    color = flightFg.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun WeatherMiniStat(
    icon: ImageVector,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

private fun apiaryFlightStyle(level: com.example.beesmart.data.repository.BeeFlightAdvisor.Level): Triple<Color, Color, ImageVector> {
    return when (level) {
        com.example.beesmart.data.repository.BeeFlightAdvisor.Level.OPTIMAL -> Triple(
            GreenSuccess.copy(alpha = 0.16f),
            GreenSuccess,
            Icons.Default.CheckCircle
        )
        com.example.beesmart.data.repository.BeeFlightAdvisor.Level.LIMITED -> Triple(
            YellowPrimary.copy(alpha = 0.18f),
            BrownPrimary,
            Icons.Default.Warning
        )
        com.example.beesmart.data.repository.BeeFlightAdvisor.Level.GROUNDED -> Triple(
            RedError.copy(alpha = 0.12f),
            RedError,
            Icons.Default.Error
        )
    }
}

private fun weatherIconFor(condition: String): ImageVector {
    val normalized = condition.lowercase()
    return when {
        normalized.contains("ploa") || normalized.contains("rain") -> Icons.Default.WaterDrop
        normalized.contains("nor") || normalized.contains("cloud") -> Icons.Default.Cloud
        normalized.contains("furt") || normalized.contains("storm") -> Icons.Default.Thunderstorm
        else -> Icons.Default.WbSunny
    }
}

private fun getHiveCountText(count: Int): String {
    return when (count) {
        0 -> "Niciun stup"
        1 -> "1 stup"
        else -> "$count stupi"
    }
}
