package com.example.beesmart.ui.hives

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.beesmart.R
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveType
import com.example.beesmart.ui.components.UnsavedChangesDialog
import com.example.beesmart.ui.components.VoiceFormFillerButton
import com.example.beesmart.ui.components.VoiceTextField
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.*
import java.text.Normalizer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiveFormScreen(
    apiaryName: String?,
    onNavigateBack: () -> Unit,
    viewModel: HiveFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val validationState by viewModel.validationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedHiveType by remember { mutableStateOf(HiveType.Langstroth) }
    var selectedHiveStatus by remember { mutableStateOf(HiveStatus.Active) }
    var reginaPrezenta by remember { mutableStateOf(false) }
    var varstaRegina by remember { mutableStateOf("") }
    var rameAlbine by remember { mutableStateOf("") }
    var ramePuiet by remember { mutableStateOf("") }
    var rameMiere by remember { mutableStateOf("") }

    var showTypeDropdown by remember { mutableStateOf(false) }
    var showStatusDropdown by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }

    var baseName by remember { mutableStateOf("") }
    var baseNotes by remember { mutableStateOf("") }
    var baseHiveType by remember { mutableStateOf(HiveType.Langstroth) }
    var baseHiveStatus by remember { mutableStateOf(HiveStatus.Active) }
    var baseReginaPrezenta by remember { mutableStateOf(false) }
    var baseVarstaRegina by remember { mutableStateOf("") }
    var baseRameAlbine by remember { mutableStateOf("") }
    var baseRamePuiet by remember { mutableStateOf("") }
    var baseRameMiere by remember { mutableStateOf("") }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val isEditMode = viewModel.isEditMode
    val isLoading = uiState is HiveFormUiState.Loading
    val isInteractionLocked = isLoading || isFinishing

    val title = remember(isEditMode, name) {
        if (isEditMode && name.isNotEmpty()) "Editează $name"
        else if (isEditMode) "Editează stup"
        else "Stup nou în ${apiaryName ?: ""}"
    }

    // Load data when in edit mode
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is HiveFormUiState.LoadedData -> {
                if (name.isEmpty()) {
                    name = state.name
                    notes = state.notes ?: ""
                    selectedHiveType = state.type
                    selectedHiveStatus = state.status
                    reginaPrezenta = state.reginaPrezenta
                    varstaRegina = state.varstaRegina.toString()
                    rameAlbine = state.rameAlbine.toString()
                    ramePuiet = state.ramePuiet.toString()
                    rameMiere = state.rameMiere.toString()
                    baseName = state.name
                    baseNotes = state.notes ?: ""
                    baseHiveType = state.type
                    baseHiveStatus = state.status
                    baseReginaPrezenta = state.reginaPrezenta
                    baseVarstaRegina = state.varstaRegina.toString()
                    baseRameAlbine = state.rameAlbine.toString()
                    baseRamePuiet = state.ramePuiet.toString()
                    baseRameMiere = state.rameMiere.toString()
                }
            }
            is HiveFormUiState.Success -> {
                if (!isFinishing) {
                    isFinishing = true
                    viewModel.resetState()
                    onNavigateBack()
                }
            }
            is HiveFormUiState.Error -> {
                isFinishing = false
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.resetState()
            }
            else -> {}
        }
    }

    val isDirty = name != baseName ||
        notes != baseNotes ||
        selectedHiveType != baseHiveType ||
        selectedHiveStatus != baseHiveStatus ||
        reginaPrezenta != baseReginaPrezenta ||
        varstaRegina != baseVarstaRegina ||
        rameAlbine != baseRameAlbine ||
        ramePuiet != baseRamePuiet ||
        rameMiere != baseRameMiere
    val canSave = !isInteractionLocked && validationState.isValid && name.trim().isNotEmpty()

    val saveHive = {
        if (!isInteractionLocked) {
            viewModel.saveHive(
                name = name.trim(),
                type = selectedHiveType,
                status = selectedHiveStatus,
                notes = notes.trim().takeIf { it.isNotEmpty() },
                reginaPrezenta = reginaPrezenta,
                varstaRegina = varstaRegina.toNonNegativeInt(),
                rameAlbine = rameAlbine.toNonNegativeInt(),
                ramePuiet = ramePuiet.toNonNegativeInt(),
                rameMiere = rameMiere.toNonNegativeInt()
            )
        }
    }
    val attemptBack = {
        if (isDirty) showUnsavedDialog = true else onNavigateBack()
    }

    BackHandler(enabled = isDirty) { showUnsavedDialog = true }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            canSave = canSave,
            onSave = {
                showUnsavedDialog = false
                saveHive()
            },
            onDiscard = {
                showUnsavedDialog = false
                onNavigateBack()
            },
            onDismiss = { showUnsavedDialog = false }
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
                    IconButton(onClick = attemptBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Înapoi")
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Form Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Icon and Title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_hive_box),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = if (isEditMode) "Modifică detaliile" else "Detalii stup",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Completează informațiile de mai jos",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Voice Form Filler Button
                        VoiceFormFillerButton(
                            onFieldsFilled = { fields ->
                                fields["name"]?.let {
                                    name = it
                                    viewModel.validateName(it)
                                }
                                fields["notes"]?.let { notes = it }
                                fields["type"]?.let { typeStr ->
                                    detectHiveTypeFromText(typeStr)?.let { selectedHiveType = it }
                                }
                                fields["status"]?.let { statusStr ->
                                    detectHiveStatusFromText(statusStr)?.let { selectedHiveStatus = it }
                                }
                            },
                            fields = listOf("name", "notes", "type", "status"),
                            enabled = !isInteractionLocked,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Hive Name (Required) with Voice
                        VoiceTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                viewModel.validateName(it)
                            },
                            label = "Nume stup *",
                            placeholder = "ex: Stup #1, Regina Maria",
                            voicePrompt = "Spune numele stupului",
                            isError = validationState.nameError != null,
                            errorMessage = validationState.nameError,
                            enabled = !isInteractionLocked,
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Hive Type Dropdown
                        ExposedDropdownMenuBox(
                            expanded = showTypeDropdown,
                            onExpandedChange = { if (!isInteractionLocked) showTypeDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = selectedHiveType.localizedName(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.sx_hive_form_type_label)) },
                                enabled = !isInteractionLocked,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Build, contentDescription = null)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = YellowPrimary,
                                    focusedLabelColor = YellowPrimary,
                                    focusedLeadingIconColor = YellowPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = showTypeDropdown,
                                onDismissRequest = { showTypeDropdown = false }
                            ) {
                                HiveType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.localizedName()) },
                                        onClick = {
                                            selectedHiveType = type
                                            showTypeDropdown = false
                                        },
                                        leadingIcon = {
                                            if (selectedHiveType == type) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = YellowPrimary)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Hive Status Dropdown
                        ExposedDropdownMenuBox(
                            expanded = showStatusDropdown,
                            onExpandedChange = { if (!isInteractionLocked) showStatusDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = selectedHiveStatus.localizedName(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.sx_hive_form_status_label)) },
                                enabled = !isInteractionLocked,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showStatusDropdown)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = selectedHiveStatus.statusIcon(),
                                        contentDescription = null,
                                        tint = selectedHiveStatus.statusColor()
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = YellowPrimary,
                                    focusedLabelColor = YellowPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = showStatusDropdown,
                                onDismissRequest = { showStatusDropdown = false }
                            ) {
                                HiveStatus.entries.forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status.localizedName()) },
                                        onClick = {
                                            selectedHiveStatus = status
                                            showStatusDropdown = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = status.statusIcon(),
                                                contentDescription = null,
                                                tint = status.statusColor()
                                            )
                                        },
                                        trailingIcon = {
                                            if (selectedHiveStatus == status) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = YellowPrimary)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Regină și rame",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrownPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(YellowPrimary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = YellowPrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.sx_hive_form_queen_present), fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (reginaPrezenta) "Confirmată la ultima verificare" else "Nu este confirmată",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = reginaPrezenta,
                                onCheckedChange = { reginaPrezenta = it },
                                enabled = !isInteractionLocked,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = YellowPrimary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = varstaRegina,
                            onValueChange = { varstaRegina = it.onlyDigits(maxLength = 2) },
                            label = { Text(stringResource(R.string.sx_hive_form_queen_age_label)) },
                            placeholder = { Text(stringResource(R.string.sx_hive_form_queen_age_placeholder)) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            enabled = !isInteractionLocked,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = YellowPrimary,
                                focusedLabelColor = YellowPrimary,
                                cursorColor = YellowPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FrameCountField(
                                value = rameAlbine,
                                onValueChange = { rameAlbine = it },
                                label = "Rame albine",
                                enabled = !isInteractionLocked,
                                modifier = Modifier.weight(1f)
                            )
                            FrameCountField(
                                value = ramePuiet,
                                onValueChange = { ramePuiet = it },
                                label = "Rame puiet",
                                enabled = !isInteractionLocked,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        FrameCountField(
                            value = rameMiere,
                            onValueChange = { rameMiere = it },
                            label = "Rame miere",
                            enabled = !isInteractionLocked,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Notes (Optional) with Voice
                        VoiceTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = "Notițe (opțional)",
                            placeholder = "Observații despre stup...",
                            voicePrompt = "Spune notițele despre stup",
                            enabled = !isInteractionLocked,
                            singleLine = false,
                            maxLines = 5
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = attemptBack,
                        modifier = Modifier.weight(1f),
                        enabled = !isInteractionLocked,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = YellowPrimary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sx_hive_form_cancel), maxLines = 1, softWrap = false)
                    }

                    // Save Button
                    Button(
                        onClick = { saveHive() },
                        modifier = Modifier.weight(1f),
                        enabled = !isInteractionLocked && validationState.isValid && name.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isEditMode) "Salvează" else "Creează")
                        }
                    }
                }

                // Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = BrownPrimary.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = BrownPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Monitorizează starea stupului și actualizează statusul " +
                                    "după fiecare inspecție pentru o evidență precisă.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Loading Overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x4D000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = YellowPrimary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (isEditMode) "Actualizare..." else "Creare stup...",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrameCountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.onlyDigits(maxLength = 2)) },
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.sx_hive_form_frame_count_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = YellowPrimary,
            focusedLabelColor = YellowPrimary,
            cursorColor = YellowPrimary
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}

