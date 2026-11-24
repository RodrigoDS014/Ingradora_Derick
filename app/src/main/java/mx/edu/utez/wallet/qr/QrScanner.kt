
package mx.edu.utez.wallet.sensors.qr

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.BarcodeFormat
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cámara CameraX con análisis QR mediante ZXing.
 *
 * El ImageProxy siempre se cierra en finally.
 * El analizador se ejecuta en un executor independiente.
 */
@Composable
fun QrScanner(
    onCode: (String) -> Unit,
    dismiss: () -> Unit
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(context)

        val cameraExecutor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val closed = AtomicBoolean(false)
        val delivered = AtomicBoolean(false)

        val preview = Preview.Builder()
            .build()
            .also {
                it.surfaceProvider = previewView.surfaceProvider
            }

        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.QR_CODE
            ),
            DecodeHintType.TRY_HARDER to true
        )

        val reader = MultiFormatReader().apply {
            setHints(hints)
        }

        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(
                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            )
            .build()

        analysis.setAnalyzer(cameraExecutor) { imageProxy ->

            try {

                if (closed.get() || delivered.get()) {
                    return@setAnalyzer
                }

                val plane = imageProxy.planes.firstOrNull()
                    ?: return@setAnalyzer

                val width = imageProxy.width
                val height = imageProxy.height

                val buffer = plane.buffer.duplicate()
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride

                val yData = ByteArray(width * height)

                for (y in 0 until height) {

                    val rowStart = y * rowStride

                    for (x in 0 until width) {

                        val index = rowStart + x * pixelStride

                        yData[y * width + x] =
                            buffer.get(index)
                    }
                }

                val source = PlanarYUVLuminanceSource(
                    yData,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    false
                )

                fun decode(
                    luminanceSource: com.google.zxing.LuminanceSource
                ): String? {

                    return try {

                        val bitmap = BinaryBitmap(
                            HybridBinarizer(luminanceSource)
                        )

                        reader.decode(bitmap).text

                    } catch (_: Exception) {

                        null

                    } finally {

                        reader.reset()
                    }
                }

                var result = decode(source)

                if (result == null &&
                    imageProxy.imageInfo.rotationDegrees == 90
                ) {
                    result = decode(
                        source.rotateCounterClockwise()
                    )
                }

                if (result == null &&
                    imageProxy.imageInfo.rotationDegrees == 180
                ) {
                    result = decode(
                        source.rotateCounterClockwise()
                            .rotateCounterClockwise()
                    )
                }

                if (result == null &&
                    imageProxy.imageInfo.rotationDegrees == 270
                ) {
                    result = decode(
                        source.rotateCounterClockwise()
                            .rotateCounterClockwise()
                            .rotateCounterClockwise()
                    )
                }

                if (!result.isNullOrBlank() &&
                    delivered.compareAndSet(false, true)
                ) {

                    mainExecutor.execute {
                        if (!closed.get()) {
                            onCode(result)
                        }
                    }
                }

            } catch (_: Exception) {

                // Los fotogramas inválidos se descartan.

            } finally {

                imageProxy.close()
            }
        }

        cameraProviderFuture.addListener({

            if (!closed.get()) {

                try {

                    val cameraProvider =
                        cameraProviderFuture.get()

                    cameraProvider.unbindAll()

                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )

                } catch (_: Exception) {

                    // Cámara no disponible o vinculación fallida.
                }
            }

        }, mainExecutor)

        onDispose {

            closed.set(true)

            analysis.clearAnalyzer()

            if (cameraProviderFuture.isDone) {

                runCatching {
                    cameraProviderFuture.get().unbind(
                        preview,
                        analysis
                    )
                }
            }

            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text = "Coloca el código QR frente a la cámara",
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(onClick = dismiss) {
                Text("Cancelar")
            }
        }
    }
}
