package com.example.beesmart.ui.treatment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.beesmart.R
import com.example.beesmart.network.models.HiveTreatment
import com.example.beesmart.network.models.TreatmentType
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.BrownPrimary
import com.example.beesmart.ui.theme.RedError
import com.example.beesmart.ui.theme.WaxSurface
import com.example.beesmart.ui.theme.YellowPrimary
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentListScreen(
    hiveId: String?,
    onNavigateBack: () -> Unit,
    onAddTreatment: () -> Unit,
    onTreatmentClick: (HiveTreatment) -> Unit,
    viewModel: TreatmentListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var hasSeenInitialResume = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!hasSeenInitialResume) hasSeenInitialResume = true
                else viewModel.loadTreatments(forceRefresh = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.sx_tte_treatment_delete_title)) },
            text = { Text(stringResource(R.string.sx_tte_treatment_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTreatment(pendingDeleteId!!)
                    pendingDeleteId = null
                }) { Text(stringResource(R.string.sx_tte_delete_cd), color = RedError) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.sx_tte_task_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sx_tte_treatments_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.sx_tte_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadTreatments(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.sx_tte_refresh))
                    }
                },
                colors = beeSmartTopAppBarColors()
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTreatment,
                containerColor = YellowPrimary,
                contentColor = BrownPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.sx_tte_new_treatment), fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        color = BrownPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    TreatmentEmptyState(
                        title = stringResource(R.string.sx_tte_treatments_load_error),
                        subtitle = uiState.error ?: stringResource(R.string.sx_tte_unknown_error),
                        tint = RedError,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.items.isEmpty() -> {
                    TreatmentEmptyState(
                        title = stringResource(R.string.sx_tte_no_treatments_title),
                        subtitle = stringResource(R.string.sx_tte_no_treatments_subtitle),
                        tint = BrownPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.items, key = { it.treatment.id }) { item ->
                            TreatmentItem(
                                item = item,
                                showHiveName = hiveId == null,
                                onClick = { onTreatmentClick(item.treatment) },
                                onDelete = { pendingDeleteId = item.treatment.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TreatmentItem(
    item: TreatmentWithHive,
    showHiveName: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val treatment = item.treatment
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BrownPrimary.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_shield_check),
                        contentDescription = null,
                        tint = BrownPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = treatment.type.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = treatment.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showHiveName && item.hiveName != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.hiveName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTreatmentDate(treatment.treatmentDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = BrownPrimary
                )
                treatment.nextTreatmentDate?.let { nextDate ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Următoarea verificare / aplicare: ${formatTreatmentDate(nextDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrownPrimary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.sx_tte_delete_cd),
                    tint = RedError
                )
            }
        }
    }
}

@Composable
private fun TreatmentEmptyState(
    title: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = tint.copy(alpha = 0.12f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_shield_check),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(22.dp)
                    .size(56.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTreatmentDate(dateString: String): String {
    return try {
        ZonedDateTime.parse(dateString).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    } catch (e: Exception) {
        dateString
    }
}
