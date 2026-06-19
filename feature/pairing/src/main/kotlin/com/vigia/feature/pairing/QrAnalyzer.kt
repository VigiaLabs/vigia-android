package com.vigia.feature.pairing

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX [ImageAnalysis.Analyzer] that runs ML Kit barcode scanning on each frame.
 *
 * Calls [onQrDetected] exactly once with the raw QR string value when a QR code is found.
 * The analyzer self-disables after the first successful detection to avoid double-callbacks.
 *
 * Wire format expected in QR: vigia://pair?mac=AA:BB:CC:DD:EE:FF&pk=<base64url>&id=vigia-001&v=1
 */
internal class QrAnalyzer(
    private val onQrDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile private var detected = false

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (detected) { imageProxy.close(); return }

        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull()?.rawValue
                    ?.takeIf { it.startsWith("vigia://pair") }
                    ?.let { qr ->
                        if (!detected) {
                            detected = true
                            onQrDetected(qr)
                        }
                    }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
