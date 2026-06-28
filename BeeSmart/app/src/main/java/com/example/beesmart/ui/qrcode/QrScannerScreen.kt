package com.example.beesmart.ui.qrcode

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.beesmart.BuildConfig
import com.example.beesmart.R
import com.example.beesmart.ui.components.beeSmartTopAppBarColors
import com.example.beesmart.ui.theme.BrownPrimary
import com.example.beesmart.ui.theme.YellowPrimary
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun QrScannerScreen(
    onNavigateBack: () -> Unit,
    onHiveDetected: (String) -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var scanLocked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (cameraPermissionState.status !is PermissionStatus.Granted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.sx_misc_qr_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.sx_misc_qr_back))
                    }
                },
                colors = beeSmartTopAppBarColors()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val status = cameraPermissionState.status) {
            PermissionStatus.Granted -> {
                ScannerContent(
                    padding = padding,
                    onScanResult = { rawValue ->
                        if (scanLocked) return@ScannerContent
                        val hiveId = extractHiveId(rawValue)
                        if (hiveId != null) {
                            scanLocked = true
                            onHiveDetected(hiveId)
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Codul nu aparține BeeSmart")
                            }
                        }
                    },
                    onScanError = { message ->
                        scope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                )
            }
            is PermissionStatus.Denied -> {
                PermissionFallback(
                    padding = padding,
                    shouldShowRationale = status.shouldShowRationale,
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
        }
    }
}

@Composable
private fun PermissionFallback(
    padding: PaddingValues,
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painter = painterResource(id = R.drawable.ic_qr), contentDescription = null, tint = YellowPrimary)
        Text(
            text = if (shouldShowRationale) {
                stringResource(R.string.sx_misc_qr_permission_rationale)
            } else {
                stringResource(R.string.sx_misc_qr_permission_request)
            },
            modifier = Modifier.padding(vertical = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRequestPermission,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = YellowPrimary,
                contentColor = BrownPrimary
            )
        ) {
            Text(stringResource(R.string.sx_misc_qr_allow_camera))
        }
    }
}

@Composable
private fun ScannerContent(
    padding: PaddingValues,
    onScanResult: (String) -> Unit,
    onScanError: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color(0xFF000000))
    ) {
        ScannerPreview(
            modifier = Modifier.fillMaxSize(),
            onQrDetected = onScanResult,
            onError = onScanError
        )

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sx_misc_qr_aim_hint), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.sx_misc_qr_auto_open_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@SuppressLint("MissingPermission", "UnsafeOptInUsageError")
@Composable
private fun ScannerPreview(
    modifier: Modifier,
    onQrDetected: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val currentOnDetected by rememberUpdatedState(onQrDetected)
    val currentOnError by rememberUpdatedState(onError)

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.unbind()
        }
    }

    DisposableEffect(Unit) {
        val analyzer = ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return@Analyzer
            }

            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    val rawValue = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                    if (rawValue != null) {
                        currentOnDetected(rawValue)
                    }
                }
                .addOnFailureListener { error ->
                    currentOnError(error.localizedMessage ?: "Scanare QR eșuată")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }

        cameraController.setImageAnalysisAnalyzer(executor, analyzer)

        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            barcodeScanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                controller = cameraController
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
    )
}

internal fun extractHiveId(raw: String): String? {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("http", ignoreCase = true) || trimmed.startsWith("beesmart", ignoreCase = true) -> {
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
            when {
                uri.scheme.equals(BuildConfig.CUSTOM_SCHEME, ignoreCase = true) &&
                    uri.host.equals("hive", ignoreCase = true) -> uri.lastPathSegment
                uri.scheme.equals(BuildConfig.DEEP_LINK_SCHEME, ignoreCase = true) &&
                    uri.host.equals(BuildConfig.DEEP_LINK_HOST, ignoreCase = true) -> uri.pathSegments.getOrNull(1)
                else -> null
            }
        }
        trimmed.contains("hive/", ignoreCase = true) -> trimmed.substringAfter("hive/")
        else -> null
    }
}
