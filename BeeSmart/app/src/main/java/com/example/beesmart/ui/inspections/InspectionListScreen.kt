package com.example.beesmart.ui.inspections

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
import com.example.beesmart.network.models.InspectionResponse
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionListScreen(
    hiveId: String?,
    hiveName: String?,
    apiaryId: String?,
    apiaryName: String?,
    onNavigateBack: () -> Unit,
    onCreateInspection: (String?) -> Unit, // hiveId
    onEditInspection: (String) -> Unit, // inspectionId
    viewModel: InspectionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var inspectionToDelete by remember { mutableStateOf<InspectionResponse?>(null) }

    // Search / filter state
    var searchQuery by remember { mutableStateOf("") }
    var hiveFilterId by remember { mutableStateOf<String?>(null) }
    var dateFrom by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var dateTo by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // The ViewModel's init{} already triggers the first load, so skip the initial
        // ON_RESUME and only reload when the user returns to this screen (e.g. after
        // creating or editing an inspection). Avoids loading the list several times on entry.
        var hasSeenInitialResume = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!hasSeenInitialResume) hasSeenInitialResume = true
                else viewModel.loadInspections(forceRefresh = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val title = remember(hiveId, hiveName, apiaryId, apiaryName) {
        when {
            hiveId != null -> "Inspecții - ${hiveName ?: ""}"
            apiaryId != null -> "Inspecții - ${apiaryName ?: ""}"
            else -> "Toate inspecțiile"
        }
    }

    // Handle state changes
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is InspectionListUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
            }
            is InspectionListUiState.DeleteSuccess -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Short
                )
            }
            else -> {}
        }
    }

    // Delete confirmation dialog
    inspectionToDelete?.let { inspection ->
        AlertDialog(
            onDismissRequest = { inspectionToDelete = null },
            title = { Text(stringResource(R.string.sx_insp_delete_title)) },
            text = {
                Text("Sigur vrei să ștergi inspecția pentru ${inspection.hiveName}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteInspection(inspection.id)
                        inspectionToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.sx_insp_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectionToDelete = null }) {
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
                        title,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sx_insp_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadInspections(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.sx_insp_refresh))
                    }
                },
                colors = beeSmartTopAppBarColors()
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreateInspection(hiveId) },
                icon = { Icon(Icons.Default.Add, stringResource(R.string.sx_insp_add_inspection_cd)) },
                text = { Text(stringResource(R.string.sx_insp_new_inspection_fab)) },
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
                is InspectionListUiState.Success -> {
                    val allInspections = state.inspections
                    val hiveOptions = remember(allInspections) {
                        allInspections.distinctBy { it.hiveId }.map { it.hiveId to it.hiveName }
                    }
                    val filtered = remember(allInspections, searchQuery, hiveFilterId, dateFrom, dateTo) {
                        allInspections.filter { insp ->
                            val q = searchQuery.trim()
                            val matchesQuery = q.isBlank() ||
                                insp.hiveName.contains(q, ignoreCase = true) ||
                                insp.apiaryName.contains(q, ignoreCase = true)
                            val matchesHive = hiveFilterId == null || insp.hiveId == hiveFilterId
                            val date = parseInspectionLocalDate(insp.inspectionDate)
                            val matchesFrom = dateFrom == null || (date != null && !date.isBefore(dateFrom))
                            val matchesTo = dateTo == null || (date != null && !date.isAfter(dateTo))
                            matchesQuery && matchesHive && matchesFrom && matchesTo
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (allInspections.isNotEmpty()) {
                            InspectionFilterBar(
                                searchQuery = searchQuery,
                                onSearchChange = { searchQuery = it },
                                hiveOptions = hiveOptions,
                                hiveFilterId = hiveFilterId,
                                onHiveFilterChange = { hiveFilterId = it },
                                dateFrom = dateFrom,
                                dateTo = dateTo,
                                onPickFrom = { showFromPicker = true },
                                onPickTo = { showToPicker = true },
                                onClearDates = { dateFrom = null; dateTo = null }
                            )

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_inspection),
                                        contentDescription = null,
                                        tint = GreenSuccess,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${filtered.size} din ${allInspections.size} ${if (allInspections.size == 1) "inspecție" else "inspecții"}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (allInspections.isEmpty()) {
                            // Empty state
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_inspection),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.sx_insp_none_yet),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.sx_insp_add_first_hint),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (filtered.isEmpty()) {
                            // No results for current filters
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(96.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.sx_insp_no_results),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = {
                                    searchQuery = ""
                                    hiveFilterId = null
                                    dateFrom = null
                                    dateTo = null
                                }) {
                                    Text(stringResource(R.string.sx_insp_clear_filters))
                                }
                            }
                        } else {
                            // Inspections list
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    top = 10.dp,
                                    end = 16.dp,
                                    bottom = 96.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = filtered,
                                    key = { it.id }
                                ) { inspection ->
                                    InspectionCard(
                                        inspection = inspection,
                                        onClick = { onEditInspection(inspection.id) },
                                        onDelete = { inspectionToDelete = inspection }
                                    )
                                }
                            }
                        }
                    }
                }
                is InspectionListUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = GreenSuccess)
                    }
                }
                is InspectionListUiState.Error -> {
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
                            stringResource(R.string.sx_insp_load_error),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadInspections(forceRefresh = true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GreenSuccess
                            )
                        ) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.sx_insp_retry_short))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.sx_insp_retry_short))
                        }
                    }
                }
                is InspectionListUiState.DeleteSuccess -> {
                    // Success is shown via snackbar
                }
            }

            if (showFromPicker) {
                DatePickerDialog(
                    onDismiss = { showFromPicker = false },
                    onDateSelected = { year, month, day ->
                        dateFrom = java.time.LocalDate.of(year, month, day)
                        showFromPicker = false
                    },
                    initialDate = dateFrom?.atStartOfDay() ?: java.time.LocalDateTime.now()
                )
            }

            if (showToPicker) {
                DatePickerDialog(
                    onDismiss = { showToPicker = false },
                    onDateSelected = { year, month, day ->
                        dateTo = java.time.LocalDate.of(year, month, day)
                        showToPicker = false
                    },
                    initialDate = dateTo?.atStartOfDay() ?: java.time.LocalDateTime.now()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectionFilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    hiveOptions: List<Pair<String, String>>,
    hiveFilterId: String?,
    onHiveFilterChange: (String?) -> Unit,
    dateFrom: java.time.LocalDate?,
    dateTo: java.time.LocalDate?,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onClearDates: () -> Unit
) {
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale("ro")) }
    var hiveMenuExpanded by remember { mutableStateOf(false) }
    val compactControlHeight = 48.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChange("") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.sx_insp_clear_search),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GreenSuccess,
                focusedLeadingIconColor = GreenSuccess,
                cursorColor = GreenSuccess
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(compactControlHeight)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(compactControlHeight)
            ) {
                OutlinedIconButton(
                    onClick = { hiveMenuExpanded = true },
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        contentColor = if (hiveFilterId == null) BrownPrimary else GreenSuccess
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_hive_box),
                        contentDescription = stringResource(R.string.sx_insp_filter_hive),
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = hiveMenuExpanded,
                    onDismissRequest = { hiveMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sx_insp_all_hives)) },
                        onClick = {
                            onHiveFilterChange(null)
                            hiveMenuExpanded = false
                        }
                    )
                    hiveOptions.forEach { (id, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onHiveFilterChange(id)
                                hiveMenuExpanded = false
                            }
                        )
                    }
                }
            }

            CompactInspectionDateButton(
                text = dateFrom?.format(dateFmt) ?: stringResource(R.string.sx_insp_date_from),
                onClick = onPickFrom,
                modifier = Modifier.weight(1f)
            )
            CompactInspectionDateButton(
                text = dateTo?.format(dateFmt) ?: stringResource(R.string.sx_insp_date_to),
                onClick = onPickTo,
                modifier = Modifier.weight(1f)
            )
            if (dateFrom != null || dateTo != null) {
                IconButton(
                    onClick = onClearDates,
                    modifier = Modifier.size(compactControlHeight)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.sx_insp_clear_dates),
                        tint = RedError,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactInspectionDateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = YellowDark)
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun parseInspectionLocalDate(dateString: String): java.time.LocalDate? = try {
    java.time.OffsetDateTime.parse(dateString).toLocalDate()
} catch (e: Exception) {
    try {
        java.time.ZonedDateTime.parse(dateString).toLocalDate()
    } catch (e2: Exception) {
        try {
            java.time.LocalDate.parse(dateString.take(10))
        } catch (e3: Exception) {
            null
        }
    }
}