private fun String.onlyDigits(maxLength: Int): String =
    filter { it.isDigit() }.take(maxLength)

private fun String.toNonNegativeInt(): Int =
    toIntOrNull()?.coerceAtLeast(0) ?: 0

// Helper functions for translations
private val hiveTypeKeywords = mapOf(
    HiveType.Langstroth to listOf(
        "langstroth",
        "stup vertical modular",
        "vertical modular",
        "stup modular",
        "modular"
    ),
    HiveType.Dadant to listOf(
        "dadant",
        "stup vertical cu rame mari",
        "vertical cu rame mari",
        "cu rame mari",
        "rame mari"
    ),
    HiveType.TopBar to listOf("top bar", "top-bar", "orizontal cu bare", "stup orizontal"),
    HiveType.Warre to listOf(
        "warre",
        "stup vertical natural",
        "vertical natural",
        "stup natural",
        "natural"
    ),
    HiveType.Other to listOf("alt tip", "altul", "alt tip de stup")
)

private val hiveStatusKeywords = mapOf(
    HiveStatus.Active to listOf("activ", "in activitate", "este activ"),
    HiveStatus.Queenless to listOf("fara regina", "fără regină", "fara regină", "queenless"),
    HiveStatus.Weak to listOf("slab", "slăbit", "slabă"),
    HiveStatus.Sick to listOf("bolnav", "bolnavă", "sunt bolnav"),
    HiveStatus.Preparing to listOf("in pregatire", "în pregătire", "pregatire"),
    HiveStatus.Inactive to listOf("inactiv", "inactivă", "neactiv")
)

