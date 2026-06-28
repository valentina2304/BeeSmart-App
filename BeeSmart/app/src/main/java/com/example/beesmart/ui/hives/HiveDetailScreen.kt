package com.example.beesmart.ui.hives

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.beesmart.R
import com.example.beesmart.data.repository.BroodAnalyzer
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.InspectionAiAnalysisResponse
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.qrcode.QrCodeUtils
import com.example.beesmart.ui.theme.BrownPrimary
import com.example.beesmart.ui.theme.Gray
import com.example.beesmart.ui.theme.GreenSuccess
import com.example.beesmart.ui.theme.LightGray
import com.example.beesmart.ui.theme.RedError
import com.example.beesmart.ui.theme.YellowPrimary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiveDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTreatments: (String) -> Unit,
    onNavigateToExtractions: (String) -> Unit,
    onNavigateToStats: (String) -> Unit,
    viewModel: HiveDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val qrBitmap: Bitmap? = remember(uiState) {
        (uiState as? HiveDetailUiState.Success)?.let { state ->
            runCatching { QrCodeUtils.generateQrBitmap(state.qrContent) }.getOrNull()
        }
    }
    val qrImageBitmap = remember(qrBitmap) { qrBitmap?.asImageBitmap() }
    val legacyPermissionNeeded = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    val storagePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) {
            action?.invoke()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Permisiune refuzată. Nu putem salva fără acces la stocare.")
            }
        }
    }
    val runWithStoragePermission: (() -> Unit) -> Unit = remember(legacyPermissionNeeded, storagePermission, context) {
        { action ->
            if (legacyPermissionNeeded &&
                ContextCompat.checkSelfPermission(context, storagePermission) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingAction = action
                permissionLauncher.launch(storagePermission)
            } else {
                action()
            }
        }
    }

    Scaffold(
        topBar = {
            HiveDetailTopBar(
                uiState = uiState,
                qrAvailable = qrBitmap != null,
                onNavigateBack = onNavigateBack,
                onRefresh = { viewModel.refresh() },
                onShare = {
                    val successState = uiState as? HiveDetailUiState.Success ?: return@HiveDetailTopBar
                    runWithStoragePermission {
                        qrBitmap?.let {
                            runCatching { QrCodeUtils.shareBitmap(context, it, successState.hive.name) }
                                .onFailure { error ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = error.message ?: "Distribuire eșuată",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val state = uiState) {
            HiveDetailUiState.Loading -> LoadingState(modifier = Modifier.padding(padding))
            is HiveDetailUiState.Error -> ErrorState(
                message = state.message,
                onRetry = { viewModel.refresh() },
                modifier = Modifier.padding(padding)
            )
            is HiveDetailUiState.Success -> DetailContent(
                hive = state.hive,
                qrImage = qrImageBitmap,
                qrBitmap = qrBitmap,
                qrLink = state.qrContent,
                aiAnalyses = state.aiAnalyses,
                statsMessage = state.statsMessage,
                isStatsLoading = state.isStatsLoading,
                snackbarHostState = snackbarHostState,
                runWithStoragePermission = runWithStoragePermission,
                onTreatmentsClick = { onNavigateToTreatments(state.hive.id) },
                onExtractionsClick = { onNavigateToExtractions(state.hive.id) },
                onStatsClick = { onNavigateToStats(state.hive.id) },
                modifier = Modifier.padding(padding)
            )

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiveDetailTopBar(
    uiState: HiveDetailUiState,
    qrAvailable: Boolean,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit
) {
    val title = when (uiState) {
        is HiveDetailUiState.Success -> uiState.hive.name
        else -> stringResource(R.string.sx_hive_detail_title)
    }
    TopAppBar(
        title = {
            Text(title, fontWeight = FontWeight.Bold)
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.sx_hive_back))
            }
        },
        actions = {
            if (qrAvailable && uiState is HiveDetailUiState.Success) {
                IconButton(onClick = onShare) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = stringResource(R.string.sx_hive_share_qr))
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = stringResource(R.string.sx_hive_reload))
            }
        },
        colors = beeSmartTopAppBarColors()
    )
}

@Composable
private fun DetailContent(
    hive: HiveResponse,
    qrImage: androidx.compose.ui.graphics.ImageBitmap?,
    qrBitmap: Bitmap?,
    qrLink: String,
    aiAnalyses: List<InspectionAiAnalysisResponse>,
    statsMessage: String?,
    isStatsLoading: Boolean,
    snackbarHostState: SnackbarHostState,
    runWithStoragePermission: (() -> Unit) -> Unit,
    onTreatmentsClick: () -> Unit,
    onExtractionsClick: () -> Unit,
    onStatsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HiveOverviewCard(
            hive = hive,
            onTreatmentsClick = onTreatmentsClick,
            onExtractionsClick = onExtractionsClick
        )

        HiveAiStatsCard(
            analyses = aiAnalyses,
            message = statsMessage,
            isLoading = isStatsLoading,
            onOpenStats = onStatsClick
        )

        HiveQrAccessCard(
            qrAvailable = qrImage != null,
            onShowQr = { showQrDialog = true },
            onCopyLink = {
                clipboard.setText(AnnotatedString(qrLink))
                scope.launch {
                    snackbarHostState.showSnackbar("Link-ul QR a fost copiat", duration = SnackbarDuration.Short)
                }
            }
        )
    }

    if (showQrDialog) {
        HiveQrDialog(
            qrImage = qrImage,
            qrBitmap = qrBitmap,
            hiveName = hive.name,
            isSaving = isSaving,
            onDismiss = { showQrDialog = false },
            onSaveQr = {
                if (qrBitmap != null && !isSaving) {
                    runWithStoragePermission {
                        scope.launch {
                            isSaving = true
                            runCatching {
                                QrCodeUtils.saveToGallery(context, qrBitmap, hive.name)
                            }.onSuccess {
                                snackbarHostState.showSnackbar("Imagine salvată în galerie", duration = SnackbarDuration.Short)
                            }.onFailure { error ->
                                snackbarHostState.showSnackbar(
                                    error.message ?: "Eroare la salvare",
                                    duration = SnackbarDuration.Short
                                )
                            }
                            isSaving = false
                        }
                    }
                }
            }
        )
    }

}

@Composable
private fun HiveQrAccessCard(
    qrAvailable: Boolean,
    onShowQr: () -> Unit,
    onCopyLink: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(YellowPrimary.copy(alpha = 0.18f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCode2,
                    contentDescription = null,
                    tint = BrownPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.sx_hive_qr_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.sx_hive_qr_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onShowQr,
                        enabled = qrAvailable,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.sx_hive_qr_show), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onCopyLink,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.sx_hive_qr_copy), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun HiveQrDialog(
    qrImage: androidx.compose.ui.graphics.ImageBitmap?,
    qrBitmap: Bitmap?,
    hiveName: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSaveQr: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Cod QR - $hiveName", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.sx_hive_qr_dialog_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (qrImage != null) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Image(
                            bitmap = qrImage,
                            contentDescription = stringResource(R.string.sx_hive_qr_image_desc),
                            modifier = Modifier
                                .size(220.dp)
                                .padding(14.dp)
                        )
                    }
                } else {
                    Text(text = stringResource(R.string.sx_hive_qr_generate_failed), color = RedError)
                }
                Button(
                    onClick = onSaveQr,
                    enabled = qrBitmap != null && !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = BrownPrimary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onSecondary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sx_hive_qr_save_gallery))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sx_hive_close))
            }
        }
    )
}