@Composable
fun InspectionCard(
    inspection: InspectionResponse,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with hive name and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Hive name
                    Text(
                        text = inspection.hiveName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Apiary name
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
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action buttons
                Row {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.sx_insp_delete),
                            tint = RedError,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
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
                    fontWeight = FontWeight.Medium,
                    color = GreenSuccess
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Inspection details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Queen seen
                if (inspection.queenSeen) {
                    InspectionDetailChip(
                        icon = Icons.Default.Star,
                        label = stringResource(R.string.sx_insp_chip_queen)
                    )
                }

                // Photos count
                if (inspection.photosCount > 0) {
                    InspectionDetailChip(
                        icon = Icons.Default.CameraAlt,
                        label = "${inspection.photosCount} ${if (inspection.photosCount == 1) "foto" else "foto"}"
                    )
                }
            }

            // Frames info
            inspection.framesCount?.let { frames ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildFramesText(inspection),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            val v2Notes = buildInspectionV2Text(inspection)
            if (v2Notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = v2Notes,
                    fontSize = 13.sp,
                    color = BrownPrimary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun InspectionDetailChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = GreenSuccess.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = GreenSuccess
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = GreenSuccess,
                fontWeight = FontWeight.Medium
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

private fun buildInspectionV2Text(inspection: InspectionResponse): String {
    val parts = mutableListOf<String>()

    if (inspection.queenCellsWithEggs) parts.add("botci cu ouă")
    else if (inspection.queenCellsSeen) parts.add("botci")
    if (inspection.beardingAtEntrance) parts.add("barbă la urdiniș")
    if (inspection.spaceNeeded) parts.add("spațiu insuficient")
    inspection.broodPattern?.takeIf { it.isNotBlank() }?.let { parts.add("puiet: $it") }
    if (inspection.feedingGiven) parts.add("hrănire")
    if (inspection.waterAvailable) parts.add("apă")
    if (inspection.moistureOrMold) parts.add("umezeală/mucegai")
    if (inspection.deadBeesAtEntrance) parts.add("mortalitate")
    if (inspection.unusualBehavior) parts.add("comportament neobișnuit")
    inspection.temperament?.takeIf { it.isNotBlank() }?.let { parts.add("temperament: $it") }
    inspection.oldCombsToReplace?.let { if (it > 0) parts.add("$it faguri vechi") }

    return parts.joinToString(" | ")
}

private fun buildFramesText(inspection: InspectionResponse): String {
    val parts = mutableListOf<String>()

    inspection.framesCount?.let { parts.add("$it rame total") }
    inspection.broodFrames?.let { if (it > 0) parts.add("$it cu puiet") }
    inspection.honeyFrames?.let { if (it > 0) parts.add("$it cu miere") }
    inspection.pollenFrames?.let { if (it > 0) parts.add("$it cu polen") }

    return parts.joinToString(" • ")
}
