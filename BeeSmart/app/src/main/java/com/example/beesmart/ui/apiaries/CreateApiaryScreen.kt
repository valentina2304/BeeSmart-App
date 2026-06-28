package com.example.beesmart.ui.apiaries

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.beesmart.R
import com.example.beesmart.ui.components.UnsavedChangesDialog
import com.example.beesmart.ui.components.VoiceFormFillerButton
import com.example.beesmart.ui.components.VoiceTextField
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateApiaryScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateApiaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val validationState by viewModel.validationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var isFinishing by remember { mutableStateOf(false) }

    var baseName by remember { mutableStateOf("") }
    var baseDescription by remember { mutableStateOf("") }
    var baseLocation by remember { mutableStateOf("") }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val isEditMode = viewModel.isEditMode
    val isLoading = uiState is CreateApiaryUiState.Loading
    val isInteractionLocked = isLoading || isFinishing

    // Load data when in edit mode
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is CreateApiaryUiState.LoadedData -> {
                if (name.isEmpty()) {
                    name = state.name
                    description = state.description ?: ""
                    location = state.location ?: ""
                    baseName = state.name
                    baseDescription = state.description ?: ""
                    baseLocation = state.location ?: ""
                }
            }
            is CreateApiaryUiState.Success -> {
                if (!isFinishing) {
                    isFinishing = true
                    viewModel.resetState()
                    onNavigateBack()
                }
            }
            is CreateApiaryUiState.Error -> {
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
        description != baseDescription ||
        location != baseLocation
    val canSave = !isInteractionLocked && validationState.isValid && name.trim().isNotEmpty()

    val saveApiary = {
        if (!isInteractionLocked) {
            viewModel.saveApiary(
                name = name.trim(),
                description = description.trim().takeIf { it.isNotEmpty() },
                location = location.trim().takeIf { it.isNotEmpty() }
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
                saveApiary()
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
                        if (isEditMode) stringResource(R.string.sx_aph_create_edit_title) else stringResource(R.string.sx_aph_create_new_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.sx_aph_create_back))
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
                                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = if (isEditMode) stringResource(R.string.sx_aph_create_edit_details) else stringResource(R.string.sx_aph_create_new_details),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.sx_aph_create_fill_info),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Voice Form Filler Button
                        VoiceFormFillerButton(
                            onFieldsFilled = { fields ->
                                fields["name"]?.let { name = it }
                                fields["description"]?.let { description = it }
                                fields["location"]?.let { location = it }
                            },
                            fields = listOf("name", "description", "location"),
                            enabled = !isInteractionLocked,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Apiary Name (Required) with Voice
                        VoiceTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                viewModel.validateName(it)
                            },
                            label = stringResource(R.string.sx_aph_create_name_label),
                            placeholder = stringResource(R.string.sx_aph_create_name_placeholder),
                            voicePrompt = stringResource(R.string.sx_aph_create_name_voice_prompt),
                            isError = validationState.nameError != null,
                            errorMessage = validationState.nameError,
                            enabled = !isInteractionLocked,
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Description (Optional) with Voice
                        VoiceTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = stringResource(R.string.sx_aph_create_desc_label),
                            placeholder = stringResource(R.string.sx_aph_create_desc_placeholder),
                            voicePrompt = stringResource(R.string.sx_aph_create_desc_voice_prompt),
                            enabled = !isInteractionLocked,
                            singleLine = false,
                            maxLines = 5
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Location (Optional) with Voice
                        VoiceTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = stringResource(R.string.sx_aph_create_location_label),
                            placeholder = stringResource(R.string.sx_aph_create_location_placeholder),
                            voicePrompt = stringResource(R.string.sx_aph_create_location_voice_prompt),
                            enabled = !isInteractionLocked,
                            singleLine = true
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
                        Text(stringResource(R.string.sx_aph_create_cancel), maxLines = 1, softWrap = false)
                    }

                    // Save Button
                    Button(
                        onClick = { saveApiary() },
                        modifier = Modifier.weight(1f),
                        enabled = !isInteractionLocked && validationState.isValid && name.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowPrimary,
                            contentColor = BrownPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isEditMode) stringResource(R.string.sx_aph_create_save) else stringResource(R.string.sx_aph_create_create))
                        }
                    }
                }

                // Info Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = YellowPrimary.copy(alpha = 0.1f)
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
                            tint = YellowPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.sx_aph_create_info),
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
                                text = if (isEditMode) stringResource(R.string.sx_aph_create_updating) else stringResource(R.string.sx_aph_create_creating),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
