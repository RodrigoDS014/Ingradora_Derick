package mx.edu.utez.wallet.ui

import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable fun QrScanner(onCode: (String) -> Unit, dismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val latestCode by rememberUpdatedState(onCode)
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var error by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle, previewView) {
        val executor = Executors.newSingleThreadExecutor()
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val disposed = AtomicBoolean(false)
        val delivered = AtomicBoolean(false)
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var lastFrame = 0L
        analysis.setAnalyzer(executor) { frame ->
            try {
                val now = SystemClock.elapsedRealtime()
                if (!disposed.get() && !delivered.get() && now - lastFrame >= 150) {
                    lastFrame = now
                    val plane = frame.planes[0]
                    val buffer = plane.buffer.duplicate()
                    val luminance = ByteArray(frame.width * frame.height)
                    val origin = buffer.position()
                    for (row in 0 until frame.height) for (column in 0 until frame.width) {
                        luminance[row * frame.width + column] = buffer.get(origin + row * plane.rowStride + column * plane.pixelStride)
                    }
                    QrFrameDecoder.decode(luminance, frame.width, frame.height)?.let { value ->
                        if (delivered.compareAndSet(false, true)) mainExecutor.execute { if (!disposed.get()) latestCode(value) }
                    }
                }
            } catch (_: Exception) { /* A dropped or partial frame is retried on the next image. */ }
            finally { frame.close() }
        }
        future.addListener({
            if (!disposed.get()) try {
                camera = future.get().bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) { error = "No se pudo abrir la cámara. Puedes pegar el contenido del QR en la terminal." }
        }, mainExecutor)
        onDispose {
            disposed.set(true); analysis.clearAnalyzer()
            if (future.isDone) runCatching { future.get().unbind(preview, analysis) }
            executor.shutdown()
        }
    }
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.Center).size(260.dp).border(3.dp, Mint, RoundedCornerShape(24.dp)))
                Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth().background(Color.Black.copy(alpha = .6f)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Escanear código QR", style = MaterialTheme.typography.titleLarge)
                    Text("Centra el código. Después revisarás los datos y confirmarás la operación.", style = MaterialTheme.typography.bodyMedium)
                    error?.let { InfoBox(it, true) }
                }
                Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().background(Color.Black.copy(alpha = .6f)).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(dismiss, Modifier.weight(1f)) { Text("Cancelar") }
                    Button({ torch = !torch; camera?.cameraControl?.enableTorch(torch) }, Modifier.weight(1f), enabled = camera?.cameraInfo?.hasFlashUnit() == true) {
                        Text(if(torch) "Apagar luz" else "Linterna")
                    }
                }
            }
        }
    }
}
