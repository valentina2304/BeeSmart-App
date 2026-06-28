package com.example.beesmart.ui.inspections

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.beesmart.R
import com.example.beesmart.ui.theme.*
import com.example.beesmart.utils.PhotoManager
import com.example.beesmart.ui.components.UnsavedChangesDialog
import com.example.beesmart.ui.components.VoiceInputButton
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import android.util.Base64
import android.graphics.BitmapFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

private data class InspectionSnapshot(
    val hiveId: String?,
    val date: LocalDateTime,
    val framesCount: Int?,
    val broodFrames: Int?,
    val honeyFrames: Int?,
    val pollenFrames: Int?,
    val queenSeen: Boolean,
    val eggsSeen: Boolean,
    val larvaeSeen: Boolean,
    val queenCellsSeen: Boolean,
    val queenCellsWithEggs: Boolean,
    val beardingAtEntrance: Boolean,
    val spaceNeeded: Boolean,
    val broodPattern: String,
    val feedingGiven: Boolean,
    val waterAvailable: Boolean,
    val moistureOrMold: Boolean,
    val deadBeesAtEntrance: Boolean,
    val unusualBehavior: Boolean,
    val temperament: String,
    val oldCombsToReplace: Int?,
    val notes: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun InspectionFormScreen(
    inspectionId: String?,
    hiveId: String?,
    onNavigateBack: () -> Unit,
    viewModel: InspectionFormViewModel = hiltViewModel(),
    photoManager: PhotoManager
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val hives by viewModel.hives.collectAsState()
    val allPhotos by viewModel.allPhotos.collectAsState()
    val cellsAnalysisState by viewModel.cellsAnalysisState.collectAsState()
    val photoAnalyses by viewModel.photoAnalyses.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var selectedHiveId by remember { mutableStateOf(hiveId) }
    var selectedDate by remember { mutableStateOf(LocalDateTime.now()) }
    var framesCount by remember { mutableStateOf<Int?>(null) }
    var broodFrames by remember { mutableStateOf<Int?>(null) }
    var honeyFrames by remember { mutableStateOf<Int?>(null) }
    var pollenFrames by remember { mutableStateOf<Int?>(null) }
    var queenSeen by remember { mutableStateOf(false) }
    var eggsSeen by remember { mutableStateOf(false) }
    var larvaeSeen by remember { mutableStateOf(false) }
    var queenCellsSeen by remember { mutableStateOf(false) }
    var queenCellsWithEggs by remember { mutableStateOf(false) }
    var beardingAtEntrance by remember { mutableStateOf(false) }
    var spaceNeeded by remember { mutableStateOf(false) }
    var broodPattern by remember { mutableStateOf("") }
    var feedingGiven by remember { mutableStateOf(false) }
    var waterAvailable by remember { mutableStateOf(false) }
    var moistureOrMold by remember { mutableStateOf(false) }
    var deadBeesAtEntrance by remember { mutableStateOf(false) }
    var unusualBehavior by remember { mutableStateOf(false) }
    var temperament by remember { mutableStateOf("") }
    var oldCombsToReplace by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var showHiveDropdown by remember { mutableStateOf(false) }
    var previewPhoto by remember { mutableStateOf<PhotoItem?>(null) }
    var selectedPhotoKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    var currentPhotoFile by remember { mutableStateOf<File?>(null) }

    val isEditMode = inspectionId != null
    val isLoading = uiState is InspectionFormUiState.Loading
    val hasLocalPhotos = allPhotos.any { it is PhotoItem.Local }
    val isCellsAnalyzing = photoAnalyses.values.any { it is AnalyzeCellsUiState.Loading }
    val selectedHive = hives.find { it.id == selectedHiveId }

    var showUnsavedDialog by remember { mutableStateOf(false) }
    var baseline by remember { mutableStateOf<InspectionSnapshot?>(null) }
    val currentSnapshot = {
        InspectionSnapshot(
            selectedHiveId, selectedDate, framesCount, broodFrames,
            honeyFrames, pollenFrames, queenSeen, eggsSeen, larvaeSeen, queenCellsSeen,
            queenCellsWithEggs, beardingAtEntrance, spaceNeeded, broodPattern,
            feedingGiven, waterAvailable, moistureOrMold,
            deadBeesAtEntrance, unusualBehavior, temperament, oldCombsToReplace, notes
        )
    }
    // For a new inspection, capture the default baseline once at first composition.
    LaunchedEffect(Unit) {
        if (!isEditMode && baseline == null) baseline = currentSnapshot()
    }

    // Camera permission
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoFile?.let { photoFile ->
                viewModel.processAndAddPhoto(photoFile)
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val tempFile = photoManager.createTempPhotoFile().first
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            viewModel.processAndAddPhoto(tempFile)
        }
    }

    // Load data in edit mode
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is InspectionFormUiState.LoadedData -> {
                val inspection = state.inspection
                selectedHiveId = inspection.hiveId
                try {
                    selectedDate = LocalDateTime.parse(
                        inspection.inspectionDate,
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    )
                } catch (e: Exception) {
                    selectedDate = LocalDateTime.now()
                }
                framesCount = inspection.framesCount
                broodFrames = inspection.broodFrames
                honeyFrames = inspection.honeyFrames
                pollenFrames = inspection.pollenFrames
                queenSeen = inspection.queenSeen
                eggsSeen = inspection.eggsSeen
                larvaeSeen = inspection.larvaeSeen
                queenCellsSeen = inspection.queenCellsSeen
                queenCellsWithEggs = inspection.queenCellsWithEggs
                beardingAtEntrance = inspection.beardingAtEntrance
                spaceNeeded = inspection.spaceNeeded
                broodPattern = inspection.broodPattern ?: ""
                feedingGiven = inspection.feedingGiven
                waterAvailable = inspection.waterAvailable
                moistureOrMold = inspection.moistureOrMold
                deadBeesAtEntrance = inspection.deadBeesAtEntrance
                unusualBehavior = inspection.unusualBehavior
                temperament = inspection.temperament ?: ""
                oldCombsToReplace = inspection.oldCombsToReplace
                notes = inspection.notes ?: ""
                baseline = currentSnapshot()
            }
            is InspectionFormUiState.Success -> {
                onNavigateBack()
                viewModel.resetState()
            }
            is InspectionFormUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.resetState()
            }
            else -> {}
        }
    }

    val saveInspection = {
        viewModel.saveInspection(
            hiveId = selectedHiveId,
            date = selectedDate,
            frames = framesCount,
            brood = broodFrames,
            honey = honeyFrames,
            pollen = pollenFrames,
            queen = queenSeen,
            eggs = eggsSeen,
            larvae = larvaeSeen,
            queenCellsSeen = queenCellsSeen,
            queenCellsWithEggs = queenCellsWithEggs,
            beardingAtEntrance = beardingAtEntrance,
            spaceNeeded = spaceNeeded,
            broodPattern = broodPattern.takeIf { it.isNotBlank() },
            feedingGiven = feedingGiven,
            waterAvailable = waterAvailable,
            moistureOrMold = moistureOrMold,
            deadBeesAtEntrance = deadBeesAtEntrance,
            unusualBehavior = unusualBehavior,
            temperament = temperament.takeIf { it.isNotBlank() },
            oldCombsToReplace = oldCombsToReplace?.coerceAtLeast(0),
            notes = notes.takeIf { it.isNotBlank() }
        )
        Unit
    }

    // Unsaved-changes guard: dirty if any field differs from the baseline, or there are
    // photos added in this session that haven't been saved yet.
    val isDirty = (baseline?.let { it != currentSnapshot() } ?: false) || hasLocalPhotos
    val canSave = !isLoading && selectedHiveId != null
    val attemptBack = {
        if (isDirty) showUnsavedDialog = true else onNavigateBack()
    }

    BackHandler(enabled = isDirty) { showUnsavedDialog = true }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            canSave = canSave,
            onSave = {
                showUnsavedDialog = false
                saveInspection()
            },
            onDiscard = {
                showUnsavedDialog = false
                onNavigateBack()
            },
            onDismiss = { showUnsavedDialog = false }
        )
    }

    // Photo source dialog
    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text(stringResource(R.string.sx_insp_add_photo)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            if (cameraPermission.status.isGranted) {
                                val (photoFile, photoUri) = photoManager.createTempPhotoFile()
                                currentPhotoFile = photoFile
                                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                    putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                                }
                                cameraLauncher.launch(intent)
                            } else {
                                cameraPermission.launchPermissionRequest()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sx_insp_camera))
                    }
                    TextButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sx_insp_gallery))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) {
                    Text(stringResource(R.string.sx_insp_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) stringResource(R.string.sx_insp_edit_title) else stringResource(R.string.sx_insp_new_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sx_insp_back))
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                InspectionReadinessPanel(
                    hiveName = selectedHive?.name,
                    dateLabel = selectedDate.format(DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())),
                    photoCount = allPhotos.size,
                    analysisState = cellsAnalysisState
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Hive selection
                ExposedDropdownMenuBox(
                    expanded = showHiveDropdown,
                    onExpandedChange = { showHiveDropdown = !isLoading && it }
                ) {
                    OutlinedTextField(
                        value = selectedHive?.let {
                            "${it.name} (${it.apiaryName})"
                        } ?: stringResource(R.string.sx_insp_select_hive),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.sx_insp_hive_label)) },
                        enabled = !isLoading,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showHiveDropdown)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GreenSuccess,
                            focusedLabelColor = GreenSuccess
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = showHiveDropdown,
                        onDismissRequest = { showHiveDropdown = false }
                    ) {
                        hives.forEach { hive ->
                            DropdownMenuItem(
                                text = { Text("${hive.name} (${hive.apiaryName})") },
                                onClick = {
                                    selectedHiveId = hive.id
                                    showHiveDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date picker — a disabled text field with a clickable overlay so the
                // tap actually reaches the date dialog (a read-only field swallows it).
                Box {
                    OutlinedTextField(
                        value = selectedDate.format(
                            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
                        ),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.sx_insp_date_label)) },
                        enabled = false,
                        readOnly = true,
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null)
                        },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = GreenSuccess,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(enabled = !isLoading) { showDatePicker = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Frames section — feeds smart suggestions
                InspectionSectionHeader(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.sx_insp_population_title),
                    subtitle = stringResource(R.string.sx_insp_population_subtitle)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InspectionStepper(stringResource(R.string.sx_insp_frames_total), framesCount, !isLoading, max = 99, modifier = Modifier.weight(1f)) { framesCount = it }
                    InspectionStepper(stringResource(R.string.sx_insp_frames_brood), broodFrames, !isLoading, max = 99, modifier = Modifier.weight(1f)) { broodFrames = it }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InspectionStepper(stringResource(R.string.sx_insp_frames_honey), honeyFrames, !isLoading, max = 99, modifier = Modifier.weight(1f)) { honeyFrames = it }
                    InspectionStepper(stringResource(R.string.sx_insp_frames_pollen), pollenFrames, !isLoading, max = 99, modifier = Modifier.weight(1f)) { pollenFrames = it }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.sx_insp_dash_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Queen & brood — feeds smart suggestions
                InspectionSectionHeader(
                    icon = Icons.Default.Visibility,
                    title = stringResource(R.string.sx_insp_queen_brood_title),
                    subtitle = stringResource(R.string.sx_insp_queen_brood_subtitle)
                )

                Spacer(modifier = Modifier.height(8.dp))

                InspectionQuickCheck(stringResource(R.string.sx_insp_queen_seen), queenSeen, !isLoading) { queenSeen = it }
                InspectionQuickCheck(stringResource(R.string.sx_insp_eggs_seen), eggsSeen, !isLoading) { eggsSeen = it }
                InspectionQuickCheck(stringResource(R.string.sx_insp_larvae_seen), larvaeSeen, !isLoading) { larvaeSeen = it }

                Spacer(modifier = Modifier.height(8.dp))

                InspectionOptionSelector(
                    label = stringResource(R.string.sx_insp_brood_uniformity),
                    value = broodPattern,
                    options = listOf(stringResource(R.string.sx_insp_brood_compact), stringResource(R.string.sx_insp_brood_uneven), stringResource(R.string.sx_insp_brood_spotty)),
                    enabled = !isLoading
                ) { broodPattern = it }

                Spacer(modifier = Modifier.height(16.dp))

                // Swarming & space — feeds smart suggestions
                InspectionSectionHeader(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    title = stringResource(R.string.sx_insp_swarm_title),
                    subtitle = stringResource(R.string.sx_insp_swarm_subtitle)
                )

                Spacer(modifier = Modifier.height(8.dp))

                InspectionQuickCheck(stringResource(R.string.sx_insp_queen_cells_seen), queenCellsSeen, !isLoading) { queenCellsSeen = it }
                InspectionQuickCheck(stringResource(R.string.sx_insp_queen_cells_eggs), queenCellsWithEggs, !isLoading) { queenCellsWithEggs = it }
                InspectionQuickCheck(stringResource(R.string.sx_insp_bearding), beardingAtEntrance, !isLoading) { beardingAtEntrance = it }
                InspectionQuickCheck(stringResource(R.string.sx_insp_space_needed), spaceNeeded, !isLoading) { spaceNeeded = it }

                Spacer(modifier = Modifier.height(16.dp))

                // Optional extras — stored but NOT used by smart suggestions
                InspectionSectionHeader(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.sx_insp_extra_title),
                    subtitle = stringResource(R.string.sx_insp_extra_subtitle)
                )

                Spacer(modifier = Modifier.height(8.dp))

                InspectionOptionSelector(
                    label = stringResource(R.string.sx_insp_temperament),
                    value = temperament,
                    options = listOf(stringResource(R.string.sx_insp_temperament_gentle), stringResource(R.string.sx_insp_temperament_normal), stringResource(R.string.sx_insp_temperament_aggressive)),
                    enabled = !isLoading
                ) { temperament = it }

                Spacer(modifier = Modifier.height(8.dp))

                InspectionStepper(
                    stringResource(R.string.sx_insp_old_combs),
                    oldCombsToReplace,
                    !isLoading,
                    max = 50,
                    modifier = Modifier.fillMaxWidth()
                ) { oldCombsToReplace = it }

                Spacer(modifier = Modifier.height(8.dp))

                InspectionQuickCheck(stringResource(R.string.sx_insp_feeding_given), feedingGiven, !isLoading) { feedingGiven = it }
                InspectionQuickCheck(stringResource(R.string.sx_insp_water_available), waterAvailable, !isLoading) { waterAvailable = it }
                InspectionQuickCheck(stringResource(R.string.sx_insp_moisture_mold), moistureOrMold, !isLoading) { moistureOrMold = it }
                InspectionQuickCheck(stringResource(R.string.sx_insp_dead_bees), deadBeesAtEntrance, !isLoading) { deadBeesAtEntrance = it }
                InspectionQuickCheck(stringResource(R.string.sx_insp_unusual_behavior), unusualBehavior, !isLoading) { unusualBehavior = it }

                Spacer(modifier = Modifier.height(16.dp))

                // Photos section
                InspectionSectionHeader(
                    icon = Icons.Default.CameraAlt,
                    title = stringResource(R.string.sx_insp_photos_deepbee_title),
                    subtitle = "${allPhotos.size} fotografii atașate"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Photo grid
                if (allPhotos.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(allPhotos) { photoItem ->
                            PhotoItemCard(
                                photoItem = photoItem,
                                analysisState = photoAnalyses[photoItem.key],
                                selected = photoItem.key in selectedPhotoKeys,
                                onToggleSelect = {
                                    selectedPhotoKeys = if (photoItem.key in selectedPhotoKeys) {
                                        selectedPhotoKeys - photoItem.key
                                    } else {
                                        selectedPhotoKeys + photoItem.key
                                    }
                                },
                                onClick = { previewPhoto = photoItem },
                                onDelete = { viewModel.removePhoto(photoItem) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { showPhotoSourceDialog = true },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenSuccess.copy(alpha = 0.2f),
                        contentColor = GreenSuccess
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sx_insp_add_photo))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Photo selection + AI analysis controls
                if (allPhotos.isNotEmpty()) {
                    val allKeys = allPhotos.map { it.key }.toSet()
                    val allSelected = allKeys.isNotEmpty() && selectedPhotoKeys.containsAll(allKeys)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                selectedPhotoKeys = if (allSelected) emptySet() else allKeys
                            },
                            enabled = !isLoading
                        ) {
                            Text(if (allSelected) stringResource(R.string.sx_insp_deselect_all) else stringResource(R.string.sx_insp_select_all))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${selectedPhotoKeys.size} selectate",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.analyzePhotos(allPhotos.filter { it.key in selectedPhotoKeys })
                            },
                            enabled = !isLoading && selectedPhotoKeys.isNotEmpty() && !isCellsAnalyzing,
                            colors = ButtonDefaults.buttonColors(containerColor = YellowDark),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isCellsAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Camera, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isCellsAnalyzing) stringResource(R.string.sx_insp_analyzing)
                                else "Analizează selecția (${selectedPhotoKeys.size})",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.analyzeAllPhotos() },
                            enabled = !isLoading && !isCellsAnalyzing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.sx_insp_all))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.sx_insp_analyze_hint),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                AnalysisStatusBanner(
                    state = cellsAnalysisState,
                    hasLocalPhotos = allPhotos.isNotEmpty(),
                    photoCount = allPhotos.size
                )

                when (val state = cellsAnalysisState) {
                    is AnalyzeCellsUiState.Success -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.sx_insp_summary_label),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GreenSuccess
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        BroodAdvisoryCard(state = state)
                    }
                    is AnalyzeCellsUiState.Error -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = RedError.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = RedError
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.sx_insp_analysis_failed),
                                        fontWeight = FontWeight.Bold,
                                        color = RedError
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(state.message, fontSize = 14.sp)
                                if (state.isRetryable) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(onClick = { viewModel.analyzeAllPhotos() }) {
                                        Text(stringResource(R.string.sx_insp_retry))
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Notes section
                InspectionSectionHeader(
                    icon = Icons.Default.Edit,
                    title = stringResource(R.string.sx_insp_notes_title),
                    subtitle = stringResource(R.string.sx_insp_notes_subtitle)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 2000) notes = it },
                    label = { Text(stringResource(R.string.sx_insp_notes_label)) },
                    placeholder = { Text(stringResource(R.string.sx_insp_notes_placeholder)) },
                    enabled = !isLoading,
                    minLines = 3,
                    maxLines = 8,
                    trailingIcon = {
                        VoiceInputButton(
                            onVoiceResult = { spoken ->
                                val addition = spoken.trim()
                                if (addition.isNotEmpty()) {
                                    notes = (if (notes.isBlank()) addition else "$notes $addition").take(2000)
                                }
                            },
                            prompt = stringResource(R.string.sx_insp_notes_voice_prompt),
                            enabled = !isLoading
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenSuccess,
                        focusedLabelColor = GreenSuccess,
                        cursorColor = GreenSuccess
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Save button
                Button(
                    onClick = { saveInspection() },
                    enabled = !isLoading && selectedHiveId != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = BrownPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = BrownPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEditMode) stringResource(R.string.sx_insp_save) else stringResource(R.string.sx_insp_create), fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Photo preview + per-photo analysis dialog
    previewPhoto?.let { photo ->
        val analysis = photoAnalyses[photo.key]
        Dialog(
            onDismissRequest = { previewPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.sx_insp_preview_title),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { previewPhoto = null }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.sx_insp_close))
                        }
                    }

                    val zoomScale = remember(photo.key) { mutableStateOf(1f) }
                    val zoomOffset = remember(photo.key) { mutableStateOf(Offset.Zero) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF101010))
                            .pointerInput(photo.key) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (zoomScale.value * zoom).coerceIn(1f, 5f)
                                    zoomScale.value = newScale
                                    zoomOffset.value =
                                        if (newScale <= 1f) Offset.Zero else zoomOffset.value + pan
                                }
                            }
                            .pointerInput(photo.key) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (zoomScale.value > 1f) {
                                            zoomScale.value = 1f
                                            zoomOffset.value = Offset.Zero
                                        } else {
                                            zoomScale.value = 2.5f
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val imageModifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale.value,
                                scaleY = zoomScale.value,
                                translationX = zoomOffset.value.x,
                                translationY = zoomOffset.value.y
                            )
                        when (photo) {
                            is PhotoItem.Local -> AsyncImage(
                                model = photo.file,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = imageModifier
                            )
                            is PhotoItem.Remote -> Base64Image(
                                base64String = photo.photo.photoUrl,
                                contentDescription = null,
                                modifier = imageModifier,
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.sx_insp_zoom_hint),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    when (val s = analysis) {
                        is AnalyzeCellsUiState.Loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = GreenSuccess,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.sx_insp_analyzing_photo), fontSize = 14.sp)
                            }
                        }
                        is AnalyzeCellsUiState.Success -> {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                BroodAdvisoryCard(state = s)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        is AnalyzeCellsUiState.Error -> {
                            Text(s.message, fontSize = 14.sp, color = RedError)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        else -> {}
                    }

                    Button(
                        onClick = { viewModel.analyzePhoto(photo) },
                        enabled = analysis !is AnalyzeCellsUiState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = YellowDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Camera, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (analysis is AnalyzeCellsUiState.Success) stringResource(R.string.sx_insp_reanalyze_photo)
                            else stringResource(R.string.sx_insp_analyze_photo)
                        )
                    }
                }
            }
        }
    }

    // Date picker dialog (simple implementation)
    if (showDatePicker) {
        // Note: For production, use a proper date picker library or DatePickerDialog
        DatePickerDialog(
            onDismiss = { showDatePicker = false },
            onDateSelected = { year, month, day ->
                selectedDate = LocalDateTime.of(year, month, day, LocalDateTime.now().hour, 0)
                showDatePicker = false
            },
            initialDate = selectedDate
        )
    }
}