@Composable
private fun HiveOverviewCard(
    hive: HiveResponse,
    onTreatmentsClick: () -> Unit,
    onExtractionsClick: () -> Unit
) {
    val statusColor = hive.status.statusColor()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hive.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Stupină: ${hive.apiaryName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = hive.status.statusIcon(),
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            hive.type.shortName(),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_hive_box),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = YellowPrimary.copy(alpha = 0.14f),
                        labelColor = BrownPrimary,
                        leadingIconContentColor = BrownPrimary
                    )
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            hive.status.localizedName(),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = hive.status.statusIcon(),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = statusColor.copy(alpha = 0.12f),
                        labelColor = statusColor,
                        leadingIconContentColor = statusColor
                    )
                )
            }

            Text(
                text = "Actualizat: ${formatHiveUpdatedAt(hive.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            QueenAndFramesSection(hive = hive)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HiveActionButton(
                    label = stringResource(R.string.sx_hive_action_treatments),
                    iconRes = R.drawable.ic_shield_check,
                    containerColor = YellowPrimary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onTreatmentsClick,
                    modifier = Modifier.weight(1f)
                )
                HiveActionButton(
                    label = stringResource(R.string.sx_hive_action_extractions),
                    iconRes = R.drawable.ic_honey,
                    containerColor = BrownPrimary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    onClick = onExtractionsClick,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!hive.notes.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrownPrimary.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.sx_hive_notes), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = hive.notes.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QueenAndFramesSection(hive: HiveResponse) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(YellowPrimary.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = BrownPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.sx_hive_queen_and_frames),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HiveInfoTile(
                label = stringResource(R.string.sx_hive_queen),
                value = if (hive.reginaPrezenta) stringResource(R.string.sx_hive_queen_present) else stringResource(R.string.sx_hive_queen_unconfirmed),
                modifier = Modifier.weight(1f)
            )
            HiveInfoTile(
                label = stringResource(R.string.sx_hive_queen_age),
                value = if (hive.varstaRegina > 0) "${hive.varstaRegina} ani" else "-",
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HiveInfoTile(stringResource(R.string.sx_hive_frames_bees), hive.rameAlbine.toString(), Modifier.weight(1f))
            HiveInfoTile(stringResource(R.string.sx_hive_frames_brood), hive.ramePuiet.toString(), Modifier.weight(1f))
            HiveInfoTile(stringResource(R.string.sx_hive_frames_honey), hive.rameMiere.toString(), Modifier.weight(1f))
        }

        Text(
            text = "Ultima inspecție: ${
                hive.ultimaInspectie
                    ?.takeIf { it.isNotBlank() }
                    ?.let { formatHiveUpdatedAt(it) }
                    ?: "-"
            }",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HiveInfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = BrownPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HiveActionButton(
    label: String,
    iconRes: Int,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HiveAiStatsCard(
    analyses: List<InspectionAiAnalysisResponse>,
    message: String?,
    isLoading: Boolean,
    onOpenStats: () -> Unit
) {
    val points = remember(analyses) { analyses.mapNotNull { it.toHealthPoint() } }
    val latestAnalysis = remember(analyses) { analyses.maxByOrNull { it.inspectionTimestamp() } }
    val sortedPoints = remember(points) { points.sortedWith(compareBy({ it.year }, { it.month })) }
    val latestPoint = sortedPoints.lastOrNull()
    val previousPoint = sortedPoints.dropLast(1).lastOrNull()
    val trendDelta = latestPoint?.let { latest ->
        previousPoint?.let { latest.score - it.score }
    }
    val periodLabel = remember(sortedPoints) {
        if (sortedPoints.isEmpty()) {
            "-"
        } else {
            val first = sortedPoints.first()
            val last = sortedPoints.last()
            "${first.month.shortMonthName()} ${first.year} - ${last.month.shortMonthName()} ${last.year}"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenStats() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(GreenSuccess.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = GreenSuccess)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(R.string.sx_hive_ai_deepbee_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.sx_hive_ai_deepbee_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                stringResource(R.string.sx_hive_ai_open_full_report_hint),
                color = BrownPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (points.isEmpty()) {
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = YellowPrimary
                        )
                        Text(
                            text = "Se încarcă istoricul DeepBee...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Text(
                        text = message ?: stringResource(R.string.sx_hive_ai_no_analyses),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                latestAnalysis?.let { analysis ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            label = stringResource(R.string.sx_hive_metric_cells),
                            value = analysis.totalCells.toString(),
                            accent = BrownPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = stringResource(R.string.sx_hive_metric_brood),
                            value = analysis.broodCells.toString(),
                            accent = GreenSuccess,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = stringResource(R.string.sx_hive_metric_stores),
                            value = analysis.storesCells.toString(),
                            accent = YellowPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = stringResource(R.string.sx_hive_metric_frame_indicator),
                        value = latestPoint?.score?.let { "${it.toInt()}%" } ?: "-",
                        accent = latestPoint?.score.healthColor(),
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = stringResource(R.string.sx_hive_metric_trend),
                        value = trendDelta?.formatDelta() ?: "-",
                        accent = trendDelta.trendColor(),
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = stringResource(R.string.sx_hive_metric_extractions),
                        value = "${analyses.size}",
                        accent = BrownPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    "Perioadă analizată: $periodLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = onOpenStats,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = YellowPrimary)
            ) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sx_hive_view_full_report), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AiStatsDetailsDialog(
    analyses: List<InspectionAiAnalysisResponse>,
    latestAnalysis: InspectionAiAnalysisResponse?,
    message: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sx_hive_report_deepbee_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (latestAnalysis == null) {
                    Text(
                        message ?: stringResource(R.string.sx_hive_report_no_analyses),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val spatial = remember(latestAnalysis) { latestAnalysis.spatialMetrics() }

                    Text(
                        "Ultima analiză: ${latestAnalysis.inspectionDate.take(10)} | ${analyses.size} analize în istoric",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile(stringResource(R.string.sx_hive_metric_total), latestAnalysis.totalCells.toString(), BrownPrimary, Modifier.weight(1f))
                        MetricTile(stringResource(R.string.sx_hive_metric_brood), latestAnalysis.broodCells.toString(), GreenSuccess, Modifier.weight(1f))
                        MetricTile(stringResource(R.string.sx_hive_metric_food), latestAnalysis.storesCells.toString(), YellowPrimary, Modifier.weight(1f))
                    }

                    SectionTitle(
                        stringResource(R.string.sx_hive_section_cell_distribution),
                        stringResource(R.string.sx_hive_section_cell_distribution_subtitle)
                    )
                    latestAnalysis.cellStats().forEach { stat ->
                        CellDistributionRow(stat = stat, total = latestAnalysis.totalCells)
                    }

                    SectionTitle(
                        stringResource(R.string.sx_hive_section_indicators),
                        stringResource(R.string.sx_hive_section_indicators_subtitle)
                    )
                    IndicatorRow(stringResource(R.string.sx_hive_indicator_brood_density), latestAnalysis.broodDensity.asPercent(), stringResource(R.string.sx_hive_indicator_brood_density_note))
                    IndicatorRow(stringResource(R.string.sx_hive_indicator_larvae_capped), latestAnalysis.larvaeToCappedRatio.asRatio(), stringResource(R.string.sx_hive_indicator_larvae_capped_note))
                    IndicatorRow(stringResource(R.string.sx_hive_indicator_stores), latestAnalysis.storesRatio.asPercent(), stringResource(R.string.sx_hive_indicator_stores_note))
                    if (spatial.hasCoordinates) {
                        IndicatorRow(stringResource(R.string.sx_hive_indicator_brood_compactness), spatial.broodCompactness.asPercent(), stringResource(R.string.sx_hive_indicator_brood_compactness_note))
                        IndicatorRow(stringResource(R.string.sx_hive_indicator_brood_gaps), spatial.broodGapRatio.asPercent(), stringResource(R.string.sx_hive_indicator_brood_gaps_note))
                        IndicatorRow(stringResource(R.string.sx_hive_indicator_stores_edge), spatial.storesEdgeRatio.asPercent(), stringResource(R.string.sx_hive_indicator_stores_edge_note))
                        IndicatorRow(stringResource(R.string.sx_hive_indicator_pollen_near_brood), spatial.pollenNearBroodRatio.asPercent(), stringResource(R.string.sx_hive_indicator_pollen_near_brood_note))
                    }

                    SectionTitle(stringResource(R.string.sx_hive_section_interpretation), stringResource(R.string.sx_hive_section_interpretation_subtitle))
                    latestAnalysis.apicultureInsights().forEach { insight ->
                        Text("- $insight", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sx_hive_close))
            }
        }
    )
}

@Composable
private fun CellDistributionRow(stat: CellStat, total: Int) {
    val pct = if (total <= 0) 0.0 else stat.count.toDouble() / total.toDouble()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("${stat.count} (${(pct * 100).toInt()}%)", fontWeight = FontWeight.SemiBold)
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
private fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(value, color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LongitudinalLineChart(points: List<ChartPoint>) {
    val valid = points.mapIndexedNotNull { index, point ->
        point.value?.let { index to it.coerceIn(0.0, 100.0) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
                .background(Color(0xFFF8FAF7), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            val left = 34f
            val top = 16f
            val right = size.width - 10f
            val bottom = size.height - 28f
            val chartWidth = right - left
            val chartHeight = bottom - top

            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { ratio ->
                val y = bottom - chartHeight * ratio
                drawLine(
                    color = Color(0xFFE4E8DD),
                    start = androidx.compose.ui.geometry.Offset(left, y),
                    end = androidx.compose.ui.geometry.Offset(right, y),
                    strokeWidth = 1.2f
                )
            }

            drawLine(Color(0xFFB8BFAF), androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left, bottom), 1.6f)
            drawLine(Color(0xFFB8BFAF), androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(right, bottom), 1.6f)

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
                    drawCircle(color = value.healthColor(), radius = 7f, center = offset)
                    drawCircle(color = Color(0xFFF8FAF7), radius = 3f, center = offset)
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
private fun MonthlyBars(points: List<ChartPoint>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        points.forEach { point ->
            VerticalBar(point = point, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun YearBars(points: List<ChartPoint>) {
    if (points.isEmpty()) {
        Text(stringResource(R.string.sx_hive_no_analyses_for_month), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            points.forEach { point ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(point.label, modifier = Modifier.width(48.dp), fontWeight = FontWeight.Medium)
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
                        modifier = Modifier.width(44.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalBar(point: ChartPoint, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(point.value?.let { "${it.toInt()}" } ?: "-", style = MaterialTheme.typography.labelSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((((point.value ?: 0.0) / 100.0) * 104).toFloat()).dp.coerceAtLeast(6.dp))
                    .background(point.value.healthColor(), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(point.label, style = MaterialTheme.typography.labelSmall)
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
private fun MethodologyNote() {
    Column(
        modifier = Modifier
            .background(BrownPrimary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(R.string.sx_hive_methodology_title), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(R.string.sx_hive_methodology_text),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private data class HiveHealthPoint(val year: Int, val month: Int, val score: Double)
private data class ChartPoint(val label: String, val value: Double?)
private data class CellStat(val label: String, val count: Int, val color: Color)

private fun formatHiveUpdatedAt(value: String): String {
    if (value.isBlank()) return "-"
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("ro-RO"))
    return runCatching {
        val zoned = if (value.all { it.isDigit() }) {
            Instant.ofEpochMilli(value.toLong()).atZone(ZoneId.systemDefault())
        } else {
            OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
        }
        zoned.format(formatter)
    }.getOrElse {
        value.replace("T", " ").take(16)
    }
}

private fun InspectionAiAnalysisResponse.inspectionTimestamp(): Long =
    runCatching { OffsetDateTime.parse(inspectionDate).toInstant().toEpochMilli() }.getOrDefault(0L)

private fun InspectionAiAnalysisResponse.cellStats(): List<CellStat> = listOf(
    CellStat("Puiet căpăcit", cappedBroodCells, GreenSuccess),
    CellStat("Larve", larvaeCells, Color(0xFF2E7D32)),
    CellStat("Ouă", eggsCells, Color(0xFF66BB6A)),
    CellStat("Miere", honeyCells, YellowPrimary),
    CellStat("Polen", pollenCells, Color(0xFFFF9800)),
    CellStat("Celule goale", emptyCells, Color(0xFF9E9E9E)),
    CellStat("Alte celule", otherCells, BrownPrimary)
).filter { it.count > 0 }

private fun Double?.asPercent(): String =
    this?.let { "${(it.coerceIn(0.0, 1.0) * 100).toInt()}%" } ?: "-"

private fun Double?.asRatio(): String =
    this?.let { String.format(Locale.US, "%.2f", it) } ?: "-"

private fun InspectionAiAnalysisResponse.spatialMetrics(): BroodAnalyzer.SpatialMetrics =
    BroodAnalyzer.analyze(results, cellDetections).spatial

private fun Double.finiteOrNull(): Double? = takeIf { it.isFinite() }

private fun Double?.orZero(): Double = this ?: 0.0

private fun InspectionAiAnalysisResponse.apicultureInsights(): List<String> {
    val insights = mutableListOf<String>()
    val broodPct = if (totalCells > 0) broodCells.toDouble() / totalCells else 0.0
    val storesPct = storesRatio ?: 0.0
    val spatial = spatialMetrics()

    if (broodCells == 0 && totalCells > 0) {
        insights += "Nu s-a detectat puiet pe rama analizată; verifică mai întâi dacă rama aparține cuibului și compară cu ramele vecine."
    }
    if (cappedBroodCells > 0 && larvaeCells == 0) {
        insights += "Există puiet căpăcit, dar nu s-au identificat larve pe această ramă; compară cu ramele vecine și cu evoluția coloniei."
    }
    if (larvaeCells > 0 && cappedBroodCells == 0) {
        insights += "Larve prezente fără puiet căpăcit; colonia poate fi într-o etapă timpurie a ciclului de puiet."
    }
    if (eggsCells > 0 && larvaeCells > 0 && cappedBroodCells > 0) {
        insights += "Sunt prezente ouă, larve și puiet căpăcit; semnal bun pentru continuitatea pontei."
    }
    if (broodPct >= 0.55) {
        insights += "Ponderea puietului pe rama analizată este ridicată; uniformitatea se confirmă prin inspecție vizuală."
    } else if (broodPct in 0.01..0.25) {
        insights += "Ponderea puietului pe rama analizată este redusă; compară cu inspecțiile anterioare și cu ramele vecine."
    }
    if (storesPct >= 0.20) {
        insights += "Rezervele de miere și polen sunt consistente pentru rama analizată."
    } else if (totalCells > 30) {
        insights += "Rezervele par limitate pe rama analizată; verifică ramele vecine, sezonul și vremea înainte de a decide hrănirea."
    }
    if ((larvaeToCappedRatio ?: 1.0) < 0.20 && cappedBroodCells >= 20) {
        insights += "Raportul larve/căpăcit este scăzut; merită urmărit la următoarea inspecție."
    }

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

private fun InspectionAiAnalysisResponse.toHealthPoint(): HiveHealthPoint? {
    val date = runCatching { OffsetDateTime.parse(inspectionDate) }.getOrNull() ?: return null
    val brood = (broodDensity ?: 0.0).coerceIn(0.0, 1.0)
    val stores = (storesRatio ?: 0.0).coerceIn(0.0, 1.0)
    val spatial = spatialMetrics()
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
    return HiveHealthPoint(date.year, date.monthValue, score)
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

private fun Double.formatDelta(): String {
    val sign = if (this > 0) "+" else ""
    return "$sign${toInt()} pp"
}

private fun Double?.trendColor(): Color = when {
    this == null -> Gray
    this >= 4.0 -> GreenSuccess
    this <= -4.0 -> RedError
    else -> YellowPrimary
}

private fun Double?.healthColor(): Color = when {
    this == null -> LightGray
    this >= 70.0 -> GreenSuccess
    this >= 45.0 -> YellowPrimary
    else -> RedError
}

private fun Int.shortMonthName(): String =
    java.time.Month.of(this).getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ro"))
        .replaceFirstChar { it.uppercase(Locale.forLanguageTag("ro")) }

private fun Int.monthName(): String =
    java.time.Month.of(this).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ro"))
        .replaceFirstChar { it.uppercase(Locale.forLanguageTag("ro")) }

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = YellowPrimary)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painter = painterResource(id = R.drawable.ic_qr), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
            Text(text = message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.sx_hive_retry))
            }
        }
    }
}
