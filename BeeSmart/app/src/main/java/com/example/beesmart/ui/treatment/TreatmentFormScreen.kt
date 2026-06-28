package com.example.beesmart.ui.treatment

import android.Manifest
import android.app.DatePickerDialog
import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.beesmart.R
import com.example.beesmart.network.models.TreatmentType
import com.example.beesmart.ui.components.UnsavedChangesDialog
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.BrownPrimary
import com.example.beesmart.ui.theme.RedError
import com.example.beesmart.ui.theme.WaxSurface
import com.example.beesmart.ui.theme.YellowPrimary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class TreatmentSnapshot(
    val hiveId: String,
    val type: TreatmentType,
    val productName: String,
    val substance: String,
    val dosage: String,
    val notes: String,
    val treatmentDate: LocalDate,
    val nextTreatmentDate: LocalDate?
)

fun TreatmentType.displayName(): String = when (this) {
    TreatmentType.Varroa -> "Varroa"
    TreatmentType.Nosema -> "Nosemoză"
    TreatmentType.Fungal -> "Fungică"
    TreatmentType.Viral -> "Virală"
    TreatmentType.Bacterial -> "Bacteriană"
    TreatmentType.Preventive -> "Preventiv"
    TreatmentType.Other -> "Altul"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun TreatmentFormScreen(
    hiveId: String?,
    treatmentId: String?,
    onNavigateBack: () -> Unit,
    viewModel: TreatmentFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hives by viewModel.hives.collectAsState()
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notifPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(Unit) {
            if (!notifPermission.status.isGranted) notifPermission.launchPermissionRequest()
        }
    }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onNavigateBack()
    }

    // Capture a baseline once the form is ready, to detect unsaved changes on back.
    val formReady = !(uiState.isLoading && treatmentId != null && uiState.productName.isEmpty())
    var baseline by remember { mutableStateOf<TreatmentSnapshot?>(null) }
    LaunchedEffect(formReady) {
        if (formReady && baseline == null) {
            baseline = TreatmentSnapshot(
                uiState.selectedHiveId,
                uiState.treatmentType,
                uiState.productName,
                uiState.substance,
                uiState.dosage,
                uiState.notes,
                uiState.treatmentDate,
                uiState.nextTreatmentDate
            )
        }
    }
    val isDirty = baseline?.let { b ->
        b.hiveId != uiState.selectedHiveId ||
            b.type != uiState.treatmentType ||
            b.productName != uiState.productName ||
            b.substance != uiState.substance ||
            b.dosage != uiState.dosage ||
            b.notes != uiState.notes ||
            b.treatmentDate != uiState.treatmentDate ||
            b.nextTreatmentDate != uiState.nextTreatmentDate
    } ?: false
    val canSave = !uiState.isLoading &&
        uiState.selectedHiveId.isNotBlank() &&
        uiState.productName.isNotBlank()
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
                viewModel.saveTreatment()
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
                        if (treatmentId == null) stringResource(R.string.sx_tte_add_treatment) else stringResource(R.string.sx_tte_edit_treatment),
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
        if (uiState.isLoading && treatmentId != null && uiState.productName.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrownPrimary)
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
                                        tint = BrownPrimary,
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

                        // Treatment type selector
                        var typeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = !typeExpanded }
                        ) {
                            OutlinedTextField(
                                value = uiState.treatmentType.displayName(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.sx_tte_treatment_type_label)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_shield_check),
                                        contentDescription = null,
                                        tint = BrownPrimary
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
                                TreatmentType.values().forEach { type ->
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

                        OutlinedTextField(
                            value = uiState.productName,
                            onValueChange = viewModel::onProductNameChange,
                            label = { Text(stringResource(R.string.sx_tte_product_name_label)) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_shield_check),
                                    contentDescription = null,
                                    tint = BrownPrimary
                                )
                            },
                            isError = uiState.error != null && uiState.productName.isBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.treatmentDate.format(dateFormatter),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.sx_tte_treatment_date_label)) },
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = null, tint = BrownPrimary)
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
                                            uiState.treatmentDate.year,
                                            uiState.treatmentDate.monthValue - 1,
                                            uiState.treatmentDate.dayOfMonth
                                        ).show()
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.nextTreatmentDate?.format(dateFormatter) ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.sx_tte_next_treatment_label)) },
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = null, tint = BrownPrimary)
                            },
                            trailingIcon = {
                                Row {
                                    if (uiState.nextTreatmentDate != null) {
                                        IconButton(onClick = { viewModel.onNextTreatmentDateChange(null) }) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.sx_tte_clear_deadline_cd))
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val initialDate = uiState.nextTreatmentDate ?: uiState.treatmentDate
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, dayOfMonth ->
                                                    viewModel.onNextTreatmentDateChange(
                                                        LocalDate.of(year, month + 1, dayOfMonth)
                                                    )
                                                },
                                                initialDate.year,
                                                initialDate.monthValue - 1,
                                                initialDate.dayOfMonth
                                            ).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.sx_tte_select_deadline_cd))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.substance,
                            onValueChange = viewModel::onSubstanceChange,
                            label = { Text(stringResource(R.string.sx_tte_active_substance_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.dosage,
                            onValueChange = viewModel::onDosageChange,
                            label = { Text(stringResource(R.string.sx_tte_dosage_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.notes,
                            onValueChange = viewModel::onNotesChange,
                            label = { Text(stringResource(R.string.sx_tte_notes_label)) },
                            leadingIcon = {
                                Icon(Icons.Default.Description, contentDescription = null)
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
                    onClick = viewModel::saveTreatment,
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
                        Text(stringResource(R.string.sx_tte_save_treatment))
                    }
                }
            }
        }
    }
}
