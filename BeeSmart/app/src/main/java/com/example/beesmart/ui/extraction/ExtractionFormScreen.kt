package com.example.beesmart.ui.extraction

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.beesmart.R
import com.example.beesmart.network.models.ExtractionType
import com.example.beesmart.ui.components.UnsavedChangesDialog
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.BrownPrimary
import com.example.beesmart.ui.theme.GreenSuccess
import com.example.beesmart.ui.theme.RedError
import com.example.beesmart.ui.theme.WaxSurface
import com.example.beesmart.ui.theme.YellowPrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class ExtractionSnapshot(
    val hiveId: String,
    val type: ExtractionType,
    val quantity: String,
    val unit: String,
    val extractionDate: LocalDate,
    val notes: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractionFormScreen(
    hiveId: String?,
    extractionId: String?,
    onNavigateBack: () -> Unit,
    viewModel: ExtractionFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hives by viewModel.hives.collectAsState()
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onNavigateBack()
    }

    // Capture a baseline once the form is ready, to detect unsaved changes on back.
    val formReady = !(uiState.isLoading && extractionId != null && uiState.quantity.isEmpty())
    var baseline by remember { mutableStateOf<ExtractionSnapshot?>(null) }
    LaunchedEffect(formReady) {
        if (formReady && baseline == null) {
            baseline = ExtractionSnapshot(
                uiState.selectedHiveId,
                uiState.extractionType,
                uiState.quantity,
                uiState.unit,
                uiState.extractionDate,
                uiState.notes
            )
        }
    }
    val isDirty = baseline?.let { b ->
        b.hiveId != uiState.selectedHiveId ||
            b.type != uiState.extractionType ||
            b.quantity != uiState.quantity ||
            b.unit != uiState.unit ||
            b.extractionDate != uiState.extractionDate ||
            b.notes != uiState.notes
    } ?: false
    val quantityValid = uiState.quantity.toDoubleOrNull()?.let {
        it in ExtractionFormViewModel.MIN_QUANTITY..ExtractionFormViewModel.MAX_QUANTITY
    } == true
    val canSave = !uiState.isLoading &&
        uiState.selectedHiveId.isNotBlank() &&
        quantityValid &&
        uiState.unit.isNotBlank()
    var showUnsavedDialog by remember { mutableStateOf(false) }
    val attemptBack = {
        if (isDirty) showUnsavedDialog = true else onNavigateBack()
    }

    BackHandler(enabled = isDirty) { showUnsavedDialog = true }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            canSave = canSave,
            onSave = {
                showUnsavedDialog = false
                viewModel.saveExtraction()
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
                        if (extractionId == null) "Adaugă extracție" else "Editează extracție",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.sx_tte_back))
                    }
                },
                colors = beeSmartTopAppBarColors()
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading && extractionId != null && uiState.quantity.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = GreenSuccess)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Hive selector
                        val selectedHiveName = hives.find { it.id == uiState.selectedHiveId }?.name ?: ""
                        var hiveExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = hiveExpanded && !viewModel.isEditMode,
                            onExpandedChange = { if (!viewModel.isEditMode) hiveExpanded = !hiveExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedHiveName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.sx_tte_hive_required)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_hive_box),
                                        contentDescription = null,
                                        tint = GreenSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (!viewModel.isEditMode)
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = hiveExpanded)
                                },
                                enabled = !viewModel.isEditMode,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            if (!viewModel.isEditMode) {
                                ExposedDropdownMenu(
                                    expanded = hiveExpanded,
                                    onDismissRequest = { hiveExpanded = false }
                                ) {
                                    hives.forEach { hive ->
                                        DropdownMenuItem(
                                            text = { Text(hive.name) },
                                            onClick = {
                                                viewModel.onHiveSelected(hive.id)
                                                hiveExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Extraction type selector
                        var typeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = !typeExpanded }
                        ) {
                            OutlinedTextField(
                                value = uiState.extractionType.displayName(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.sx_tte_extraction_type_label)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_honey),
                                        contentDescription = null,
                                        tint = GreenSuccess
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false }
                            ) {
                                ExtractionType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.displayName()) },
                                        onClick = {
                                            viewModel.onTypeChange(type)
                                            typeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Quantity with − / + stepper (mirrors the inspection form)
                        QuantityStepper(
                            value = uiState.quantity,
                            error = uiState.quantityError,
                            enabled = !uiState.isLoading,
                            onValueChange = viewModel::onQuantityChange,
                            onStep = viewModel::stepQuantity
                        )

                        // Unit of measure dropdown
                        UnitDropdown(
                            value = uiState.unit,
                            error = uiState.unitError,
                            enabled = !uiState.isLoading,
                            onUnitChange = viewModel::onUnitChange
                        )

                        OutlinedTextField(
                            value = uiState.extractionDate.format(dateFormatter),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.sx_tte_extraction_date_label)) },
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = null, tint = GreenSuccess)
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = stringResource(R.string.sx_tte_select_date_cd),
                                    modifier = Modifier.clickable {
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, dayOfMonth ->
                                                viewModel.onDateChange(LocalDate.of(year, month + 1, dayOfMonth))
                                            },
                                            uiState.extractionDate.year,
                                            uiState.extractionDate.monthValue - 1,
                                            uiState.extractionDate.dayOfMonth
                                        ).show()
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.notes,
                            onValueChange = viewModel::onNotesChange,
                            label = { Text(stringResource(R.string.sx_tte_notes_label)) },
                            leadingIcon = {
                                Icon(Icons.Default.Description, contentDescription = null)
                            },
                            supportingText = {
                                Text("${uiState.notes.length} / ${ExtractionFormViewModel.MAX_NOTES}")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                }

                uiState.error?.let { error ->
                    Text(
                        text = error,
                        color = RedError,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Button(
                    onClick = viewModel::saveExtraction,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowPrimary,
                        contentColor = BrownPrimary
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = BrownPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sx_tte_save_extraction))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuantityStepper(
    value: String,
    error: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onStep: (Double) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = { onStep(-ExtractionFormViewModel.QUANTITY_STEP) },
                enabled = enabled && (value.toDoubleOrNull() ?: 0.0) > 0.0,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = GreenSuccess.copy(alpha = 0.15f),
                    contentColor = GreenSuccess
                )
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Scade cantitatea")
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Cantitate *") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_honey),
                        contentDescription = null,
                        tint = GreenSuccess
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = error != null,
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )

            FilledTonalIconButton(
                onClick = { onStep(ExtractionFormViewModel.QUANTITY_STEP) },
                enabled = enabled,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = GreenSuccess.copy(alpha = 0.15f),
                    contentColor = GreenSuccess
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = "Crește cantitatea")
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = RedError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
    value: String,
    error: String?,
    enabled: Boolean,
    onUnitChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text("Unitate de măsură *") },
                isError = error != null,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                ExtractionFormViewModel.UNITS.forEach { unit ->
                    DropdownMenuItem(
                        text = { Text(unit) },
                        onClick = {
                            onUnitChange(unit)
                            expanded = false
                        }
                    )
                }
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = RedError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}
