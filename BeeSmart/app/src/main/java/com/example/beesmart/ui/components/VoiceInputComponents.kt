package com.example.beesmart.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beesmart.ui.theme.YellowPrimary
import com.example.beesmart.utils.VoiceCommandManager
import com.example.beesmart.utils.VoiceResult
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

/**
 * Voice Input Button Component
 * Displays a microphone button that starts voice recognition when clicked
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceInputButton(
    onVoiceResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    prompt: String = "Spune comanda...",
    language: String = "ro-RO",
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceManager = remember { VoiceCommandManager(context) }

    var isListening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    // Audio permission state
    val audioPermissionState = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )

    // Collect voice results
    LaunchedEffect(Unit) {
        voiceManager.resultFlow.collect { result ->
            when (result) {
                is VoiceResult.Ready -> {
                    voiceStatus = "Ascult..."
                }
                is VoiceResult.Speaking -> {
                    voiceStatus = "Vorbește..."
                }
                is VoiceResult.Processing -> {
                    voiceStatus = "Procesez..."
                }
                is VoiceResult.Partial -> {
                    voiceStatus = result.text
                }
                is VoiceResult.Success -> {
                    isListening = false
                    showDialog = false
                    voiceStatus = ""
                    if (result.results.isNotEmpty()) {
                        onVoiceResult(result.results[0])
                    }
                    voiceManager.stopListening()
                }
                is VoiceResult.Error -> {
                    isListening = false
                    voiceStatus = result.message
                    voiceManager.stopListening()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.release()
        }
    }

    // Animated mic button
    val scale by animateFloatAsState(
        targetValue = if (isListening) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    IconButton(
        onClick = {
            when (audioPermissionState.status) {
                is PermissionStatus.Granted -> {
                    if (isListening) {
                        isListening = false
                        showDialog = false
                        voiceManager.stopListening()
                    } else {
                        isListening = true
                        showDialog = true
                        voiceStatus = ""
                        scope.launch {
                            voiceManager.startListening(language, prompt)
                        }
                    }
                }
                is PermissionStatus.Denied -> {
                    audioPermissionState.launchPermissionRequest()
                }
            }
        },
        enabled = enabled && voiceManager.isSpeechRecognitionAvailable(),
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isListening) "Oprește" else "Comandă vocală",
            tint = if (isListening) Color.Red else YellowPrimary,
            modifier = Modifier
                .size(24.dp)
                .scale(if (isListening) scale else 1f)
        )
    }

    // Voice status dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                isListening = false
                showDialog = false
                voiceManager.stopListening()
            },
            title = {
                Text(
                    text = "Comandă Vocală",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Animated microphone indicator
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                color = if (isListening) YellowPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isListening) YellowPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .scale(scale)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = voiceStatus.ifEmpty { prompt },
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        color = if (voiceStatus.startsWith("Eroare")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Exemplu: \"Nume stupină de test, locație București, descriere pentru testare\"",
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isListening = false
                        showDialog = false
                        voiceManager.stopListening()
                    }
                ) {
                    Text("Închide")
                }
            }
        )
    }
}

/**
 * Voice-enabled TextField
 * A text field with an integrated voice input button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = false,
    maxLines: Int = 1,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    voicePrompt: String = "Spune $label"
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            maxLines = maxLines,
            isError = isError,
            enabled = enabled,
            trailingIcon = {
                VoiceInputButton(
                    onVoiceResult = { result ->
                        onValueChange(result)
                    },
                    prompt = voicePrompt,
                    enabled = enabled
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = YellowPrimary,
                focusedLabelColor = YellowPrimary,
                cursorColor = YellowPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        AnimatedVisibility(
            visible = isError && errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Voice Form Filler Button
 * Fills multiple form fields at once using voice command
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceFormFillerButton(
    onFieldsFilled: (Map<String, String>) -> Unit,
    fields: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceManager = remember { VoiceCommandManager(context) }

    var isListening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    val audioPermissionState = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )

    // Collect voice results
    LaunchedEffect(Unit) {
        voiceManager.resultFlow.collect { result ->
            when (result) {
                is VoiceResult.Ready -> voiceStatus = "Ascult..."
                is VoiceResult.Speaking -> voiceStatus = "Vorbește..."
                is VoiceResult.Processing -> voiceStatus = "Procesez..."
                is VoiceResult.Partial -> voiceStatus = result.text
                is VoiceResult.Success -> {
                    isListening = false
                    showDialog = false
                    voiceStatus = ""
                    if (result.results.isNotEmpty()) {
                        val parsedFields = voiceManager.parseFormCommand(result.results[0], fields)
                        onFieldsFilled(parsedFields)
                    }
                    voiceManager.stopListening()
                }
                is VoiceResult.Error -> {
                    isListening = false
                    voiceStatus = result.message
                    voiceManager.stopListening()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.release()
        }
    }

    Button(
        onClick = {
            when (audioPermissionState.status) {
                is PermissionStatus.Granted -> {
                    isListening = true
                    showDialog = true
                    voiceStatus = ""
                    scope.launch {
                        voiceManager.startListening(
                            prompt = "Dictează toate informațiile..."
                        )
                    }
                }
                is PermissionStatus.Denied -> {
                    audioPermissionState.launchPermissionRequest()
                }
            }
        },
        enabled = enabled && voiceManager.isSpeechRecognitionAvailable(),
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = YellowPrimary
        )
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Completare Vocală")
    }

    // Voice status dialog
    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                isListening = false
                showDialog = false
                voiceManager.stopListening()
            },
            title = {
                Text("Completare Vocală Formular", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                color = YellowPrimary.copy(alpha = 0.2f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = YellowPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = voiceStatus.ifEmpty { "Dictează toate câmpurile..." },
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isListening = false
                        showDialog = false
                        voiceManager.stopListening()
                    }
                ) {
                    Text("Închide")
                }
            }
        )
    }
}
