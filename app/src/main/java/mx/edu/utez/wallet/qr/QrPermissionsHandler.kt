package mx.edu.utez.wallet.sensors.qr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

object QrPermissionsHandler {
    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

/** Devuelve una función que pide el permiso de cámara (o llama a [onGranted] si ya está concedido). */
@Composable
fun rememberCameraPermissionRequester(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latestGranted by rememberUpdatedState(onGranted)
    val latestDenied by rememberUpdatedState(onDenied)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) latestGranted() else latestDenied()
    }
    return remember(launcher, context) {
        {
            if (QrPermissionsHandler.hasCameraPermission(context)) latestGranted()
            else launcher.launch(Manifest.permission.CAMERA)
        }
    }
}
