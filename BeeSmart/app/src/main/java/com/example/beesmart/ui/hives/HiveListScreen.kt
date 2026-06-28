package com.example.beesmart.ui.hives

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.BrownPrimary
import com.example.beesmart.ui.theme.Gray
import com.example.beesmart.ui.theme.GreenSuccess
import com.example.beesmart.ui.theme.RedError
import com.example.beesmart.ui.theme.StatusWatch
import com.example.beesmart.ui.theme.WaxSurface
import com.example.beesmart.ui.theme.YellowPrimary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HiveListScreen(
    apiaryName: String,
    onNavigateBack: () -> Unit,
    onHiveClick: (String) -> Unit,
    onCreateHive: (String, String) -> Unit,
    onEditHive: (String) -> Unit,
    onViewHiveInspections: (hiveId: String, hiveName: String) -> Unit,
    viewModel: HiveListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val weatherState by viewModel.weatherState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var hiveToDelete by remember { mutableStateOf<HiveResponse?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var hasSeenInitialResume = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!hasSeenInitialResume) {
                    hasSeenInitialResume = true
                } else {
                    viewModel.loadHives(forceRefresh = true)
                    viewModel.loadWeather(forceRefresh = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is HiveListUiState.Error -> snackbarHostState.showSnackbar(
                message = state.message,
                duration = SnackbarDuration.Long
            )
            is HiveListUiState.DeleteSuccess -> snackbarHostState.showSnackbar(
                message = state.message,
                duration = SnackbarDuration.Short
            )
            else -> Unit
        }
    }

    hiveToDelete?.let { hive ->
        AlertDialog(
            onDismissRequest = { hiveToDelete = null },
            title = { Text("Șterge stupul") },
            text = { Text("Sigur vrei să ștergi \"${hive.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteHive(hive.id)
                        hiveToDelete = null
                    }
                ) {
                    Text("Șterge", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { hiveToDelete = null }) {
                    Text("Anulează")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stupi - $apiaryName", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Înapoi")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadHives(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, "Reîmprospătează")
                    }
                },
                colors = beeSmartTopAppBarColors()
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreateHive(viewModel.apiaryId, apiaryName) },
                icon = { Icon(Icons.Default.Add, "Adaugă stup") },
                text = { Text("Stup nou") },
                containerColor = YellowPrimary,
                contentColor = BrownPrimary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is HiveListUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (weatherState !is WeatherUiState.Idle) {
                            item(key = "weather") {
                                WeatherCard(state = weatherState)
                            }
                        }

                        if (state.hives.isNotEmpty()) {
                            item(key = "summary") {
                                HiveSummaryCard(
                                    hives = state.hives,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .animateItem()
                                )
                            }
                        }

                        if (state.hives.isEmpty()) {
                            item(key = "empty") {
                                EmptyHiveState(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                        .animateItem()
                                )
                            }
                        } else {
                            items(
                                items = state.hives,
                                key = { it.id }
                            ) { hive ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .animateItem()
                                ) {
                                    HiveCard(
                                        hive = hive,
                                        onClick = { onHiveClick(hive.id) },
                                        onEdit = { onEditHive(hive.id) },
                                        onDelete = { hiveToDelete = hive },
                                        onViewHistory = { onViewHiveInspections(hive.id, hive.name) }
                                    )
                                }
                            }
                        }
                    }
                }
                is HiveListUiState.Loading -> LoadingState()
                is HiveListUiState.Error -> ErrorState(onRetry = { viewModel.loadHives(forceRefresh = true) })
                is HiveListUiState.DeleteSuccess -> Unit
            }
        }
    }
}

@Composable
private fun HiveSummaryCard(
    hives: List<HiveResponse>,
    modifier: Modifier = Modifier
) {
    val activeCount = hives.count { it.status == HiveStatus.Active }
    val attentionCount = hives.count { it.status != HiveStatus.Active && it.status != HiveStatus.Inactive }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_hive_box),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${hives.size} ${if (hives.size == 1) "stup" else "stupi"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$activeCount activi · $attentionCount de urmărit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AssistChip(
                onClick = {},
                label = { Text(if (attentionCount == 0) "Stabil" else "Atenție", maxLines = 1, softWrap = false) },
                leadingIcon = {
                    Icon(
                        imageVector = if (attentionCount == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = (if (attentionCount == 0) GreenSuccess else YellowPrimary).copy(alpha = 0.16f),
                    labelColor = if (attentionCount == 0) GreenSuccess else BrownPrimary,
                    leadingIconContentColor = if (attentionCount == 0) GreenSuccess else BrownPrimary
                )
            )
        }
    }
}

@Composable
fun HiveCard(
    hive: HiveResponse,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewHistory: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "hive_card_press"
    )
    val statusColor = getHiveStatusColor(hive.status)
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surface,
        label = "hive_card_container"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPressed) 1.dp else 3.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(YellowPrimary.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_hive_box),
                            contentDescription = null,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hive.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = hive.type.shortName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Edit, "Editează", tint = BrownPrimary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Delete, "Șterge", tint = RedError, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(
                    label = getHiveStatusTranslation(hive.status),
                    icon = getHiveStatusIcon(hive.status),
                    color = statusColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onViewHistory) {
                    Icon(Icons.Default.History, contentDescription = null, tint = BrownPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Istoric inspecții", color = BrownPrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            AnimatedVisibility(
                visible = !hive.notes.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = hive.notes.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    icon: ImageVector,
    color: Color
) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.12f),
            labelColor = color,
            leadingIconContentColor = color
        )
    )
}

@Composable
private fun EmptyHiveState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_hive_box),
            contentDescription = null,
            modifier = Modifier.size(112.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Niciun stup încă", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Apasă pe + pentru a adăuga primul stup", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BrownPrimary)
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
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
            modifier = Modifier.size(112.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Eroare la încărcarea stupilor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = BrownPrimary)
        ) {
            Icon(Icons.Default.Refresh, "Reîncearcă")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reîncearcă")
        }
    }
}

private fun getHiveStatusTranslation(status: HiveStatus): String = when (status) {
    HiveStatus.Active -> "Activ"
    HiveStatus.Queenless -> "Fără regină"
    HiveStatus.Weak -> "Slab"
    HiveStatus.Sick -> "Bolnav"
    HiveStatus.Preparing -> "În pregătire"
    HiveStatus.Inactive -> "Inactiv"
}

private fun getHiveStatusIcon(status: HiveStatus): ImageVector = when (status) {
    HiveStatus.Active -> Icons.Default.CheckCircle
    HiveStatus.Queenless -> Icons.Default.Warning
    HiveStatus.Weak -> Icons.Default.Warning
    HiveStatus.Sick -> Icons.Default.Warning
    HiveStatus.Preparing -> Icons.Default.Build
    HiveStatus.Inactive -> Icons.Default.Clear
}

private fun getHiveStatusColor(status: HiveStatus): Color = when (status) {
    HiveStatus.Active -> GreenSuccess
    HiveStatus.Queenless -> StatusWatch
    HiveStatus.Weak -> StatusWatch
    HiveStatus.Sick -> RedError
    HiveStatus.Preparing -> YellowPrimary
    HiveStatus.Inactive -> Gray
}
