package com.vigia.feature.pairing

import android.Manifest
import android.app.Activity
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.Executors

/**
 * One-time pairing screen (design spec §5).
 *
 * Shows a live camera preview and runs ML Kit QR analysis. When a valid
 * vigia:// QR code is detected, requests camera permission first if needed,
 * then drives CompanionDeviceManager pairing via the ViewModel.
 *
 * @param onPairingComplete Called when [PairingState.Success] is emitted — navigate away.
 */
@Composable
fun PairingScreen(
    onPairingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ActivityResult launcher for CDM chooser intent.
    val cdmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Extract association ID from CDM result.
            val associationId = result.data
                ?.getIntExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, -1) ?: -1
            viewModel.onCdmResultReceived(associationId)
        } else {
            viewModel.onCdmResultCancelled()
        }
    }

    // Camera permission launcher.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> /* Handled by next recomposition — PreviewView only mounts when permission is granted */ }

    LaunchedEffect(Unit) {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Launch CDM chooser when ViewModel has an IntentSender ready.
    LaunchedEffect(state) {
        if (state is PairingState.AwaitingCdmLaunch) {
            val s = state as PairingState.AwaitingCdmLaunch
            cdmLauncher.launch(IntentSenderRequest.Builder(s.intentSender).build())
        }
        if (state is PairingState.Success) {
            onPairingComplete()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is PairingState.Scanning,
            is PairingState.AwaitingCdmLaunch -> CameraQrScanner(
                onQrDetected = viewModel::onQrDetected,
            )

            is PairingState.PairingRejected -> CenteredMessage(
                title = "Pairing cancelled",
                body  = "You can try again by scanning the QR code on your VIGIA device.",
                actionLabel = "Scan again",
                onAction = viewModel::retryScanning,
            )

            is PairingState.Error -> CenteredMessage(
                title = "Pairing error",
                body  = s.message,
                actionLabel = "Try again",
                onAction = viewModel::retryScanning,
            )

            is PairingState.DeviceAlreadyClaimed -> {
                val title: String
                val body: String
                if (s.reason == "wallet_taken") {
                    title = "Account already linked"
                    body  = "Your account is already linked to a different VIGIA unit. " +
                            "Unlink it from Settings before pairing a new device."
                } else {
                    title = "Device already claimed"
                    body  = "This unit (${s.deviceId.ifBlank { "unknown" }}) is linked to " +
                            "another account. If this is your device, contact support."
                }
                CenteredMessage(
                    title = title,
                    body  = body,
                    actionLabel = "Scan a different device",
                    onAction = viewModel::retryScanning,
                )
            }

            is PairingState.Success -> CenteredMessage(
                title = "Paired!",
                body  = "Connected to ${s.deviceId.ifBlank { "VIGIA Blackbox" }}. Auto-connect is now active.",
                actionLabel = "Continue",
                onAction = onPairingComplete,
            )
        }

        // Scanning overlay: dim corners, bright center window.
        if (state is PairingState.Scanning) {
            ScanOverlay()
        }

        if (state is PairingState.AwaitingCdmLaunch) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CameraQrScanner(onQrDetected: (String) -> Unit) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzer      = remember { QrAnalyzer(onQrDetected) }
    val executor      = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraFuture = ProcessCameraProvider.getInstance(ctx)
            cameraFuture.addListener({
                val provider = cameraFuture.get()
                val preview  = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) { /* Camera unavailable in emulator */ }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ScanOverlay() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Transparent)
            // In production: draw corner bracket SVG overlays here for visual guidance.
        )
        Text(
            text = "Point at the QR code\non your VIGIA device",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAction) { Text(actionLabel) }
        Spacer(Modifier.weight(1f))
    }
}
