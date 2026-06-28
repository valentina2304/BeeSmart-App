package com.example.beesmart.ui.test

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beesmart.ui.components.VoiceFormFillerButton
import com.example.beesmart.ui.components.VoiceTextField
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.YellowPrimary
import com.example.beesmart.utils.VoiceCommandManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Test screen for the voice command feature.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun VoiceCommandTestScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val voiceManager = remember { VoiceCommandManager(context) }
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    var testName by remember { mutableStateOf("") }
    var testDescription by remember { mutableStateOf("") }
    var testLocation by remember { mutableStateOf("") }
    var testLog by remember { mutableStateOf("") }

    val isSpeechAvailable = remember { voiceManager.isSpeechRecognitionAvailable() }
    val isPermissionGranted = audioPermissionState.status.isGranted

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test comenzi vocale", fontWeight = FontWeight.Bold) },
                colors = beeSmartTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSpeechAvailable && isPermissionGranted)
                        Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Stare sistem",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSpeechAvailable) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isSpeechAvailable) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Recunoaștere vocală: ${if (isSpeechAvailable) "Disponibilă ✓" else "Indisponibilă ✗"}")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isPermissionGranted) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Permisiune microfon: ${if (isPermissionGranted) "Acordată ✓" else "Neacordată ✗"}")
                    }

                    if (!isPermissionGranted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { audioPermissionState.launchPermissionRequest() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Solicită permisiunea")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "Instrucțiuni de test",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. Apasă pictograma microfon de pe orice câmp\n" +
                            "2. Vorbește clar: \"Nume test\"\n" +
                            "3. Sau folosește butonul „Completare vocală”\n" +
                            "4. Spune: \"Nume test stupină, locație București, descriere pentru testare\"",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            VoiceFormFillerButton(
                onFieldsFilled = { fields ->
                    fields["name"]?.let {
                        testName = it
                        testLog += "✓ Nume completat: $it\n"
                    }
                    fields["description"]?.let {
                        testDescription = it
                        testLog += "✓ Descriere completată: $it\n"
                    }
                    fields["location"]?.let {
                        testLocation = it
                        testLog += "✓ Locație completată: $it\n"
                    }
                },
                fields = listOf("name", "description", "location"),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            VoiceTextField(
                value = testName,
                onValueChange = {
                    testName = it
                    testLog += "Nume schimbat: $it\n"
                },
                label = "Nume test",
                placeholder = "Spune: Nume test",
                voicePrompt = "Spune numele testului"
            )

            Spacer(modifier = Modifier.height(16.dp))

            VoiceTextField(
                value = testDescription,
                onValueChange = {
                    testDescription = it
                    testLog += "Descriere schimbată: $it\n"
                },
                label = "Descriere test",
                placeholder = "Spune: Descriere test",
                voicePrompt = "Spune descrierea",
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            VoiceTextField(
                value = testLocation,
                onValueChange = {
                    testLocation = it
                    testLog += "Locație schimbată: $it\n"
                },
                label = "Locație test",
                placeholder = "Spune: Locație test",
                voicePrompt = "Spune locația"
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (testLog.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Jurnal test",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { testLog = "" }) {
                                Text("Curăță")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            testLog,
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    testName = ""
                    testDescription = ""
                    testLocation = ""
                    testLog = "Câmpuri curățate\n"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Curăță toate câmpurile")
            }
        }
    }
}
