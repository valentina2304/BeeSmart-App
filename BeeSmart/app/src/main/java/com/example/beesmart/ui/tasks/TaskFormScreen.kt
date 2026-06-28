package com.example.beesmart.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.beesmart.R
import com.example.beesmart.network.models.ApiaryResponse
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.TaskPriority
import com.example.beesmart.ui.components.UnsavedChangesDialog
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormScreen(
    taskId: String?,
    onNavigateBack: () -> Unit,
    viewModel: TaskFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val apiaries by viewModel.apiaries.collectAsState()
    val hives by viewModel.hives.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(TaskPriority.Normal) }
    var selectedDueDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedApiaryId by remember { mutableStateOf<String?>(null) }
    var selectedHiveId by remember { mutableStateOf<String?>(null) }

    var showPriorityDropdown by remember { mutableStateOf(false) }
    var showApiaryDropdown by remember { mutableStateOf(false) }
    var showHiveDropdown by remember { mutableStateOf(false) }
    val isEditMode = taskId != null
    val isLoading = uiState is TaskFormUiState.Loading

    // Baseline snapshot of the form, used to detect unsaved changes. For create mode it stays
    // at the defaults; for edit mode it is set to the loaded values once they arrive.
    var baseTitle by remember { mutableStateOf("") }
    var baseDescription by remember { mutableStateOf("") }
    var basePriority by remember { mutableStateOf(TaskPriority.Normal) }
    var baseDueDate by remember { mutableStateOf<LocalDate?>(null) }
    var baseApiaryId by remember { mutableStateOf<String?>(null) }
    var baseHiveId by remember { mutableStateOf<String?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    // Load data in edit mode
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is TaskFormUiState.LoadedData -> {
                title = state.title
                description = state.description ?: ""
                selectedPriority = state.priority
                selectedDueDate = state.dueDate
                selectedApiaryId = state.apiaryId
                selectedHiveId = state.hiveId
                baseTitle = state.title
                baseDescription = state.description ?: ""
                basePriority = state.priority
                baseDueDate = state.dueDate
                baseApiaryId = state.apiaryId
                baseHiveId = state.hiveId
            }
            is TaskFormUiState.Success -> {
                onNavigateBack()
                viewModel.resetState()
            }
            is TaskFormUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.resetState()
            }
            else -> {}
        }
    }

    val isDirty = title != baseTitle ||
        description != baseDescription ||
        selectedPriority != basePriority ||
        selectedDueDate != baseDueDate ||
        selectedApiaryId != baseApiaryId ||
        selectedHiveId != baseHiveId
    val canSave = !isLoading && title.trim().length >= 3

    val saveTask = {
        viewModel.saveTask(
            title = title.trim(),
            description = description.trim().takeIf { it.isNotEmpty() },
            priority = selectedPriority,
            dueDate = selectedDueDate,
            apiaryId = selectedApiaryId,
            hiveId = selectedHiveId,
            currentStatus = com.example.beesmart.network.models.TaskStatus.Pending
        )
    }
    val attemptBack = {
        if (isDirty) showUnsavedDialog = true else onNavigateBack()
    }

    // Intercept the system back button when there are unsaved changes.
    BackHandler(enabled = isDirty) { showUnsavedDialog = true }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            canSave = canSave,
            onSave = {
                showUnsavedDialog = false
                saveTask()
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
                        if (isEditMode) stringResource(R.string.sx_tte_edit_task) else stringResource(R.string.sx_tte_new_task),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sx_tte_back))
                    }
                },
                colors = beeSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.sx_tte_title_label)) },
                enabled = !isLoading,
                leadingIcon = {
                    Icon(Icons.Default.Title, contentDescription = null)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrownPrimary,
                    focusedLabelColor = BrownPrimary,
                    focusedLeadingIconColor = BrownPrimary,
                    cursorColor = BrownPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.sx_tte_description_label)) },
                enabled = !isLoading,
                minLines = 3,
                maxLines = 5,
                leadingIcon = {
                    Icon(Icons.Default.Description, contentDescription = null)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrownPrimary,
                    focusedLabelColor = BrownPrimary,
                    focusedLeadingIconColor = BrownPrimary,
                    cursorColor = BrownPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Priority dropdown
            ExposedDropdownMenuBox(
                expanded = showPriorityDropdown,
                onExpandedChange = { showPriorityDropdown = !isLoading && it }
            ) {
                OutlinedTextField(
                    value = getPriorityText(selectedPriority),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.sx_tte_priority_label)) },
                    enabled = !isLoading,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPriorityDropdown)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrownPrimary,
                        focusedLabelColor = BrownPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = showPriorityDropdown,
                    onDismissRequest = { showPriorityDropdown = false }
                ) {
                    TaskPriority.values().forEach { priority ->
                        DropdownMenuItem(
                            text = { Text(getPriorityText(priority)) },
                            onClick = {
                                selectedPriority = priority
                                showPriorityDropdown = false
                            },
                            leadingIcon = {
                                if (selectedPriority == priority) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = BrownPrimary)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Due date
            OutlinedTextField(
                value = selectedDueDate?.format(
                    DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
                ) ?: "",
                onValueChange = {},
                label = { Text(stringResource(R.string.sx_tte_due_date_label)) },
                enabled = !isLoading,
                readOnly = true,
                trailingIcon = {
                    if (selectedDueDate != null) {
                        IconButton(onClick = { selectedDueDate = null }) {
                            Icon(Icons.Default.Clear, stringResource(R.string.sx_tte_clear_date_cd))
                        }
                    } else {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.sx_tte_select_date_cd),
                            modifier = Modifier.clickable(enabled = !isLoading) {
                                val today = selectedDueDate ?: LocalDate.now()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        selectedDueDate = LocalDate.of(year, month + 1, dayOfMonth)
                                    },
                                    today.year,
                                    today.monthValue - 1,
                                    today.dayOfMonth
                                ).show()
                            }
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrownPrimary,
                    focusedLabelColor = BrownPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isLoading) {
                        val today = selectedDueDate ?: LocalDate.now()
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                selectedDueDate = LocalDate.of(year, month + 1, dayOfMonth)
                            },
                            today.year,
                            today.monthValue - 1,
                            today.dayOfMonth
                        ).show()
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Apiary dropdown
            ExposedDropdownMenuBox(
                expanded = showApiaryDropdown,
                onExpandedChange = { showApiaryDropdown = !isLoading && it }
            ) {
                OutlinedTextField(
                    value = apiaries.find { it.id == selectedApiaryId }?.name ?: stringResource(R.string.sx_tte_apiary_none),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.sx_tte_apiary_label)) },
                    enabled = !isLoading,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showApiaryDropdown)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrownPrimary,
                        focusedLabelColor = BrownPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = showApiaryDropdown,
                    onDismissRequest = { showApiaryDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sx_tte_apiary_none)) },
                        onClick = {
                            selectedApiaryId = null
                            selectedHiveId = null
                            viewModel.clearHives()
                            showApiaryDropdown = false
                        }
                    )
                    apiaries.forEach { apiary ->
                        DropdownMenuItem(
                            text = { Text(apiary.name) },
                            onClick = {
                                selectedApiaryId = apiary.id
                                selectedHiveId = null
                                viewModel.loadHivesForApiary(apiary.id)
                                showApiaryDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hive dropdown (visible only if apiary selected and hives available)
            if (selectedApiaryId != null && hives.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = showHiveDropdown,
                    onExpandedChange = { showHiveDropdown = !isLoading && it }
                ) {
                    OutlinedTextField(
                        value = hives.find { it.id == selectedHiveId }?.name ?: stringResource(R.string.sx_tte_hive_none_specific),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.sx_tte_hive_label)) },
                        enabled = !isLoading,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showHiveDropdown)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrownPrimary,
                            focusedLabelColor = BrownPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = showHiveDropdown,
                        onDismissRequest = { showHiveDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sx_tte_hive_none_specific)) },
                            onClick = {
                                selectedHiveId = null
                                showHiveDropdown = false
                            }
                        )
                        hives.forEach { hive ->
                            DropdownMenuItem(
                                text = { Text(hive.name) },
                                onClick = {
                                    selectedHiveId = hive.id
                                    showHiveDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = { saveTask() },
                enabled = !isLoading && title.length >= 3,
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
                    Text(if (isEditMode) stringResource(R.string.sx_tte_save) else stringResource(R.string.sx_tte_create), fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun getPriorityText(priority: TaskPriority): String = when (priority) {
    TaskPriority.Low -> "Prioritate scăzută"
    TaskPriority.Normal -> "Prioritate normală"
    TaskPriority.High -> "Prioritate înaltă"
    TaskPriority.Critical -> "Prioritate critică"
}