private fun normalizeVoiceValue(raw: String): String {
    if (raw.isBlank()) return " "
    val base = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("[^\\p{L}\\p{Nd}]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
    return " $base "
}

private fun detectHiveTypeFromText(value: String): HiveType? {
    val normalizedValue = normalizeVoiceValue(value)
    var bestMatchType: HiveType? = null
    var bestMatchScore = 0

    hiveTypeKeywords.forEach { (type, keywords) ->
        keywords.forEach { keyword ->
            val normalizedKeyword = normalizeVoiceValue(keyword)
            if (normalizedValue.contains(normalizedKeyword)) {
                val score = normalizedKeyword.length
                if (score > bestMatchScore) {
                    bestMatchType = type
                    bestMatchScore = score
                }
            }
        }
    }

    return bestMatchType
}

private fun detectHiveStatusFromText(value: String): HiveStatus? {
    val normalizedValue = normalizeVoiceValue(value)
    return hiveStatusKeywords.entries.firstOrNull { entry ->
        entry.value.any { keyword -> normalizedValue.contains(normalizeVoiceValue(keyword)) }
    }?.key
}

private fun getHiveTypeTranslation(type: HiveType): String = when (type) {
    HiveType.Langstroth -> "Stup vertical modular (Langstroth)"
    HiveType.Dadant -> "Stup vertical cu rame mari (Dadant)"
    HiveType.TopBar -> "Stup orizontal cu bare (Top-Bar)"
    HiveType.Warre -> "Stup vertical natural (Warré)"
    HiveType.Other -> "Alt tip de stup"
}

private fun getHiveStatusTranslation(status: HiveStatus): String = when (status) {
    HiveStatus.Active -> "Activ"
    HiveStatus.Queenless -> "Fără regină"
    HiveStatus.Weak -> "Slab"
    HiveStatus.Sick -> "Bolnav"
    HiveStatus.Preparing -> "În pregătire"
    HiveStatus.Inactive -> "Inactiv"
}

private fun getStatusIcon(status: HiveStatus) = when (status) {
    HiveStatus.Active -> Icons.Default.CheckCircle
    HiveStatus.Queenless -> Icons.Default.Warning
    HiveStatus.Weak -> Icons.Default.Warning
    HiveStatus.Sick -> Icons.Default.Warning
    HiveStatus.Preparing -> Icons.Default.Build
    HiveStatus.Inactive -> Icons.Default.Clear
}

private fun getStatusColor(status: HiveStatus): Color = when (status) {
    HiveStatus.Active -> GreenSuccess
    HiveStatus.Queenless -> StatusWatch
    HiveStatus.Weak -> StatusWatch
    HiveStatus.Sick -> RedError
    HiveStatus.Preparing -> YellowPrimary
    HiveStatus.Inactive -> Gray
}
