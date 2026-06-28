package com.example.beesmart.ui.inspections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.beesmart.R
import com.example.beesmart.network.models.InspectionDetailResponse
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.*
import com.example.beesmart.utils.Base64Fetcher
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionDetailScreen(
    inspectionId: String,
    hiveId: String?,
    onNavigateBack: () -> Unit,
    onEditInspection: (String) -> Unit,
    viewModel: InspectionDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiAnalysis by viewModel.aiAnalysis.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadDetails()
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is InspectionDetailUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
            }
            is InspectionDetailUiState.OperationSuccess -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Short
                )
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Detalii Inspecție",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Înapoi")
                    }
                },
                actions = {
                    if (uiState is InspectionDetailUiState.Success) {
                        IconButton(onClick = { onEditInspection(inspectionId) }) {
                            Icon(Icons.Default.Edit, "Editează")
                        }
                    }
                },
                colors = beeSmartTopAppBarColors()
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
                is InspectionDetailUiState.Success -> {
                    InspectionDetailContent(
                        inspection = state.inspection,
                        aiAnalysis = aiAnalysis
                    )
                }
                is InspectionDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = GreenSuccess)
                    }
                }
                is InspectionDetailUiState.Error -> {
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
                            "Eroare la încărcarea detaliilor",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is InspectionDetailUiState.OperationSuccess -> {
                    // Handled by snackbar
                }
            }
        }
    }
}

@Composable
fun InspectionDetailContent(
    inspection: InspectionDetailResponse,
    aiAnalysis: AnalyzeCellsUiState.Success? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Hive and Date Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = inspection.hiveName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = GreenSuccess
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = inspection.apiaryName,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = GreenSuccess
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDate(inspection.inspectionDate),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Measurements
        Text(
            "Măsurători",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = GreenSuccess
        )

        Spacer(modifier = Modifier.height(8.dp))

        inspection.framesCount?.let { frames ->
            DetailRow(
                icon = Icons.Default.ViewModule,
                label = "Total rame",
                value = "$frames"
            )
        }

        inspection.broodFrames?.let { brood ->
            DetailRow(
                icon = Icons.Default.BabyChangingStation,
                label = "Rame cu puiet",
                value = "$brood"
            )
        }

        inspection.honeyFrames?.let { honey ->
            DetailRow(
                icon = Icons.Default.WaterDrop,
                label = "Rame cu miere",
                value = "$honey"
            )
        }

        inspection.pollenFrames?.let { pollen ->
            DetailRow(
                icon = Icons.Default.Grass,
                label = "Rame cu polen",
                value = "$pollen"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Observations
        Text(
            "Observații",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = GreenSuccess
        )

        Spacer(modifier = Modifier.height(8.dp))

        ObservationChip("Regină văzută", inspection.queenSeen)
        ObservationChip("Ouă văzute", inspection.eggsSeen)
        ObservationChip("Larve văzute", inspection.larvaeSeen)

        // Notes
        inspection.notes?.takeIf { it.isNotBlank() }?.let { noteText ->
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Notițe",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = GreenSuccess
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = noteText,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
        }

        // AI analysis
        aiAnalysis?.let { analysis ->
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Analiză AI",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = GreenSuccess
            )

            Spacer(modifier = Modifier.height(8.dp))

            BroodAdvisoryCard(state = analysis)
        }

        // Photos
        if (inspection.photos.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Fotografii (${inspection.photos.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = GreenSuccess
            )

            Spacer(modifier = Modifier.height(8.dp))

            val context = LocalContext.current
            val imageLoader = remember {
                ImageLoader.Builder(context)
                    .components {
                        add(Base64Fetcher.Factory())
                    }
                    .build()
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(((inspection.photos.size + 2) / 3 * 120).dp)
            ) {
                items(inspection.photos) { photo ->
                    Card(
                        modifier = Modifier.aspectRatio(1f),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Base64Image(
                            base64String = photo.photoUrl,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = GreenSuccess
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ObservationChip(
    label: String,
    isPresent: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isPresent) GreenSuccess.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isPresent) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isPresent) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (isPresent) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDate(dateString: String): String {
    return try {
        val zonedDateTime = ZonedDateTime.parse(dateString)
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ro"))
        zonedDateTime.format(formatter)
    } catch (e: Exception) {
        dateString
    }
}

@Composable
fun Base64Image(
    base64String: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(base64String) {
        try {
            // Remove the data:image/jpeg;base64, prefix if present
            val base64Data = if (base64String.startsWith("data:image")) {
                base64String.substring(base64String.indexOf(",") + 1)
            } else {
                base64String
            }

            val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            android.util.Log.e("Base64Image", "Failed to decode base64 image", e)
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Photo",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        // Show error placeholder
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Error",
                tint = Color.Red,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