@Composable
private fun InspectionReadinessPanel(
    hiveName: String?,
    dateLabel: String,
    photoCount: Int,
    analysisState: AnalyzeCellsUiState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, tint = GreenSuccess)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.sx_insp_status_title), fontWeight = FontWeight.Bold, color = GreenSuccess)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadinessTile(stringResource(R.string.sx_insp_tile_hive), hiveName ?: stringResource(R.string.sx_insp_tile_unset), hiveName != null, Modifier.weight(1f))
            ReadinessTile(stringResource(R.string.sx_insp_tile_date), dateLabel, true, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadinessTile(stringResource(R.string.sx_insp_tile_photo), photoCount.toString(), photoCount > 0, Modifier.weight(1f))
            ReadinessTile(stringResource(R.string.sx_insp_tile_deepbee), analysisState.readinessLabel(), analysisState is AnalyzeCellsUiState.Success, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReadinessTile(
    label: String,
    value: String,
    ready: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (ready) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .background(color.copy(alpha = if (ready) 0.12f else 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InspectionSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalysisStatusBanner(
    state: AnalyzeCellsUiState,
    hasLocalPhotos: Boolean,
    photoCount: Int
) {
    val (icon, title, message, color) = when (state) {
        AnalyzeCellsUiState.Idle -> if (hasLocalPhotos) {
            AnalysisBanner(
                icon = Icons.Default.Camera,
                title = stringResource(R.string.sx_insp_banner_ready_title),
                message = stringResource(R.string.sx_insp_banner_ready_message),
                color = YellowDark
            )
        } else {
            AnalysisBanner(
                icon = Icons.Default.AddPhotoAlternate,
                title = if (photoCount > 0) stringResource(R.string.sx_insp_banner_add_new_photo) else stringResource(R.string.sx_insp_banner_add_first_photo),
                message = stringResource(R.string.sx_insp_banner_add_photo_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnalyzeCellsUiState.Loading -> AnalysisBanner(
            icon = Icons.Default.Bolt,
            title = stringResource(R.string.sx_insp_banner_running_title),
            message = stringResource(R.string.sx_insp_banner_running_message),
            color = YellowDark
        )
        is AnalyzeCellsUiState.Success -> {
            val spatialLabel = if (state.report.spatial.hasCoordinates) {
                "${state.report.spatial.analyzedCells} coordonate"
            } else {
                stringResource(R.string.sx_insp_banner_saved_counts)
            }
            AnalysisBanner(
                icon = Icons.Default.CheckCircle,
                title = stringResource(R.string.sx_insp_banner_available_title),
                message = "${state.report.metrics.total} celule analizate · $spatialLabel",
                color = GreenSuccess
            )
        }
        is AnalyzeCellsUiState.Error -> AnalysisBanner(
            icon = Icons.Default.Error,
            title = stringResource(R.string.sx_insp_banner_retry_title),
            message = state.message,
            color = RedError
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = color)
            Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class AnalysisBanner(
    val icon: ImageVector,
    val title: String,
    val message: String,
    val color: Color
)

private fun AnalyzeCellsUiState.readinessLabel(): String = when (this) {
    AnalyzeCellsUiState.Idle -> "neanalizat"
    AnalyzeCellsUiState.Loading -> "rulează"
    is AnalyzeCellsUiState.Success -> "gata"
    is AnalyzeCellsUiState.Error -> "eroare"
}

@Composable
private fun InspectionStepper(
    label: String,
    value: Int?,
    enabled: Boolean,
    min: Int = 0,
    max: Int = 99,
    step: Int = 1,
    suffix: String? = null,
    modifier: Modifier = Modifier,
    onValueChange: (Int?) -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GreenSuccess.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    val decremented = (value ?: min) - step
                    onValueChange(if (decremented < min) null else decremented)
                },
                enabled = enabled && value != null
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = stringResource(R.string.sx_insp_stepper_decrease),
                    tint = if (enabled && value != null) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            Text(
                text = value?.let { "$it${suffix ?: ""}" } ?: "—",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = {
                    val current = value ?: (min - step)
                    onValueChange((current + step).coerceIn(min, max))
                },
                enabled = enabled && (value == null || value < max)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.sx_insp_stepper_increase),
                    tint = if (enabled && (value == null || value < max)) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectionOptionSelector(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = enabled && it }
    ) {
        OutlinedTextField(
            value = value.ifBlank { "—" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            enabled = enabled,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GreenSuccess,
                focusedLabelColor = GreenSuccess
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sx_insp_option_unset)) },
                onClick = {
                    onValueChange("")
                    expanded = false
                }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun InspectionQuickCheck(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (checked) GreenSuccess.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = GreenSuccess)
        )
        Text(
            text = label,
            color = if (checked) GreenSuccess else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun PhotoItemCard(
    photoItem: PhotoItem,
    analysisState: AnalyzeCellsUiState? = null,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onClick: () -> Unit = {},
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        when (photoItem) {
            is PhotoItem.Local -> {
                AsyncImage(
                    model = photoItem.file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                )
            }
            is PhotoItem.Remote -> {
                Base64Image(
                    base64String = photoItem.photo.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                )
            }
        }

        // Per-photo analysis badge
        when (val s = analysisState) {
            is AnalyzeCellsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color(0xFFFFFFFF),
                        strokeWidth = 2.dp
                    )
                }
            }
            is AnalyzeCellsUiState.Success -> {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(GreenSuccess.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFFFFFFF), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("${s.report.metrics.total}", color = Color(0xFFFFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            is AnalyzeCellsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(RedError.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    Icon(Icons.Default.Error, contentDescription = stringResource(R.string.sx_insp_analysis_error_cd), tint = Color(0xFFFFFFFF), modifier = Modifier.size(14.dp))
                }
            }
            else -> {}
        }

        // Selection toggle
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(28.dp)
                .background(Color(0x80000000), RoundedCornerShape(14.dp))
                .clickable(onClick = onToggleSelect),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (selected) stringResource(R.string.sx_insp_deselect_photo) else stringResource(R.string.sx_insp_select_photo),
                tint = if (selected) GreenSuccess else Color(0xFFFFFFFF),
                modifier = Modifier.size(20.dp)
            )
        }

        // Delete button
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(32.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.sx_insp_delete),
                tint = Color(0xFFFFFFFF),
                modifier = Modifier
                    .background(Color(0x80000000), RoundedCornerShape(16.dp))
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun DatePickerDialog(
    onDismiss: () -> Unit,
    onDateSelected: (Int, Int, Int) -> Unit,
    initialDate: LocalDateTime
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val calendar = Calendar.getInstance().apply {
            set(initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth)
        }

        android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                onDateSelected(year, month + 1, day)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setOnDismissListener { onDismiss() }
            show()
        }
    }
}

@Composable
fun Base64Image(
    base64String: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap = remember(base64String) {
        try {
            // Remove the data URI prefix if present
            val base64Data = if (base64String.startsWith("data:image")) {
                base64String.substringAfter("base64,")
            } else {
                base64String
            }

            val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            android.util.Log.e("Base64Image", "Failed to decode Base64: ${e.message}")
            null
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } ?: Box(
        modifier = modifier.background(Color(0x4D9E9E9E)),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.sx_insp_image_load_failed), color = Color(0xFFFFFFFF), fontSize = 10.sp)
    }
}

