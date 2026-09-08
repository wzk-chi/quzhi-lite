package com.quzhi.lite.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScanner(
    onCodeDetected: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = context as androidx.lifecycle.LifecycleOwner
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val scannerOptions = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(scannerOptions) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val detected = remember { AtomicBoolean(false) }
    val disposed = remember { AtomicBoolean(false) }
    val latestOnCodeDetected by rememberUpdatedState(onCodeDetected)
    val latestOnError by rememberUpdatedState(onError)

    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        val listener = Runnable {
            if (disposed.get()) {
                return@Runnable
            }
            try {
                cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || detected.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees,
                    )
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes.asSequence()
                                .mapNotNull { it.rawValue?.trim() }
                                .firstOrNull { it.isNotBlank() }
                            if (value != null && detected.compareAndSet(false, true)) {
                                mainExecutor.execute { latestOnCodeDetected(value) }
                            }
                        }
                        .addOnFailureListener { error ->
                            if (!detected.get()) {
                                mainExecutor.execute {
                                    latestOnError(error.message ?: "二维码识别失败")
                                }
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
            } catch (error: Exception) {
                mainExecutor.execute {
                    latestOnError(error.message ?: "相机启动失败")
                }
            }
        }
        providerFuture.addListener(listener, mainExecutor)

        onDispose {
            disposed.set(true)
            cameraProvider?.unbindAll()
            scanner.close()
            analyzerExecutor.shutdown()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
        )
    }
}
